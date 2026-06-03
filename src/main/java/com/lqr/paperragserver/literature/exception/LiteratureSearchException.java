package com.lqr.paperragserver.literature.exception;

import org.springframework.http.HttpStatus;

/**
 * 文献搜索异常。
 */
public class LiteratureSearchException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    /**
     * 创建不带底层原因的文献搜索异常。
     */
    public LiteratureSearchException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    /**
     * 创建保留底层原因的文献搜索异常。
     */
    public LiteratureSearchException(HttpStatus status, String code, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
    }

    /**
     * 获取异常对应的 HTTP 状态。
     */
    public HttpStatus status() {
        return status;
    }

    /**
     * 获取异常对应的业务错误码。
     */
    public String code() {
        return code;
    }
}