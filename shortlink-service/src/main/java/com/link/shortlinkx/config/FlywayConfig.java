package com.link.shortlinkx.config;

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

@Configuration
public class FlywayConfig {

    @Value("${spring.flyway.url}")
    private String url;

    @Value("${spring.flyway.user}")
    private String user;

    @Value("${spring.flyway.password}")
    private String password;

    @Bean(initMethod = "migrate")
    public Flyway flyway() throws Exception {
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
