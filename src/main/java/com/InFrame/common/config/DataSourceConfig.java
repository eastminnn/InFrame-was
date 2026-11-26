package com.InFrame.common.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class DataSourceConfig {

    // 메인 MySQL
    @Bean
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties mainDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Primary
    public DataSource mainDataSource(
            @Qualifier("mainDataSourceProperties") DataSourceProperties properties,
            @Value("${app.db.lock-wait-timeout-seconds:3}") int lockWaitTimeoutSeconds
    ) {
        HikariDataSource dataSource = properties
                .initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
        dataSource.setPoolName("mysql"); // 메트릭의 pool 라벨
        // 커넥션마다 행 락 대기 상한 설정. JPA 락 타임아웃 힌트는 MySQL 에서 SQL 에 반영되지 않는다
        dataSource.setConnectionInitSql("SET SESSION innodb_lock_wait_timeout = " + lockWaitTimeoutSeconds);
        return dataSource;
    }

    // pgvector용 PostgreSQL
    @Bean
    @ConfigurationProperties("pg.datasource")
    public DataSourceProperties pgDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "pgDataSource")
    public DataSource pgDataSource(
            @Qualifier("pgDataSourceProperties") DataSourceProperties properties
    ) {
        HikariDataSource dataSource = properties
                .initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
        dataSource.setPoolName("pgvector"); // 메트릭의 pool 라벨
        return dataSource;
    }

    @Bean(name = "pgJdbcTemplate")
    public JdbcTemplate pgJdbcTemplate(
            @Qualifier("pgDataSource") DataSource dataSource
    ) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    public VectorStore vectorStore(
            @Qualifier("pgJdbcTemplate") JdbcTemplate jdbcTemplate,
            EmbeddingModel embeddingModel
    ) {
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .initializeSchema(true)
                .build();
    }
}
