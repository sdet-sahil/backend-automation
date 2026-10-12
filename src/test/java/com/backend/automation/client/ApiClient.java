package com.backend.automation.client;

import com.backend.automation.auth.AuthStrategy;
import com.backend.automation.auth.NoAuthStrategy;
import com.backend.automation.config.ApiConfig;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import static io.restassured.RestAssured.given;

public class ApiClient {
    private final ApiConfig config;
    private final AuthStrategy authStrategy;

    public ApiClient(ApiConfig config) {
        this(config, new NoAuthStrategy());
    }

    public ApiClient(ApiConfig config, AuthStrategy authStrategy) {
        this.config = config;
        this.authStrategy = authStrategy;
    }

    public Response get(String path) {
        return buildRequestSpec().when().get(path);
    }

    public Response post(String path, Object body) {
        return buildRequestSpec().body(body).when().post(path);
    }

    public Response put(String path, Object body) {
        return buildRequestSpec().body(body).when().put(path);
    }

    public Response delete(String path) {
        return buildRequestSpec().when().delete(path);
    }

    public ApiClient withAuth(AuthStrategy newAuthStrategy) {
        return new ApiClient(config, newAuthStrategy);
    }

    private RequestSpecification buildRequestSpec() {
        return given().spec(RequestSpecFactory.create(config, authStrategy));
    }
}
