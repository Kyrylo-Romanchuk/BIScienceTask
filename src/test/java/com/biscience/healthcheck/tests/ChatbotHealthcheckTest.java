package com.biscience.healthcheck.tests;

import com.biscience.healthcheck.base.BaseTest;
import com.biscience.healthcheck.steps.ChatbotHealthcheckSteps;
import io.qameta.allure.Description;
import io.qameta.allure.Story;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Smoke healthcheck for the AI Chatbot ("Ask anything...") feature.
 *
 * Scope is deliberately narrow: this confirms the feature is reachable, usable, and
 * completes a response without a blocking error - not that the AI's answer is correct.
 * Answer correctness is non-deterministic and out of scope for a recurring smoke check
 * (see cloud/test-next-steps.md ss3 for the full reasoning).
 */
@Story("AI Chatbot (\"Ask anything...\") smoke healthcheck")
public class ChatbotHealthcheckTest extends BaseTest {

    private ChatbotHealthcheckSteps steps;

    @BeforeMethod(alwaysRun = true)
    public void setUpSteps() {
        steps = new ChatbotHealthcheckSteps(page(), config);
    }

    @Test(description = "AI Chatbot is reachable, accepts input, and completes a response without a blocking error")
    @Description("End-to-end smoke check: authenticate, open the chatbot, submit a canned question, and "
            + "confirm the assistant reaches a completed response. Answer content/quality is out of scope; "
            + "the signal is that the feature works, not what it says.")
    public void chatbotRespondsToACannedQuestion() {
        steps.logIn();
        steps.openChatbot();
        steps.askCannedQuestion();
        steps.verifyResponseCompleted();
    }
}
