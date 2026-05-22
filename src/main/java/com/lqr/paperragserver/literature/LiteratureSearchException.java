package com.lqr.paperragserver.literature;

import org.springframework.http.HttpStatus;

/**
 * 文献搜索异常。
 */
public class LiteratureSearchException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public LiteratureSearchException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public LiteratureSearchException(HttpStatus status, String code, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}