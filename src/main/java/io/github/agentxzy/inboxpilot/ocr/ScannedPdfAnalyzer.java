package io.github.agentxzy.inboxpilot.ocr;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.*;

@Component
public class ScannedPdfAnalyzer {

    @Value("${ai.base-url}")
    private String baseUrl;

    @Value("${ai.api-key}")
    private String apiKey;

    @Value("${ai.vision-model}")
    private String visionModel;

    private final ObjectMapper mapper = new ObjectMapper();

    public String analyzeScannedPdf(byte[] pdfBytes) {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            int totalPages = document.getNumberOfPages();
            List<Integer> pagesToAnalyze = selectPagesToAnalyze(totalPages);
            PDFRenderer renderer = new PDFRenderer(document);

            List<String> pageResults = new ArrayList<>();
            for (int pageIndex : pagesToAnalyze) {
                BufferedImage image = renderer.renderImageWithDPI(pageIndex, 150); // 150 DPI: readable, not huge
                String base64Image = encodeImageToBase64(image);
                String result = callVisionModel(base64Image, pageIndex + 1, totalPages);
                if (result != null) pageResults.add(result);
            }

            return pageResults.isEmpty() ? "" : String.join("\n", pageResults);

        } catch (Exception e) {
            return "[Could not analyze scanned document: " + e.getMessage() + "]";
        }
    }

    // Front 5 pages (main content) + back 2 pages (trailing dates/signatures/deadlines),
    // capped so cost never scales with document length — a 100-page PDF still only costs 7 vision calls max.
    private List<Integer> selectPagesToAnalyze(int totalPages) {
        Set<Integer> pages = new LinkedHashSet<>();
        int frontCount = Math.min(5, totalPages);
        for (int i = 0; i < frontCount; i++) pages.add(i);

        int backCount = Math.min(2, totalPages);
        for (int i = Math.max(0, totalPages - backCount); i < totalPages; i++) pages.add(i);

        return new ArrayList<>(pages);
    }

    private String encodeImageToBase64(BufferedImage image) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        return Base64.getEncoder().encodeToString(baos.toByteArray());
    }

    private String callVisionModel(String base64Image, int pageNumber, int totalPages) {
        try {
            RestClient client = RestClient.create(baseUrl);

            String systemPrompt = "You are analyzing page " + pageNumber + " of " + totalPages +
                " from a scanned document. Extract ONLY: any deadline or date mentioned, any required action, " +
                "and a one-sentence gist of this page. Respond ONLY with raw JSON, no markdown: " +
                "{\"deadline\": string or null, \"action\": string or null, \"gist\": string or null}. " +
                "If this page has no relevant content, return all nulls.";

            Map<String, Object> requestBody = Map.of(
                "model", visionModel,
                "temperature", 0,
                "messages", List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", List.of(
                        Map.of("type", "image_url", "image_url", Map.of(
                            "url", "data:image/png;base64," + base64Image
                        ))
                    ))
                )
            );

            JsonNode response = client.post()
                .uri("/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .body(requestBody)
                .retrieve()
                .body(JsonNode.class);

            String rawText = response.get("choices").get(0).get("message").get("content").asString();
            String cleaned = rawText.replaceAll("```json|```", "").trim();
            JsonNode json = mapper.readTree(cleaned);

            String deadline = safeText(json.get("deadline"));
            String action = safeText(json.get("action"));
            String gist = safeText(json.get("gist"));

            if (deadline == null && action == null && gist == null) return null;

            return "Page " + pageNumber + ": " +
                (gist != null ? gist + " " : "") +
                (deadline != null ? "[Deadline: " + deadline + "] " : "") +
                (action != null ? "[Action: " + action + "]" : "");

        } catch (Exception e) {
            return null; // one bad page shouldn't kill the whole extraction
        }
    }
    
    public String analyzeRawImage(byte[] imageBytes, String mimeType) {
        try {
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);
            String result = callVisionModelRaw(base64Image, mimeType);
            return result != null ? result : "";
        } catch (Exception e) {
            return "[Could not analyze image attachment: " + e.getMessage() + "]";
        }
    }

    private String callVisionModelRaw(String base64Image, String mimeType) {
        try {
            RestClient client = RestClient.create(baseUrl);

            String systemPrompt = "You are analyzing an image attachment from an email (possibly a document photo or screenshot). " +
                "Extract ONLY: any deadline or date mentioned, any required action, and a one-sentence gist. " +
                "Respond ONLY with raw JSON, no markdown: " +
                "{\"deadline\": string or null, \"action\": string or null, \"gist\": string or null}. " +
                "If there's no relevant content, return all nulls."+
                "Only report a deadline if the document explicitly states an action must be taken by that date " +
                "(e.g., 'apply by', 'deadline is', 'expires on', 'due date'). " +
                "A document's own issue date, letterhead date, or 'Date:' field is NOT a deadline — ignore it. " +
                "Never invent details from bracketed placeholder text like [Example] — if a document is clearly a template " +
                "with unfilled placeholders, note that explicitly rather than treating example text as real.";

            Map<String, Object> requestBody = Map.of(
                "model", visionModel,
                "temperature", 0,
                "messages", List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", List.of(
                        Map.of("type", "image_url", "image_url", Map.of(
                            "url", "data:" + mimeType + ";base64," + base64Image
                        ))
                    ))
                )
            );

            JsonNode response = client.post()
                .uri("/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .body(requestBody)
                .retrieve()
                .body(JsonNode.class);

            String rawText = response.get("choices").get(0).get("message").get("content").asString();
            String cleaned = rawText.replaceAll("```json|```", "").trim();
            JsonNode json = mapper.readTree(cleaned);

            String deadline = safeText(json.get("deadline"));
            String action = safeText(json.get("action"));
            String gist = safeText(json.get("gist"));

            if (deadline == null && action == null && gist == null) return null;

            return (gist != null ? gist + " " : "") +
                (deadline != null ? "[Deadline: " + deadline + "] " : "") +
                (action != null ? "[Action: " + action + "]" : "");

        } catch (Exception e) {
            return null;
        }
    }

    private String safeText(JsonNode node) {
        return (node != null && !node.isNull()) ? node.asString() : null;
    }
}