package com.biscience.healthcheck.base;

import com.biscience.healthcheck.config.EnvironmentConfig;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;

/**
 * Boots one Playwright/Browser per test class and a fresh BrowserContext (and Page) per
 * test method, so each test is fully isolated (no shared cookies/storage) while still
 * amortizing the relatively expensive browser launch across a class's test methods.
 */
public abstract class BaseTest {

    private static final Logger LOG = LoggerFactory.getLogger(BaseTest.class);

    protected EnvironmentConfig config;

    private Playwright playwright;
    private Browser browser;
    private BrowserContext context;
    private Page page;

    @BeforeClass(alwaysRun = true)
    public void launchBrowser() {
        config = EnvironmentConfig.get();
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(config.headless()));
    }

    @BeforeMethod(alwaysRun = true)
    public void newContext() {
        context = browser.newContext();
        context.setDefaultTimeout(config.navigationTimeoutMs());
        page = context.newPage();
        // Chatbot API calls are logged so a CI failure is diagnosable from the console
        // output alone, without needing a screenshot/HTML dump to see what the backend did.
        page.onResponse(response -> {
            if (response.url().contains("/api/rest/v2/chatbot/")) {
                LOG.info("[chatbot-api] {} {} {}", response.request().method(), response.status(), response.url());
            }
        });
    }

    @AfterMethod(alwaysRun = true)
    public void closeContext() {
        if (context != null) {
            context.close();
        }
    }

    @AfterClass(alwaysRun = true)
    public void closeBrowser() {
        if (browser != null) {
            browser.close();
        }
        if (playwright != null) {
            playwright.close();
        }
    }

    public Page page() {
        return page;
    }
}
