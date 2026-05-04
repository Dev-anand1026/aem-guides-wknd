package com.adobe.aem.guides.wknd.core.services;

/**
 * Service interface for generating AEM page structures using Claude AI.
 * 
 * This service calls the Anthropic Claude API and returns a JSON structure
 * that can be used to create AEM pages programmatically.
 * 
 * @author Anand
 * @version 1.0
 */
public interface ClaudePageGeneratorService {

    /**
     * Generates AEM page structure JSON from a user prompt using Claude AI.
     *
     * @param prompt - Natural language prompt e.g. "Create an adventure page for Bali surfing"
     * @return JSON string containing pageTitle, pageDescription, slug and components array
     */
    String generatePageStructure(String prompt);
}