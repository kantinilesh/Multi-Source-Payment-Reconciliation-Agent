package com.razorpay.buildathon.recon.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Type-safe configuration binding for {@code recon.ai.*} properties in {@code application.yml}.
 */
@ConfigurationProperties(prefix = "recon.ai")
public class ReconAiConfig {

    private double confidenceAutoAcceptThreshold = 0.85;
    private String providerBaseUrl = "https://api.anthropic.com";
    private String apiKey = "";
    private String model = "claude-sonnet-4-6";
    private int maxTokens = 1000;
    private boolean mockMode = false;
    private int timeoutSeconds = 10;

    public double getConfidenceAutoAcceptThreshold() {
        return confidenceAutoAcceptThreshold;
    }

    public void setConfidenceAutoAcceptThreshold(double threshold) {
        this.confidenceAutoAcceptThreshold = threshold;
    }

    public String getProviderBaseUrl() {
        return providerBaseUrl;
    }

    public void setProviderBaseUrl(String providerBaseUrl) {
        this.providerBaseUrl = providerBaseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public boolean isMockMode() {
        return mockMode;
    }

    public void setMockMode(boolean mockMode) {
        this.mockMode = mockMode;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }
}
