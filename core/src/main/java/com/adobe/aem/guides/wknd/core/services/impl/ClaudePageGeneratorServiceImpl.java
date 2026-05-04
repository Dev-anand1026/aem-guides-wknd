package com.adobe.aem.guides.wknd.core.services.impl;

import com.adobe.aem.guides.wknd.core.config.ClaudeServiceConfig;
import com.adobe.aem.guides.wknd.core.services.ClaudePageGeneratorService;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OSGi Service implementation that integrates Claude AI with AEM.
 * Calls the Anthropic Claude API and returns structured JSON
 * for AEM page creation.
 *
 * @author Anand
 * @version 1.0
 */
@Component(service = ClaudePageGeneratorService.class, immediate = true)
@Designate(ocd = ClaudeServiceConfig.class)
public class ClaudePageGeneratorServiceImpl implements ClaudePageGeneratorService {

    private static final Logger LOG = LoggerFactory.getLogger(ClaudePageGeneratorServiceImpl.class);
    private static final String CLAUDE_API_URL = "https://api.anthropic.com/v1/messages";
    private static final String DEFAULT_MODEL = "claude-opus-4-6";
    private static final int DEFAULT_MAX_TOKENS = 2000;

    private String apiKey;
    private String model;
    private int maxTokens;

    @Activate
    @Modified
    protected void activate(ClaudeServiceConfig config) {
        this.apiKey = config.apiKey();
        this.model = config.model();
        this.maxTokens = config.maxTokens();

        if (this.model == null || this.model.trim().isEmpty()) {
            this.model = DEFAULT_MODEL;
        }
        if (this.maxTokens <= 0) {
            this.maxTokens = DEFAULT_MAX_TOKENS;
        }

        LOG.info("Claude Service activated - model: {}, maxTokens: {}",
            this.model, this.maxTokens);
    }

    @Override
    public String generatePageStructure(String prompt) {

        if (apiKey == null || apiKey.trim().isEmpty()) {
            LOG.error("Claude API key is not configured in OSGi config");
            return "{\"error\": \"Claude API key is not configured\"}";
        }

        if (prompt == null || prompt.trim().isEmpty()) {
            return "{\"error\": \"Prompt cannot be empty\"}";
        }

        LOG.info("Generating AEM page structure for prompt: {}", prompt);

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {

            HttpPost request = new HttpPost(CLAUDE_API_URL);
            request.setHeader("Content-Type", "application/json");
            request.setHeader("x-api-key", apiKey);
            request.setHeader("anthropic-version", "2023-06-01");

            String systemPrompt = "You are an AEM (Adobe Experience Manager) expert. "
                + "When given a prompt, respond ONLY with a valid JSON object with NO markdown code fences. "
                + "The JSON must follow this exact structure: "
                + "{ \"pageTitle\": \"string\", \"pageDescription\": \"string\", "
                + "\"slug\": \"string (url-friendly lowercase with hyphens)\", \"components\": ["
                + "{ \"type\": \"hero\", \"heading\": \"string\", \"description\": \"string\" },"
                + "{ \"type\": \"text\", \"content\": \"string (can include HTML)\" },"
                + "{ \"type\": \"cta\", \"label\": \"string\", \"link\": \"string\" }] }. "
                + "Do NOT wrap in ```json or any code block. Return raw JSON only.";

            String requestBody = "{"
                + "\"model\": \"" + model + "\","
                + "\"max_tokens\": " + maxTokens + ","
                + "\"system\": " + toJsonString(systemPrompt) + ","
                + "\"messages\": [{\"role\": \"user\", \"content\": " + toJsonString(prompt) + "}]"
                + "}";

            request.setEntity(new StringEntity(requestBody, "UTF-8"));

            try (CloseableHttpResponse response = httpClient.execute(request)) {
                String rawResponse = EntityUtils.toString(response.getEntity());
                LOG.debug("Claude API raw response: {}", rawResponse);

                return extractPageJson(rawResponse);
            }

        } catch (Exception e) {
            LOG.error("Error calling Claude API: {}", e.getMessage(), e);
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    /**
     * Extracts the page JSON from Claude API raw response.
     * Claude returns: {"content":[{"type":"text","text":"{ ... json ... }"}],...}
     */
    private String extractPageJson(String rawResponse) {
        String textMarker = "\"text\":\"";
        int start = rawResponse.indexOf(textMarker);
        if (start == -1) {
            LOG.error("No text marker found in Claude response");
            return "{\"error\": \"Unexpected response format from Claude API\"}";
        }
        start += textMarker.length();

        // Parse escaped JSON string character by character
        StringBuilder sb = new StringBuilder();
        int i = start;
        while (i < rawResponse.length()) {
            char c = rawResponse.charAt(i);
            if (c == '\\' && i + 1 < rawResponse.length()) {
                char next = rawResponse.charAt(i + 1);
                if (next == '"')  { sb.append('"');  i += 2; continue; }
                if (next == 'n')  { sb.append('\n'); i += 2; continue; }
                if (next == '\\') { sb.append('\\'); i += 2; continue; }
                if (next == 't')  { sb.append('\t'); i += 2; continue; }
                if (next == 'r')  { sb.append('\r'); i += 2; continue; }
            }
            if (c == '"') break; // end of text value
            sb.append(c);
            i++;
        }

        String pageJson = sb.toString().trim();

        // Remove code fences if Claude added them despite instructions
        if (pageJson.startsWith("```json")) {
            pageJson = pageJson.substring(7).trim();
        } else if (pageJson.startsWith("```")) {
            pageJson = pageJson.substring(3).trim();
        }
        if (pageJson.endsWith("```")) {
            pageJson = pageJson.substring(0, pageJson.length() - 3).trim();
        }

        // Ensure starts with {
        if (!pageJson.startsWith("{")) {
            int jsonStart = pageJson.indexOf("{");
            if (jsonStart >= 0) {
                pageJson = pageJson.substring(jsonStart);
            }
        }

        LOG.info("Successfully extracted page JSON, length: {}", pageJson.length());
        return pageJson;
    }

    /**
     * Safely escapes a Java string for use as a JSON string value.
     */
    private String toJsonString(String value) {
        return "\"" + value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            + "\"";
    }
}