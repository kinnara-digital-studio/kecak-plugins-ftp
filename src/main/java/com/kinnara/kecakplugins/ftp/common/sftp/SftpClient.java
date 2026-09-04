package com.kinnara.kecakplugins.ftp.common.sftp;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import org.joget.commons.util.LogUtil;

import java.util.Properties;

public class SftpClient implements AutoCloseable, Utils {
    private final String host;
    private final Session jschSession;
    private final ChannelSftp channelSftp;

    public SftpClient(String host, int port, String username, String password, String keyFile, String pathKnownHosts, boolean isStrictHostKeyChecking) throws JSchException {
        this.host = host;

        JSch jsch = new JSch();
        jsch.setKnownHosts(pathKnownHosts);
        
        if (keyFile != null && !keyFile.trim().isEmpty() && !keyFile.equals("null")) {
            jsch.addIdentity(keyFile);
        }

        jschSession = jsch.getSession(username, host, port);
        
        if (password != null && !password.trim().isEmpty() && !password.equals("null")) {
            jschSession.setPassword(password);
        }

        if (!isStrictHostKeyChecking) {
            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            jschSession.setConfig(config);
        }

        LogUtil.info(getClass().getName(), "Connecting session to host [" + host + "]");
        jschSession.connect(TIMEOUT);

        channelSftp = (ChannelSftp) jschSession.openChannel("sftp");

        LogUtil.info(getClass().getName(), "Connecting to SFTP channel");
        channelSftp.connect(TIMEOUT);
    }

    @Override
    public void close() throws Exception {
        if(channelSftp != null && channelSftp.isConnected()) {
            LogUtil.info(getClass().getName(), "Disconnecting from SFTP channel");
            channelSftp.disconnect();
        }

        if(jschSession != null && jschSession.isConnected()) {
            LogUtil.info(getClass().getName(), "Disconnecting session from host [" + host + "]");
            jschSession.disconnect();
        }
    }

    public Session getJschSession() {
        return jschSession;
    }

    public ChannelSftp getChannelSftp() {
        return channelSftp;
    }
}
