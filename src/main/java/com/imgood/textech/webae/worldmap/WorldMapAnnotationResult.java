package com.imgood.textech.webae.worldmap;

/** Explicit service outcome used by the future HTTP adapter. */
public final class WorldMapAnnotationResult<T> {

    public final boolean success;
    public final String code;
    public final String message;
    public final T result;

    private WorldMapAnnotationResult(boolean success, String code, String message, T result) {
        this.success = success;
        this.code = code;
        this.message = message;
        this.result = result;
    }

    public static <T> WorldMapAnnotationResult<T> success(T result) {
        return new WorldMapAnnotationResult<T>(true, "ok", "ok", result);
    }

    public static <T> WorldMapAnnotationResult<T> failure(String code, String message) {
        return new WorldMapAnnotationResult<T>(false, code, message, null);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public T getResult() {
        return result;
    }
}
