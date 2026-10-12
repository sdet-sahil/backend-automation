package com.backend.automation.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Properties;

public final class ConfigLoader {

    private static final String DEFAULT_ENV = "qa";
    private static final String CONFIG_DIRECTORY = "config";

    private ConfigLoader() {
    }

    public static String getEnvironment() {
        String env = System.getProperty("env");
        if (env == null || env.isBlank()) {
            env = System.getProperty("environment");
        }
        if (env == null || env.isBlank()) {
            env = System.getenv("APP_ENV");
        }
        return normalizeEnv(env);
    }

    public static String getProperty(String key) {
        return getProperty(key, getEnvironment());
    }

    public static String getProperty(String key, String env) {
        String normalizedEnv = normalizeEnv(env);
        Properties properties = loadProperties(normalizedEnv);

        String value = properties.getProperty(key);
        if (value != null && !value.trim().isEmpty()) {
            return value.trim();
        }

        String systemValue = System.getProperty(key);
        if (systemValue != null && !systemValue.trim().isEmpty()) {
            return systemValue.trim();
        }

        return null;
    }

    private static Properties loadProperties(String env) {
        Properties properties = new Properties();
        String fileName = env + ".properties";
        Path projectConfigPath = Paths.get(CONFIG_DIRECTORY, fileName);

        try {
            if (Files.exists(projectConfigPath)) {
                try (InputStream inputStream = Files.newInputStream(projectConfigPath)) {
                    properties.load(inputStream);
                }
                return properties;
            }
        } catch (IOException ignored) {
            // Fall back to classpath lookup below.
        }

        String classpathLocation = "config/" + fileName;
        try (InputStream inputStream = ConfigLoader.class.getClassLoader().getResourceAsStream(classpathLocation)) {
            if (inputStream != null) {
                properties.load(inputStream);
            }
        } catch (IOException ignored) {
            // Ignore and return empty properties.
        }

        return properties;
    }

    private static String normalizeEnv(String env) {
        if (env == null || env.trim().isEmpty()) {
            return DEFAULT_ENV;
        }
        return env.trim().toLowerCase(Locale.ROOT);
    }
}
