package com.doc_manag.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.core.env.PropertiesPropertySource;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;

/**
 * Loads the project's .env file no matter which folder the app is started from
 * (IDE run configs often use a different working directory than the project root).
 *
 * Lookup order: the working directory and its parents, then the folder the compiled
 * classes live in (target/classes) and its parents. Real environment variables always win.
 * In a packaged jar / Docker image there is usually no .env, and nothing happens.
 */
public class DotEnvLoader implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    private static final Logger log = LoggerFactory.getLogger(DotEnvLoader.class);
    private static final String FILE_NAME = ".env";
    private static final String SOURCE_NAME = "dotenv";

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        ConfigurableEnvironment env = event.getEnvironment();
        if (env.getPropertySources().contains(SOURCE_NAME)) {
            return;
        }

        Optional<Path> file = findUpwards(Path.of("").toAbsolutePath())
                .or(() -> classesDir().flatMap(DotEnvLoader::findUpwards));

        if (file.isEmpty()) {
            log.info("No {} file found (working directory: {}); using environment variables only",
                    FILE_NAME, Path.of("").toAbsolutePath());
            return;
        }

        try {
            Properties props = PropertiesLoaderUtils.loadProperties(new FileSystemResource(file.get()));
            // Right after OS environment variables, so a real env var always overrides .env
            env.getPropertySources().addAfter(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                    new PropertiesPropertySource(SOURCE_NAME, props));
            log.info("Loaded {} variables from {}", props.size(), file.get());
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + file.get(), e);
        }
    }

    private static Optional<Path> findUpwards(Path start) {
        for (Path dir = start; dir != null; dir = dir.getParent()) {
            Path candidate = dir.resolve(FILE_NAME);
            if (Files.isRegularFile(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private static Optional<Path> classesDir() {
        try {
            var location = DotEnvLoader.class.getProtectionDomain().getCodeSource().getLocation();
            Path path = Path.of(location.toURI());
            return Files.isDirectory(path) ? Optional.of(path) : Optional.empty();
        } catch (URISyntaxException | SecurityException | NullPointerException e) {
            return Optional.empty();
        }
    }
}
