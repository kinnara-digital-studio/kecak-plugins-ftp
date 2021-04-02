package com.kinnara.kecakplugins.ftp.common.externalstorage;

import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.lib.FileUpload;
import org.joget.apps.form.model.*;
import org.joget.apps.form.service.FormService;
import org.joget.apps.form.service.FormUtil;
import org.joget.commons.util.LogUtil;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.model.WorkflowProcessLink;
import org.joget.workflow.model.service.WorkflowManager;
import org.springframework.context.ApplicationContext;

import java.io.File;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class ExternalStorageUtil {
    public static FormData getAssignmentFormData(Optional<WorkflowAssignment> workflowAssignment) {
        WorkflowManager workflowManager = (WorkflowManager) AppUtil.getApplicationContext().getBean("workflowManager");

        FormData formData = new FormData();

        Optional<String> primaryKey = workflowAssignment
                .map(WorkflowAssignment::getProcessId)
                .map(workflowManager::getWorkflowProcessLink)
                .map(WorkflowProcessLink::getOriginProcessId)
                .filter(s -> !s.isEmpty());

        if(!primaryKey.isPresent()) {
            primaryKey = workflowAssignment
                    .map(WorkflowAssignment::getProcessId)
                    .filter(s -> !s.isEmpty());
        }

        primaryKey.ifPresent(formData::setPrimaryKeyValue);

        workflowAssignment.ifPresent(a -> {
            formData.setActivityId(a.getActivityId());
            formData.setProcessId(a.getProcessId());
        });

        return formData;
    }
    /**
     *
     * @param fileUpload
     * @param temporaryFile
     * @return
     * @throws ExternalStorageException
     */
    public static FormData storeFileInFileUpload(Element fileUpload, File temporaryFile, FormData formData) throws ExternalStorageException {
        if(!(fileUpload instanceof FileUpload)) {
            throw new ExternalStorageException("Element [" + fileUpload.getPropertyString("id") + "] is not FileUpload element");
        }

        ApplicationContext applicationContext = AppUtil.getApplicationContext();
        AppService appService = (AppService) applicationContext.getBean("appService");
        FormService formService = (FormService) applicationContext.getBean("formService");

        Form form = FormUtil.findRootForm(fileUpload);
        String fileElementParameterName = FormUtil.getElementParameterName(fileUpload);

        // get path in app_tempfile
        Pattern pattern = Pattern.compile("(?<=app_tempfile/).+");
        Matcher matcher = pattern.matcher(temporaryFile.getPath());
        String filename = Optional.of(matcher).filter(Matcher::find).map(Matcher::group).orElse("");

        // put file in request parameter
        formData.addRequestParameterValues(fileElementParameterName, new String[]{ filename });

        // execute load binder to keep current data
        formService.executeFormLoadBinders(form, formData);

        // get current data
        FormRowSet rowSet = formData.getLoadBinderData(form);

        // inject current data to request parameter
        Optional.ofNullable(rowSet)
                .map(Collection::stream)
                .orElseGet(Stream::empty)
                .findFirst()
                .map(FormRow::getCustomProperties)
                .map(m -> (Map<String, String>) m)
                .map(Map::entrySet)
                .map(Collection::stream)
                .orElseGet(Stream::empty)

                .forEach(e -> {
                    Element element = FormUtil.findElement(String.valueOf(e.getKey()), form, formData);
                    if (element != null) {
                        String elementParameterName = FormUtil.getElementParameterName(element);
                        formData.addRequestParameterValues(elementParameterName, new String[]{String.valueOf(e.getValue())});
                    }
                });

        // submit form
        return appService.submitForm(form, formData, false);
    }
}
