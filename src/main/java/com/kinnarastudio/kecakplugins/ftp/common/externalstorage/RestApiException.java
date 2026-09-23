package com.kinnarastudio.kecakplugins.ftp.common.externalstorage;

import org.kecak.apps.exception.ApiException;

public class RestApiException extends ApiException {
    private int errorCode;

    public RestApiException(int errorCode, String message) {
        super(errorCode, message);
        this.errorCode = errorCode;
    }

    public RestApiException(int errorCode, Throwable cause) {
        super(errorCode, cause);
        this.errorCode = errorCode;
    }

    public int getErrorCode() {
        return errorCode;
    }
}
