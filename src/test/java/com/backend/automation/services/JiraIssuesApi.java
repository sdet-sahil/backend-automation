package com.backend.automation.services;

import com.backend.automation.client.ApiClient;
import groovy.json.JsonOutput;
import io.restassured.response.Response;

import java.util.Map;

public class JiraIssuesApi {

    private final ApiClient apiClient;

    public JiraIssuesApi(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public Response getProjectIssueTypes(String projectKey, int startAt) {
        return apiClient.get("/rest/api/3/issue/createmeta/" + encodePathSegment(projectKey)
                + "/issuetypes?startAt=" + startAt + "&maxResults=100");
    }

    public Response getIssueTypeFields(String projectKey, String issueTypeId, int startAt) {
        return apiClient.get("/rest/api/3/issue/createmeta/" + encodePathSegment(projectKey)
                + "/issuetypes/" + encodePathSegment(issueTypeId)
                + "?startAt=" + startAt + "&maxResults=100");
    }

    public Response createIssue(Map<String, Object> payload) {
        return apiClient.post("/rest/api/3/issue", JsonOutput.toJson(payload));
    }

    public Response getIssue(String issueKey) {
        return apiClient.get("/rest/api/3/issue/" + encodePathSegment(issueKey) + "?fields=status");
    }

    public Response getIssueDetails(String issueKey) {
        return apiClient.get("/rest/api/3/issue/" + encodePathSegment(issueKey)
                + "?fields=project,issuetype,summary,parent");
    }

    public Response deleteIssue(String issueKey) {
        return apiClient.delete("/rest/api/3/issue/" + encodePathSegment(issueKey));
    }

    public Response getTransitions(String issueKey) {
        return apiClient.get("/rest/api/3/issue/" + encodePathSegment(issueKey) + "/transitions");
    }

    public Response transitionIssue(String issueKey, String transitionId) {
        return apiClient.post(
                "/rest/api/3/issue/" + encodePathSegment(issueKey) + "/transitions",
                JsonOutput.toJson(Map.of("transition", Map.of("id", transitionId))));
    }

    private static String encodePathSegment(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20");
    }
}
