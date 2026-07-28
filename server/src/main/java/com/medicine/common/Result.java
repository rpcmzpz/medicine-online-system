package com.medicine.common;

public class Result {
    private int code;
    private String message;
    private Object data;
    private long timestamp;

    private Result(int code, String message, Object data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.timestamp = System.currentTimeMillis();
    }

    public static Result success(Object data) {
        return new Result(200, "success", data);
    }

    public static Result success(Object data, String message) {
        return new Result(200, message, data);
    }

    public static Result created(Object data, String message) {
        return new Result(200, message, data);
    }

    public static Result fail(String message, int code) {
        return new Result(code, message, null);
    }

    public int getCode() { return code; }
    public String getMessage() { return message; }
    public Object getData() { return data; }
    public long getTimestamp() { return timestamp; }
    public void setCode(int code) { this.code = code; }
    public void setMessage(String message) { this.message = message; }
    public void setData(Object data) { this.data = data; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
