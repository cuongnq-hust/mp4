package com.example.localAI;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public class OllamaTranslator {

    public static Map<Integer, String> translateBatch(Map<Integer, String> chunks, String glossary, String ip) throws Exception {
        System.out.println("   -> 📡 Đang gửi dữ liệu sang Máy Phụ (" + ip + ") để dịch...");

        // 1. Soạn Prompt (Ra lệnh cho AI) - Phiên bản Phá bẫy Đối xứng
        StringBuilder prompt = new StringBuilder();
        prompt.append("Bạn là một chuyên gia lồng tiếng phim. Hãy dịch chính xác các câu tiếng Trung sau sang tiếng Việt.\\n");
        prompt.append("BẮT BUỘC TUÂN THỦ:\\n");
        prompt.append("- TRẢ LỜI THEO ĐỊNH DẠNG: ID|||Bản dịch tiếng Việt.\\n");
        prompt.append("- TUYỆT ĐỐI KHÔNG in lại tiếng Trung. KHÔNG giải thích.\\n\\n");

        if (glossary != null && !glossary.isEmpty()) {
            prompt.append("Từ điển tham khảo:\\n").append(glossary.replace("\n", "\\n").replace("\"", "\\\"")).append("\\n\\n");
        }

        prompt.append("--- DỮ LIỆU CẦN DỊCH ---\\n");

        for (Map.Entry<Integer, String> entry : chunks.entrySet()) {
            String safeText = entry.getValue().replace("\"", "\\\"").replace("\n", " ");
            // Đưa vào bằng dấu hai chấm (:) thay vì ||| để AI không bị nhầm lẫn quy luật
            prompt.append(entry.getKey()).append(": ").append(safeText).append("\\n");
        }

        prompt.append("--- KẾT THÚC DỮ LIỆU ---\\n\\n");

        // Cú chốt hạ: Ép AI phải bắt đầu viết bằng tiếng Việt với định dạng chuẩn
        prompt.append("BẢN DỊCH TIẾNG VIỆT CỦA TÔI LÀ:\\n");

        // 2. Đóng gói thành chuẩn JSON để gửi đi (Đã khóa sáng tạo bằng Temperature 0)
        String jsonPayload = "{"
                + "\"model\": \"qwen2.5:7b\","
                + "\"prompt\": \"" + prompt.toString() + "\","
                + "\"stream\": false,"
                + "\"options\": {"
                + "  \"temperature\": 0.0,"  // Bắt AI nghiêm túc tuyệt đối
                + "  \"top_p\": 0.1"
                + "}"
                + "}";

        // 3. Khởi tạo đường truyền HTTP
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://" + ip + ":11434/api/generate"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload, java.nio.charset.StandardCharsets.UTF_8))
                .timeout(Duration.ofMinutes(15)) // Chờ tối đa 15 phút vì Máy phụ dùng CPU sẽ dịch khá chậm
                .build();

        // 4. Nhận kết quả từ Máy phụ và bóc tách
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        String responseBody = response.body();

        Map<Integer, String> resultMap = new HashMap<>();
        // Tách lấy giá trị trong trường "response" của JSON mà không cần dùng thư viện ngoài
        String[] parts = responseBody.split("\"response\":\"", 2);
        if (parts.length > 1) {
            String data = parts[1];
            int endIndex = data.lastIndexOf("\",\"done\"");
            if (endIndex != -1) {
                String cleanText = data.substring(0, endIndex).replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\");

                // Đưa vào thư viện Map trả về cho Main.java
                String[] lines = cleanText.split("\n");
                for (String line : lines) {
                    String[] p = line.split("\\|\\|\\|");
                    if (p.length >= 2) {
                        try {
                            int id = Integer.parseInt(p[0].trim());
                            resultMap.put(id, p[1].trim());
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        }
        return resultMap;
    }
}