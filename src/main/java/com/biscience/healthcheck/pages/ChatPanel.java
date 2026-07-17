package com.biscience.healthcheck.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

/**
 * Page Object for the "Ask anything..." AI Chatbot widget/panel.
 *
 * The docked "Ask anything..." textbox is a single persistent component: clicking it
 * opens the full chat panel, and the same element remains the input field once open.
 */
public class ChatPanel {

    private static final String ASK_ANYTHING_NAME = "Ask anything...";
    private static final String CONTINUE_WITHOUT_FILTERS = "Continue without filters";
    private static final String DATA_SOURCES_BUTTON = "Data sources";
    private static final String SUBMIT_FEEDBACK_BUTTON = "Submit feedback";

    private final Page page;

    public ChatPanel(Page page) {
        this.page = page;
    }

    public Locator askAnythingInput() {
        return page.getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName(ASK_ANYTHING_NAME));
    }

    /**
     * Opens the chat panel via the docked entry-point widget and waits for it to finish
     * initializing before returning.
     *
     * This wait is deliberate, not cosmetic: submitting a question immediately after the
     * click - before the panel has finished setting up - is a real race that silently
     * drops the assistant's reply (confirmed by reproducing it: the backend accepts the
     * message and creates the conversation, but no response stream is ever consumed by
     * the UI). The welcome heading alone isn't a sufficient signal - it renders before
     * that setup completes. The panel's own {@code suggestions} call is consistently the
     * last network activity observed before a stuck session, so waiting for it to finish
     * is the most directly evidenced proxy for "ready" available without app instrumentation.
     */
    public void open() {
        page.waitForResponse(response -> response.url().contains("/suggestions") && response.ok(),
                () -> askAnythingInput().click());
    }

    /** Types the given question and submits it with Enter. */
    public void ask(String question) {
        Locator input = askAnythingInput();
        input.fill(question);
        input.press("Enter");
    }

    /**
     * Drives the conversation to a terminal, completed response.
     *
     * The assistant either answers directly, or first inserts a clarifying-questions
     * step (e.g. country/channel filters) - both are valid outcomes of a healthy
     * feature. Rather than racing a fixed guess-timeout against the clarifying step
     * (which can appear anywhere from ~1s to several seconds in), this waits for
     * *either* signal to show up, using Playwright's Locator#or - whichever comes
     * first - then, if it was the clarifying step, dismisses it via "Continue without
     * filters" and waits for the real completed response.
     *
     * The completed-response action bar (Data sources / Submit feedback) is a stable
     * signal that the assistant actually finished, independent of whether the answer
     * itself contains data - a "No results found" answer shows the same action bar and
     * is a valid, healthy outcome.
     */
    public void resolveToCompletedResponse(double timeoutMs) {
        Locator continueWithoutFilters = page.getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName(CONTINUE_WITHOUT_FILTERS));
        Locator dataSources = dataSourcesButton();

        continueWithoutFilters.or(dataSources).first()
                .waitFor(new Locator.WaitForOptions().setTimeout(timeoutMs));

        if (continueWithoutFilters.isVisible()) {
            continueWithoutFilters.click();
            dataSources.waitFor(new Locator.WaitForOptions().setTimeout(timeoutMs));
        }
        submitFeedbackButton().waitFor(new Locator.WaitForOptions().setTimeout(timeoutMs));
    }

    public boolean isCompletedResponseVisible() {
        return dataSourcesButton().isVisible() && submitFeedbackButton().isVisible();
    }

    private Locator dataSourcesButton() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(DATA_SOURCES_BUTTON));
    }

    private Locator submitFeedbackButton() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(SUBMIT_FEEDBACK_BUTTON));
    }
}
