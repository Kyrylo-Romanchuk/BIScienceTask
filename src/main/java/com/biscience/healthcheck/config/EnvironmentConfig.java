package com.biscience.healthcheck.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Loads environment-specific settings from {@code config/<env>.properties} on the
 * classpath, selected via the {@code -Denv=<name>} system property (defaults to "staging").
 *
 * Credentials are deliberately never read from these committed property files - only
 * from environment variables - so they never end up in this public repository.
 */
public final class EnvironmentConfig {

    private static final String ENV_SYSTEM_PROPERTY = "env";
    private static final String DEFAULT_ENV = "staging";
    private static final String USER_EMAIL_ENV_VAR = "HEALTHCHECK_USER_EMAIL";
    private static final String USER_PASSWORD_ENV_VAR = "HEALTHCHECK_USER_PASSWORD";

    private static EnvironmentConfig instance;

    private final String environmentName;
    private final Properties properties;

    private EnvironmentConfig(String environmentName, Properties properties) {
        this.environmentName = environmentName;
        this.properties = properties;
    }

    public static synchronized EnvironmentConfig get() {
        if (instance == null) {
            instance = load(System.getProperty(ENV_SYSTEM_PROPERTY, DEFAULT_ENV));
        }
        return instance;
    }

    private static EnvironmentConfig load(String environmentName) {
        String resourcePath = "config/" + environmentName + ".properties";
        Properties props = new Properties();
        try (InputStream in = EnvironmentConfig.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException(
                        "No config file found for environment '" + environmentName + "' (expected "
                                + resourcePath + " on the classpath). Pass -Denv=<name> matching a file "
                                + "under src/test/resources/config/.");
            }
            props.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + resourcePath, e);
        }
        return new EnvironmentConfig(environmentName, props);
    }

    public String environmentName() {
        return environmentName;
    }

    public String baseUrl() {
        return require("baseUrl");
    }

    public String chatQuestion() {
        return require("chatQuestion");
    }

    public double navigationTimeoutMs() {
        return Double.parseDouble(require("navigationTimeoutMs"));
    }

    public double chatResponseTimeoutMs() {
        return Double.parseDouble(require("chatResponseTimeoutMs"));
    }

    public boolean headless() {
        return Boolean.parseBoolean(properties.getProperty("headless", "true"));
    }

    /** Read verbatim from the environment - do not trim/strip, leading punctuation can be significant. */
    public String userEmail() {
        return requireEnvVar(USER_EMAIL_ENV_VAR);
    }

    /** Read verbatim from the environment - do not trim/strip, leading punctuation can be significant. */
    public String userPassword() {
        return requireEnvVar(USER_PASSWORD_ENV_VAR);
    }

    private String require(String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Missing required config key '" + key + "' for environment '" + environmentName + "'.");
        }
        return value;
    }

    private String requireEnvVar(String name) {
        String value = System.getenv(name);
        if (value == null || value.isEmpty()) {
            throw new IllegalStateException(
                    "Missing required environment variable '" + name + "'. Credentials are intentionally "
                            + "kept out of this repository - set it before running the suite.");
        }
        return value;
    }
}
