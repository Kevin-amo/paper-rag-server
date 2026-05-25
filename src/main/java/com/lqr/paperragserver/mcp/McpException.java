package com.lqr.paperragserver.mcp;

import org.springframework.http.HttpStatus;

/**
 * MCP 调用异常。
 */
public class McpException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public McpException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public McpException(HttpStatus status, String code, String message, Throwable cause) {
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