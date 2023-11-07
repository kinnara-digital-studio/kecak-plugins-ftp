package com.kinnara.kecakplugins.ftp.common.ftp;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Paths;

public class FtpClient implements AutoCloseable {
    private final FTPClient ftpClient;

    public FtpClient(String hostname, String username, String password) throws IOException {
        this(hostname, 21, username, password);
    }

    public FtpClient(String hostname, int port, String username, String password) throws IOException {
        this.ftpClient = new FTPClient();
        ftpClient.connect(hostname, port);
        int replyCode = ftpClient.getReplyCode();
        if (!FTPReply.isPositiveCompletion(replyCode)) {
            ftpClient.disconnect();
            throw new IOException("Exception in connecting to FTP Server");
        }
        ftpClient.login(username, password);
    }

    @Override
    public void close() throws Exception {
        if(ftpClient != null && ftpClient.isConnected()) {
            ftpClient.disconnect();
        }
    }

    public FTPClient getFtpClient() {
        return ftpClient;
    }

    public boolean retrieveFile(String remote, OutputStream local) throws IOException {
        return ftpClient.retrieveFile(remote, local);
    }

    public boolean sendFile(InputStream local, String remote) throws IOException {
        final String folder = remote.replaceAll("[^\\/]+$", "");
        ftpClient.mkd(folder);
        return ftpClient.storeFile(remote, local);
    }
}
