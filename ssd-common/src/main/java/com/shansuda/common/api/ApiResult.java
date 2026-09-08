package com.shansuda.common.api;

public record ApiResult<T>(boolean ok, T data, boolean replayed, String code, String message) {

    public static <T> ApiResult<T> ok(T data) {
        return new ApiResult<>(true, data, false, null, null);
    }

    public static <T> ApiResult<T> replay(T data) {
        return new ApiResult<>(true, data, true, null, null);
    }

    public static <T> ApiResult<T> fail(String code, String message) {
        return new ApiResult<>(false, null, false, code, message);
    }
}
