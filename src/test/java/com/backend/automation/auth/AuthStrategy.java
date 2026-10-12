package com.backend.automation.auth;

import io.restassured.specification.RequestSpecification;

public interface AuthStrategy {
    RequestSpecification apply(RequestSpecification requestSpec);
}
