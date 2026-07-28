package com.medicine.common;

public class BusinessException extends RuntimeException {
    private int code;
    private int httpStatus;

    public BusinessException(String message, int code) {
        super(message);
        this.code = code;
        this.httpStatus = 400;
    }

    public BusinessException(String message, int code, int httpStatus) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public int getCode() { return code; }
    public int getHttpStatus() { return httpStatus; }
}
