package zas.admin.zec.backend;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.utility.DockerImageName;

import java.nio.file.Path;

@TestConfiguration(proxyBeanMethods = false)
public class LocalConfig {

    @Bean(initMethod = "start", destroyMethod = "stop")
    @ServiceConnection
    @SuppressWarnings("resource")
    PostgreSQLContainer<?> postgresContainer() {
        String imageName = new ImageFromDockerfile("copilot-backend-postgres-test", false)
                .withFileFromPath(".", Path.of("src/test/resources/docker"))
                .withFileFromPath("init-extensions.sql", Path.of("src/test/resources/init-extensions.sql"))
                .withFileFromPath("pg-textsearch-postgresql-18_1.2.0-1_amd64.deb", Path.of("src/test/resources/pg-textsearch-postgresql-18_1.2.0-1_amd64.deb"))
                .get();

        return new PostgreSQLContainer<>(DockerImageName.parse(imageName).asCompatibleSubstituteFor("postgres"))
                .withDatabaseName("pg_db")
                .withUsername("admin")
                .withPassword("pg_password");
    }
}
