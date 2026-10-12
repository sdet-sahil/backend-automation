# Jira Integration Tests

This project contains Jira integration tests built with Java, RestAssured, and TestNG.

## Prerequisites

- Java 17+
- Maven 3.9+

## Run tests

```bash
mvn test
```

The suite runs both Jira issue-creation and parent-child workflow tests. Tests
delete the Jira issues they create after the assertions complete. CI requires
Jira credentials and fails before running tests if required configuration is
missing.
TestNG also writes `target/surefire-reports/jira-test-report.html`. HTTP
request/response details are included only for failed tests; authorization,
cookie, and common credential fields are redacted. Successful and skipped tests
show their result without HTTP payloads.

GitHub Actions runs all Jira tests in one job for pushes, pull requests, and
manual workflow runs. Configure the repository variables
`JIRA_BASE_URL`, `JIRA_PROJECT_KEY`, `JIRA_ISSUE_TYPE`,
`JIRA_CHILD_DONE_TIMEOUT_MS`, and `JIRA_CHILD_DONE_POLL_INTERVAL_MS`, plus the
repository secrets `JIRA_EMAIL`, `JIRA_API_TOKEN`, and
`JIRA_REQUIRED_FIELDS_JSON`. The CI account must be able to create, view,
transition, and delete issues in the Jira project. Set
`JIRA_REQUIRED_FIELDS_JSON` to `{}` only if no extra Jira fields are required.

## Jira issue creation test

The TestNG suite includes an independent Jira Cloud issue-creation test. Configure
these environment variables before running it:

```bash
export JIRA_BASE_URL="https://your-site.atlassian.net"
export JIRA_EMAIL="your-account-email"
export JIRA_API_TOKEN="your-api-token"
export JIRA_PROJECT_KEY="your-project-key"
export JIRA_ISSUE_TYPE="the-project-supported-issue-type-name-or-id"
mvn -Dtest=JiraIssueApiTest test
```

Run `mvn test` to execute it alongside the other Jira workflow test.

The test checks the project’s Jira v3 create metadata before creating an issue, so
it will not assume that an issue type is available. If the selected type has
additional required fields, provide their Jira field IDs and values as a JSON
object in `JIRA_REQUIRED_FIELDS_JSON`; for example:

```bash
export JIRA_REQUIRED_FIELDS_JSON='{"customfield_12345":"Example value"}'
```

The TestNG scenario expresses the behavior (create an issue and verify its
returned key and ID), then deletes the created issue. Jira metadata lookup,
required-field validation, request construction, and deletion live in the
`JiraIssueWorkflow` business service.

Do not commit API tokens or other credentials. Locally, the test skips without
making an authenticated request when any of the five required settings above is
missing. It saves the created issue key in the TestNG context as
`createdJiraIssueKey`.

The same settings can be supplied through JVM properties or the active
`config/<env>.properties` file using `jira.base.url`, `jira.email`,
`jira.api.token`, `jira.project.key`, `jira.issue.type`, and
`jira.required.fields.json`. Environment variables take precedence.

## Jira parent-child workflow test

The separate TestNG scenario creates a Task and its project-supported Sub-task,
transitions the parent to Done using Jira's available transitions, and polls
the child's status until it reaches Done. This verifies that the project's
parent-child automation is configured. After each creation, it reads the issue
back and checks the project, issue type, summary, and (for the child) parent
key. The test deletes the child and parent after the checks, including when a
test assertion or workflow step fails.

Run only this scenario with:

```bash
mvn -Dtest=JiraParentChildWorkflowTest test
```

The polling timeout and interval default to 180000 ms and 5000 ms. Override them
with `JIRA_CHILD_DONE_TIMEOUT_MS` and `JIRA_CHILD_DONE_POLL_INTERVAL_MS`, or set
`jira.child.done.timeout.ms` and `jira.child.done.poll.interval.ms` in the active
properties file. The test retains the created issue keys in TestNG context as
`jiraParentIssueKey` and `jiraChildIssueKey`.

Jira settings can also be supplied as JVM properties or in an optional local
`config/<env>.properties` file. Do not commit credentials or API tokens; use
environment variables or your CI secret store for sensitive values.

The TestNG suite is defined in [testng.xml](testng.xml), and the Jira workflow
is implemented by `JiraIssueWorkflow`.
