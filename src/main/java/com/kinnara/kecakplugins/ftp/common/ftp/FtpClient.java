package com.kinnara.kecakplugins.ftp.common.ftp;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.commons.net.ftp.FTPSClient;
import org.apache.commons.net.util.TrustManagerUtils;
import org.joget.commons.util.LogUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;

public class FtpClient implements AutoCloseable {
    private final FTPClient ftpClient;

    public FtpClient(String host, String username, String password) throws IOException, GeneralSecurityException {
        this(host, 21, username, password, false);
    }

    public FtpClient(String host, int port, String username, String password, boolean isSecure) throws IOException, GeneralSecurityException {

        if (isSecure) {
            final FTPSClient secureClient = new FTPSClient();
            secureClient.setTrustManager(TrustManagerUtils.getAcceptAllTrustManager());
            this.ftpClient = secureClient;
        } else {
            this.ftpClient = new FTPClient();
        }
        ftpClient.connect(host, port);
        int replyCode = ftpClient.getReplyCode();
        if (!FTPReply.isPositiveCompletion(replyCode)) {
            ftpClient.disconnect();
            throw new IOException("Exception in connecting to FTP Server");
        }

        LogUtil.info(getClass().getName(), "Connected with reply code [" + ftpClient.getReplyCode() + "] status [" + ftpClient.getStatus() + "]");

        if (!ftpClient.login(username, password)) {
            throw new IOException("Exception in logging into FTP Server status [" + ftpClient.getStatus() + "]");
        }

        LogUtil.info(getClass().getName(), "Logged in as [" + username + "] with reply code [" + ftpClient.getReplyCode() + "] status [" + ftpClient.getStatus() + "]");

//        ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
//        ftpClient.setControlEncoding("UTF-8");

        if (ftpClient instanceof FTPSClient) {
            ftpClient.sendCommand("OPTS", "UTF8 ON");
            LogUtil.info(getClass().getName(), "sendFile : sendCommand reply [" + ftpClient.getReplyString() + "]");

            ((FTPSClient) ftpClient).execPBSZ(0);
            LogUtil.info(getClass().getName(), "sendFile : execPBSZ reply [" + ftpClient.getReplyString() + "]");

            ((FTPSClient) ftpClient).execPROT("P");
            LogUtil.info(getClass().getName(), "sendFile : execPROT reply [" + ftpClient.getReplyString() + "]");
        }
    }

    @Override
    public void close() throws Exception {
        if (ftpClient != null && ftpClient.isConnected()) {
            ftpClient.disconnect();
        }
    }

    public FTPClient getFtpClient() {
        return ftpClient;
    }

    public boolean retrieveFile(String remote, OutputStream local) throws IOException {
        return ftpClient.retrieveFile(remote, local);
    }

    public void sendFile(InputStream local, String remote) throws IOException, KecakFtpException {
        final String workingDirectory = remote.replaceAll("[^/]+$", "");

        if (!ftpClient.changeWorkingDirectory(workingDirectory)) {
            throw new KecakFtpException("changeWorkingDirectory reply [" + ftpClient.getReplyString() + "]");
        }

        if (!ftpClient.setFileType(FTP.ASCII_FILE_TYPE)) {
            throw new KecakFtpException("setFileType reply [" + ftpClient.getReplyString() + "]");
        }

        ftpClient.enterLocalPassiveMode();

        try (OutputStream outputStream = ftpClient.storeFileStream(remote)) {
            if (outputStream != null) {
                byte[] buffer = new byte[1024];
                int len;
                while ((len = local.read(buffer)) > 0) {
                    outputStream.write(buffer, 0, len);
                }
            } else {
                throw new KecakFtpException("storeFileStream reply [" + ftpClient.getReplyString() + "]");
            }
        }

        if (!ftpClient.completePendingCommand()) {
            throw new KecakFtpException("completePendingCommand reply [" + ftpClient.getReplyString() + "]");
        }
    }
}
