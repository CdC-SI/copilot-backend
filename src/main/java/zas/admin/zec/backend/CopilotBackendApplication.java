package zas.admin.zec.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CopilotBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(CopilotBackendApplication.class, args);
    }

}
