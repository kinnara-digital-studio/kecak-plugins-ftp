package com.kinnara.kecakplugins.ftp.common.sftp;

public class KecakFtpException extends Exception {
    public KecakFtpException(String message, Throwable cause) {
        super(message, cause);
    }

    public KecakFtpException(String message) {
        super(message);
    }

    public KecakFtpException(Throwable cause) {
        super(cause);
    }
}
