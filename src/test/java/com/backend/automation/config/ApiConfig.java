package com.backend.automation.config;

public class ApiConfig {
    private final String environment;
    private final String baseUri;
    private final int timeoutMs;

    public ApiConfig(String environment, String baseUri, int timeoutMs) {
        this.environment = environment;
        this.baseUri = baseUri;
        this.timeoutMs = timeoutMs;
    }

    public static ApiConfig fromEnvironment(String env) {
        String normalizedEnvironment = env == null || env.isBlank() ? ConfigLoader.getEnvironment() : env;
        String baseUri = ConfigLoader.getProperty("base.uri", normalizedEnvironment);
        String timeoutValue = ConfigLoader.getProperty("api.timeout.ms", normalizedEnvironment);
        int timeoutMs = timeoutValue != null ? Integer.parseInt(timeoutValue) : 30000;

        return new ApiConfig(normalizedEnvironment, baseUri, timeoutMs);
    }

    public String getEnvironment() {
        return environment;
    }

    public String getBaseUri() {
        return baseUri;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }
}
