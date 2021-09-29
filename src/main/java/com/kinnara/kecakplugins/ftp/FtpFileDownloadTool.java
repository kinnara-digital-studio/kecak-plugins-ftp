package com.kinnara.kecakplugins.ftp;

import com.kinnara.kecakplugins.ftp.common.externalstorage.ExternalStorageDownloadTool;
import com.kinnara.kecakplugins.ftp.common.externalstorage.ExternalStorageException;
import com.kinnara.kecakplugins.ftp.common.externalstorage.ExternalStorageUtil;
import com.kinnara.kecakplugins.ftp.common.ftp.FtpClient;
import com.kinnara.kecakplugins.ftp.common.ftp.KecakFtpException;
import org.joget.apps.app.dao.FormDefinitionDao;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.FormDefinition;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.Form;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.service.FormService;
import org.joget.apps.form.service.FormUtil;
import org.joget.commons.util.FileManager;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.UuidGenerator;
import org.joget.plugin.base.Plugin;
import org.joget.plugin.base.PluginManager;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.model.WorkflowProcessLink;
import org.joget.workflow.model.service.WorkflowManager;
import org.springframework.context.ApplicationContext;

import java.io.*;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class FtpFileDownloadTool extends ExternalStorageDownloadTool<FtpClient> {

    @Override
    protected void execute(FtpClient client) throws ExternalStorageException {
        final File tempOutputFile = getTemporaryDownloadFile();
        try (OutputStream fos = new FileOutputStream(tempOutputFile);
             OutputStream bos = new BufferedOutputStream(fos)) {

            String remoteFile = getRemoteFile();

            LogUtil.info(getClassName(), "Retrieving file from remote [" + remoteFile + "] to local [" + tempOutputFile.getAbsolutePath() + "]");
            client.retrieveFile(remoteFile, bos);

            Optional<WorkflowAssignment> workflowAssignment = Optional.ofNullable((WorkflowAssignment) getProperties().get("workflowAssignment"));
            FormData formData = ExternalStorageUtil.getAssignmentFormData(workflowAssignment);

            Form form = getForm();
            Element elementFileUpload = FormUtil.findElement(getFileUploadField(), form, formData, true);

            LogUtil.info(getClassName(), "Storing file [" + tempOutputFile.getAbsolutePath() + "] to form [" + form.getPropertyString("id") + "] element ["+elementFileUpload.getPropertyString("id")+"]");
            ExternalStorageUtil.storeFileInFileUpload(elementFileUpload, tempOutputFile, formData);

        } catch (IOException e) {
            throw new ExternalStorageException(e);
        }
    }

    @Override
    protected FtpClient generateClient(Plugin plugin) throws ExternalStorageException {
        try {
            return new FtpClient(getHostname(), getUsername(), getPassword());
        } catch (IOException e) {
            throw new ExternalStorageException(e);
        }
    }

    @Override
    public String getName() {
        return getLabel() + getVersion();
    }

    @Override
    public String getVersion() {
        return getClass().getPackage().getImplementationVersion();
    }

    @Override
    public String getDescription() {
        return getClass().getPackage().getImplementationTitle();
    }

    @Override
    public String getLabel() {
        return "FTP File Download Tool";
    }

    @Override
    public String getClassName() {
        return getClass().getName();
    }

    @Override
    public String getPropertyOptions() {
        return "";
    }

    protected File getTemporaryDownloadFile() throws ExternalStorageException {
        Map properties = getProperties();
        WorkflowAssignment workflowAssignment = (WorkflowAssignment) properties.get("workflowAssignment");
        String id = UuidGenerator.getInstance().getUuid();
        String path = id + File.separator;

        String filename = path + getRemoteFile().replaceAll(".+/", "");
        File file = new File(FileManager.getBaseDirectory(), filename);
        if (!file.isDirectory()) {
            // create temp file directory
            new File(FileManager.getBaseDirectory(), path).mkdirs();
            return file;
        }

        throw new ExternalStorageException("Cannot read file [" + file.getAbsolutePath() + "]");
    }

    /**
     * Determine primary key
     *
     * @return
     */
    protected final String getPrimaryKey() {
        return Optional.of("recordId")
                .map(getProperties()::get)
                .map(String::valueOf)
                .filter(s -> !s.isEmpty())

                // or get from workflow assignment, originProcessId
                .orElseGet(() -> {
                    final PluginManager pluginManager = (PluginManager) getProperties().get("pluginManager");
                    final WorkflowManager workflowManager = (WorkflowManager) pluginManager.getBean("workflowManager");
                    final WorkflowAssignment workflowAssignment = (WorkflowAssignment) getProperties().get("workflowAssignment");

                    return Optional.ofNullable(workflowAssignment)
                            .map(WorkflowAssignment::getProcessId)
                            .map(workflowManager::getWorkflowProcessLink)
                            .map(WorkflowProcessLink::getOriginProcessId)
                            .filter(s -> !s.isEmpty())

                            // or get from workflow assignment's process ID
                            .orElseGet(() -> Optional.ofNullable(workflowAssignment)
                                    .map(WorkflowAssignment::getProcessId)
                                    .filter(s -> !s.isEmpty())

                                    // desperately use UUID
                                    .orElseGet(() -> {
                                        String uuid = UUID.randomUUID().toString();
                                        LogUtil.warn(getClassName(), "getPrimaryKey : using UUID [" + uuid + "] as primary key");
                                        return uuid;
                                    }));
                });
    }

    protected String getHostname() {
        return String.valueOf(getProperties().get("host"));
    }

    protected String getUsername() {
        return String.valueOf(getProperties().get("username"));
    }

    protected String getPassword() {
        return String.valueOf(getProperties().get("password"));
    }

    protected String getRemoteFile() {
        return String.valueOf(getProperties().get("remoteFile"));
    }

    protected Form getForm() throws KecakFtpException {
        String formDefId = String.valueOf(getProperties().get("formDefId"));
        return generateForm(formDefId);
    }

    protected String getFileUploadField() {
        return String.valueOf(getProperties().get("fileUploadField"));
    }

    public final Form generateForm(String formDefId) throws KecakFtpException {
        AppDefinition appDefinition = AppUtil.getCurrentAppDefinition();
        ApplicationContext appContext = AppUtil.getApplicationContext();
        FormService formService = (FormService) appContext.getBean("formService");
        FormDefinitionDao formDefinitionDao = (FormDefinitionDao)appContext.getBean("formDefinitionDao");

        if(appDefinition == null) {
            throw new KecakFtpException("Application definition is not available");
        }

        Form form = Optional.ofNullable(formDefId)
                .map(s -> formDefinitionDao.loadById(s, appDefinition))
                .map(FormDefinition::getJson)
                .map(formService::createElementFromJson)
                .map(e -> (Form) e)
                .orElseThrow(() -> new KecakFtpException("Error generating form [" + formDefId + "]"));

        return form;
    }
}
