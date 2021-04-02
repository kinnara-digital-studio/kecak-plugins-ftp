package com.kinnara.kecakplugins.ftp;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSchException;
import com.kinnara.kecakplugins.ftp.common.externalstorage.ExternalStorageDownloadTool;
import com.kinnara.kecakplugins.ftp.common.externalstorage.ExternalStorageException;
import com.kinnara.kecakplugins.ftp.common.sftp.KecakSftpException;
import com.kinnara.kecakplugins.ftp.common.sftp.SftpTool;
import com.kinnara.kecakplugins.ftp.common.sftp.SftpUtils;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.Form;
import org.joget.apps.form.model.FormData;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.Plugin;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.model.service.WorkflowManager;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author aristo
 *
 * Download CSV files from SFTP server to local
 *
 */
public class SftpSpreadsheetFileDownloadTool extends ExternalStorageDownloadTool<ChannelSftp> implements SftpUtils, SftpTool {
    @Override
    protected void execute(ChannelSftp storageClient) {
        Map<String, Object> properties = getProperties();
        WorkflowManager workflowManager = (WorkflowManager) AppUtil.getApplicationContext().getBean("workflowManager");
        WorkflowAssignment workflowAssignment = (WorkflowAssignment) properties.get("workflowAssignment");
        String statusWorkflowVariable = getStatusWorkflowVariable();

        try {
            AppDefinition appDefinition = (AppDefinition) properties.get("appDef");
            if(appDefinition == null && (appDefinition = AppUtil.getCurrentAppDefinition()) == null) {
                throw new KecakSftpException("Property [appDef] is null");
            }

            Form form = getForm(appDefinition, getFormDefId(), new FormData());
            processCsvFile(this, loadFile(storageClient, getFileName(properties)), form, getSkipLines(), true, getCellMapping(properties), getDefaultValues(properties));

            if(!statusWorkflowVariable.isEmpty()) {
                workflowManager.processVariable(workflowAssignment.getProcessId(), statusWorkflowVariable, getStatusSucceed());
            }
        } catch (KecakSftpException e) {
            if(!statusWorkflowVariable.isEmpty()) {
                workflowManager.processVariable(workflowAssignment.getProcessId(), statusWorkflowVariable, getStatusFailed());
            }
            LogUtil.error(getClassName(), e, e.getMessage());
        }
    }

    @Override
    public ChannelSftp generateClient(Plugin plugin) throws ExternalStorageException {
        try {
            return generateSftpChannel(getHost(), getUsername(), getPassword(), getKnownHostsFile(), isStrictHostKeyChecking());
        } catch (KecakSftpException e) {
            throw new ExternalStorageException(e);
        }
    }

    @Override
    public void connect(ChannelSftp client) throws ExternalStorageException {
        try {
            if (!client.isConnected()) {
                client.connect();
            }
        } catch (JSchException e) {
            throw new ExternalStorageException(e);
        }
    }

    @Override
    public void disconnect(ChannelSftp client) {
        if (client.isConnected()) {
            client.disconnect();
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

    protected String getHost() {
        return getPropertyString("host");
    }

    protected String getPassword() {
        return getPropertyString("password");
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
}
