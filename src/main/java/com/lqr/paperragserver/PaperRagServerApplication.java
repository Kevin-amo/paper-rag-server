package com.lqr.paperragserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 论文 RAG 服务启动类。
 *
 * <p>该类位于根包 {@code com.lqr.paperragserver} 下，Spring Boot 会从这里开始扫描
 * common、document、paper、rag、ai、vector 等子包中的组件。</p>
 */
@SpringBootApplication
public class PaperRagServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaperRagServerApplication.class, args);
    }

}