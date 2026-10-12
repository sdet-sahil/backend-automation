package com.backend.automation.reporting;

import com.backend.automation.reporting.FailureLoggingFilter.HttpExchange;
import io.restassured.RestAssured;
import org.testng.IReporter;
import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestResult;
import org.testng.ISuite;
import org.testng.ISuiteListener;
import org.testng.ISuiteResult;
import org.testng.xml.XmlSuite;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;


public final class FailureHtmlReportListener implements ITestListener, ISuiteListener, IReporter {

    private static final String EXCHANGES_ATTRIBUTE = "failureHttpExchanges";
    private static final List<ReportTest> TEST_RESULTS = new ArrayList<>();

    @Override
    public void onStart(ISuite suite) {
        synchronized (TEST_RESULTS) {
            TEST_RESULTS.clear();
        }
        RestAssured.filters(new FailureLoggingFilter());
    }

    @Override
    public void onTestStart(ITestResult result) {
        FailureLoggingFilter.clear();
    }

    @Override
    public void onTestSuccess(ITestResult result) {
        record(result, "PASSED");
        FailureLoggingFilter.clear();
    }

    @Override
    public void onTestFailure(ITestResult result) {
        result.setAttribute(EXCHANGES_ATTRIBUTE, FailureLoggingFilter.drain());
        record(result, "FAILED");
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        record(result, "SKIPPED");
        FailureLoggingFilter.clear();
    }

    @Override
    public void onTestFailedButWithinSuccessPercentage(ITestResult result) {
        onTestFailure(result);
    }

    @Override
    public void generateReport(List<XmlSuite> xmlSuites, List<ISuite> suites, String outputDirectory) {
        for (ISuite suite : suites) {
            for (ISuiteResult suiteResult : suite.getResults().values()) {
                ITestContext context = suiteResult.getTestContext();
                collect(context.getPassedTests().getAllResults(), "PASSED");
                collect(context.getFailedTests().getAllResults(), "FAILED");
                collect(context.getSkippedTests().getAllResults(), "SKIPPED");
            }
        }

        List<ReportTest> results;
        synchronized (TEST_RESULTS) {
            results = TEST_RESULTS.stream().distinct()
                    .sorted(Comparator.comparing(ReportTest::className).thenComparing(ReportTest::methodName))
                    .toList();
        }

        Path reportPath = Path.of(outputDirectory, "jira-test-report.html");
        try {
            Files.createDirectories(reportPath.getParent());
            Files.writeString(reportPath, render(results), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not write HTML test report to " + reportPath, exception);
        }
    }

    private static void collect(Iterable<ITestResult> results, String status) {
        for (ITestResult result : results) {
            record(result, status);
        }
    }

    private static void record(ITestResult result, String status) {
        Object captured = result.getAttribute(EXCHANGES_ATTRIBUTE);
        List<HttpExchange> exchanges = captured instanceof List<?> list
                ? list.stream().filter(HttpExchange.class::isInstance).map(HttpExchange.class::cast).toList()
                : List.of();
        String errorMessage = result.getThrowable() == null
                ? ""
                : sanitizeError(result.getThrowable().getClass().getSimpleName() + ": "
                        + result.getThrowable().getMessage());
        ReportTest reportTest = new ReportTest(
                result.getTestClass() == null ? "" : result.getTestClass().getName(),
                result.getMethod().getMethodName(),
                status,
                errorMessage,
                status.equals("FAILED") ? exchanges : List.of(),
                result.getStartMillis());
        synchronized (TEST_RESULTS) {
            TEST_RESULTS.add(reportTest);
        }
    }

    private static String sanitizeError(String message) {
        return message == null ? "" : message.replaceAll(
                "(?i)(token|password|authorization|email|accountid)(\\s*[:=]\\s*)[^,;\\s]+",
                "$1$2[REDACTED]");
    }

    private static String render(List<ReportTest> results) {
        long failed = results.stream().filter(test -> test.status().equals("FAILED")).count();
        long passed = results.stream().filter(test -> test.status().equals("PASSED")).count();
        long skipped = results.stream().filter(test -> test.status().equals("SKIPPED")).count();
        StringBuilder html = new StringBuilder("""
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>API Test Report</title>
                  <style>
                    :root{color-scheme:light;--ink:#172033;--muted:#667085;--line:#d9e0ea;--panel:#fff;--page:#f3f6fa;--red:#b42318;--green:#067647;--amber:#93370d;--blue:#175cd3}
                    *{box-sizing:border-box}
                    body{font:14px/1.5 system-ui,-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif;margin:0;background:var(--page);color:var(--ink)}
                    main{max-width:1120px;margin:0 auto;padding:32px 20px 56px}
                    h1,h2,h3,h4,p{margin-top:0}
                    h1{font-size:28px;margin-bottom:4px}
                    h2{font-size:20px;margin:0 0 16px}
                    .subtitle,.muted{color:var(--muted)}
                    .summary{display:grid;grid-template-columns:repeat(3,minmax(120px,1fr));gap:12px;margin:24px 0 32px}
                    .metric,.test-row,.failure-card{background:var(--panel);border:1px solid var(--line);border-radius:10px}
                    .metric{padding:16px 18px}
                    .metric strong{display:block;font-size:26px;line-height:1.2}
                    .metric span{color:var(--muted)}
                    .test-list{display:grid;gap:8px;margin-bottom:32px}
                    .test-row{display:flex;align-items:center;gap:12px;padding:11px 14px;overflow-wrap:anywhere}
                    .test-name{font-family:ui-monospace,SFMono-Regular,Menlo,monospace}
                    .badge{font-size:11px;font-weight:700;letter-spacing:.04em;border-radius:999px;padding:3px 9px;min-width:68px;text-align:center}
                    .badge.PASSED{color:var(--green);background:#ecfdf3}.badge.FAILED{color:var(--red);background:#fef3f2}.badge.SKIPPED{color:var(--amber);background:#fffaeb}
                    .failure-card{padding:18px 20px;margin:12px 0 18px;border-left:4px solid var(--red)}
                    .failure-title{font-weight:700;font-size:16px;overflow-wrap:anywhere}
                    .failure-message{color:var(--red);background:#fff6f5;border-radius:6px;padding:10px 12px;overflow-wrap:anywhere}
                    details.exchange{border:1px solid var(--line);border-radius:8px;margin-top:12px;background:#fff;overflow:hidden}
                    details.exchange>summary{cursor:pointer;list-style:none;padding:12px 14px;font-weight:650;display:flex;gap:10px;align-items:center;flex-wrap:wrap}
                    details.exchange>summary::-webkit-details-marker{display:none}
                    .method{background:#eff8ff;color:var(--blue);border-radius:4px;padding:2px 7px;font:700 11px ui-monospace,monospace}
                    .http-status{color:var(--muted);margin-left:auto}
                    .exchange-content{padding:0 14px 14px}
                    .direction{margin-top:12px;border-top:1px solid var(--line);padding-top:10px}
                    .direction h4{font-size:13px;margin:0 0 8px}
                    pre{white-space:pre-wrap;overflow-wrap:anywhere;background:#f6f8fb;border:1px solid #e7ebf1;border-radius:6px;padding:10px 12px;margin:6px 0;font:12px/1.5 ui-monospace,SFMono-Regular,Menlo,monospace}
                    .empty{background:#ecfdf3;border:1px solid #abefc6;color:#067647;border-radius:8px;padding:14px}
                    @media(max-width:560px){main{padding:22px 12px}.summary{gap:7px}.metric{padding:12px}.metric strong{font-size:22px}.test-row{align-items:flex-start;flex-direction:column}}
                  </style>
                </head>
                <body>
                <main>
                <h1>API Test Report</h1>
                <p class="subtitle">Test results and HTTP diagnostics for failed tests</p>
                <section class="summary" aria-label="Test totals">
                  <div class="metric"><strong>%d</strong><span>Passed</span></div>
                  <div class="metric"><strong>%d</strong><span>Failed</span></div>
                  <div class="metric"><strong>%d</strong><span>Skipped</span></div>
                </section>
                <section><h2>Test results</h2><div class="test-list">
                """.formatted(passed, failed, skipped));
        for (ReportTest test : results) {
            html.append("<div class=\"test-row\"><span class=\"badge ")
                    .append(escape(test.status())).append("\">")
                    .append(escape(test.status())).append("</span><span class=\"test-name\">")
                    .append(escape(test.className())).append("#")
                    .append(escape(test.methodName())).append("</span></div>");
        }
        html.append("</div></section><section><h2>Failure details</h2>");
        if (failed == 0) {
            html.append("<p class=\"empty\">No failed tests in this run.</p>");
        } else {
            for (ReportTest test : results) {
                if (!test.status().equals("FAILED")) {
                    continue;
                }
                html.append("<article class=\"failure-card\"><p class=\"failure-title\">")
                        .append(escape(test.className())).append("#")
                        .append(escape(test.methodName())).append("</p><p class=\"failure-message\">")
                        .append(escape(test.errorMessage())).append("</p>");
                if (test.exchanges().isEmpty()) {
                    html.append("<p class=\"muted\">No HTTP exchange was captured for this failure.</p>");
                }
                for (HttpExchange exchange : test.exchanges()) {
                    html.append("<details class=\"exchange\" open><summary><span class=\"method\">")
                            .append(escape(exchange.method())).append("</span><span>")
                            .append(escape(exchange.uri())).append("</span><span class=\"http-status\">")
                            .append(exchange.responseStatus() < 0
                                    ? "Request failed" : "HTTP " + exchange.responseStatus())
                            .append("</span></summary><div class=\"exchange-content\">");
                    appendDirection(html, "Request", exchange.requestHeaders(), exchange.requestBody());
                    appendDirection(html, "Response", exchange.responseHeaders(), exchange.responseBody());
                    appendExchangePart(html, "Transport error", exchange.error());
                    html.append("</div></details>");
                }
                html.append("</article>");
            }
        }
        return html.append("</section></main></body></html>").toString();
    }

    private static void appendDirection(
            StringBuilder html, String direction, String headers, String body) {
        if ((headers == null || headers.isBlank()) && (body == null || body.isBlank())) {
            return;
        }
        html.append("<div class=\"direction\"><h4>").append(escape(direction)).append("</h4>");
        appendExchangePart(html, "Headers", headers);
        appendExchangePart(html, "Body", body);
        html.append("</div>");
    }

    private static void appendExchangePart(StringBuilder html, String title, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        html.append("<p class=\"muted\">").append(escape(title)).append("</p><pre>")
                .append(escape(value)).append("</pre>");
    }

    private static String escape(String value) {
        return value == null ? "" : value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private record ReportTest(
            String className,
            String methodName,
            String status,
            String errorMessage,
            List<HttpExchange> exchanges,
            long startMillis) {
    }
}
