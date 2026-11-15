package com.legacy.framework.utils;

import com.microsoft.playwright.Page;

/**
 * Written by another team member who did not find LoginPage.login.
 */
public class LoginHelper {

    public static void doSignIn(Page page, String userName, String pwd) {
        page.fill("#username", userName);
        page.fill("#password", pwd);
        page.click("#login-btn");
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
        }
    }

    public static void loginAsAdmin(Page page) {
        page.fill("#username", "admin");
        page.fill("#password", "admin123");
        page.click("#login-btn");
    }
}
