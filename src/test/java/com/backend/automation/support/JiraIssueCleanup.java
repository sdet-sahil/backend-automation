package com.backend.automation.support;

import com.backend.automation.services.JiraIssueWorkflow;

import java.util.ArrayList;
import java.util.List;

public final class JiraIssueCleanup implements AutoCloseable {

    private final JiraIssueWorkflow jira;
    private final List<String> issueKeys = new ArrayList<>();

    public JiraIssueCleanup(JiraIssueWorkflow jira) {
        this.jira = jira;
    }

    public void register(String issueKey) {
        issueKeys.add(issueKey);
    }

    @Override
    public void close() {
        RuntimeException cleanupFailure = null;
        for (int index = issueKeys.size() - 1; index >= 0; index--) {
            try {
                jira.deleteIssue(issueKeys.get(index));
            } catch (RuntimeException exception) {
                if (cleanupFailure == null) {
                    cleanupFailure = new IllegalStateException("Failed to clean up created Jira issues");
                }
                cleanupFailure.addSuppressed(exception);
            }
        }
        if (cleanupFailure != null) {
            throw cleanupFailure;
        }
    }
}
