package com.adobe.aem.guides.wknd.core.servlets;

import com.adobe.aem.guides.wknd.core.services.ClaudePageGeneratorService;
import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import java.io.IOException;

/**
 * Sling Servlet that exposes Claude AI page generation as an HTTP endpoint.
 *
 * Endpoints:
 * GET /bin/wknd/claude/generate-page?prompt=YOUR_PROMPT
 *     Returns Claude-generated AEM page structure as JSON
 *
 * GET /bin/wknd/claude/generate-page?prompt=YOUR_PROMPT&create=true
 *     Generates page structure AND creates the page in AEM
 *
 * Optional parameters:
 *     parentPath - Parent path for page creation (default: /content/wknd/us/en/adventures)
 *
 * @author Anand
 * @version 1.0
 */
@Component(service = Servlet.class, property = {
    "sling.servlet.paths=/bin/wknd/claude/generate-page",
    "sling.servlet.methods=GET",
    "sling.servlet.methods=POST"
})
public class ClaudePageGeneratorServlet extends SlingAllMethodsServlet {

    private static final Logger LOG = LoggerFactory.getLogger(ClaudePageGeneratorServlet.class);
    private static final String DEFAULT_PARENT_PATH = "/content/wknd/us/en/adventures";
    private static final String WKND_TEMPLATE = "/conf/wknd/settings/wcm/templates/adventure-page-template";

    @Reference
    private ClaudePageGeneratorService claudeService;

    @Override
    protected void doGet(SlingHttpServletRequest request,
                         SlingHttpServletResponse response)
                         throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try {
            String prompt = request.getParameter("prompt");
            String create = request.getParameter("create");
            String parentPath = request.getParameter("parentPath");

            if (parentPath == null || parentPath.isEmpty()) {
                parentPath = DEFAULT_PARENT_PATH;
            }

            // Validate prompt
            if (prompt == null || prompt.trim().isEmpty()) {
                response.setStatus(400);
                response.getWriter().write("{\"error\": \"prompt parameter is required\"}");
                return;
            }

            LOG.info("Claude page generator called - prompt: {}, create: {}", prompt, create);

            // Step 1 — Generate page structure from Claude AI
            String pageStructureJson = claudeService.generatePageStructure(prompt);

            if (pageStructureJson == null || pageStructureJson.trim().isEmpty()) {
                response.setStatus(500);
                response.getWriter().write("{\"error\": \"Claude service returned empty response\"}");
                return;
            }

            // Return error if Claude service failed
            if (pageStructureJson.startsWith("{\"error\"")) {
                response.setStatus(500);
                response.getWriter().write(pageStructureJson);
                return;
            }

            // Step 2 — If create=true, create the page in AEM
            if ("true".equalsIgnoreCase(create)) {
                createPageInAEM(request, response, pageStructureJson, parentPath);
            } else {
                // Just return the generated JSON structure
                response.getWriter().write(pageStructureJson);
            }

        } catch (Exception e) {
            LOG.error("Error in ClaudePageGeneratorServlet: {}", e.getMessage(), e);
            response.setStatus(500);
            response.getWriter().write("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }

    /**
     * Creates an AEM page from the Claude-generated JSON structure.
     */
    private void createPageInAEM(SlingHttpServletRequest request,
                                  SlingHttpServletResponse response,
                                  String pageStructureJson,
                                  String parentPath) throws IOException {

        String pageTitle = extractJsonValue(pageStructureJson, "pageTitle");
        String pageDescription = extractJsonValue(pageStructureJson, "pageDescription");
        String slug = extractJsonValue(pageStructureJson, "slug");

        // Clean and sanitize slug
        if (slug.contains("/")) {
            slug = slug.substring(slug.lastIndexOf("/") + 1);
        }
        slug = slug.toLowerCase()
                   .replaceAll("[^a-z0-9-]", "-")
                   .replaceAll("-+", "-");

        if (slug.isEmpty()) {
            slug = pageTitle.toLowerCase()
                            .replaceAll("[^a-z0-9-]", "-")
                            .replaceAll("-+", "-");
        }

        LOG.info("Creating AEM page - title: {}, slug: {}, parent: {}",
            pageTitle, slug, parentPath);

        try {
            ResourceResolver resolver = request.getResourceResolver();
            PageManager pageManager = resolver.adaptTo(PageManager.class);

            if (pageManager == null) {
                response.setStatus(500);
                response.getWriter().write("{\"error\": \"Could not obtain PageManager\"}");
                return;
            }

            // Create the page using WKND template
            Page newPage = pageManager.create(
                parentPath,
                slug,
                WKND_TEMPLATE,
                pageTitle,
                true
            );

            if (newPage == null) {
                response.setStatus(500);
                response.getWriter().write("{\"error\": \"Page creation failed\"}");
                return;
            }

            // Set page description property
            if (pageDescription != null && !pageDescription.isEmpty()) {
                javax.jcr.Node pageNode = newPage.getContentResource()
                    .adaptTo(javax.jcr.Node.class);
                if (pageNode != null) {
                    pageNode.setProperty("jcr:description", pageDescription);
                    pageNode.getSession().save();
                }
            }

            String pagePath = newPage.getPath();
            LOG.info("AEM page created successfully at: {}", pagePath);

            // Return success response
            response.getWriter().write("{"
                + "\"success\": true,"
                + "\"pagePath\": \"" + pagePath + "\","
                + "\"pageTitle\": \"" + pageTitle + "\","
                + "\"slug\": \"" + slug + "\","
                + "\"authorUrl\": \"/editor.html" + pagePath + ".html\","
                + "\"pageStructure\": " + pageStructureJson
                + "}");

        } catch (Exception e) {
            LOG.error("Error creating AEM page: {}", e.getMessage(), e);
            response.setStatus(500);
            response.getWriter().write("{\"error\": \"Page creation error: " + e.getMessage() + "\"}");
        }
    }

    @Override
    protected void doPost(SlingHttpServletRequest request,
                          SlingHttpServletResponse response)
                          throws ServletException, IOException {
        doGet(request, response);
    }

    /**
     * Extracts a string value from a simple JSON object by key.
     */
    private String extractJsonValue(String json, String key) {
        String searchKey = "\"" + key + "\":\"";
        int keyIndex = json.indexOf(searchKey);
        if (keyIndex == -1) return "";
        int valueStart = keyIndex + searchKey.length();
        int valueEnd = json.indexOf("\"", valueStart);
        if (valueStart <= 0 || valueEnd <= 0) return "";
        return json.substring(valueStart, valueEnd);
    }
}