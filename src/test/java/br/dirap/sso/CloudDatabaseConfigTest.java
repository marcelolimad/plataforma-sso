package br.dirap.sso;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CloudDatabaseConfigTest {
    @Test void convertsPrivatePostgresUrl() {
        var values = CloudDatabaseConfig.fromUrl("postgresql://sso_user:p%40ss%3Aword@db.internal:5432/sso_teste");
        assertEquals("jdbc:postgresql://db.internal:5432/sso_teste", values.get("spring.datasource.url"));
        assertEquals("sso_user", values.get("spring.datasource.username"));
        assertEquals("p@ss:word", values.get("spring.datasource.password"));
    }

    @Test void rejectsNonPostgresUrl() {
        assertThrows(IllegalArgumentException.class, () -> CloudDatabaseConfig.fromUrl("https://example.com"));
    }
}
