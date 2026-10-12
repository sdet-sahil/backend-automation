package com.backend.automation.config;

import java.util.ArrayList;
import java.util.List;

public final class JiraConfig {

    private final String baseUrl;
    private final String email;
    private final String apiToken;
    private final String projectKey;
    private final String issueType;
    private final String additionalFieldsJson;
    private final long childDoneTimeoutMs;
    private final long childDonePollIntervalMs;

    private JiraConfig(
            String baseUrl,
            String email,
            String apiToken,
            String projectKey,
            String issueType,
            String additionalFieldsJson,
            long childDoneTimeoutMs,
            long childDonePollIntervalMs) {
        this.baseUrl = baseUrl;
        this.email = email;
        this.apiToken = apiToken;
        this.projectKey = projectKey;
        this.issueType = issueType;
        this.additionalFieldsJson = additionalFieldsJson;
        this.childDoneTimeoutMs = childDoneTimeoutMs;
        this.childDonePollIntervalMs = childDonePollIntervalMs;
    }

    public static JiraConfig fromEnvironment() {
        return new JiraConfig(
                getValue("JIRA_BASE_URL", "jira.base.url"),
                getValue("JIRA_EMAIL", "jira.email"),
                getValue("JIRA_API_TOKEN", "jira.api.token"),
                getValue("JIRA_PROJECT_KEY", "jira.project.key"),
                getValue("JIRA_ISSUE_TYPE", "jira.issue.type"),
                getValue("JIRA_REQUIRED_FIELDS_JSON", "jira.required.fields.json"),
                getPositiveLongValue("JIRA_CHILD_DONE_TIMEOUT_MS", "jira.child.done.timeout.ms", 180_000),
                getPositiveLongValue(
                        "JIRA_CHILD_DONE_POLL_INTERVAL_MS", "jira.child.done.poll.interval.ms", 5_000));
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getEmail() {
        return email;
    }

    public String getApiToken() {
        return apiToken;
    }

    public String getProjectKey() {
        return projectKey;
    }

    public String getIssueType() {
        return issueType;
    }

    public String getAdditionalFieldsJson() {
        return additionalFieldsJson;
    }

    public long getChildDoneTimeoutMs() {
        return childDoneTimeoutMs;
    }

    public long getChildDonePollIntervalMs() {
        return childDonePollIntervalMs;
    }

    public List<String> getMissingRequiredSettings() {
        List<String> missing = new ArrayList<>();
        addIfMissing(missing, baseUrl, "JIRA_BASE_URL (or jira.base.url)");
        addIfMissing(missing, email, "JIRA_EMAIL (or jira.email)");
        addIfMissing(missing, apiToken, "JIRA_API_TOKEN (or jira.api.token)");
        addIfMissing(missing, projectKey, "JIRA_PROJECT_KEY (or jira.project.key)");
        addIfMissing(missing, issueType, "JIRA_ISSUE_TYPE (or jira.issue.type)");
        return missing;
    }

    private static String getValue(String environmentVariable, String property) {
        String value = System.getenv(environmentVariable);
        if (value == null || value.isBlank()) {
            value = ConfigManager.getProperty(property);
        }
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static long getPositiveLongValue(String environmentVariable, String property, long defaultValue) {
        String value = getValue(environmentVariable, property);
        if (value == null) {
            return defaultValue;
        }
        try {
            long parsed = Long.parseLong(value);
            if (parsed <= 0) {
                throw new IllegalArgumentException(property + " must be greater than zero");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(property + " must be a positive integer", exception);
        }
    }

    private static void addIfMissing(List<String> missing, String value, String setting) {
        if (value == null) {
            missing.add(setting);
        }
    }
}
