package com.legacy.framework.pages;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

public class DashboardPage {

    private final Page page;

    public DashboardPage(Page page) {
        this.page = page;
    }

    public String getWelcomeText() {
        return page.locator("#welcome").innerText();
    }

    public void verifyWelcome(String name) {
        assertThat(page.locator("#welcome")).isVisible();
        assertThat(page.locator("#welcome")).containsText(name);
    }

    public void openOrders() {
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Orders")).click();
        page.waitForNavigation(() -> {});
    }

    public void searchProduct(String product) {
        page.locator("#search").type(product);
        page.keyboard().press("Enter");
    }
}
