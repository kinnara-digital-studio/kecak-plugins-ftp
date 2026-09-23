package com.kinnarastudio.kecakplugins.ftp.common.exceptions;

import com.kinnarastudio.kecakplugins.ftp.common.externalstorage.ExternalStorageException;

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
