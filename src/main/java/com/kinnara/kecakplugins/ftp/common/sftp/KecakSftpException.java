package com.kinnara.kecakplugins.ftp.common.sftp;

public class KecakSftpException extends Exception {
    public KecakSftpException(String message, Throwable cause) {
        super(message, cause);
    }

    public KecakSftpException(String message) {
        super(message);
    }

    public KecakSftpException(Throwable cause) {
        super(cause);
    }
}
