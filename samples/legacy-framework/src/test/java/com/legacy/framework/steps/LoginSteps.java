package com.legacy.framework.steps;

import com.legacy.framework.pages.DashboardPage;
import com.legacy.framework.pages.LoginPage;
import com.microsoft.playwright.Page;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

public class LoginSteps {

    private final Page page;
    private final LoginPage loginPage;
    private final DashboardPage dashboardPage;

    public LoginSteps(Page page) {
        this.page = page;
        this.loginPage = new LoginPage(page);
        this.dashboardPage = new DashboardPage(page);
    }

    @Given("user is on login page")
    public void userIsOnLoginPage() {
        page.navigate("https://qa.shop.example.com/login");
    }

    @When("user logs in with {string} and {string}")
    public void userLogsIn(String user, String password) {
        loginPage.login(user, password);
    }

    @When("I sign in as {string} with password {string}")
    public void iSignIn(String user, String password) {
        page.locator("#username").fill(user);
        page.locator("#password").fill(password);
        page.locator("#login-btn").click();
    }

    @When("user clicks on login button")
    public void clickLogin() {
        page.locator("#login-btn").click();
    }

    @Then("dashboard is shown")
    public void dashboardIsShown() {
        dashboardPage.getWelcomeText();
    }

    @Then("the welcome message shows {string}")
    public void welcomeShows(String name) {
        dashboardPage.verifyWelcome(name);
    }
}
