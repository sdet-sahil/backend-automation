package com.backend.automation.services;

import com.backend.automation.auth.BasicAuthStrategy;
import com.backend.automation.client.ApiClient;
import com.backend.automation.config.ApiConfig;
import com.backend.automation.config.ConfigManager;
import com.backend.automation.config.JiraConfig;
import com.backend.automation.support.PollingUtil;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class JiraIssueWorkflow {

    private static final String DONE_STATUS = "Done";
    private static final String SUBTASK_NAME = "Subtask";

    private final JiraConfig config;
    private final JiraIssuesApi issuesApi;

    public JiraIssueWorkflow(JiraConfig config) {
        this.config = config;
        ApiConfig apiConfig = new ApiConfig(
                ConfigManager.getApiConfig().getEnvironment(),
                config.getBaseUrl(),
                ConfigManager.getApiConfig().getTimeoutMs());
        ApiClient client = new ApiClient(
                apiConfig,
                new BasicAuthStrategy(config.getEmail(), config.getApiToken()));
        this.issuesApi = new JiraIssuesApi(client);
    }

    public CreatedIssue createIssue(String summary) {
        return createIssue(summary, findIssueType(config.getIssueType(), false), null);
    }

    public CreatedIssue createParentTask(String summary) {
        Map<String, Object> taskType = findIssueType("Task", false);
        return createIssue(summary, taskType, null);
    }

    public CreatedIssue createChildSubtask(String summary, String parentKey) {
        Map<String, Object> subtaskType = findIssueType(SUBTASK_NAME, true);
        return createIssue(summary, subtaskType, parentKey);
    }

    public void deleteIssue(String issueKey) {
        Response response = issuesApi.deleteIssue(issueKey);
        if (response.statusCode() != 204) {
            throw new IllegalStateException("Deleting Jira issue " + issueKey
                    + " failed with HTTP " + response.statusCode());
        }
    }

    public PollResult transitionParentToDone(String parentKey) {
        String transitionId = findDoneTransition(parentKey);
        Response transitionResponse = issuesApi.transitionIssue(parentKey, transitionId);
        if (transitionResponse.statusCode() != 204) {
            throw new IllegalStateException("Transitioning parent issue " + parentKey
                    + " to Done failed with HTTP " + transitionResponse.statusCode());
        }
        return awaitIssueStatus(parentKey, DONE_STATUS);
    }

    public PollResult awaitChildDone(String parentKey, String childKey) {
        try {
            return awaitIssueStatus(childKey, DONE_STATUS);
        } catch (PollingUtil.PollingTimeoutException exception) {
            throw new IllegalStateException("Child issue " + childKey
                    + " did not reach Done after parent issue " + parentKey
                    + " reached Done. Verify the project's parent-child automation rule. "
                    + exception.getMessage(), exception);
        }
    }

    private CreatedIssue createIssue(
            String summary, Map<String, Object> issueType, String parentKey) {
        String issueTypeId = String.valueOf(issueType.get("id"));
        Map<String, Object> fields = new LinkedHashMap<>(readAdditionalFields());
        fields.put("project", Map.of("key", config.getProjectKey()));
        fields.put("issuetype", Map.of("id", issueTypeId));
        fields.put("summary", summary);
        if (parentKey != null) {
            fields.put("parent", Map.of("key", parentKey));
        }

        List<Map<String, Object>> fieldMetadata = getIssueTypeFields(issueTypeId);
        List<String> missingFields = findMissingRequiredFields(fieldMetadata, fields);
        if (!missingFields.isEmpty()) {
            throw new IllegalStateException(
                    "Configure required Jira field values in JIRA_REQUIRED_FIELDS_JSON: "
                            + String.join(", ", missingFields));
        }

        Response response = issuesApi.createIssue(Map.of("fields", fields));
        if (response.statusCode() != 201) {
            throw new IllegalStateException(
                    "Jira issue creation failed with HTTP " + response.statusCode());
        }
        String key = response.jsonPath().getString("key");
        String id = response.jsonPath().getString("id");
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("Jira issue creation returned HTTP 201 without an issue key");
        }
        if (id == null || id.isBlank()) {
            IllegalStateException exception =
                    new IllegalStateException("Jira issue creation returned HTTP 201 without an issue ID");
            deleteAfterFailedCreation(key, exception);
            throw exception;
        }

        Response issueDetails = issuesApi.getIssueDetails(key);
        if (issueDetails.statusCode() != 200) {
            IllegalStateException exception = new IllegalStateException("Jira created issue " + key
                    + " but reading it back failed with HTTP " + issueDetails.statusCode());
            deleteAfterFailedCreation(key, exception);
            throw exception;
        }

        return new CreatedIssue(
                response.statusCode(),
                key,
                id,
                issueDetails.jsonPath().getString("fields.project.key"),
                issueDetails.jsonPath().getString("fields.issuetype.name"),
                issueDetails.jsonPath().getString("fields.summary"),
                issueDetails.jsonPath().getString("fields.parent.key"));
    }

    private void deleteAfterFailedCreation(String issueKey, IllegalStateException creationFailure) {
        try {
            deleteIssue(issueKey);
        } catch (RuntimeException cleanupFailure) {
            creationFailure.addSuppressed(cleanupFailure);
        }
    }

    private Map<String, Object> findIssueType(String requestedName, boolean requireSubtask) {
        int startAt = 0;
        while (true) {
            Response response = issuesApi.getProjectIssueTypes(config.getProjectKey(), startAt);
            requireSuccessfulMetadataResponse(response, "Jira issue-type lookup");
            List<Map<String, Object>> issueTypes = response.jsonPath().getList("issueTypes");
            if (issueTypes != null) {
                for (Map<String, Object> issueType : issueTypes) {
                    String name = String.valueOf(issueType.get("name"));
                    if (requireSubtask ? isSubtaskType(issueType) : requestedName.equalsIgnoreCase(name)) {
                        return issueType;
                    }
                }
            }

            int total = response.jsonPath().getInt("total");
            int pageSize = response.jsonPath().getInt("maxResults");
            if (pageSize <= 0 || startAt + pageSize >= total) {
                String description = requireSubtask ? "Sub-task" : "Task";
                throw new IllegalStateException(
                        "Project " + config.getProjectKey() + " does not support the required "
                                + description + " issue type");
            }
            startAt += pageSize;
        }
    }

    private static boolean isSubtaskType(Map<String, Object> issueType) {
        if (Boolean.TRUE.equals(issueType.get("subtask"))) {
            return true;
        }
        String name = String.valueOf(issueType.get("name")).replace("-", "").replace(" ", "");
        return "subtask".equalsIgnoreCase(name);
    }

    private String findDoneTransition(String parentKey) {
        Response response = issuesApi.getTransitions(parentKey);
        requireSuccessfulMetadataResponse(response, "Parent transition lookup");
        List<Map<String, Object>> transitions = response.jsonPath().getList("transitions");
        if (transitions != null) {
            for (Map<String, Object> transition : transitions) {
                Map<?, ?> destination = (Map<?, ?>) transition.get("to");
                if (destination != null && DONE_STATUS.equalsIgnoreCase(String.valueOf(destination.get("name")))) {
                    Object transitionId = transition.get("id");
                    if (transitionId == null || String.valueOf(transitionId).isBlank()) {
                        throw new IllegalStateException(
                                "Parent transition to Done did not include a Jira transition ID");
                    }
                    return String.valueOf(transitionId);
                }
            }
        }
        throw new IllegalStateException(
                "Parent issue " + parentKey + " has no available transition whose destination status is Done");
    }

    private PollResult awaitIssueStatus(String issueKey, String expectedStatus) {
        long intervalMs = config.getChildDonePollIntervalMs();
        PollingUtil.PollResult<Response> pollResult = PollingUtil.await(
                "Jira issue " + issueKey + " to reach " + expectedStatus,
                config.getChildDoneTimeoutMs(),
                intervalMs,
                () -> {
                    Response response = issuesApi.getIssue(issueKey);
                    int httpStatus = response.statusCode();
                    if (!isTransient(httpStatus) && httpStatus != 200) {
                        throw new IllegalStateException("Cannot poll Jira issue " + issueKey
                                + ": GET issue returned HTTP " + httpStatus
                                + "; authentication, permission, and request errors are not retried");
                    }
                    return response;
                },
                response -> response.statusCode() == 200,
                response -> {
                    if (response.statusCode() != 200
                            || !expectedStatus.equalsIgnoreCase(
                                    response.jsonPath().getString("fields.status.name"))) {
                        throw new AssertionError("Jira issue " + issueKey
                                + " did not reach " + expectedStatus);
                    }
                },
                response -> response.statusCode() == 429
                        ? retryAfterMillis(response.getHeader("Retry-After"), System.currentTimeMillis())
                        : 0,
                response -> response.statusCode() == 200
                        ? response.jsonPath().getString("fields.status.name")
                        : "unavailable (transient HTTP " + response.statusCode() + ")");
        Response response = pollResult.value();
        return new PollResult(
                response.jsonPath().getString("fields.status.name"),
                response.statusCode(),
                pollResult.attempts(),
                pollResult.elapsedMs());
    }

    private static boolean isTransient(int statusCode) {
        return statusCode == 408 || statusCode == 429 || statusCode >= 500 && statusCode <= 599;
    }

    private static long retryAfterMillis(String retryAfter, long currentTimeMillis) {
        if (retryAfter == null || retryAfter.isBlank()) {
            return 0;
        }
        try {
            long seconds = Long.parseLong(retryAfter.trim());
            if (seconds <= 0) {
                return 0;
            }
            return seconds > Long.MAX_VALUE / 1_000 ? Long.MAX_VALUE : seconds * 1_000;
        } catch (NumberFormatException ignored) {
            try {
                long retryAtMillis = ZonedDateTime.parse(
                        retryAfter, DateTimeFormatter.RFC_1123_DATE_TIME.withLocale(Locale.US))
                        .toInstant()
                        .toEpochMilli();
                return Math.max(retryAtMillis - currentTimeMillis, 0);
            } catch (DateTimeParseException ignoredDate) {
                return 0;
            }
        }
    }

    private List<Map<String, Object>> getIssueTypeFields(String issueTypeId) {
        List<Map<String, Object>> fields = new ArrayList<>();
        int startAt = 0;
        while (true) {
            Response response = issuesApi.getIssueTypeFields(config.getProjectKey(), issueTypeId, startAt);
            requireSuccessfulMetadataResponse(response, "Jira create-field lookup");
            List<Map<String, Object>> page = response.jsonPath().getList("fields");
            if (page != null) {
                fields.addAll(page);
            }

            int total = response.jsonPath().getInt("total");
            int pageSize = response.jsonPath().getInt("maxResults");
            if (pageSize <= 0 || startAt + pageSize >= total) {
                return fields;
            }
            startAt += pageSize;
        }
    }

    private Map<String, Object> readAdditionalFields() {
        if (config.getAdditionalFieldsJson() == null) {
            return Map.of();
        }
        Map<String, Object> fields = JsonPath.from(config.getAdditionalFieldsJson()).getMap("$");
        if (fields == null) {
            throw new IllegalArgumentException("JIRA_REQUIRED_FIELDS_JSON must be a JSON object");
        }
        return fields;
    }

    private static void requireSuccessfulMetadataResponse(Response response, String operation) {
        if (response.statusCode() != 200) {
            throw new IllegalStateException(operation + " failed with HTTP " + response.statusCode());
        }
    }

    private static List<String> findMissingRequiredFields(
            List<Map<String, Object>> metadata, Map<String, Object> suppliedFields) {
        List<String> missing = new ArrayList<>();
        for (Map<String, Object> field : metadata) {
            if (Boolean.TRUE.equals(field.get("required"))) {
                String fieldId = String.valueOf(field.get("fieldId"));
                if (!suppliedFields.containsKey(fieldId)) {
                    missing.add(String.valueOf(field.get("name")) + " (" + fieldId + ")");
                }
            }
        }
        return missing;
    }

    public record CreatedIssue(
            int statusCode,
            String key,
            String id,
            String projectKey,
            String issueType,
            String summary,
            String parentKey) {
    }

    public record PollResult(String status, int httpStatus, int attempts, long elapsedMs) {
    }
}
