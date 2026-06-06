package com.lqr.paperragserver;

import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 启动类冒烟测试。
 *
 * <p>完整 Spring 上下文依赖 PostgreSQL、Redis 和向量库配置，单元回归只验证启动类可被加载。</p>
 */
class PaperRagServerApplicationTests {

    @Test
    void applicationClassShouldBeLoadable() {
        assertThat(PaperRagServerApplication.class).isNotNull();
    }

    @Test
    void mapperScanShouldCoverBaseMapperAndCustomMapperPackages() {
        MapperScan mapperScan = PaperRagServerApplication.class.getAnnotation(MapperScan.class);

        assertThat(mapperScan).isNotNull();
        assertThat(mapperScan.basePackages()).containsExactlyInAnyOrder(
                "com.lqr.paperragserver.auth.mapper",
                "com.lqr.paperragserver.conversation.mapper",
                "com.lqr.paperragserver.document.mapper",
                "com.lqr.paperragserver.document.structured.mapper",
                "com.lqr.paperragserver.review.mapper",
                "com.lqr.paperragserver.vector.mapper"
        );
    }
}
