package com.kinnara.kecakplugins.ftp.common.externalstorage;

import org.joget.apps.app.dao.FormDefinitionDao;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.FormDefinition;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.*;
import org.joget.apps.form.service.FormService;
import org.joget.apps.form.service.FormUtil;
import org.joget.commons.util.FileManager;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.Plugin;
import org.joget.plugin.base.PluginWebSupport;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.URLEncoder;
import java.util.*;

/**
 *
 * @param <T> External storage client
 */
public abstract class ExternalStorageFileElement<T extends AutoCloseable> extends Element implements FormBuilderPaletteElement, FileDownloadSecurity,PluginWebSupport {
    abstract protected T generateClient(Plugin plugin) throws ExternalStorageException;

    @Override
    public final String getFormBuilderTemplate() {
        return "<label class='label'>" + getLabel() + "</label><input type='file' />";
    }

    @Override
    public final String renderTemplate(FormData formData, Map dataModel) {
        String template = "ExternalStorageElement.ftl";

        // set value
        String[] values = FormUtil.getElementPropertyValues(this, formData);

        //check is there a stored value
        String storedValue = formData.getStoreBinderDataProperty(this);
        if (storedValue != null) {
            values = storedValue.split(";");
        }

        Map<String, String> filePaths = new HashMap<>();

        AppDefinition appDefinition = AppUtil.getCurrentAppDefinition();

        String formDefId = Optional.of(this)
                .map(FormUtil::findRootForm)
                .map(e -> e.getPropertyString(FormUtil.PROPERTY_ID)).orElse("");

        String elementId = getPropertyString(FormUtil.PROPERTY_ID);

        for (String value : values) {
            try {
                String fileName = URLEncoder.encode(value, "UTF8").replaceAll("\\+", "%20");
                String filePath = String.format("/web/json/app/%s/%d/plugin/%s/service?formDefId=%s&fieldId=%s&primaryKey=%s&fileName=%s", appDefinition.getAppId(), appDefinition.getVersion(), getClassName(), formDefId, elementId, getPrimaryKeyValue(formData), fileName);
                filePaths.put(filePath, value);
            } catch (UnsupportedEncodingException e) {
                LogUtil.error(getClassName(), e, e.getMessage());
            }
        }

        if(!filePaths.isEmpty())
            dataModel.put("filePaths", filePaths);

        String html = FormUtil.generateElementHtml(this, formData, template, dataModel);
        return html;
    }

    @Override
    public final FormRowSet formatData(FormData formData) {
        try(T client = generateClient(this)) {

            // get value
            String id = getPropertyString(FormUtil.PROPERTY_ID);
            if (id == null) {
                return null;
            }

            String[] values = FormUtil.getElementPropertyValues(this, formData);
            if (values == null || values.length == 0) {
                return null;
            }

            List<String> resultedValue = new ArrayList<>();
            for (String value : values) {
                // check if the file is in temp file
                File file = FileManager.getFileByPath(value);
                if (file != null) {
                    resultedValue.add(file.getName());

                    // store file
                    storeFile(client, file, this, formData);

                    FileManager.deleteFile(file);
                } else {
                    resultedValue.add(value);
                    LogUtil.warn(getClassName(), "File [" + value + "] not found in temp file");
                }
            }

            // formulate values
            String delimitedValue = FormUtil.generateElementPropertyValues(resultedValue.toArray(new String[]{}));
            String paramName = FormUtil.getElementParameterName(this);
            formData.addRequestParameterValues(paramName, resultedValue.toArray(new String[]{}));

            // set value into Properties and FormRowSet object
            FormRow result = new FormRow();
            result.setProperty(id, delimitedValue);
            FormRowSet rowSet = new FormRowSet();
            rowSet.add(result);

            return rowSet;
        } catch (Exception e) {
            LogUtil.error(getClassName(), e, e.getMessage());
        }

        return super.formatData(formData);
    }

    @Override
    public final void webService(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AppDefinition appDefinition = AppUtil.getCurrentAppDefinition();
        FormDefinitionDao formDefinitionDao = (FormDefinitionDao) AppUtil.getApplicationContext().getBean("formDefinitionDao");
        FormService formService = (FormService) AppUtil.getApplicationContext().getBean("formService");

        try {
            String formDefId = getRequiredParameter(request, "formDefId");
            String elementId = getRequiredParameter(request, "fieldId");
            String primaryKey = getRequiredParameter(request, "primaryKey");
            String fileName = getRequiredParameter(request, "fileName");
            String attachment = getOptionalParameter(request, "attachment", "false");

            FormDefinition formDef = formDefinitionDao.loadById(formDefId, appDefinition);
            if (formDef == null) {
                throw new RestApiException(HttpServletResponse.SC_BAD_REQUEST, "Form [" + formDefId + "] not found");
            }

            String json = formDef.getJson();
            Form form = (Form) formService.createElementFromJson(json);
            if (form == null) {
                throw new RestApiException(HttpServletResponse.SC_BAD_REQUEST, "Form [" + formDefId + "] is null");
            }

            if(form.getLoadBinder() == null) {
                throw new RestApiException(HttpServletResponse.SC_BAD_REQUEST, "Form [" + formDefId + "] does not have load binder");
            }

            FormData formData = new FormData();
            formData.setPrimaryKeyValue(primaryKey);

            FormRowSet rows = form.getLoadBinder().load(form, primaryKey, formData);
            if (rows != null && !rows.isEmpty()) {
                FormRow row = rows.get(0);
                for (Object fieldId : row.keySet()) {
                    String compareValue = fileName;
                    if (compareValue.endsWith(FileManager.THUMBNAIL_EXT)) {
                        compareValue = compareValue.replace(FileManager.THUMBNAIL_EXT, "");
                    }

                    String value = row.getProperty(fieldId.toString());

                    if (value.equals(compareValue)
                            || (value.contains(";")
                            && (value.startsWith(compareValue + ";")
                            || value.contains(";" + compareValue + ";")
                            || value.endsWith(";" + compareValue)))) {
                        Element field = FormUtil.findElement(fieldId.toString(), form, formData);
                        if (field instanceof FileDownloadSecurity) {
                            FileDownloadSecurity security = (FileDownloadSecurity) field;
                            if(!security.isDownloadAllowed(request.getParameterMap()))
                                throw new RestApiException(HttpServletResponse.SC_UNAUTHORIZED, "Not authorized");
                        }
                    }
                }
            }

            Element element = FormUtil.findElement(elementId, form, formData);
            try(T client = generateClient(element)) {
                // send file to response
                byte[] bbuf = new byte[65536];
                try(InputStream fileInputStream = loadFile(client, element, formData, fileName);
                    DataInputStream in = new DataInputStream(fileInputStream);
                    ServletOutputStream stream = response.getOutputStream()) {

                    String contentType = request.getSession().getServletContext().getMimeType(fileName);
                    if (contentType != null) {
                        response.setContentType(contentType);
                    }

                    // set attachment filename
                    if (Boolean.parseBoolean(attachment)) {
                        String name = URLEncoder.encode(fileName, "UTF8").replaceAll("\\+", "%20");
                        response.setHeader("Content-Disposition", "attachment; filename="+name+"; filename*=UTF-8''" + name);
                    }

                    // send output
                    int length = 0;
                    while ((length = in.read(bbuf)) != -1) {
                        stream.write(bbuf, 0, length);
                    }

                    stream.flush();
                }

            } catch (Exception e) {
                throw new RestApiException(HttpServletResponse.SC_BAD_REQUEST, e);
            }
        } catch (RestApiException e) {
            LogUtil.error(getClassName(), e, e.getMessage());
            response.sendError(e.getErrorCode(), e.getMessage());
        }
    }

    protected String getRequiredParameter(HttpServletRequest request, String parameterName) throws RestApiException {
        return Optional.of(parameterName)
                .map(request::getParameter)
                .filter(s -> !s.isEmpty())
                .orElseThrow(() -> new RestApiException(HttpServletResponse.SC_BAD_REQUEST, "Parameter ["+parameterName+"] is required"));
    }

    protected String getOptionalParameter(HttpServletRequest request, String parameterName, String defaultValue) {
        return Optional.of(parameterName)
                .map(request::getParameter)
                .filter(s -> !s.isEmpty())
                .orElse(defaultValue);
    }

    protected abstract InputStream loadFile(T client, Element element, FormData formData, String fileName) throws ExternalStorageException;

    protected abstract void storeFile(T client, File file, Element element, FormData formData) throws ExternalStorageException;
}
