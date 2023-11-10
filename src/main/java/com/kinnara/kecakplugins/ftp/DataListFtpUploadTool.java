package com.kinnara.kecakplugins.ftp;

import com.kinnara.kecakplugins.ftp.common.externalstorage.ExternalStorageException;
import com.kinnara.kecakplugins.ftp.common.externalstorage.ExternalStorageUploadTool;
import com.kinnara.kecakplugins.ftp.common.ftp.FtpClient;
import com.kinnara.kecakplugins.ftp.common.sftp.CsvTool;
import com.kinnara.kecakplugins.ftp.common.sftp.KecakFtpException;
import com.kinnara.kecakplugins.ftp.common.sftp.Utils;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.datalist.model.DataList;
import org.joget.commons.util.FileManager;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.Plugin;
import org.joget.plugin.base.PluginManager;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DataListFtpUploadTool extends ExternalStorageUploadTool<FtpClient> implements Utils, CsvTool {

    public final static int DEFAULT_FTP_PORT = 21;

    public final static String LABEL = "DataList FTP Upload Tool";

    @Override
    protected void execute(FtpClient storageClient) {
        final boolean isDebug = isDebug();
        try {
            if(".csv".equalsIgnoreCase(getFileFormat())) {
                String remoteFolder = getRemoteFolder();
                DataList dataList = getDataList(getPropertyString("dataListId"));
                Map<String, List<String>> filters = getDataListFilter();
                String[] headerValues = getHeaderValues();
                String filenameWithExtension = getFileName().replaceAll("(?<!\\.\\w{3})$", getFileFormat());
                File localFile = getDataListRow(this, dataList, filters, filenameWithExtension, 0, headerValues);

                if(isDebug) {
                    LogUtil.info(getClass().getName(), "Uploading temp file [" + localFile + "] to remote folder ["+ remoteFolder + "]");
                }

                storeFile(storageClient, localFile, remoteFolder);

                if(isDeleteTemporaryFile()) {
                    if(isDebug) {
                        LogUtil.info(getClass().getName(), "Deleting temp file [" + localFile.getParentFile() + "]");
                    }

                    FileManager.deleteFile(localFile.getParentFile());
                }
            } else {
                throw new KecakFtpException("File format is not supported");
            }
        } catch (KecakFtpException | IOException e) {
            LogUtil.error(getClassName(), e, e.getMessage());
        }
    }

    @Override
    protected FtpClient generateClient(Plugin plugin) throws ExternalStorageException {
        try {
            final String host = getHost();
            int port = getPort();
            boolean ignoreCertificateError = ignoreSslCertificateError();
            return new FtpClient(host, port, getUsername(), getPassword(), true, ignoreCertificateError);
        } catch (IOException | GeneralSecurityException e) {
            throw new ExternalStorageException(e);
        }
    }

    @Override
    public String getName() {
        return LABEL;
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
        return LABEL;
    }

    @Override
    public String getClassName() {
        return getClass().getName();
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClass().getName(), "/properties/DataListFtpUploadTool.json", null, true, "/messages/Ftp");
    }

    protected String getHost() {
        return getPropertyString("host");
    }

    protected int getPort() {
        return Integer.parseInt(ifEmptyThen(getPropertyString("port"), String.valueOf(DEFAULT_FTP_PORT)));
    }
    protected String getPassword() {
        return getPropertyString("password");
    }

    protected String getUsername() {
        return getPropertyString("username");
    }

    /**
     * File format
     *
     * @return
     */
    protected String getFileFormat() {
        return getPropertyString("fileFormat");
    }

    protected String getFileName() {
        return getPropertyString("fileName");
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
        return getPropertyString("remoteFolder");
    }

    protected boolean isDeleteTemporaryFile() {
        return "true".equalsIgnoreCase(getPropertyString("deleteTemporaryFile"));
    }

    protected String[] getHeaderValues() {
        return new String[0];
    }
    protected boolean isDebug() {
        return "true".equalsIgnoreCase(getPropertyString("debug"));
    }
    @Override
    public String getCsvDelimiter() {
        return getPropertyString("columnDelimiter");
    }

    protected boolean ignoreSslCertificateError() {
        return "true".equalsIgnoreCase(getPropertyString("strictHostKeyChecking");
    }
}
