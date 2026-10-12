package com.backend.automation.tests;

import com.backend.automation.config.JiraConfig;
import com.backend.automation.services.JiraIssueWorkflow;
import com.backend.automation.services.JiraIssueWorkflow.CreatedIssue;
import com.backend.automation.services.JiraIssueWorkflow.PollResult;
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

public class JiraParentChildWorkflowTest {

    private static final String PARENT_ISSUE_KEY_ATTRIBUTE = "jiraParentIssueKey";
    private static final String CHILD_ISSUE_KEY_ATTRIBUTE = "jiraChildIssueKey";

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
    public void completingAParentTask_automaticallyCompletesItsSubtask(ITestContext testContext) {
        if (!missingSettings.isEmpty()) {
            throw new SkipException("Skipping Jira parent-child workflow; configure: "
                    + String.join(", ", missingSettings));
        }

        try (JiraIssueCleanup cleanup = new JiraIssueCleanup(jira)) {
            String uniqueLabel = UUID.randomUUID().toString();
            String parentSummary = "Parent workflow " + uniqueLabel;
            CreatedIssue parent = jira.createParentTask(parentSummary);
            cleanup.register(parent.key());
            testContext.setAttribute(PARENT_ISSUE_KEY_ATTRIBUTE, parent.key());
            assertCreatedIssue(parent, "parent Task");
            assertIssueFields(parent, "Task", parentSummary, null);

            String childSummary = "Child workflow " + uniqueLabel;
            CreatedIssue child = jira.createChildSubtask(childSummary, parent.key());
            cleanup.register(child.key());
            testContext.setAttribute(CHILD_ISSUE_KEY_ATTRIBUTE, child.key());
            assertCreatedIssue(child, "child Sub-task");
            assertIssueFields(child, "Subtask", childSummary, parent.key());

            PollResult parentResult = jira.transitionParentToDone(parent.key());
            assertEquals(parentResult.status(), "Done", "Parent Task should reach Done");

            PollResult childResult = jira.awaitChildDone(parent.key(), child.key());
            assertEquals(childResult.status(), "Done", "Child Sub-task should reach Done");
        }
    }

    private static void assertCreatedIssue(CreatedIssue issue, String description) {
        assertEquals(issue.statusCode(), 201, "Jira should create the " + description);
        assertNotNull(issue.key(), "Created " + description + " should have a key");
        assertFalse(issue.key().isBlank(), "Created " + description + " key should not be blank");
        assertNotNull(issue.id(), "Created " + description + " should have an ID");
        assertFalse(issue.id().isBlank(), "Created " + description + " ID should not be blank");
    }

    private void assertIssueFields(
            CreatedIssue issue, String expectedIssueType, String expectedSummary, String expectedParentKey) {
        assertEquals(issue.projectKey(), projectKey,
                "Created issue should be in the configured project");
        assertEquals(issue.issueType(), expectedIssueType, "Created issue should have the expected issue type");
        assertEquals(issue.summary(), expectedSummary, "Created issue should retain its unique summary");
        assertEquals(issue.parentKey(), expectedParentKey, "Created issue should have the expected parent");
    }
}
