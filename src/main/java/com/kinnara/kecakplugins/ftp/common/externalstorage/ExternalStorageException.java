package com.kinnara.kecakplugins.ftp.common.externalstorage;

public class ExternalStorageException extends Exception {
    public ExternalStorageException(String message) {
        super(message);
    }

    public ExternalStorageException(Throwable cause) {
        super(cause);
    }

    public ExternalStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
