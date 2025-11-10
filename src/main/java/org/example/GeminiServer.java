package org.example;

import com.google.gson.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

import org.apache.poi.xwpf.usermodel.*;
import java.io.FileOutputStream;
import java.nio.file.Path;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public class GeminiServer {

    public enum Mode { RESUME, REFINEMENT }
    private Mode currentMode;

    private static final String API_URL =
            "https://generativelanguage.googleapis.com/v1/models/gemini-2.5-flash:generateContent";

    private final String apiKey;

    private final List<Map<String, Object>> conversation = new ArrayList<>();
    private String systemInstruction;

    private String latestResumeText = "";
    public String getLatestResumeText() { return latestResumeText; }

    // controls the amount of recent turns to limit token usage
    private static final int MAX_TURNS = 6;

    public GeminiServer(String apiKeyResourcePath, Mode initialMode) throws Exception {
        this.apiKey = loadApiKey(apiKeyResourcePath);
        if (this.apiKey == null || this.apiKey.isEmpty()) {
            throw new IllegalStateException("API key is empty or missing!");
        }
        setMode(initialMode);
    }

    private String loadApiKey(String resourcePath) throws Exception {
        try (var in = getClass().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException("API key resource not found: " + resourcePath);
            }
            return new String(in.readAllBytes()).trim();
        }
    }

    public void setMode(Mode mode) {
        this.currentMode = mode;
        switch (mode) {
            case RESUME:
                this.systemInstruction =
                        "You are a professional job coach and resume/interview expert. " +
                                "Your input will come in the form of the user's personal information, " +
                                "as well as their qualifications/experience in plain language. " +
                                "Create a full professional resume based on this information. " +
                                "Afterwards, the user may opt to participate in a mock interview. " +
                                "Responses should be professional in nature. " +
                                "Do not intentionally shorten responses. " +
                                "Do not include emojis or emoticons of any kind in your responses. PLEASE DO NOT INCLUDE ANY MARKDOWN FORMATTING.";
                break;
            case REFINEMENT:
                this.systemInstruction =
                        "You are a professional career coach and resume expert. The user will provide an existing resume in plain text format. Your task is to:\n" +
                                "\n" +
                                "1. Review the provided resume carefully.\n" +
                                "2. Suggest improvements to formatting, phrasing, and structure.\n" +
                                "3. Incorporate any new information the user provides to update the resume.\n" +
                                "4. Maintain a professional and concise tone; do not add emojis or informal language.\n" +
                                "5. When generating updated content, preserve existing information unless instructed otherwise.\n" +
                                "6. Provide responses that can be directly converted into a Word document without losing clarity.\n" +
                                "\n" +
                                "The user may provide additional details, ask for sections to be rewritten, or request new content to be added. Build the refined resume based on the original content and any new instructions, and respond with the updated resume text." +
                                "PLEASE DO NOT INCLUDE ANY MARKDOWN FORMATTING IN THE RESPONSE ITSELF. NOTE THAT WE ARE TRYING TO EXPORT INTO A MICROSOFT WORD DOCUMENT WITH SATISFYING FORMAT.";
                break;
        }
    }

    public Mode getMode() {
        return currentMode;
    }

    public void setSystemInstruction(String instruction) {
        this.systemInstruction = instruction;
    }

    public String sendUserMessage(String userMessage) throws Exception {
        addMessage("user", userMessage);
        trimHistory();

        try {
            JsonObject requestBody = new JsonObject();
            JsonArray contents = new JsonArray();

            // Encode "system" behavior as the first user message
            if (systemInstruction != null && !systemInstruction.isEmpty()) {
                JsonObject sysContent = new JsonObject();
                sysContent.addProperty("role", "user");

                JsonArray sysParts = new JsonArray();
                JsonObject sysPart = new JsonObject();
                sysPart.addProperty("text", systemInstruction);
                sysParts.add(sysPart);

                sysContent.add("parts", sysParts);
                contents.add(sysContent);
            }

            // Add conversation messages
            for (Map<String, Object> msg : conversation) {
                JsonObject content = new JsonObject();
                JsonArray parts = new JsonArray();

                String text = (String) ((Map<?, ?>) ((List<?>) msg.get("parts")).get(0)).get("text");
                JsonObject partObj = new JsonObject();
                partObj.addProperty("text", text);
                parts.add(partObj);

                content.addProperty("role", (String) msg.get("role")); // "user" or "model"
                content.add("parts", parts);
                contents.add(content);
            }

            requestBody.add("contents", contents);

            // Send HTTP request
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL + "?key=" + apiKey))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                    .build();

            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());

            String text = parseResponse(response.body());
            addMessage("model", text);
            return text;

        } catch (Exception e) {
            e.printStackTrace();
            return "[Error: " + e.getMessage() + "]";
        }
    }

    public void sendMessageAsync(String userMessage, VBox chatBox) {
        addUIMessage(chatBox, userMessage, true); // user bubble

        new Thread(() -> {
            try {
                // get Gemini reply
                String reply = sendUserMessage(userMessage);

                // Save the AI’s last response so it can be exported later
                latestResumeText = reply;

                Platform.runLater(() -> addUIMessage(chatBox, reply, false));
            } catch (Exception e) {
                Platform.runLater(() ->
                        addUIMessage(chatBox, "Error communicating with Gemini: " + e.getMessage(), false)
                );
            }
        }).start();
    }


    public Path exportLatestResume(UserInfo user) {
        if (latestResumeText == null || latestResumeText.isEmpty()) {
            System.err.println("No resume text to export!");
            return null;
        }

        Path outputPath = Path.of(
                System.getProperty("user.home"), "Downloads",
                user.getName().replaceAll("\\s+", "_") + "_AI_Resume.docx"
        );

        try (XWPFDocument doc = new XWPFDocument()) {

            // ===== HEADER =====
            XWPFParagraph header = doc.createParagraph();
            header.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun nameRun = header.createRun();
            nameRun.setBold(true);
            nameRun.setFontSize(20);
            nameRun.setText(user.getName());
            nameRun.addBreak();

            XWPFRun contactRun = header.createRun();
            contactRun.setFontSize(11);
            contactRun.setText(user.getCityState() + " | " + user.getEmail() + " | " + user.getPhone());
            contactRun.addBreak();
            contactRun.addBreak();

            // ===== EDUCATION =====
            XWPFParagraph eduTitle = doc.createParagraph();
            XWPFRun eduRun = eduTitle.createRun();
            eduRun.setBold(true);
            eduRun.setFontSize(14);
            eduRun.setText("Education");
            eduRun.addBreak();

            XWPFParagraph eduBody = doc.createParagraph();
            XWPFRun eduBodyRun = eduBody.createRun();
            eduBodyRun.setFontSize(12);
            eduBodyRun.setText("Quinnipiac University, Hamden, CT");
            eduBodyRun.addBreak();
            eduBodyRun.setText("Bachelor of Science in Computer Science — Expected Graduation: May 2027");
            eduBodyRun.addBreak();
            eduBodyRun.addBreak();

            // ===== SKILLS =====
            XWPFParagraph skillTitle = doc.createParagraph();
            XWPFRun skillRun = skillTitle.createRun();
            skillRun.setBold(true);
            skillRun.setFontSize(14);
            skillRun.setText("Skills");
            skillRun.addBreak();

            for (String s : user.getQuals().split("\\.")) {
                if (s.trim().isEmpty()) continue;
                XWPFParagraph bullet = doc.createParagraph();
                bullet.setIndentationLeft(400);
                XWPFRun run = bullet.createRun();
                run.setFontSize(12);
                run.setText("• " + s.trim());
            }

            // ===== EXPERIENCE =====
            XWPFParagraph expTitle = doc.createParagraph();
            XWPFRun expRun = expTitle.createRun();
            expRun.setBold(true);
            expRun.setFontSize(14);
            expRun.setText("Experience");
            expRun.addBreak();

            // Try to locate Experience portion from Gemini output
            String experienceText = extractSection(latestResumeText, "Experience", "Projects");
            if (experienceText.isEmpty()) experienceText = "Researcher — April 2025 to July 2025\nConducted technological research and development work in software and cybersecurity.";
            for (String line : experienceText.split("\\*")) {
                if (line.trim().isEmpty()) continue;
                XWPFParagraph p = doc.createParagraph();
                p.setIndentationLeft(400);
                XWPFRun run = p.createRun();
                run.setFontSize(12);
                run.setText("• " + line.trim());
            }

            // ===== PROJECTS =====
            XWPFParagraph projTitle = doc.createParagraph();
            XWPFRun projRun = projTitle.createRun();
            projRun.setBold(true);
            projRun.setFontSize(14);
            projRun.setText("Projects");
            projRun.addBreak();

            String projectText = extractSection(latestResumeText, "Projects", null);
            if (projectText.isEmpty()) projectText = "Developed two Android applications using Java and Kotlin, focusing on backend integration and user experience.";
            for (String line : projectText.split("\\*")) {
                if (line.trim().isEmpty()) continue;
                XWPFParagraph p = doc.createParagraph();
                p.setIndentationLeft(400);
                XWPFRun run = p.createRun();
                run.setFontSize(12);
                run.setText("• " + line.trim());
            }

            // ===== FOOTER =====
            XWPFParagraph footer = doc.createParagraph();
            footer.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun footerRun = footer.createRun();
            footerRun.setFontSize(9);
            footerRun.setColor("808080");
            footerRun.setText("Generated by HackQU AI Resume Builder");

            try (FileOutputStream out = new FileOutputStream(outputPath.toFile())) {
                doc.write(out);
            }

            System.out.println("Professional resume saved to: " + outputPath);
            return outputPath;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /** Extract a section by keyword */
    private String extractSection(String text, String startKeyword, String endKeyword) {
        text = text.replace("\r", "");
        int start = text.toLowerCase().indexOf(startKeyword.toLowerCase());
        if (start == -1) return "";
        int end = (endKeyword != null)
                ? text.toLowerCase().indexOf(endKeyword.toLowerCase(), start + startKeyword.length())
                : -1;
        if (end == -1) end = text.length();
        return text.substring(start + startKeyword.length(), end).trim();
    }



    private void addUIMessage(VBox chatBox, String text, boolean isUser) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setStyle(isUser
                ? "-fx-background-color: #0078D7; -fx-text-fill: white; -fx-padding: 10 14 10 14; -fx-background-radius: 12 12 0 12;"
                : "-fx-background-color: #e8e8e8; -fx-text-fill: black; -fx-padding: 10 14 10 14; -fx-background-radius: 12 12 12 0;");
        HBox box = new HBox(label);
        box.setAlignment(isUser ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        chatBox.getChildren().add(box);
        chatBox.layout(); // refresh
    }

    private void addMessage(String role, String text) {
        Map<String, Object> msg = Map.of(
                "role", role,
                "parts", List.of(Map.of("text", text))
        );
        conversation.add(msg);
    }

    private void trimHistory() {
        int maxSize = MAX_TURNS * 2; // user+model per turn
        if (conversation.size() > maxSize) {
            conversation.subList(0, conversation.size() - maxSize).clear();
        }
    }

    /** Parse Gemini response JSON to extract text */
    private String parseResponse(String responseBody) {
        JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();

        // Handle API errors gracefully
        if (json.has("error")) {
            JsonObject error = json.getAsJsonObject("error");
            String message = error.has("message")
                    ? error.get("message").getAsString()
                    : "Unknown API error";
            System.err.println("Gemini API error: " + message);
            return "[Error: " + message + "]";
        }

        if (!json.has("candidates")) {
            System.err.println("No candidates field in response: " + responseBody);
            return "[No candidates in response]";
        }

        JsonArray candidates = json.getAsJsonArray("candidates");
        if (candidates.size() == 0) {
            System.err.println("Empty candidates array in response");
            return "[Empty response]";
        }

        JsonObject firstCandidate = candidates.get(0).getAsJsonObject();
        if (!firstCandidate.has("content")) {
            return "[No content in candidate]";
        }

        JsonObject contentObj = firstCandidate.getAsJsonObject("content");
        if (!contentObj.has("parts")) {
            return "[No parts in content]";
        }

        JsonArray parts = contentObj.getAsJsonArray("parts");
        if (parts.size() == 0) {
            return "[Empty parts array]";
        }

        JsonObject firstPart = parts.get(0).getAsJsonObject();
        if (firstPart.has("text")) {
            return firstPart.get("text").getAsString();
        }

        return "[No text in response]";
    }
}
