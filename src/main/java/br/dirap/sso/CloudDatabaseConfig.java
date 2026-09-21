package br.dirap.sso;

import java.net.URI;
import java.util.Map;

/** Converts Render's private Postgres URL to the JDBC settings expected by Spring. */
final class CloudDatabaseConfig {
    private CloudDatabaseConfig() {}

    static Map<String,Object> fromUrl(String value) {
        URI uri = URI.create(value);
        if (!("postgresql".equals(uri.getScheme()) || "postgres".equals(uri.getScheme())) ||
                uri.getHost() == null || uri.getPath() == null || uri.getPath().length() < 2 ||
                uri.getUserInfo() == null || !uri.getUserInfo().contains(":")) {
            throw new IllegalArgumentException("DATABASE_URL do PostgreSQL está incompleta ou inválida.");
        }
        String[] credentials = uri.getUserInfo().split(":", 2);
        int port = uri.getPort() < 0 ? 5432 : uri.getPort();
        String jdbcUrl = "jdbc:postgresql://" + uri.getHost() + ":" + port + uri.getPath();
        if (uri.getRawQuery() != null) jdbcUrl += "?" + uri.getRawQuery();
        return Map.of("spring.datasource.url", jdbcUrl,
                "spring.datasource.username", credentials[0],
                "spring.datasource.password", credentials[1]);
    }
}
