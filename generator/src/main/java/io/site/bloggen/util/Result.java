package io.site.bloggen.util;

public sealed interface Result<T> permits Result.Ok, Result.Err {
    record Ok<T>(T value) implements Result<T> {}
    record Err<T>(int code, String message) implements Result<T> {}

    static <T> Ok<T> ok(T v) { return new Ok<>(v); }
    static <T> Err<T> err(int code, String msg) { return new Err<>(code, msg); }
}

