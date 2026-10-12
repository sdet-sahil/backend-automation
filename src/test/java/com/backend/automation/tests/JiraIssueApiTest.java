package com.backend.automation.tests;

import com.backend.automation.config.JiraConfig;
import com.backend.automation.services.JiraIssueWorkflow;
import com.backend.automation.services.JiraIssueWorkflow.CreatedIssue;
import com.backend.automation.support.JiraIssueCleanup;
import org.testng.ITestContext;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;
import java.util.UUID;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class JiraIssueApiTest {

    private static final String CREATED_ISSUE_KEY_ATTRIBUTE = "createdJiraIssueKey";

    private JiraIssueWorkflow jira;
    private String projectKey;
    private List<String> missingSettings;

    @BeforeClass
    public void prepareConfiguredJira() {
        JiraConfig config = JiraConfig.fromEnvironment();
        missingSettings = config.getMissingRequiredSettings();
        if (missingSettings.isEmpty()) {
            projectKey = config.getProjectKey();
            jira = new JiraIssueWorkflow(config);
        }
    }

    @Test
    public void creatingAnIssue_returnsItsKeyAndId(ITestContext testContext) {
        if (!missingSettings.isEmpty()) {
            throw new SkipException("Skipping Jira issue test; configure: "
                    + String.join(", ", missingSettings));
        }

        try (JiraIssueCleanup cleanup = new JiraIssueCleanup(jira)) {
            String summary = "API automation test " + UUID.randomUUID();
            CreatedIssue issue = jira.createIssue(summary);
            cleanup.register(issue.key());

            assertEquals(issue.statusCode(), 201, "Jira should create the issue");
            assertNotNull(issue.key(), "Created issue should have a key");
            assertFalse(issue.key().isBlank(), "Created issue key should not be blank");
            assertNotNull(issue.id(), "Created issue should have an ID");
            assertFalse(issue.id().isBlank(), "Created issue ID should not be blank");
            assertTrue(issue.key().regionMatches(
                            true, 0, projectKey + "-", 0, projectKey.length() + 1),
                    "Created issue should belong to the configured project");
            testContext.setAttribute(CREATED_ISSUE_KEY_ATTRIBUTE, issue.key());
        }
    }
}
