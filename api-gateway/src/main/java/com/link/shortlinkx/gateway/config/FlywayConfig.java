package com.link.shortlinkx.gateway.config;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.util.FileCopyUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Configuration
@ConditionalOnProperty(prefix = "spring.flyway", name = "enabled", matchIfMissing = true)
public class FlywayConfig {

    @Value("${spring.flyway.url:${spring.datasource.url:}}")
    private String url;

    @Value("${spring.flyway.user:${spring.datasource.username:postgres}}")
    private String user;

    @Value("${spring.flyway.password:${spring.datasource.password:postgres}}")
    private String password;

    @Bean(initMethod = "migrate")
    public Flyway flyway() throws Exception {
        // Flyway's native scanner fails to read inside Spring Boot 3+ nested Fat JARs 
        // without the missing Spring Boot FlywayAutoConfiguration.
        // Workaround: Extract migrations to a temp directory using Spring's ResourceLoader,
        // which perfectly understands the nested jar protocol.
        
        File tempDir = Files.createTempDirectory("flyway_migrations").toFile();
        tempDir.deleteOnExit();

        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath*:db/migration/*.sql");

        for (Resource resource : resources) {
            File tempFile = new File(tempDir, resource.getFilename());
            tempFile.deleteOnExit();
            FileCopyUtils.copy(resource.getInputStream(), new FileOutputStream(tempFile));
        }

        return Flyway.configure()
                .dataSource(url, user, password)
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .locations("filesystem:" + tempDir.getAbsolutePath())
                .load();
    }
}
