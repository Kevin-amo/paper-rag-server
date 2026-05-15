package com.lqr.paperragserver;

import org.junit.jupiter.api.Test;

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
}
