package com.turnero.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.turnero.config.RailwayDatabaseUrlEnvironmentPostProcessor.DatabaseConnectionProperties;
import org.junit.jupiter.api.Test;

class RailwayDatabaseUrlEnvironmentPostProcessorTests {

    @Test
    void parsesRailwayPostgresDatabaseUrl() {
        DatabaseConnectionProperties properties = DatabaseConnectionProperties.from(
                "postgresql://turnero:secret@containers-us-west.railway.app:5433/railway?sslmode=require"
        );

        assertThat(properties.jdbcUrl())
                .isEqualTo("jdbc:postgresql://containers-us-west.railway.app:5433/railway?sslmode=require");
        assertThat(properties.username()).isEqualTo("turnero");
        assertThat(properties.password()).isEqualTo("secret");
    }

    @Test
    void defaultsPortWhenDatabaseUrlOmitsIt() {
        DatabaseConnectionProperties properties = DatabaseConnectionProperties.from(
                "postgres://user:pass@example.com/turnero"
        );

        assertThat(properties.jdbcUrl()).isEqualTo("jdbc:postgresql://example.com:5432/turnero");
    }
}
