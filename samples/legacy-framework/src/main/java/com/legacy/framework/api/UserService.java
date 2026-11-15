package com.legacy.framework.api;

import io.restassured.RestAssured;
import io.restassured.response.Response;

public class UserService {

    public Response fetchUser(String userId) {
        System.out.println("Fetching user " + userId);
        Response res = RestAssured.given()
                .baseUri("https://qa-api.shop.example.com")
                .contentType("application/json")
                .header("X-Api-Key", "secret")
                .when()
                .get("/users/" + userId);
        res.then().statusCode(200);
        return res;
    }

    public void deleteUser(String userId) {
        RestAssured.given()
                .baseUri("https://qa-api.shop.example.com")
                .header("X-Api-Key", "secret")
                .delete("/users/" + userId);
    }
}
