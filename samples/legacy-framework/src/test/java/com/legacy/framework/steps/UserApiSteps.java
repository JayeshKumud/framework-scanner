package com.legacy.framework.steps;

import com.legacy.framework.api.UserApi;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.response.Response;

import static org.testng.Assert.assertEquals;

public class UserApiSteps {

    private final UserApi userApi = new UserApi();
    private Response response;

    @When("I request user {string}")
    public void requestUser(String id) {
        response = userApi.getUser(id);
    }

    @Then("the user name is {string}")
    public void userNameIs(String name) {
        assertEquals(response.jsonPath().getString("name"), name);
    }
}
