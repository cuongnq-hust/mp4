package com.example.googleAI;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GroqTranslator {

    // ĐIỀN API KEY GROQ CỦA BẠN VÀO ĐÂY
    private static final String API_KEY = "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";

    private static final String API_URL = "https://api.groq.com/openai/v1/chat/completions";

    public static Map<Integer, String> translateBatch(Map<Integer, String> chineseBatch, String glossary) throws
            Exception {
        HttpClient client = HttpClient.newHttpClient();
        ObjectMapper mapper = new ObjectMapper();
        Map<Integer, String> translatedMap = new HashMap<>();

        ObjectNode inputJson = mapper.createObjectNode();
        for (Map.Entry<Integer, String> entry : chineseBatch.entrySet()) {
            inputJson.put(String.valueOf(entry.getKey()), entry.getValue());
        }

        // [VŨ KHÍ MỚI] System Prompt Kỷ Luật Thép: Ép AI trả đúng schema bằng ví dụ
        String systemPrompt = "You are an expert translator specializing in Chinese to Vietnamese movie translations. "
                + "CRITICAL RULES:\n"
                + "1. Output MUST be ONLY a valid flat JSON object.\n"
                + "2. DO NOT include any Chinese characters in the output values.\n"
                + "3. DO NOT add any explanations, markdown formatting, or extra keys.\n"
                + "4. The JSON keys MUST exactly match the numeric IDs from the input.\n\n"
                + "Example Input: {\"0\": \"你好\", \"1\": \"谢谢\"}\n"
                + "Example Output: {\"0\": \"Xin chào\", \"1\": \"Cảm ơn\"}\n\n"
                + (glossary != null && !glossary.isBlank() ? "Glossary to strictly follow:\n" + glossary : "");
        String userMessage = inputJson.toString();

        ObjectNode rootNode = mapper.createObjectNode();
        // 2. ĐỔI SANG QWEN (Nếu tài khoản Groq của bạn hỗ trợ, nếu không thì trả lại llama)
        rootNode.put("model", "qwen/qwen3-32b");
        rootNode.putObject("response_format").put("type", "json_object");
        var messagesArray = rootNode.putArray("messages");
        messagesArray.addObject().put("role", "system").put("content", systemPrompt);
        messagesArray.addObject().put("role", "user").put("content", userMessage);

        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(API_URL))
                .header("Content-Type", "application/json").header("Authorization", "Bearer " + API_KEY)
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(rootNode))).build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new Exception("API Lỗi " + response.statusCode() + ": " + response.body());
        }

        JsonNode resJson = mapper.readTree(response.body());
        String aiRawResponse = resJson.path("choices").path(0).path("message").path("content").asText().trim();

        if (aiRawResponse.isEmpty()) {
            throw new Exception("API trả về content rỗng.");
        }

        String cleanOutput = aiRawResponse.replaceAll("(?s)^```json\\s*", "").replaceAll("(?s)```$", "").trim();

        // [LỚP PHÒNG THỦ] Phân tích JSON thông minh, chấp nhận mọi kiểu định dạng lỗi của AI
        try {
            JsonNode parsedJson = mapper.readTree(cleanOutput);
            Iterator<Map.Entry<String, JsonNode>> fields = parsedJson.fields();

            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String key = field.getKey().trim();
                JsonNode value = field.getValue();

                // Nếu AI ngớ ngẩn tự bọc object vào một key lạ (VD: "bản_dịch": {"0": "...", "1": "..."})
                if (value.isObject()) {
                    Iterator<Map.Entry<String, JsonNode>> innerFields = value.fields();
                    while (innerFields.hasNext()) {
                        Map.Entry<String, JsonNode> innerField = innerFields.next();
                        try {
                            translatedMap.put(Integer.parseInt(innerField.getKey().trim()),
                                    innerField.getValue().asText());
                        } catch (Exception ignored) {
                        } // Bỏ qua key rác
                    }
                } else {
                    // Cấu trúc phẳng chuẩn xác
                    try {
                        translatedMap.put(Integer.parseInt(key), value.asText());
                    } catch (Exception ignored) {
                    } // Bỏ qua nếu key là chữ (như "description")
                }
            }
        } catch (Exception e) {
            System.out.println("   -> ⚠️ Parse JSON thất bại, thử RegExp. Raw: " + cleanOutput);
            String[] lines = cleanOutput.split("\n");
            Pattern p1 = Pattern.compile("[\"']?(\\d+)[\"']?\\s*:\\s*[\"'](.*)[\"'],?");
            for (String line : lines) {
                Matcher m1 = p1.matcher(line.trim());
                if (m1.matches()) translatedMap.put(Integer.parseInt(m1.group(1)), m1.group(2).trim());
            }
        }

        // Kích hoạt báo lỗi (để chạy Retry) nếu không vớt vát được câu nào
        if (translatedMap.isEmpty()) {
            throw new Exception("Không bóc tách được ID số học nào từ AI. Output thô: " + cleanOutput);
        }

        return translatedMap;
    }

}