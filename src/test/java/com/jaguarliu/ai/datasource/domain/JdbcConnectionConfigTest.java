package com.jaguarliu.ai.datasource.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("JdbcConnectionConfig Tests")
class JdbcConnectionConfigTest {

    @Test
    @DisplayName("builds GaussDB URL with postgresql protocol")
    void buildsGaussUrlWithPostgresqlProtocol() {
        JdbcConnectionConfig config = new JdbcConnectionConfig(
                "127.0.0.1",
                5432,
                "demo_db",
                "demo_user",
                "secret",
                Map.of()
        );

        String url = config.buildJdbcUrl(DataSourceType.GAUSS);

        assertEquals("jdbc:postgresql://127.0.0.1:5432/demo_db", url);
    }
}

