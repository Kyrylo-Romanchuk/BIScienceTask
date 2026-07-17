package com.biscience.healthcheck.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

/** Page Object for the {@code /login} form. */
public class LoginPage {

    private final Page page;

    public LoginPage(Page page) {
        this.page = page;
    }

    public void open(String baseUrl) {
        page.navigate(baseUrl + "/login");
    }

    public void login(String email, String password) {
        emailInput().fill(email);
        passwordInput().fill(password);
        loginButton().click();
    }

    public Locator emailInput() {
        return page.getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName("E-mail address"));
    }

    public Locator passwordInput() {
        return page.getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName("Password"));
    }

    public Locator loginButton() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Login"));
    }
}
