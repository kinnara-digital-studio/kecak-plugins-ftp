package com.kinnara.kecakplugins.ftp;

import com.jcraft.jsch.JSchException;
import com.kinnara.kecakplugins.ftp.common.externalstorage.ExternalStorageException;
import com.kinnara.kecakplugins.ftp.common.externalstorage.ExternalStorageUploadTool;
import com.kinnara.kecakplugins.ftp.common.sftp.KecakSftpException;
import com.kinnara.kecakplugins.ftp.common.sftp.SftpClient;
import com.kinnara.kecakplugins.ftp.common.sftp.CsvTool;
import com.kinnara.kecakplugins.ftp.common.sftp.Utils;
import com.kinnara.kecakplugins.ftp.exception.FileException;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.datalist.model.DataList;
import org.joget.commons.util.FileManager;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.Plugin;
import org.joget.plugin.base.PluginManager;
import org.joget.workflow.model.WorkflowAssignment;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author aristo
 */
public class DataListSftpUploadTool extends ExternalStorageUploadTool<SftpClient> implements Utils, CsvTool {
    public final static String CSV_DELIMITER = ";";

    File localFile = null;

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
    public void execute(SftpClient storageClient) {
        try {
            if (localFile == null) {
                throw new KecakSftpException("Temporary file not found");
            }

            String remoteFolder = getRemoteFolder();
            storeFile(storageClient.getChannelSftp(), localFile, remoteFolder);
            if (isDeleteTemporaryFile()) {
                FileManager.deleteFile(localFile.getParentFile());
            }
        } catch (KecakSftpException e) {
            LogUtil.error(getClassName(), e, e.getMessage());
        }
    }

    @Override
    public String getLabel() {
        return "DataList SFTP Upload Tool";
    }

    @Override
    public String getClassName() {
        return getClass().getName();
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClassName(), "/properties/DataListSftpUploadTool.json", null, false, "/messages/Ftp");
    }

    @Override
    protected SftpClient generateClient(Plugin plugin) throws ExternalStorageException {
        try {
            // generate temporary file
            final String fileFormat = getFileFormat();
            if (".csv".equalsIgnoreCase(fileFormat)) {
                localFile = generateTemporaryFile();
            } else {
                throw new KecakSftpException("File format is not supported");
            }

            return new SftpClient(getHost(), getUsername(), getPassword(), getKnownHostsFile(), isStrictHostKeyChecking());
        } catch (JSchException | KecakSftpException | FileException e) {
            throw new ExternalStorageException(e);
        }
    }

    protected String getHost() {
        return String.valueOf(getProperties().get("host"));
    }

    protected String getPassword() {
        return String.valueOf(getProperties().get("password"));
    }

    protected String getUsername() {
        return String.valueOf(getProperties().get("username"));
    }

    protected String getKnownHostsFile() {
        return String.valueOf(getProperties().get("getKnownHostsFile"));
    }

    /**
     * File format
     *
     * @return
     */
    protected String getFileFormat() {
        return String.valueOf(getProperties().get("fileFormat"));
    }

    protected String getFileName() {
        return String.valueOf(getProperties().get("fileName"));
    }

    protected Map<String, List<String>> getDataListFilter() {
        Map<String, List<String>> filters = Optional.of("dataListFilter")
                .map(this::getProperty)
                .map(o -> (Object[]) o)
                .map(Arrays::stream)
                .orElseGet(Stream::empty)
                .map(o -> (Map<String, Object>) o)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(m -> m.getOrDefault("name", "").toString(), m -> Collections.singletonList(m.getOrDefault("value", "").toString())));
        return filters;
    }

    protected String getRemoteFolder() {
        return String.valueOf(getProperties().get("remoteFolder"));
    }

    protected boolean isStrictHostKeyChecking() {
        return "true".equalsIgnoreCase(getPropertyString("strictHostKeyChecking"));
    }

    protected String[] getHeaderValues() {
        WorkflowAssignment assignment = (WorkflowAssignment) getProperties().get("workflowAssignment");

        return Optional.of("headerValues")
                .map(getProperties()::get)
                .map(o -> (Object[]) o)
                .map(Arrays::stream)
                .orElseGet(Stream::empty)
                .map(o -> (Map<String, Object>) o)
                .map(m -> Optional.of("value")
                        .map(m::get)
                        .map(String::valueOf)
                        .map(s -> AppUtil.processHashVariable(s, assignment, null, null))
                        .orElse(""))
                .toArray(String[]::new);
    }

    protected String[] getFooterValues() {
        WorkflowAssignment assignment = (WorkflowAssignment) getProperties().get("workflowAssignment");

        return Optional.of("headerValues")
                .map(this::getProperty)
                .map(o -> (Object[]) o)
                .map(Arrays::stream)
                .orElseGet(Stream::empty)
                .map(o -> (Map<String, Object>) o)
                .map(m -> Optional.of("value")
                        .map(m::get)
                        .map(String::valueOf)
                        .map(s -> AppUtil.processHashVariable(s, assignment, null, null))
                        .orElse(""))
                .toArray(String[]::new);
    }

    protected boolean isDeleteTemporaryFile() {
        return "true".equalsIgnoreCase(getPropertyString("deleteTemporaryFile"));
    }

    @Override
    public String getCsvDelimiter() {
        return getPropertyString("columnDelimiter");
    }

    protected File generateTemporaryFile() throws FileException, KecakSftpException {
        final String fileFormat = getFileFormat();
        final String fileName = getFileName();
        String dataListId = getPropertyString("dataListId");
        DataList dataList = getDataList(dataListId)
                .orElseThrow(() -> new KecakSftpException("Error generating dataList [" + dataListId + "]"));
        Map<String, List<String>> filters = getDataListFilter();
        String[] headerValues = getHeaderValues();
        String[] footerValues = getFooterValues();
        return getDataListRow(this, dataList, filters, fileName + fileFormat, 0, headerValues, footerValues);
    }
}
