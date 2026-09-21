package br.dirap.sso;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.MapPropertySource;

@SpringBootApplication
public class SsoApplication {
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(SsoApplication.class);
        String databaseUrl = System.getenv("DATABASE_URL");
        if (databaseUrl != null && !databaseUrl.isBlank()) {
            app.addInitializers(context -> context.getEnvironment().getPropertySources().addFirst(
                    new MapPropertySource("cloudDatabase", CloudDatabaseConfig.fromUrl(databaseUrl))));
        }
        app.run(args);
    }
}
