package com.biscience.healthcheck.listeners;

import com.biscience.healthcheck.base.BaseTest;
import com.microsoft.playwright.Page;
import io.qameta.allure.Allure;
import org.testng.ITestListener;
import org.testng.ITestResult;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;

/**
 * On any test failure, captures a full-page screenshot: attaches it to the Allure report
 * and additionally writes it to ./screenshots for quick local inspection or CI artifact
 * upload, without depending on Allure being configured.
 */
public class ScreenshotOnFailureListener implements ITestListener {

    private static final Path SCREENSHOT_DIR = Paths.get("screenshots");

    @Override
    public void onTestFailure(ITestResult result) {
        Object testInstance = result.getInstance();
        if (!(testInstance instanceof BaseTest baseTest)) {
            return;
        }
        Page page = baseTest.page();
        if (page == null) {
            return;
        }

        byte[] screenshot = page.screenshot(new Page.ScreenshotOptions().setFullPage(true));
        Allure.addAttachment(result.getName() + "-failure", new ByteArrayInputStream(screenshot));

        long timestamp = Instant.now().toEpochMilli();
        try {
            Files.createDirectories(SCREENSHOT_DIR);
            Files.write(SCREENSHOT_DIR.resolve(result.getName() + "-" + timestamp + ".png"), screenshot);
            Files.writeString(SCREENSHOT_DIR.resolve(result.getName() + "-" + timestamp + ".html"), page.content());
        } catch (IOException e) {
            // Best-effort: the Allure attachment above is the primary record, so a failure
            // to also persist local copies shouldn't mask the original test failure.
        }
    }
}
