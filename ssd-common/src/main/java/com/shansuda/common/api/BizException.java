package com.shansuda.common.api;

public class BizException extends RuntimeException {

    private final String code;
    private final int httpStatus;

    public BizException(int httpStatus, String code, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.code = code;
    }

    public static BizException conflict(String code, String message) {
        return new BizException(409, code, message);
    }

    public static BizException badRequest(String code, String message) {
        return new BizException(400, code, message);
    }

    public static BizException unauthorized(String message) {
        return new BizException(401, "UNAUTHORIZED", message);
    }

    public static BizException forbidden(String message) {
        return new BizException(403, "FORBIDDEN", message);
    }

    public static BizException notFound(String message) {
        return new BizException(404, "NOT_FOUND", message);
    }

    public String code() {
        return code;
    }

    public int httpStatus() {
        return httpStatus;
    }
}
