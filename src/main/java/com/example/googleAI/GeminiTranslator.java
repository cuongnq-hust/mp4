package com.example.googleAI;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GeminiTranslator {

    private static final String DEFAULT_API_KEY = "";
    private static final String CONFIG_FILE = "config.properties";
    private static String cachedApiKey = null;
    private static String activeModel = null;

    public static final String MODEL_PRO = "gemini-3.6-flash";

    // Chuỗi 100% mô hình Free-Tier Flash & Flash-Lite (Mỗi model có hạn mức RPM/RPD độc lập).
    // Đã loại bỏ hoàn toàn gemini-pro-latest (gemini-3.1-pro) vì Free Tier có limit = 0 cho dòng Pro.
    private static final String[] MODEL_FALLBACK_CHAIN = {
            "gemini-3.6-flash",
            "gemini-3.5-flash",
            "gemini-3.5-flash-lite",
            "gemini-3.1-flash-lite",
            "gemini-2.5-flash-lite",
            "gemini-flash-lite-latest",
            "gemini-omni-1.1-flash",
            "gemini-3-flash-preview",
            "gemini-3.8-flash",
            "gemini-3.7-flash",
            "gemini-flash-latest"
    };

    private static final String[] TTS_MODEL_CHAIN = {
            "gemini-2.5-flash-preview-tts",
            "gemini-3.8-flash-tts",
            "gemini-3.8-flash-lite-tts",
            "gemini-3.1-flash-tts-preview"
    };

    public static String getOrPromptApiKey() {
        Properties props = new Properties();
        File configFile = new File(CONFIG_FILE);
        if (configFile.exists()) {
            try (FileInputStream fis = new FileInputStream(configFile)) {
                props.load(fis);
                String savedKey = props.getProperty("gemini.api.key", "").trim();
                if (!savedKey.isEmpty()) {
                    cachedApiKey = savedKey;
                    return cachedApiKey;
                }
            } catch (Exception ignored) {
            }
        }

        if (!DEFAULT_API_KEY.isBlank()) {
            cachedApiKey = DEFAULT_API_KEY.trim();
            return cachedApiKey;
        }

        String envKey = System.getenv("GEMINI_API_KEY");
        if (envKey != null && !envKey.isBlank()) {
            cachedApiKey = envKey.trim();
            return cachedApiKey;
        }

        String inputKey = JOptionPane.showInputDialog(
                null,
                "Nhập Google Gemini API Key (Lấy tại aistudio.google.com):\n(Hệ thống sẽ tự lưu vào config.properties cho các lần chạy sau)",
                "Cấu hình Gemini API",
                JOptionPane.QUESTION_MESSAGE
        );

        if (inputKey != null && !inputKey.trim().isEmpty()) {
            cachedApiKey = inputKey.trim();
            props.setProperty("gemini.api.key", cachedApiKey);
            try (FileOutputStream fos = new FileOutputStream(configFile)) {
                props.store(fos, "MediaTranslator Configuration");
            } catch (Exception ignored) {
            }
            return cachedApiKey;
        }

        throw new RuntimeException("Chưa cung cấp Gemini API Key!");
    }

    /**
     * Sinh giọng đọc AI đa cảm xúc bằng Google Gemini TTS (Charon, Kore, Puck, Fenrir...).
     * Xuất ra file WAV chuẩn 24000Hz, 16-bit, mono.
     */
    public static void generateGeminiTTS(String vietnameseText, String outputPath, String geminiVoiceName) throws Exception {
        String apiKey = getOrPromptApiKey();
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
        ObjectMapper mapper = new ObjectMapper();

        String cleanText = vietnameseText
                .replaceAll("[\\u4e00-\\u9fa5]", "")
                .replaceAll("\\s+", " ")
                .trim();

        String styledPrompt = "Đọc câu thuyết minh phim sau bằng tiếng Việt với giọng truyền cảm, cuốn hút, rõ chữ: " + cleanText;

        Exception lastErr = null;
        for (String ttsModel : TTS_MODEL_CHAIN) {
            try {
                ObjectNode rootNode = mapper.createObjectNode();
                ArrayNode contents = rootNode.putArray("contents");
                ObjectNode contentObj = contents.addObject();
                ArrayNode parts = contentObj.putArray("parts");
                parts.addObject().put("text", styledPrompt);

                ObjectNode genConfig = rootNode.putObject("generationConfig");
                ArrayNode modalities = genConfig.putArray("responseModalities");
                modalities.add("AUDIO");

                ObjectNode speechConfig = genConfig.putObject("speechConfig");
                ObjectNode voiceConfig = speechConfig.putObject("voiceConfig");
                ObjectNode prebuilt = voiceConfig.putObject("prebuiltVoiceConfig");
                prebuilt.put("voiceName", geminiVoiceName);

                String url = "https://generativelanguage.googleapis.com/v1beta/models/" + ttsModel + ":generateContent?key=" + apiKey;
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json; charset=UTF-8")
                        .timeout(Duration.ofSeconds(45))
                        .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(rootNode), StandardCharsets.UTF_8))
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() != 200) {
                    throw new Exception("HTTP " + response.statusCode() + ": " + response.body());
                }

                JsonNode resJson = mapper.readTree(response.body());
                String base64Pcm = resJson.path("candidates").path(0).path("content").path("parts").path(0)
                        .path("inlineData").path("data").asText("");

                if (base64Pcm.isEmpty()) {
                    throw new Exception("Gemini TTS không trả về dữ liệu âm thanh.");
                }

                byte[] pcmBytes = Base64.getDecoder().decode(base64Pcm);
                writePcmToWavFile(pcmBytes, 24000, 1, 16, outputPath);
                return;
            } catch (Exception e) {
                lastErr = e;
            }
        }
        throw lastErr != null ? lastErr : new Exception("Không thể tạo giọng Gemini TTS.");
    }

    private static void writePcmToWavFile(byte[] pcmData, int sampleRate, int channels, int bitsPerSample, String outputPath) throws Exception {
        int byteRate = sampleRate * channels * bitsPerSample / 8;
        int blockAlign = channels * bitsPerSample / 8;
        int dataSize = pcmData.length;
        int chunkSize = 36 + dataSize;

        ByteBuffer header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        header.put("RIFF".getBytes(StandardCharsets.US_ASCII));
        header.putInt(chunkSize);
        header.put("WAVE".getBytes(StandardCharsets.US_ASCII));
        header.put("fmt ".getBytes(StandardCharsets.US_ASCII));
        header.putInt(16);
        header.putShort((short) 1);
        header.putShort((short) channels);
        header.putInt(sampleRate);
        header.putInt(byteRate);
        header.putShort((short) blockAlign);
        header.putShort((short) bitsPerSample);
        header.put("data".getBytes(StandardCharsets.US_ASCII));
        header.putInt(dataSize);

        try (FileOutputStream fos = new FileOutputStream(outputPath)) {
            fos.write(header.array());
            fos.write(pcmData);
        }
    }

    /**
     * Bóc tách âm thanh tiếng Trung sang văn bản kèm mốc thời gian chính xác từng cụm câu:
     * - Ưu tiên 1 (Offline Local Faster-Whisper AI): Nhanh, chính xác đến từng dấu phẩy (word_timestamps),
     *   KHÔNG tốn bất kỳ Token hay Quota nào của Gemini API, không bao giờ bị lỗi 429!
     * - Ưu tiên 2 (Dự phòng Cloud Gemini Audio Flash/Lite): Tự động kích hoạt nếu máy chưa cài faster-whisper.
     */
    public static List<String> transcribeAudio(String audioPath, double offsetSeconds) throws Exception {
        List<String> localSegments = transcribeWithLocalFasterWhisper(audioPath, offsetSeconds);
        if (localSegments != null && !localSegments.isEmpty()) {
            return localSegments;
        }

        System.out.println("   -> ☁️ Chuyển sang bóc băng bằng Cloud Gemini Flash Audio...");
        String apiKey = getOrPromptApiKey();
        byte[] audioBytes = Files.readAllBytes(Paths.get(audioPath));
        String base64Audio = Base64.getEncoder().encodeToString(audioBytes);

        List<String> modelsToTry = getOrderedModelList();
        Exception lastException = null;

        for (String model : modelsToTry) {
            try {
                List<String> segments = callGeminiAudioTranscribe(base64Audio, offsetSeconds, model, apiKey);
                activeModel = model;
                return segments;
            } catch (Exception e) {
                lastException = e;
                String msg = e.getMessage() != null ? e.getMessage() : "";
                if (msg.contains("404") || msg.contains("429") || msg.contains("503") || msg.contains("RESOURCE_EXHAUSTED") || msg.contains("UNAVAILABLE") || msg.contains("NOT_FOUND")) {
                    System.out.println("   -> ⚡ Model " + model + " chạm giới hạn/bận, tự động chuyển sang model Flash/Lite kế tiếp...");
                    continue;
                }
                throw e;
            }
        }
        throw lastException != null ? lastException : new Exception("Không thể bóc băng âm thanh.");
    }

    /**
     * Bóc băng trực tiếp bằng Faster-Whisper trên máy (0% API Quota, tách câu chuẩn theo nhịp thở & dấu phẩy).
     */
    private static List<String> transcribeWithLocalFasterWhisper(String audioPath, double offsetSeconds) {
        String pyCode = "import sys, re\n"
                + "sys.stdout.reconfigure(encoding='utf-8')\n"
                + "from faster_whisper import WhisperModel\n"
                + "audio_path = sys.argv[1]\n"
                + "offset = float(sys.argv[2])\n"
                + "model = None\n"
                + "for m_name in ['small', 'base', 'tiny']:\n"
                + "    for local_only in [True, False]:\n"
                + "        try:\n"
                + "            model = WhisperModel(m_name, device='cpu', compute_type='int8', local_files_only=local_only)\n"
                + "            break\n"
                + "        except Exception:\n"
                + "            pass\n"
                + "    if model is not None:\n"
                + "        break\n"
                + "if model is None:\n"
                + "    sys.exit(2)\n"
                + "segments, _ = model.transcribe(audio_path, language='zh', vad_filter=True, word_timestamps=True)\n"
                + "for seg in segments:\n"
                + "    dur = seg.end - seg.start\n"
                + "    if dur > 4.5 and getattr(seg, 'words', None):\n"
                + "        cur_words = []\n"
                + "        cur_start = None\n"
                + "        for w in seg.words:\n"
                + "            if cur_start is None:\n"
                + "                cur_start = w.start\n"
                + "            cur_words.append(w.word)\n"
                + "            txt = ''.join(cur_words).strip()\n"
                + "            if (w.end - cur_start >= 1.5) and re.search(r'[，,。！？；;]$', txt):\n"
                + "                clean = re.sub(r'[，,。！？；;]+$', '', txt).replace('|', '').strip()\n"
                + "                if clean:\n"
                + "                    print(f'SEG|{cur_start + offset:.2f}|{w.end + offset:.2f}|{clean}', flush=True)\n"
                + "                cur_words = []\n"
                + "                cur_start = None\n"
                + "        if cur_words and cur_start is not None:\n"
                + "            clean = re.sub(r'[，,。！？；;]+$', '', ''.join(cur_words)).replace('|', '').strip()\n"
                + "            if clean:\n"
                + "                print(f'SEG|{cur_start + offset:.2f}|{seg.end + offset:.2f}|{clean}', flush=True)\n"
                + "    else:\n"
                + "        clean = seg.text.replace('|', '').strip()\n"
                + "        if clean and seg.end > seg.start:\n"
                + "            print(f'SEG|{seg.start + offset:.2f}|{seg.end + offset:.2f}|{clean}', flush=True)\n";

        List<String> results = new ArrayList<>();
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "python", "-c", pyCode,
                    new File(audioPath).getAbsolutePath(),
                    String.format(Locale.US, "%.2f", offsetSeconds)
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.startsWith("SEG|")) {
                        String segData = line.substring(4);
                        results.add(segData);
                        System.out.println("AI nghe được: " + segData);
                    }
                }
            }

            int code = p.waitFor();
            if (code == 0 && !results.isEmpty()) {
                return results;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static List<String> callGeminiAudioTranscribe(String base64Audio, double offsetSeconds, String model, String apiKey) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
        ObjectMapper mapper = new ObjectMapper();

        ObjectNode rootNode = mapper.createObjectNode();
        ArrayNode contentsArray = rootNode.putArray("contents");
        ObjectNode userContent = contentsArray.addObject();
        userContent.put("role", "user");
        ArrayNode parts = userContent.putArray("parts");

        ObjectNode inlineData = parts.addObject().putObject("inlineData");
        inlineData.put("mimeType", "audio/wav");
        inlineData.put("data", base64Audio);

        String prompt = "Hãy nghe kỹ file âm thanh tiếng Trung này và bóc tách chính xác từng câu thoại kèm mốc thời gian thực tế lúc người nói bắt đầu phát âm (start) và kết thúc phát âm (end) tính bằng giây (số thực chính xác đến 2 chữ số thập phân, ví dụ 0.12, 3.25).\n"
                + "QUY TẮC:\n"
                + "- Tách từng câu ngắn gọn theo đúng nhịp nghỉ lấy hơi của người nói (mỗi câu từ 2 đến 6 giây), không gộp nhiều câu dài vào 1 đoạn.\n"
                + "- BẮT BUỘC trả về CHỈ MỘT JSON array theo cấu trúc:\n"
                + "[{\"start\": 0.10, \"end\": 3.25, \"text\": \"Câu thoại tiếng Trung\"}]";
        parts.addObject().put("text", prompt);

        ObjectNode genConfig = rootNode.putObject("generationConfig");
        genConfig.put("responseMimeType", "application/json");
        genConfig.put("temperature", 0.0);

        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + apiKey;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json; charset=UTF-8")
                .timeout(Duration.ofMinutes(3))
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(rootNode), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new Exception("Gemini Audio API Lỗi " + response.statusCode() + ": " + response.body());
        }

        JsonNode resJson = mapper.readTree(response.body());
        String rawText = resJson.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText().trim();
        String cleanJson = rawText
                .replaceAll("(?s)^```json\\s*", "")
                .replaceAll("(?s)^```\\s*", "")
                .replaceAll("(?s)```$", "")
                .trim();

        List<String> segments = new ArrayList<>();
        JsonNode arrayNode = mapper.readTree(cleanJson);
        if (arrayNode.isObject() && arrayNode.fields().hasNext()) {
            arrayNode = arrayNode.fields().next().getValue();
        }
        if (arrayNode.isArray()) {
            for (JsonNode item : arrayNode) {
                double start = parseTimeSeconds(item.path("start").asText("0")) + offsetSeconds;
                double end = parseTimeSeconds(item.path("end").asText("0")) + offsetSeconds;
                String text = item.path("text").asText("").replace("|", "").replace("\n", " ").trim();
                if (!text.isEmpty() && end > start) {
                    String line = String.format(Locale.US, "%.2f|%.2f|%s", start, end, text);
                    segments.add(line);
                    System.out.println("AI nghe được: " + line);
                }
            }
        }
        return segments;
    }

    private static double parseTimeSeconds(String val) {
        val = val.trim();
        if (val.contains(":")) {
            String[] parts = val.split(":");
            double total = 0;
            for (String p : parts) {
                total = total * 60 + Double.parseDouble(p.replace(",", "."));
            }
            return total;
        }
        return Double.parseDouble(val.replace(",", "."));
    }

    private static List<String> getOrderedModelList() {
        List<String> list = new ArrayList<>();
        if (activeModel != null) {
            list.add(activeModel);
        }
        for (String m : MODEL_FALLBACK_CHAIN) {
            if (!list.contains(m)) {
                list.add(m);
            }
        }
        return list;
    }

    public static Map<Integer, String> translateBatch(
            Map<Integer, String> chineseBatch,
            Map<Integer, Double> durationBatch,
            String previousContext,
            String glossary,
            String preferredModel
    ) throws Exception {
        String apiKey = getOrPromptApiKey();
        List<String> modelsToTry = getOrderedModelList();
        Exception lastException = null;

        for (String model : modelsToTry) {
            try {
                Map<Integer, String> result = callGeminiApi(chineseBatch, durationBatch, previousContext, glossary, model, apiKey);
                activeModel = model;
                return result;
            } catch (Exception e) {
                lastException = e;
                String msg = e.getMessage() != null ? e.getMessage() : "";
                if (msg.contains("404") || msg.contains("429") || msg.contains("503") || msg.contains("RESOURCE_EXHAUSTED") || msg.contains("UNAVAILABLE") || msg.contains("NOT_FOUND")) {
                    System.out.println("   -> ⚡ Model " + model + " chạm giới hạn/bận, tự động chuyển sang model Flash/Lite kế tiếp...");
                    continue;
                }
                throw e;
            }
        }
        throw lastException != null ? lastException : new Exception("Không thể dịch bằng Gemini.");
    }

    private static Map<Integer, String> callGeminiApi(
            Map<Integer, String> chineseBatch,
            Map<Integer, Double> durationBatch,
            String previousContext,
            String glossary,
            String model,
            String apiKey
    ) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
        ObjectMapper mapper = new ObjectMapper();
        Map<Integer, String> translatedMap = new HashMap<>();

        ObjectNode inputJson = mapper.createObjectNode();
        for (Map.Entry<Integer, String> entry : chineseBatch.entrySet()) {
            int id = entry.getKey();
            double dur = (durationBatch != null && durationBatch.containsKey(id)) ? durationBatch.get(id) : 3.0;
            int targetWords = Math.max(3, (int) Math.round(dur * 3.5));
            inputJson.put(String.valueOf(id), String.format(Locale.US, "[Thời lượng: %.1fs | ~%d từ] %s", dur, targetWords, entry.getValue()));
        }

        StringBuilder systemInstruction = new StringBuilder();
        systemInstruction.append("Bạn là đạo diễn lồng tiếng và biên dịch phim Trung Quốc sang tiếng Việt.\n")
                .append("QUY TẮC SỐNG CÒN ĐỂ KHỚP KHẨU HÌNH (ISOCHRONY DUBBING):\n")
                .append("1. Đầu ra CHỈ LÀ một JSON object phẳng hợp lệ (key là ID số nguyên dạng chuỗi, value là câu thoại tiếng Việt đã dịch — TUYỆT ĐỐI KHÔNG lặp lại nhãn [Thời lượng: ...] trong kết quả).\n")
                .append("2. Mỗi câu đầu vào có ghi chú [Thời lượng: Xs | ~N từ]. Hãy viết câu tiếng Việt có số lượng từ xấp xỉ ~N từ (chênh lệch không quá ±20%) để khi diễn viên lồng tiếng đọc lên sẽ vừa khít thời gian nhân vật mấp máy môi.\n")
                .append("3. TUYỆT ĐỐI KHÔNG để sót bất kỳ chữ Hán (ký tự Trung Quốc) nào trong bản dịch. Tên riêng, địa danh phải dịch Hán-Việt chuẩn xác.\n")
                .append("4. Giữ sự thống nhất tuyệt đối về đại từ nhân xưng (anh-em, ta-ngươi, huynh-đệ, sư phụ-đệ tử...) xuyên suốt mạch phim.\n")
                .append("5. Phải dịch ĐẦY ĐỦ tất cả các ID có trong đầu vào, không gộp câu, không bỏ sót ID nào.\n\n")
                .append("Ví dụ Input: {\"0\": \"[Thời lượng: 1.5s | ~5 từ] 你好，快走吧\"}\n")
                .append("Ví dụ Output: {\"0\": \"Chào cậu, mau đi thôi\"}\n");

        if (glossary != null && !glossary.isBlank()) {
            systemInstruction.append("\nTừ điển thuật ngữ/tên riêng bắt buộc tuân thủ:\n").append(glossary.trim()).append("\n");
        }

        if (previousContext != null && !previousContext.isBlank()) {
            systemInstruction.append("\nNgữ cảnh các câu ngay trước đó (chỉ dùng để tham khảo mạch truyện và xưng hô, KHÔNG dịch lại):\n")
                    .append(previousContext.trim()).append("\n");
        }

        ObjectNode rootNode = mapper.createObjectNode();

        ObjectNode sysNode = rootNode.putObject("systemInstruction");
        ArrayNode sysParts = sysNode.putArray("parts");
        sysParts.addObject().put("text", systemInstruction.toString());

        ArrayNode contentsArray = rootNode.putArray("contents");
        ObjectNode userContent = contentsArray.addObject();
        userContent.put("role", "user");
        ArrayNode userParts = userContent.putArray("parts");
        userParts.addObject().put("text", inputJson.toString());

        ObjectNode genConfig = rootNode.putObject("generationConfig");
        genConfig.put("responseMimeType", "application/json");
        genConfig.put("temperature", 0.2);

        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + apiKey;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json; charset=UTF-8")
                .timeout(Duration.ofMinutes(3))
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(rootNode), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (response.statusCode() != 200) {
            throw new Exception("Gemini API Lỗi " + response.statusCode() + ": " + response.body());
        }

        JsonNode resJson = mapper.readTree(response.body());
        JsonNode candidates = resJson.path("candidates");
        if (!candidates.isArray() || candidates.isEmpty()) {
            throw new Exception("Gemini không trả về candidates hợp lệ: " + response.body());
        }

        String aiRawResponse = candidates.path(0).path("content").path("parts").path(0).path("text").asText().trim();
        if (aiRawResponse.isEmpty()) {
            throw new Exception("Gemini trả về nội dung rỗng.");
        }

        String cleanOutput = aiRawResponse
                .replaceAll("(?s)^```json\\s*", "")
                .replaceAll("(?s)^```\\s*", "")
                .replaceAll("(?s)```$", "")
                .trim();

        try {
            JsonNode parsedJson = mapper.readTree(cleanOutput);
            Iterator<Map.Entry<String, JsonNode>> fields = parsedJson.fields();

            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String key = field.getKey().trim();
                JsonNode value = field.getValue();

                if (value.isObject()) {
                    Iterator<Map.Entry<String, JsonNode>> innerFields = value.fields();
                    while (innerFields.hasNext()) {
                        Map.Entry<String, JsonNode> innerField = innerFields.next();
                        try {
                            String cleanVal = innerField.getValue().asText().replaceAll("^\\[.*?\\]\\s*", "").trim();
                            translatedMap.put(Integer.parseInt(innerField.getKey().trim()), cleanVal);
                        } catch (Exception ignored) {
                        }
                    }
                } else {
                    try {
                        String cleanVal = value.asText().replaceAll("^\\[.*?\\]\\s*", "").trim();
                        translatedMap.put(Integer.parseInt(key), cleanVal);
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("   -> ⚠️ Parse JSON Gemini thất bại, thử RegExp. Raw: " + cleanOutput);
            String[] lines = cleanOutput.split("\n");
            Pattern p1 = Pattern.compile("[\"']?(\\d+)[\"']?\\s*:\\s*[\"'](.*)[\"'],?");
            for (String line : lines) {
                Matcher m1 = p1.matcher(line.trim());
                if (m1.matches()) {
                    String cleanVal = m1.group(2).replaceAll("^\\[.*?\\]\\s*", "").trim();
                    translatedMap.put(Integer.parseInt(m1.group(1)), cleanVal);
                }
            }
        }

        if (translatedMap.isEmpty()) {
            throw new Exception("Không bóc tách được ID nào từ Gemini. Output thô: " + cleanOutput);
        }

        return translatedMap;
    }
}
