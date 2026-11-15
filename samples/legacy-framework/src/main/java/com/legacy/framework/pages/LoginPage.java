package com.legacy.framework.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

/** The login page of the shop. */
public class LoginPage {

    private final Page page;
    private final Locator username;
    private final Locator password;
    private final Locator loginButton;

    public LoginPage(Page page) {
        this.page = page;
        this.username = page.locator("#username");
        this.password = page.locator("#password");
        this.loginButton = page.locator("#login-btn");
    }

    /**
     * Logs in with the given user name and password.
     */
    public void login(String user, String pass) {
        username.fill(user);
        password.fill(pass);
        loginButton.click();
        page.waitForURL("**/dashboard");
    }

    public void open(String baseUrl) {
        page.navigate(baseUrl + "/login");
        page.waitForLoadState();
    }

    public String getErrorMessage() {
        return page.locator(".error-message").textContent();
    }

    public void Click_Logout() {
        page.locator("/html/body/div[2]/div[1]/header/button[3]").click();
    }
}
