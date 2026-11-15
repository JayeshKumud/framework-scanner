package com.legacy.framework.api;

import io.restassured.response.Response;

import static io.restassured.RestAssured.given;

/** Calls the users service. */
public class UserApi {

    private static final String USERS = "/users/";

    /** Reads one user and checks that the call succeeded. */
    public Response getUser(String id) {
        Response response = given()
                .baseUri("https://qa-api.shop.example.com")
                .contentType("application/json")
                .header("X-Api-Key", "secret")
                .when()
                .get(USERS + id);
        response.then().statusCode(200);
        return response;
    }

    public Response createUser(String first, String last, String email, String phone, String city, boolean admin) {
        String body = "{\"first\":\"" + first + "\",\"last\":\"" + last + "\",\"email\":\"" + email + "\"}";
        return given()
                .baseUri("https://qa-api.shop.example.com")
                .contentType("application/json")
                .header("X-Api-Key", "secret")
                .body(body)
                .when()
                .post("/users")
                .then().statusCode(201)
                .extract().response();
    }
}
