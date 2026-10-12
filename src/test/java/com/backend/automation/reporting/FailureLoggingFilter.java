package com.backend.automation.reporting;

import io.restassured.filter.Filter;
import io.restassured.filter.FilterContext;
import io.restassured.http.Header;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class FailureLoggingFilter implements Filter {

    private static final int MAX_CAPTURE_LENGTH = 20_000;
    private static final Pattern SENSITIVE_JSON_VALUE = Pattern.compile(
            "(?i)(\"[^\"]*(?:token|password|authorization|email|accountid)[^\"]*\"\\s*:\\s*)\"[^\"]*\"");
    private static final Pattern SENSITIVE_TEXT_VALUE = Pattern.compile(
            "(?i)((?:token|password|authorization|email|accountid)\\s*[:=]\\s*)(\"[^\"]*\"|'[^']*'|[^,;\\s]+)");
    private static final Pattern URI_CREDENTIALS = Pattern.compile("(?i)(https?://)[^/@]+@");
    private static final Pattern SENSITIVE_QUERY_VALUE = Pattern.compile(
            "(?i)([?&](?:token|password|email|authorization|api[_-]?key)=)[^&]*");

    private static final ThreadLocal<List<HttpExchange>> EXCHANGES =
            ThreadLocal.withInitial(ArrayList::new);

    @Override
    public Response filter(
            FilterableRequestSpecification request,
            FilterableResponseSpecification responseSpecification,
            FilterContext context) {
        String method = request.getMethod();
        String uri = sanitizeUri(request.getURI());
        String requestHeaders = sanitizeHeaders(request.getHeaders().asList());
        String requestBody = sanitizeBody(request.getBody());

        try {
            Response response = context.next(request, responseSpecification);
            EXCHANGES.get().add(new HttpExchange(
                    method,
                    uri,
                    requestHeaders,
                    requestBody,
                    response.statusCode(),
                    sanitizeHeaders(response.getHeaders().asList()),
                    sanitizeBody(response.asString()),
                    null));
            return response;
        } catch (RuntimeException exception) {
            EXCHANGES.get().add(new HttpExchange(
                    method, uri, requestHeaders, requestBody, -1, "", "",
                    exception.getClass().getSimpleName() + ": " + sanitizeText(exception.getMessage())));
            throw exception;
        }
    }

    public static void clear() {
        EXCHANGES.remove();
    }

    public static List<HttpExchange> drain() {
        List<HttpExchange> captured = List.copyOf(EXCHANGES.get());
        EXCHANGES.remove();
        return captured;
    }

    private static String sanitizeHeaders(List<Header> headers) {
        StringBuilder result = new StringBuilder();
        for (Header header : headers) {
            if (!result.isEmpty()) {
                result.append('\n');
            }
            String name = header.getName();
            String value = isSensitiveHeader(name) ? "[REDACTED]" : sanitizeText(header.getValue());
            result.append(name).append(": ").append(value);
        }
        return result.toString();
    }

    private static boolean isSensitiveHeader(String name) {
        String normalized = name.toLowerCase(Locale.ROOT);
        return normalized.equals("authorization")
                || normalized.equals("proxy-authorization")
                || normalized.equals("cookie")
                || normalized.equals("set-cookie")
                || normalized.contains("token")
                || normalized.contains("api-key");
    }

    private static String sanitizeUri(String uri) {
        String withoutCredentials = URI_CREDENTIALS.matcher(uri).replaceAll("$1[REDACTED]@");
        return SENSITIVE_QUERY_VALUE.matcher(withoutCredentials).replaceAll("$1[REDACTED]");
    }

    private static String sanitizeBody(Object body) {
        if (body == null) {
            return "";
        }
        String text = sanitizeText(String.valueOf(body));
        return SENSITIVE_JSON_VALUE.matcher(text).replaceAll("$1\"[REDACTED]\"");
    }

    private static String sanitizeText(String text) {
        if (text == null) {
            return "";
        }
        text = SENSITIVE_TEXT_VALUE.matcher(text).replaceAll("$1[REDACTED]");
        if (text.length() <= MAX_CAPTURE_LENGTH) {
            return text;
        }
        return text.substring(0, MAX_CAPTURE_LENGTH) + "\n[truncated]";
    }

    public record HttpExchange(
            String method,
            String uri,
            String requestHeaders,
            String requestBody,
            int responseStatus,
            String responseHeaders,
            String responseBody,
            String error) {
    }
}
