package com.kinnara.kecakplugins.ftp;

import com.jcraft.jsch.JSchException;
import com.kinnara.kecakplugins.ftp.common.externalstorage.ExternalStorageDownloadTool;
import com.kinnara.kecakplugins.ftp.common.externalstorage.ExternalStorageException;
import com.kinnara.kecakplugins.ftp.common.sftp.KecakSftpException;
import com.kinnara.kecakplugins.ftp.common.sftp.SftpClient;
import com.kinnara.kecakplugins.ftp.common.sftp.CsvTool;
import com.kinnara.kecakplugins.ftp.common.sftp.Utils;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.Form;
import org.joget.apps.form.model.FormData;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.Plugin;
import org.joget.plugin.base.PluginManager;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.model.service.WorkflowManager;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author aristo
 *
 * Download CSV files from SFTP server to local
 *
 */
public class SftpSpreadsheetFileDownloadTool extends ExternalStorageDownloadTool<SftpClient> implements Utils, CsvTool {
    @Override
    protected void execute(SftpClient storageClient) {
        Map<String, Object> properties = getProperties();
        WorkflowManager workflowManager = (WorkflowManager) AppUtil.getApplicationContext().getBean("workflowManager");
        WorkflowAssignment workflowAssignment = (WorkflowAssignment) properties.get("workflowAssignment");
        String statusWorkflowVariable = getStatusWorkflowVariable();

        try(InputStream inputStream = loadFile(storageClient.getChannelSftp(), getFileName(properties))) {

            AppDefinition appDefinition = (AppDefinition) properties.get("appDef");
            if(appDefinition == null && (appDefinition = AppUtil.getCurrentAppDefinition()) == null) {
                throw new KecakSftpException("Property [appDef] is null");
            }

            Form form = getForm(appDefinition, getFormDefId(), new FormData());

            processCsvFile(this, inputStream, form, getSkipLines(), true, getCellMapping(properties), getDefaultValues(properties));

            if(!statusWorkflowVariable.isEmpty()) {
                workflowManager.processVariable(workflowAssignment.getProcessId(), statusWorkflowVariable, getStatusSucceed());
            }
        } catch (KecakSftpException | IOException e) {
            if(!statusWorkflowVariable.isEmpty()) {
                workflowManager.processVariable(workflowAssignment.getProcessId(), statusWorkflowVariable, getStatusFailed());
            }
            LogUtil.error(getClassName(), e, e.getMessage());
        }
    }

    @Override
    public SftpClient getClientInstance(Plugin plugin) throws ExternalStorageException {
        try {
            return new SftpClient(getHost(), getPort(), getUsername(), getPassword(), getKeyFile(), getKnownHostsFile(), isStrictHostKeyChecking());
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
        return "SFTP Spreadsheet Download Tool";
    }

    @Override
    public String getClassName() {
        return getClass().getName();
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClassName(), "/properties/SftpSpreadsheetFileDownloadTool.json", null, true, "/messages/Ftp");
    }

    protected int getPort() {
        try {
            return Integer.parseInt(getPropertyString("port"));
        } catch (NumberFormatException e) {
            return 22; // default SFTP port
        }
    }

    protected String getHost() {
        return getPropertyString("host");
    }

    protected String getPassword() {
        return getPropertyString("password");
    }

    protected String getKeyFile() {
        return getPropertyString("keyFile");
    }

    protected String getUsername() {
        return getPropertyString("username");
    }

    protected String getKnownHostsFile() {
        return getPropertyString("getKnownHostsFile");
    }

    protected String getFileName(Map properties) {
        return String.valueOf(properties.get("fileName"));
    }

    protected boolean isStrictHostKeyChecking() {
        return "true".equalsIgnoreCase(getPropertyString("strictHostKeyChecking"));
    }

    protected Map<String, String>[] getCellMapping(Map properties) {
        return Optional.ofNullable((Object[]) properties.get("fieldMapping"))
                .map(Arrays::stream)
                .orElseGet(Stream::empty)
                .map(o -> (Map<String, String>)o)
                .toArray(Map[]::new);
    }

    protected Map<String, String> getDefaultValues(Map properties) {
        return Optional.ofNullable((Object[]) properties.get("defaultValues"))
                .map(Arrays::stream)
                .orElseGet(Stream::empty)
                .map(o -> (Map<String, String>)o)
                .collect(Collectors.toMap(m -> m.get("field"), m -> m.get("value")));
    }

    protected String getFormDefId() {
        return getPropertyString("formDefId");
    }

    @Override
    public String getCsvDelimiter() {
        return getPropertyString("columnDelimiter");
    }

    protected int getSkipLines() {
        try {
            return Integer.parseInt(getPropertyString("skipLines"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    protected String getStatusWorkflowVariable() {
        return getPropertyString("statusWorkflowVariable");
    }

    protected String getStatusSucceed() {
        return getPropertyString("statusSucceed");
    }

    protected String getStatusFailed() {
        return getPropertyString("statusFailed");
    }

    protected boolean isDebug() {
        return "true".equalsIgnoreCase(getPropertyString("debug"));
    }
}
