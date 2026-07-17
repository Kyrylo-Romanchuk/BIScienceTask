# AI Chatbot UI Healthcheck

An automated smoke healthcheck for AdClarity's **"Ask anything..."** AI Chatbot feature
(`https://stg-ui.adcint.com/`). It is not a functional/regression suite — it is a fast,
recurring check that the feature is reachable, usable, and completes a response without a
blocking error, so it can run on a frequent schedule and catch the feature going down or
breaking in an obvious way.

## What it checks

A single end-to-end scenario, `ChatbotHealthcheckTest.chatbotRespondsToACannedQuestion`:

1. **Access** — log in and land on the authenticated home page.
2. **Usable UI** — open the chatbot panel and confirm the input is visible and editable.
3. **Submission works** — submit a fixed canned question.
4. **Feature completes its job** — the assistant reaches a genuinely terminal state: either
   a direct answer or a clarifying-questions step (both valid, healthy outcomes), driven
   through to a completed response, signaled by the completed-response action bar
   ("Data sources" / "Submit feedback") — a stable signal that the assistant actually
   finished, independent of whether it found data. A "No results found" answer is treated
   as healthy; a stall, error state, or non-2xx API response is not.

**Deliberately out of scope** (smoke-level judgment): correctness/quality of the AI's
answer content (non-deterministic, not smoke-appropriate), multi-turn conversation
context, chat history persistence, exhaustive coverage of clarifying-question filters or
all suggested prompts, and load/performance testing. The full reasoning behind these
trade-offs and the assertion design lives in `cloud/test-next-steps.md` (private planning
notes, not part of this deliverable).

## Stack

Java 21, [Playwright for Java](https://playwright.dev/java/) (auto-waiting, network
interception, tracing/screenshots), TestNG, Allure for reporting, Maven.

## Setup

### Prerequisites

- JDK 21+
- Maven 3.8+

### Install the Playwright browser

Playwright's browser binaries aren't bundled in the repo and must be installed once per
machine/CI runner:

```bash
mvn -q test-compile
mvn -q org.codehaus.mojo:exec-maven-plugin:3.1.0:java \
  -Dexec.mainClass=com.microsoft.playwright.CLI \
  -Dexec.classpathScope=test \
  -Dexec.args="install chromium"
```

### Credentials

Credentials are **never** stored in this repository — they're read verbatim from
environment variables at runtime (verbatim matters: don't trim/reformat the password, it
can contain leading punctuation):

```bash
export HEALTHCHECK_USER_EMAIL="..."
export HEALTHCHECK_USER_PASSWORD="..."
```

## Running the suite

```bash
mvn test                    # runs against the "staging" environment (default)
mvn test -Denv=staging      # explicit
```

Runs headless by default; the target environment's `headless` flag can be flipped in its
config file to watch it locally.

## Configuration for multiple environments

Environment settings live in `src/main/resources/config/<env>.properties` (base URL,
canned question, timeouts, headless flag). `-Denv=<name>` selects which file loads; it
defaults to `staging`. To add another environment (e.g. `production`), copy
`staging.properties` to `production.properties`, adjust `baseUrl` and timeouts as needed,
and run with `-Denv=production`. Credentials always come from the environment variables
above regardless of which config file is selected.

## Reports

- **Allure**: `mvn allure:report` generates an HTML report at
  `target/site/allure-maven-plugin/index.html` (or `mvn allure:serve` to open it directly).
  Each logical step (`@Step`) shows in the report; screenshots attach automatically to
  failed steps.
- **Surefire**: raw XML/text results land in `target/surefire-reports/` regardless of
  Allure.

## Failure diagnostics

On any test failure, `ScreenshotOnFailureListener` captures a full-page screenshot and the
page's HTML into `./screenshots/` (gitignored) and attaches the screenshot to the Allure
report — no need to reproduce a failure manually to see what the page looked like.
Chatbot API calls (`/api/rest/v2/chatbot/*`) are also logged to stdout during every run,
so a CI failure is diagnosable from the console log alone.

## CI

`.github/workflows/healthcheck.yml` runs the suite:

- **On a schedule** (cron, adjustable in the workflow file) — the actual recurring healthcheck.
- **On demand** via `workflow_dispatch`, with a choice of target environment.
- **On push to `main`** touching the suite itself, so a broken healthcheck is caught
  immediately rather than at the next scheduled run.

It installs the Playwright browser, runs the suite, generates the Allure report, and
uploads the report + screenshots as a build artifact regardless of outcome. On failure, it
optionally posts to a webhook (Slack-compatible) if one is configured.

Required repository secrets:

| Secret | Purpose |
|---|---|
| `HEALTHCHECK_USER_EMAIL` | Login email for the target environment |
| `HEALTHCHECK_USER_PASSWORD` | Login password |
| `HEALTHCHECK_WEBHOOK_URL` | *(optional)* Webhook URL for failure notifications |

## Architecture

```
src/main/java/com/biscience/healthcheck/
  config/EnvironmentConfig.java   # env-specific settings + credential loading
  pages/LoginPage.java            # Page Object: /login form
  pages/ChatPanel.java            # Page Object: "Ask anything..." widget/panel

src/test/java/com/biscience/healthcheck/
  base/BaseTest.java              # Playwright/browser lifecycle, per-test isolation
  steps/ChatbotHealthcheckSteps.java  # the user-journey steps, as reusable @Step methods
  tests/ChatbotHealthcheckTest.java   # the declarative test scenario itself
  listeners/ScreenshotOnFailureListener.java  # screenshot + HTML dump on failure
```

Page Objects wrap (not extend) Playwright's `Page` — composition, not inheritance, since a
Page Object exposes only the locators/actions relevant to one screen, not the full browser
automation API surface. Steps are kept separate from the test class so the test method
itself reads as a short, declarative list of what happens, with the how living elsewhere.

Each test method gets its own isolated `BrowserContext` (no shared cookies/state between
runs), while the browser itself is launched once per test class to amortize startup cost.

## A real bug this suite caught

While building this suite, submitting a question immediately after opening the chat panel
(fast automated succession, no natural human pauses) was found to reliably cause the
assistant's reply to be silently dropped: the backend still creates the conversation and
accepts the message, but the UI never renders any response and no follow-up API call ever
fires — it hangs indefinitely. This was root-caused by ruling out browser version,
headless-vs-headed execution, and session/cookie state, leaving a client-side timing race
as the only explanation. `ChatPanel.open()` now waits for the panel's own `suggestions`
network call to complete (the last activity consistently observed before a stuck session)
before allowing submission, rather than papering over it with a blind sleep. It's exactly
the kind of "blocked by an obvious UI/runtime issue" this healthcheck exists to catch — it
just happens to affect fast automation too.
