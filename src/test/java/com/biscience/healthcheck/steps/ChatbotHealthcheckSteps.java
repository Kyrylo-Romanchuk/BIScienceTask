package com.biscience.healthcheck.steps;

import com.biscience.healthcheck.config.EnvironmentConfig;
import com.biscience.healthcheck.pages.ChatPanel;
import com.biscience.healthcheck.pages.LoginPage;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.assertions.LocatorAssertions;
import com.microsoft.playwright.options.AriaRole;
import io.qameta.allure.Step;

import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.testng.Assert.assertTrue;

/**
 * Step library for the AI Chatbot ("Ask anything...") smoke healthcheck - the actual
 * user-journey actions and assertions, kept separate from the TestNG test class so the
 * test class stays a readable, declarative list of steps.
 */
public class ChatbotHealthcheckSteps {

    private final Page page;
    private final EnvironmentConfig config;
    private final LoginPage loginPage;
    private final ChatPanel chatPanel;

    public ChatbotHealthcheckSteps(Page page, EnvironmentConfig config) {
        this.page = page;
        this.config = config;
        this.loginPage = new LoginPage(page);
        this.chatPanel = new ChatPanel(page);
    }

    @Step("Log in to the staging environment")
    public void logIn() {
        loginPage.open(config.baseUrl());
        loginPage.login(config.userEmail(), config.userPassword());

        page.waitForURL("**/ad-intelligence/start",
                new Page.WaitForURLOptions().setTimeout(config.navigationTimeoutMs()));

        // Rendering can lag slightly behind the URL change, so assert with Playwright's
        // auto-retrying assertThat() rather than a point-in-time isVisible() check.
        assertThat(page.getByRole(AriaRole.HEADING,
                        new Page.GetByRoleOptions().setName(Pattern.compile("^Hello, .+"))))
                .isVisible(new LocatorAssertions.IsVisibleOptions().setTimeout(config.navigationTimeoutMs()));
    }

    @Step("Open the chatbot and confirm the input is usable")
    public void openChatbot() {
        chatPanel.open();
        assertThat(chatPanel.askAnythingInput()).isEditable();
    }

    @Step("Submit a canned question")
    public void askCannedQuestion() {
        chatPanel.ask(config.chatQuestion());
    }

    @Step("Verify the assistant reaches a completed response")
    public void verifyResponseCompleted() {
        chatPanel.resolveToCompletedResponse(config.chatResponseTimeoutMs());
        assertTrue(chatPanel.isCompletedResponseVisible(),
                "Expected the completed-response action bar (Data sources / Submit feedback) to be visible");
    }
}
