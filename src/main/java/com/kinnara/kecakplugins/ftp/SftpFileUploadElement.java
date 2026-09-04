package com.kinnara.kecakplugins.ftp;

import com.jcraft.jsch.JSchException;
import com.kinnara.kecakplugins.ftp.common.externalstorage.ExternalStorageException;
import com.kinnara.kecakplugins.ftp.common.externalstorage.ExternalStorageFileElement;
import com.kinnara.kecakplugins.ftp.common.sftp.KecakFtpException;
import com.kinnara.kecakplugins.ftp.common.sftp.SftpClient;
import com.kinnara.kecakplugins.ftp.common.sftp.Utils;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.FormData;
import org.joget.plugin.base.Plugin;
import org.joget.plugin.base.PluginManager;
import org.joget.plugin.property.model.PropertyEditable;

import java.io.File;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * @author aristo
 */
public class SftpFileUploadElement extends ExternalStorageFileElement<SftpClient> implements Utils {
    @Override
    protected SftpClient generateClient(Plugin plugin) throws ExternalStorageException {
        ExternalStorageFileElement<SftpClient> element = (ExternalStorageFileElement<SftpClient>) plugin;
        try {
            return new SftpClient(getHost(element), getPort(element), getUsername(element), getPassword(element), getKeyFile(element), getKnownHostsFile(element), true);
        } catch (JSchException e) {
          throw new ExternalStorageException(e);
        }
    }

    @Override
    protected InputStream loadFile(SftpClient client, Element element, FormData formData, String fileName) throws ExternalStorageException {
        try {
            return loadFile(client.getChannelSftp(), getRemoteFolder(element), fileName, element, formData);
        } catch (KecakFtpException e) {
            throw new ExternalStorageException(e);
        }
    }

    @Override
    protected void storeFile(SftpClient client, File file, Element element, FormData formData) throws ExternalStorageException {
        try {
            storeFile(client.getChannelSftp(), file, getRemoteFolder(element), element, formData);
        } catch (KecakFtpException e) {
            throw new ExternalStorageException(e);
        }
    }

    public int getPort(PropertyEditable prop) {
        try {
            return Integer.parseInt(prop.getPropertyString("port"));
        } catch (NumberFormatException e) {
            return 22; // default SFTP port
        }
    }

    public String getKeyFile(PropertyEditable prop) {
        return prop.getPropertyString("keyFile");
    }

    public String getUsername(PropertyEditable prop) {
        return prop.getPropertyString("username");
    }

    public String getPassword(PropertyEditable prop) {
        return prop.getPropertyString("password");
    }

    public String getHost(PropertyEditable prop) {
        return prop.getPropertyString("host");
    }

    public String getKnownHostsFile(PropertyEditable prop) {
        return Optional.of("knownHostsFile")
                .map(prop::getPropertyString)
                .orElse("~/.ssh/known_hosts");
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
        return "SFTP File Upload";
    }

    @Override
    public String getClassName() {
        return getClass().getName();
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClassName(), "/properties/SftpFileUpload.json", null, true, "/messages/Ftp").replaceAll("\"", "'");
    }
}
