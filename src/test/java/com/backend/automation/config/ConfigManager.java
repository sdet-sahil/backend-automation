package com.backend.automation.config;

public final class ConfigManager {

    private static final ApiConfig API_CONFIG = ApiConfig.fromEnvironment(ConfigLoader.getEnvironment());

    private ConfigManager() {
    }

    public static ApiConfig getApiConfig() {
        return API_CONFIG;
    }

    public static String getProperty(String key) {
        return ConfigLoader.getProperty(key);
    }

    public static String getProperty(String key, String env) {
        return ConfigLoader.getProperty(key, env);
    }
}
