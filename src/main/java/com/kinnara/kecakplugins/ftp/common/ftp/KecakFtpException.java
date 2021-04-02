package com.kinnara.kecakplugins.ftp.common.ftp;

import com.kinnara.kecakplugins.ftp.common.externalstorage.ExternalStorageException;

public class KecakFtpException extends ExternalStorageException {

    public KecakFtpException(String message) {
        super(message);
    }

    public KecakFtpException(Throwable cause) {
        super(cause);
    }

    public KecakFtpException(String message, Throwable cause) {
        super(message, cause);
    }
}
