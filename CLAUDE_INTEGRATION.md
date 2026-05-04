# Claude AI + AEM Page Generator

## World First Claude AI Integration with Adobe Experience Manager

This project demonstrates how to integrate Anthropic Claude AI with AEM as a Cloud Service to automatically generate and create AEM pages from natural language prompts.

## What it does

Type a prompt → Claude generates AEM page structure → Page is created in AEM automatically

## Architecture

User Prompt
→ ClaudePageGeneratorServlet (Sling Servlet)
→ ClaudePageGeneratorService (OSGi Service)
→ Claude API (Anthropic)
→ AEM PageManager (creates page)
→ Live AEM Page

## Setup

1. Get your Claude API key from https://console.anthropic.com
2. Copy the sample config:

cp ui.config/src/main/content/jcr_root/apps/wknd/osgiconfig/config/com.adobe.aem.guides.wknd.core.services.impl.ClaudePageGeneratorServiceImpl.cfg.json.SAMPLE ui.config/src/main/content/jcr_root/apps/wknd/osgiconfig/config/com.adobe.aem.guides.wknd.core.services.impl.ClaudePageGeneratorServiceImpl.cfg.json

3. Add your API key to the config file
4. Build and deploy:

mvn clean install -pl core,ui.apps,ui.config -am -PautoInstallPackage -DskipTests

## Usage

Generate page structure only:
GET /bin/wknd/claude/generate-page?prompt=Create an adventure page for Bali surfing

Generate AND create page in AEM:
GET /bin/wknd/claude/generate-page?prompt=Create an adventure page for Bali surfing&create=true

Optional parameters:
- parentPath: Parent path for page creation (default: /content/wknd/us/en/adventures)

## Tech Stack

- AEM as a Cloud Service (AEMaaCS)
- OSGi Services + Apache Sling
- Anthropic Claude API
- Java 11
- Apache HttpComponents

## Author

Anand 
