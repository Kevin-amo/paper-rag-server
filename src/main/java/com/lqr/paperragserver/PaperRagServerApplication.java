package com.lqr.paperragserver;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 论文 RAG 服务启动类。
 */
@MapperScan({
        "com.lqr.paperragserver.paper.mapper",
        "com.lqr.paperragserver.vector.mapper",
        "com.lqr.paperragserver.auth.mapper"
})
@SpringBootApplication
public class PaperRagServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(PaperRagServerApplication.class, args);
    }

}