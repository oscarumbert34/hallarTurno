package com.turnero.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

public class RailwayDatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String PROPERTY_SOURCE_NAME = "railwayDatabaseUrl";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String databaseUrl = environment.getProperty("DATABASE_URL");
        if (!StringUtils.hasText(databaseUrl)) {
            return;
        }

        DatabaseConnectionProperties properties = DatabaseConnectionProperties.from(databaseUrl);
        Map<String, Object> datasourceProperties = new HashMap<>();
        datasourceProperties.put("spring.datasource.url", properties.jdbcUrl());
        datasourceProperties.put("spring.datasource.username", properties.username());
        datasourceProperties.put("spring.datasource.password", properties.password());

        environment.getPropertySources()
                .addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, datasourceProperties));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    record DatabaseConnectionProperties(String jdbcUrl, String username, String password) {

        static DatabaseConnectionProperties from(String databaseUrl) {
            URI uri = URI.create(databaseUrl);
            if (!"postgres".equals(uri.getScheme()) && !"postgresql".equals(uri.getScheme())) {
                throw new IllegalArgumentException("DATABASE_URL must use postgres:// or postgresql://");
            }

            String[] userInfo = StringUtils.hasText(uri.getUserInfo())
                    ? uri.getUserInfo().split(":", 2)
                    : new String[] {"", ""};
            String username = decode(userInfo[0]);
            String password = userInfo.length > 1 ? decode(userInfo[1]) : "";
            int port = uri.getPort() == -1 ? 5432 : uri.getPort();
            String query = StringUtils.hasText(uri.getRawQuery()) ? "?" + uri.getRawQuery() : "";
            String jdbcUrl = "jdbc:postgresql://%s:%d%s%s".formatted(uri.getHost(), port, uri.getPath(), query);

            return new DatabaseConnectionProperties(jdbcUrl, username, password);
        }

        private static String decode(String value) {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        }
    }
}
