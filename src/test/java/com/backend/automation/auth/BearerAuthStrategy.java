package com.backend.automation.auth;

import io.restassured.specification.RequestSpecification;

public class BearerAuthStrategy implements AuthStrategy {
    private final String token;

    public BearerAuthStrategy(String token) {
        this.token = token;
    }

    @Override
    public RequestSpecification apply(RequestSpecification requestSpec) {
        return requestSpec.header("Authorization", "Bearer " + token);
    }
}
