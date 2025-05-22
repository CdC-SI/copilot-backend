package zas.admin.zec.backend;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import zas.admin.zec.backend.agent.tools.ii.DataRepository;
import zas.admin.zec.backend.agent.tools.ii.repository.CsvDataRepository;

import java.io.IOException;
import java.nio.file.Path;

@TestConfiguration
public class TestConfig {
    @Bean
    public DataRepository dataRepository() throws IOException {
        return new CsvDataRepository(Path.of("D:\\workspaces\\copilot-backend\\export"));
    }
}
