package zas.admin.zec.backend;

import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.file.Path;

@TestConfiguration(proxyBeanMethods = false)
public class LocalConfig {

    /**
     * Conteneur PostgreSQL unique pour toute la JVM, partagé par tous les contextes Spring de test (un par combinaison
     * de configuration des {@code *IT}). Sur l'agent Jenkins (Podman), le service API ferme les connexions inactives :
     * un second conteneur, créé après plusieurs secondes sans appel Docker, échoue (« Relais brisé (pipe) » /
     * « Pipe closed ») sur la connexion réutilisée. Démarré une fois, il n'est plus arrêté avant la fin de la JVM.
     */
    private static PostgreSQLContainer sharedPostgres;

    @SuppressWarnings("resource")
    private static synchronized PostgreSQLContainer sharedPostgres() {
        if (sharedPostgres == null) {
            String imageName = new ImageFromDockerfile("copilot-backend-postgres-test", false)
                    .withFileFromPath(".", Path.of("src/test/resources/docker"))
                    .withFileFromPath("init-extensions.sql", Path.of("src/test/resources/init-extensions.sql"))
                    .withFileFromPath("docker/pg-textsearch-postgresql-18_1.2.0-1_amd64.deb", Path.of("src/test/resources/docker/pg-textsearch-postgresql-18_1.2.0-1_amd64.deb"))
                    .get();
            sharedPostgres = new PostgreSQLContainer(DockerImageName.parse(imageName).asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("pg_db")
                    .withUsername("admin")
                    .withPassword("pg_password");
        }
        return sharedPostgres;
    }

    /**
     * Démarré par Spring Boot ({@code start()} est sans effet s'il tourne déjà). Pas de méthode de destruction : la
     * fermeture d'un contexte ne doit pas l'arrêter pour les autres ; Spring Boot le ferme à l'arrêt des contextes
     * (fin de la JVM) et Ryuk le supprime en dernier recours.
     */
    @Bean(destroyMethod = "")
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return sharedPostgres();
    }

    @Bean
    Callback flywayInitUsersCallback() {
        return new Callback() {
            /** Seulement si des migrations ont été appliquées : la base partagée n'est peuplée qu'une fois. */
            @Override
            public boolean supports(Event event, Context context) {
                return event == Event.AFTER_MIGRATE_APPLIED;
            }

            @Override
            public boolean canHandleInTransaction(Event event, Context context) {
                return true;
            }

            @Override
            public void handle(Event event, Context context) {
                var populator = new ResourceDatabasePopulator(new ClassPathResource("init-users.sql"));
                populator.populate(context.getConnection());
            }

            @Override
            public String getCallbackName() {
                return "initUsersCallback";
            }
        };
    }
}
