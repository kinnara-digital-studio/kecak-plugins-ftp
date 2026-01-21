package com.kinnara.kecakplugins.ftp;

import com.jcraft.jsch.JSchException;
import com.kinnara.kecakplugins.ftp.common.externalstorage.ExternalStorageDownloadTool;
import com.kinnara.kecakplugins.ftp.common.externalstorage.ExternalStorageException;
import com.kinnara.kecakplugins.ftp.common.externalstorage.ExternalStorageFileElement;
import com.kinnara.kecakplugins.ftp.common.externalstorage.ExternalStorageUtil;
import com.kinnara.kecakplugins.ftp.common.sftp.KecakSftpException;
import com.kinnara.kecakplugins.ftp.common.sftp.SftpClient;
import com.kinnara.kecakplugins.ftp.common.sftp.Utils;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.Form;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.service.FormUtil;
import org.joget.commons.util.FileManager;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.UuidGenerator;
import org.joget.plugin.base.Plugin;
import org.joget.plugin.base.PluginManager;
import org.joget.plugin.property.model.PropertyEditable;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.model.service.WorkflowManager;

import java.io.File;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;

public class SftpFileDownloadTool extends ExternalStorageDownloadTool<SftpClient> implements Utils {
    @Override
    protected void execute(SftpClient client) throws ExternalStorageException {
        Map<String, Object> properties = getProperties();
        WorkflowManager workflowManager = (WorkflowManager) AppUtil.getApplicationContext().getBean("workflowManager");
        Optional<WorkflowAssignment> workflowAssignment = Optional.ofNullable((WorkflowAssignment) properties.get("workflowAssignment"));
        String statusWorkflowVariable = getStatusWorkflowVariable(this);

        try {
            AppDefinition appDefinition = (AppDefinition) properties.get("appDef");
            if(appDefinition == null && (appDefinition = AppUtil.getCurrentAppDefinition()) == null) {
                throw new KecakSftpException("Property [appDef] is null");
            }

            final File tempOutputFile = getTemporaryDownloadFile();

            FormData formData = ExternalStorageUtil.getAssignmentFormData(workflowAssignment);
            Form form = getForm(appDefinition, getFormDefId(this), formData);
            Element elementFileUpload = FormUtil.findElement(getFileUploadField(this), form, formData, true);

            LogUtil.info(getClassName(), "Storing file [" + tempOutputFile.getAbsolutePath() + "] to form [" + form.getPropertyString("id") + "] element ["+elementFileUpload.getPropertyString("id")+"]");
            ExternalStorageUtil.storeFileInFileUpload(elementFileUpload, tempOutputFile, formData);

            if(!statusWorkflowVariable.isEmpty() && workflowAssignment.isPresent()) {
                workflowManager.processVariable(workflowAssignment.get().getProcessId(), statusWorkflowVariable, getStatusSucceed(this));
            }
        } catch (KecakSftpException e) {
            if(!statusWorkflowVariable.isEmpty() && workflowAssignment.isPresent()) {
                workflowManager.processVariable(workflowAssignment.get().getProcessId(), statusWorkflowVariable, getStatusFailed(this));
            }
            LogUtil.error(getClassName(), e, e.getMessage());
        }
    }

    @Override
    public SftpClient getClientInstance(Plugin plugin) throws ExternalStorageException {
        ExternalStorageFileElement<SftpClient> element = (ExternalStorageFileElement<SftpClient>) plugin;
        try {
//            return generateSftpChannel(getHost(element), getUsername(element), getPassword(element), getKnownHostsFile(element), true);
            return new SftpClient(getHost(element), getUsername(element), getPassword(element), getKnownHostsFile(element), true);
        } catch (JSchException e) {
            throw new ExternalStorageException(e);
        }
    }

    @Override
    public String getName() {
        return getLabel();
    }

    @Override
    public String getVersion() {
        PluginManager pluginManager = (PluginManager) AppUtil.getApplicationContext().getBean("pluginManager");
        ResourceBundle resourceBundle = pluginManager.getPluginMessageBundle(getClassName(), "/messages/BuildNumber");
        String buildNumber = resourceBundle.getString("buildNumber");
        return buildNumber;
    }

    @Override
    public String getDescription() {
        return getClass().getPackage().getImplementationTitle();
    }

    @Override
    public String getLabel() {
        return "SFTP File Download Tool";
    }

    @Override
    public String getClassName() {
        return getClass().getName();
    }

    @Override
    public String getPropertyOptions() {
        return null;
    }

    protected File getTemporaryDownloadFile() throws ExternalStorageException {
        Map properties = getProperties();
        WorkflowAssignment workflowAssignment = (WorkflowAssignment) properties.get("workflowAssignment");
        String id = UuidGenerator.getInstance().getUuid();
        String path = id + File.separator;

        String filename = path + getRemoteFile(this).replaceAll(".+/", "");
        File file = new File(FileManager.getBaseDirectory(), filename);
        if (!file.isDirectory()) {
            // create temp file directory
            new File(FileManager.getBaseDirectory(), path).mkdirs();
            return file;
        }

        throw new ExternalStorageException("Cannot read file [" + file.getAbsolutePath() + "]");
    }

    protected String getUsername(PropertyEditable prop) {
        return prop.getPropertyString("username");
    }

    protected String getPassword(PropertyEditable prop) {
        return prop.getPropertyString("password");
    }

    protected String getHost(PropertyEditable prop) {
        return prop.getPropertyString("host");
    }

    protected String getKnownHostsFile(PropertyEditable prop) {
        return Optional.of("knownHostsFile")
                .map(prop::getPropertyString)
                .orElse("~/.ssh/known_hosts");
    }

    public String getRemoteFile(PropertyEditable prop) {
        return prop.getPropertyString("remoteFile");
    }

    protected String getFormDefId(PropertyEditable prop) {
        return prop.getPropertyString("formDefId");
    }

    protected String getStatusWorkflowVariable(PropertyEditable prop) {
        return prop.getPropertyString("statusWorkflowVariable");
    }

    protected String getStatusSucceed(PropertyEditable prop) {
        return prop.getPropertyString("statusSucceed");
    }

    protected String getStatusFailed(PropertyEditable prop) {
        return prop.getPropertyString("statusFailed");
    }

    protected String getFileUploadField(PropertyEditable prop) {
        return prop.getPropertyString("fileUploadField");
    }
}
