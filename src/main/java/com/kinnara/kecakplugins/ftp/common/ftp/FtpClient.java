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
    private final String host;
    private final int port;
    private final FTPClient ftpClient;

    public FtpClient(String hostname, String username, String password) throws IOException, GeneralSecurityException {
        this(hostname, 21, username, password, false);
    }

    public FtpClient(String hostname, int port, String username, String password, boolean isSecure) throws IOException, GeneralSecurityException {
        this.host = hostname;
        this.port = port;

        if (isSecure) {
            final FTPSClient secureClient = new FTPSClient();
            secureClient.setTrustManager(TrustManagerUtils.getAcceptAllTrustManager());
            this.ftpClient = secureClient;
        } else {
            this.ftpClient = new FTPClient();
        }
        ftpClient.connect(hostname, port);
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

            ((FTPSClient)ftpClient).execPBSZ(0);;
            LogUtil.info(getClass().getName(), "sendFile : execPBSZ reply [" + ftpClient.getReplyString() + "]");

            ((FTPSClient)ftpClient).execPROT("P");
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
        final String file = remote.replaceAll("^.+(?=[/])/", "");

        LogUtil.info(getClass().getName(), "sendFile : changeWorkingDirectory [" + workingDirectory + "]");
        if (!ftpClient.changeWorkingDirectory(workingDirectory)) {
            LogUtil.info(getClass().getName(), "changeWorkingDirectory status [" + ftpClient.getStatus() + "]");
            throw new KecakFtpException("changeWorkingDirectory reply [" + ftpClient.getReplyString() + "]");
        }
        LogUtil.info(getClass().getName(), "sendFile : changeWorkingDirectory reply [" + ftpClient.getReplyString() + "]");

        ftpClient.sendCommand("CWD", workingDirectory);
        LogUtil.info(getClass().getName(), "sendFile : sendCommand reply [" + ftpClient.getReplyString() + "]");

        LogUtil.info(getClass().getName(), "sendFile : setFileType [" + FTP.ASCII_FILE_TYPE + "]");
        if (!ftpClient.setFileType(FTP.ASCII_FILE_TYPE)) {
            LogUtil.info(getClass().getName(), "setFileType status [" + ftpClient.getStatus() + "]");
            throw new KecakFtpException("setFileType reply [" + ftpClient.getReplyString() + "]");
        }
        LogUtil.info(getClass().getName(), "sendFile : setFileType reply [" + ftpClient.getReplyString() + "]");


//        LogUtil.info(getClass().getName(), "sendFile : enterRemotePassiveMode");
//        if (!ftpClient.enterRemotePassiveMode()) {
//            LogUtil.info(getClass().getName(), "enterRemotePassiveMode status [" + ftpClient.getStatus() + "]");
//            throw new KecakFtpException("enterRemotePassiveMode reply [" + ftpClient.getReplyString() + "]");
//        }
//        LogUtil.info(getClass().getName(), "sendFile : enterRemotePassiveMode reply [" + ftpClient.getReplyString() + "]");

//        LogUtil.info(getClass().getName(), "sendFile : pasv");
//        ftpClient.pasv();
//        LogUtil.info(getClass().getName(), "sendFile : pasv reply [" + ftpClient.getReplyString() + "]");

        LogUtil.info(getClass().getName(), "sendFile : enterLocalPassiveMode");
        ftpClient.enterLocalPassiveMode();
        LogUtil.info(getClass().getName(), "sendFile : enterLocalPassiveMode reply [" + ftpClient.getReplyString() + "]");

        LogUtil.info(getClass().getName(), "sendFile : storeFileStream file [" + remote + "]");
        try (OutputStream outputStream = ftpClient.storeFileStream(remote)) {
            if (outputStream != null) {
                byte[] buffer = new byte[1024];
                int len;
                while ((len = local.read(buffer)) > 0) {
                    LogUtil.info(getClass().getName(), "sendFile : storeFileStream read len[" + len + "]");
                    outputStream.write(buffer, 0, len);
                }
            } else {
                LogUtil.warn(getClass().getName(), "sendFile : storeFileStream reply [" + ftpClient.getReplyCode() + "] [" + ftpClient.getReplyString() + "]");
            }
        } catch (IOException e) {
            throw new KecakFtpException(e);
        }

        LogUtil.info(getClass().getName(), "sendFile : completePendingCommand");
        if (!ftpClient.completePendingCommand()) {
            LogUtil.info(getClass().getName(), "sendFile : completePendingCommand reply [" + ftpClient.getReplyString() + "]");
        }
        LogUtil.warn(getClass().getName(), "sendFile : completePendingCommand status [" + ftpClient.getStatus() + "]");

//        LogUtil.info(getClass().getName(), "sendFile : storeFile remote [" + remote + "]");
//        if (!ftpClient.storeFile(remote, local)) {
//            LogUtil.info(getClass().getName(), "storeFile status [" + ftpClient.getStatus() + "]");
//            Arrays.stream(ftpClient.getReplyStrings()).forEach(s -> LogUtil.warn(getClass().getName(), "storeFile reply [" + s + "]"));
//            throw new KecakFtpException("storeFile reply [" + ftpClient.getReplyString() + "]");
//        }
//        LogUtil.info(getClass().getName(), "sendFile : storeFile reply [" + ftpClient.getReplyString() + "]");
    }

    public int mkdir(String folder) throws IOException {
        return ftpClient.mkd(folder);
    }
}
