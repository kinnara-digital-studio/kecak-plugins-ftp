package com.kinnarastudio.kecakplugins.ftp.form;

import com.kinnarastudio.kecakplugins.ftp.common.externalstorage.ExternalStorageException;
import com.kinnarastudio.kecakplugins.ftp.common.externalstorage.ExternalStorageFileElement;
import com.kinnarastudio.kecakplugins.ftp.common.externalstorage.ExternalStorageUtil;
import com.kinnarastudio.kecakplugins.ftp.common.ftp.FtpClient;
import com.kinnarastudio.kecakplugins.ftp.common.exceptions.KecakFtpException;
import com.kinnarastudio.kecakplugins.ftp.common.sftp.SftpClient;
import com.kinnarastudio.kecakplugins.ftp.common.sftp.Utils;
import com.kinnarastudio.commons.Try;
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

import java.io.*;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.stream.Stream;

/**
 * @author aristo
 */
public class FtpFileUploadElement extends ExternalStorageFileElement<FtpClient> implements Utils {
    public final static String LABEL = "FTP File Upload";


    @Override
    protected FtpClient generateClient(Plugin plugin) throws ExternalStorageException {
        assert plugin instanceof PropertyEditable;

        PropertyEditable element = (PropertyEditable) plugin;
        try {
            String host = getHost(element);
            int port = getPort(element);
            String username = getUsername(element);
            String password = getPassword(element);
            return new FtpClient(host, port, username, password, false, true);
        } catch (IOException | GeneralSecurityException e) {
            throw new ExternalStorageException(e);
        }
    }

    @Override
    protected InputStream loadFile(FtpClient client, Element element, FormData formData, String fileName) throws ExternalStorageException {
        try {
            File file = loadRemoteFile(client, getRemoteFolder(element) + "/" + fileName, element, formData);
            return Files.newInputStream(file.toPath());
        } catch (IOException e) {
            throw new ExternalStorageException(e);
        }
    }

    @Override
    protected void storeFile(FtpClient client, File file, Element element, FormData formData) throws ExternalStorageException {
        try {
            storeFile(client, file, getRemoteFolder(element));
        } catch (IOException e) {
            throw new ExternalStorageException(e);
        }
    }

    public int getPort(PropertyEditable prop) {
        try {
            return Integer.parseInt(prop.getPropertyString("ftpPort"));
        } catch (NumberFormatException e) {
            return 21; // default FTP port
        }
    }

    public String getUsername(PropertyEditable prop) {
        return prop.getPropertyString("username");
    }

    public String getPassword(PropertyEditable prop) {
        return prop.getPropertyString("password");
    }

    public String getHost(PropertyEditable prop) {
        return prop.getPropertyString("ftpHost");
    }

    public String getRemoteFolder(PropertyEditable prop) {
        return prop.getPropertyString("remoteFolder");
    }

    @Override
    public boolean isDownloadAllowed(Map map) {
        return true;
    }

    @Override
    public String getFormBuilderCategory() {
        return "Kecak";
    }

    @Override
    public int getFormBuilderPosition() {
        return 100;
    }

    @Override
    public String getFormBuilderIcon() {
        return null;
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
        return AppUtil.readPluginResource(getClassName(), "/properties/form/FtpFileUpload.json", null, true, "/messages/Ftp").replaceAll("\"", "'");
    }

    protected File loadRemoteFile(FtpClient client, String remoteFilePath, Element element, FormData formData) throws ExternalStorageException {
        final File tempOutputFile = getTemporaryDownloadFile(remoteFilePath);
        try (OutputStream fos = Files.newOutputStream(tempOutputFile.toPath());
             OutputStream bos = new BufferedOutputStream(fos)) {

            LogUtil.info(getClassName(), "Retrieving file from remote [" + remoteFilePath + "] to local [" + tempOutputFile.getAbsolutePath() + "]");
            client.retrieveFile(remoteFilePath, bos);

//            Form form = FormUtil.findRootForm(this);
//            LogUtil.info(getClassName(), "Storing file [" + tempOutputFile.getAbsolutePath() + "] to form [" + form.getPropertyString("id") + "] element [" + this.getPropertyString("id") + "]");
//            ExternalStorageUtil.storeFileInFileUpload(this, tempOutputFile, formData);

            return tempOutputFile;
        } catch (IOException e) {
            throw new ExternalStorageException(e);
        }
    }

    protected void storeFile(FtpClient client, File file, String remoteFolder) throws IOException, KecakFtpException {
        // store file in bucket
        try (InputStream fileInputStream = Files.newInputStream(file.toPath())) {
            // create folder
            String path = Optional.of("/")
                    .map(remoteFolder::split)
                    .map(Arrays::stream)
                    .orElseGet(Stream::empty)
                    .filter(Try.toNegate(String::isEmpty))
                    .reduce("", (s1, s2) -> {
                        String folder = s1 + "/" + s2;
//                        try {
//                            int replyCode = client.mkdir(folder);
//                            LogUtil.info(getClass().getName(), "Folder [" + folder + "] is created in remote server reply code [" + replyCode + "]");
//                        } catch (IOException e) {
//                            LogUtil.warn(getClass().getName(), e.getMessage());
//                        }
                        return folder;
                    }, String::concat);

            String targetFullPath = path + "/" + file.getName();
            client.sendFile(fileInputStream, targetFullPath);
        } catch (IOException e) {
            throw new KecakFtpException(e);
        }
    }

    protected File getTemporaryDownloadFile(String remoteFilePath) throws ExternalStorageException {
        String id = UuidGenerator.getInstance().getUuid();
        String path = id + File.separator;

        String filename = path + remoteFilePath.replaceAll(".+/", "");
        File file = new File(FileManager.getBaseDirectory(), filename);
        if (!file.isDirectory()) {
            // create temp file directory
            new File(FileManager.getBaseDirectory(), path).mkdirs();
            return file;
        }

        throw new ExternalStorageException("Cannot read file [" + file.getAbsolutePath() + "]");
    }
}
