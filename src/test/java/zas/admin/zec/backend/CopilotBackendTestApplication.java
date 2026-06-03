package zas.admin.zec.backend;

import org.springframework.boot.SpringApplication;

public class CopilotBackendTestApplication {
    public static void main(String[] args) {
        System.setProperty("LOG_APPENDER", "LOCAL");
        SpringApplication
                .from(CopilotBackendApplication::main)
                .with(LocalConfig.class)
                .run(args);
    }
}
