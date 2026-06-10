package com.lqr.papermind;

import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 启动类冒烟测试。
 *
 * <p>完整 Spring 上下文依赖 PostgreSQL、Redis 和向量库配置，单元回归只验证启动类可被加载。</p>
 */
class PaperMindApplicationTests {

    @Test
    void applicationClassShouldBeLoadable() {
        assertThat(PaperMindApplication.class).isNotNull();
    }

    @Test
    void mapperScanShouldCoverBaseMapperAndCustomMapperPackages() {
        MapperScan mapperScan = PaperMindApplication.class.getAnnotation(MapperScan.class);

        assertThat(mapperScan).isNotNull();
        assertThat(mapperScan.basePackages()).containsExactlyInAnyOrder(
                "com.lqr.papermind.auth.mapper",
                "com.lqr.papermind.conversation.mapper",
                "com.lqr.papermind.document.mapper",
                "com.lqr.papermind.document.structured.mapper",
                "com.lqr.papermind.review.mapper",
                "com.lqr.papermind.vector.mapper"
        );
    }
}
