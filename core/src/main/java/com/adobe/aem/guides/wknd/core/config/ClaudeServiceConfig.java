package com.adobe.aem.guides.wknd.core.config;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(
    name = "Claude AI Service Configuration",
    description = "Configuration for Claude API integration in WKND"
)
public @interface ClaudeServiceConfig {

    @AttributeDefinition(
        name = "Claude API Key",
        description = "Your Anthropic Claude API Key"
    )
    String apiKey() default "";

    @AttributeDefinition(
        name = "Claude Model",
        description = "Claude model to use"
    )
    String model() default "claude-opus-4-6";

    @AttributeDefinition(
        name = "Max Tokens",
        description = "Maximum tokens for response"
    )
    int maxTokens() default 2000;
}