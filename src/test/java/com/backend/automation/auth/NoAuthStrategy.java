package com.backend.automation.auth;

import io.restassured.specification.RequestSpecification;

public class NoAuthStrategy implements AuthStrategy {
    @Override
    public RequestSpecification apply(RequestSpecification requestSpec) {
        return requestSpec;
    }
}
