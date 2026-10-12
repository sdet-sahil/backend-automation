package com.backend.automation.client;

import com.backend.automation.auth.AuthStrategy;
import com.backend.automation.config.ApiConfig;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

public final class RequestSpecFactory {

    private RequestSpecFactory() {
    }

    public static RequestSpecification create(ApiConfig config, AuthStrategy authStrategy) {
        RequestSpecification requestSpec = new RequestSpecBuilder()
                .setBaseUri(config.getBaseUri())
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON)
                .build();

        if (authStrategy != null) {
            return authStrategy.apply(requestSpec);
        }
        return requestSpec;
    }
}
