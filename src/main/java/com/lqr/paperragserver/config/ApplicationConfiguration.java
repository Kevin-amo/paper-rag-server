package com.lqr.paperragserver.config;

import org.apache.tika.Tika;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 应用级 Bean 配置。
 *
 * <p>这里集中注册跨模块复用的基础组件，业务服务只依赖接口或明确的基础 Bean。</p>
 */
@Configuration
@EnableConfigurationProperties({RagProperties.class, SecurityProperties.class})
public class ApplicationConfiguration {

    /**
     * Tika 文档解析器，用于从 PDF、Word、Markdown、纯文本等文件中提取正文。
     *
     * @return Tika 解析器实例
     */
    @Bean
    public Tika tika() {
        return new Tika();
    }
}