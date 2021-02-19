package com.kinnara.kecakplugins.ftp;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;
import com.kinnara.kecakplugins.ftp.common.externalstorage.ExternalStorageElement;
import com.kinnara.kecakplugins.ftp.common.externalstorage.ExternalStorageException;
import com.kinnara.kecakplugins.ftp.common.sftp.SftpUtils;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.FormData;
import org.joget.plugin.base.Plugin;
import org.joget.plugin.property.model.PropertyEditable;

import java.io.File;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;

/**
 * @author aristo
 */
public class SftpFileUpload extends ExternalStorageElement<ChannelSftp> implements SftpUtils {
    @Override
    public ChannelSftp generateClient(Plugin plugin) throws ExternalStorageException {
        ExternalStorageElement<ChannelSftp> element = (ExternalStorageElement<ChannelSftp>) plugin;
        try {
            return generateSftpChannel(getHost(element), getUsername(element), getPassword(element), getKnownHostsFile(element), true);
        } catch (JSchException e) {
          throw new ExternalStorageException(e);
        }
    }

    @Override
    protected InputStream loadFile(ChannelSftp client, Element element, FormData formData, String fileName) throws ExternalStorageException {
        try {
            return loadFile(client, getRemoteFolder(element), fileName, element, formData);
        } catch (SftpException e) {
            throw new ExternalStorageException(e);
        }
    }

    @Override
    protected void storeFile(ChannelSftp client, File file, Element element, FormData formData) {
        storeFile(client, file, getRemoteFolder(element), element, formData);
    }

    @Override
    public void connect(ChannelSftp client) throws ExternalStorageException {
        try {
            if(!client.isConnected()) {
                client.connect();
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

    @Override
    public String getCsvDelimiter() {
        return ";";
    }
}
