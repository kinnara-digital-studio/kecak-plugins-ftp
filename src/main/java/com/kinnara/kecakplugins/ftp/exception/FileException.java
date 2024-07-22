package com.kinnara.kecakplugins.ftp.exception;

public class FileException extends Exception{

    public FileException(Throwable cause) {
        super(cause);
    }

    public FileException(String message) {
        super(message);
    }
}
