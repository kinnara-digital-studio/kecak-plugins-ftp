package com.kinnara.kecakplugins.ftp;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSchException;
import com.kinnara.kecakplugins.ftp.common.externalstorage.ExternalStorageException;
import com.kinnara.kecakplugins.ftp.common.externalstorage.ExternalStorageUploadTool;
import com.kinnara.kecakplugins.ftp.common.sftp.KecakSftpException;
import com.kinnara.kecakplugins.ftp.common.sftp.SftpTool;
import com.kinnara.kecakplugins.ftp.common.sftp.SftpUtils;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.datalist.model.DataList;
import org.joget.commons.util.FileManager;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.Plugin;
import org.joget.workflow.model.WorkflowAssignment;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author aristo
 */
public class DataListSftpUploadTool extends ExternalStorageUploadTool<ChannelSftp> implements SftpUtils, SftpTool {
    public final static String CSV_DELIMITER = ";";

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
    public void execute(ChannelSftp storageClient) {
        try {
            if(".csv".equalsIgnoreCase(getFileFormat())) {
                String remoteFolder = getRemoteFolder();
                DataList dataList = getDataList(getPropertyString("dataListId"));
                Map<String, List<String>> filters = getDataListFilter();
                String[] headerValues = getHeaderValues();
                File file = getDataListRow(this, dataList, filters, getFileName() + getFileFormat(), 0, headerValues);
                if (!storageClient.isConnected()) {
                    storageClient.connect(TIMEOUT);
                    LogUtil.info(getClass().getName(), "storeFile : Connected to server");
                }

                storeFile(storageClient, file, remoteFolder);
                if(isDeleteTemporaryFile()) {
                    FileManager.deleteFile(file.getParentFile());
                }
            } else {
                throw new KecakSftpException("File format is not supported");
            }
        } catch (KecakSftpException | JSchException e) {
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
            if(!client.isConnected()) {
                client.connect(TIMEOUT);
            }
        } catch (JSchException e) {
            throw new ExternalStorageException(e);
        }
    }

    @Override
    public void disconnect(ChannelSftp client) throws ExternalStorageException {
        if(client.isConnected()) {
            client.disconnect();
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
                .map(o -> (Map<String, Object>)o)
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
                .map(o -> (Map<String, Object>)o)
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
}
