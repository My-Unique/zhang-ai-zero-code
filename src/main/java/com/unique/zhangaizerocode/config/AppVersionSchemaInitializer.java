package com.unique.zhangaizerocode.config;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Keeps old app_version tables compatible with version thumbnail storage.
 */
@Slf4j
@Component
public class AppVersionSchemaInitializer {

    @Resource
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void init() {
        Integer tableCount = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM information_schema.TABLES
                        WHERE TABLE_SCHEMA = DATABASE()
                          AND TABLE_NAME = 'app_version'
                        """,
                Integer.class);
        if (tableCount == null || tableCount <= 0) {
            log.warn("Skip app_version.cover check because app_version table does not exist");
            return;
        }

        Integer count = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE()
                          AND TABLE_NAME = 'app_version'
                          AND COLUMN_NAME = 'cover'
                        """,
                Integer.class);
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE app_version ADD COLUMN cover VARCHAR(512) NULL COMMENT 'Version preview cover'");
        log.info("Added missing app_version.cover column");
    }
}
