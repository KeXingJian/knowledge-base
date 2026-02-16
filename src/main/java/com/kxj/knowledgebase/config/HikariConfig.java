package com.kxj.knowledgebase.config;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Slf4j
@Configuration
@ConditionalOnBean(DataSource.class)
public class HikariConfig {

    public HikariConfig(DataSource dataSource) {
        if (dataSource instanceof HikariDataSource) {
            HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
            log.info("[AI: HikariCP 连接池配置]");
            log.info("[AI: 最小空闲连接数: {}]", hikariDataSource.getMinimumIdle());
            log.info("[AI: 最大连接数: {}]", hikariDataSource.getMaximumPoolSize());
            log.info("[AI: 连接超时时间: {}ms]", hikariDataSource.getConnectionTimeout());
            log.info("[AI: 空闲连接超时时间: {}ms]", hikariDataSource.getIdleTimeout());
            log.info("[AI: 最大生命周期: {}ms]", hikariDataSource.getMaxLifetime());
            log.info("[AI: 连接池名称: {}]", hikariDataSource.getPoolName());
        } else {
            log.warn("[AI: 当前数据源不是 HikariDataSource: {}]", dataSource.getClass().getName());
        }
    }
}