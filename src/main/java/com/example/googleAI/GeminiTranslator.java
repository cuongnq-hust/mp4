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
import java.util.LinkedHashMap;
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
     * Sinh giọng đọc AI đa cảm xúc bằng Google Gemini TTS (Charon, Kore...).
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
     * Bóc băng siêu tốc toàn bộ danh sách file âm thanh trong 1 tiến trình Faster-Whisper duy nhất
     * (Nạp model 1 lần, đa luồng CPU, tốn 0% Quota Gemini API).
     */
    public static List<String> transcribeAllAudioParts(File[] partFiles) throws Exception {
        if (partFiles == null || partFiles.length == 0) {
            return new ArrayList<>();
        }

        String pyBatchStt = "import sys, os, re, wave\n"
                + "sys.stdout.reconfigure(encoding='utf-8')\n"
                + "from faster_whisper import WhisperModel\n"
                + "files = sys.argv[1:]\n"
                + "cpu_cnt = max(4, (os.cpu_count() or 4))\n"
                + "model = None\n"
                + "for dev, ctype, label in [('cuda', 'float16', 'NVIDIA CUDA GPU'), ('cpu', 'int8', f'{cpu_cnt} luồng CPU')]:\n"
                + "    for m_name in ['small', 'base', 'tiny']:\n"
                + "        for local_only in [True, False]:\n"
                + "            try:\n"
                + "                kw = {'device': dev, 'compute_type': ctype, 'local_files_only': local_only}\n"
                + "                if dev == 'cpu': kw['cpu_threads'] = cpu_cnt\n"
                + "                model = WhisperModel(m_name, **kw)\n"
                + "                print(f'INFO|Đã nạp mô hình AI Faster-Whisper ({m_name} - Tăng tốc: {label})', flush=True)\n"
                + "                break\n"
                + "            except Exception:\n"
                + "                pass\n"
                + "        if model is not None:\n"
                + "            break\n"
                + "    if model is not None:\n"
                + "        break\n"
                + "if model is None:\n"
                + "    sys.exit(2)\n"
                + "offset = 0.0\n"
                + "for fpath in files:\n"
                + "    segments, _ = model.transcribe(fpath, language='zh', beam_size=2, vad_filter=True, word_timestamps=True)\n"
                + "    for seg in segments:\n"
                + "        dur = seg.end - seg.start\n"
                + "        if dur > 4.2 and getattr(seg, 'words', None):\n"
                + "            cur_words = []\n"
                + "            cur_start = None\n"
                + "            for w in seg.words:\n"
                + "                if cur_start is None: cur_start = w.start\n"
                + "                cur_words.append(w.word)\n"
                + "                txt = ''.join(cur_words).strip()\n"
                + "                if (w.end - cur_start >= 1.4) and re.search(r'[，,。！？；;]$', txt):\n"
                + "                    clean = re.sub(r'[，,。！？；;]+$', '', txt).replace('|', '').strip()\n"
                + "                    if clean:\n"
                + "                        print(f'SEG|{cur_start + offset:.2f}|{w.end + offset:.2f}|{clean}', flush=True)\n"
                + "                    cur_words = []\n"
                + "                    cur_start = None\n"
                + "            if cur_words and cur_start is not None:\n"
                + "                clean = re.sub(r'[，,。！？；;]+$', '', ''.join(cur_words)).replace('|', '').strip()\n"
                + "                if clean:\n"
                + "                    print(f'SEG|{cur_start + offset:.2f}|{seg.end + offset:.2f}|{clean}', flush=True)\n"
                + "        else:\n"
                + "            clean = seg.text.replace('|', '').strip()\n"
                + "            if clean and seg.end > seg.start:\n"
                + "                print(f'SEG|{seg.start + offset:.2f}|{seg.end + offset:.2f}|{clean}', flush=True)\n"
                + "    try:\n"
                + "        with wave.open(fpath, 'rb') as wf:\n"
                + "            offset += wf.getnframes() / float(wf.getframerate())\n"
                + "    except Exception:\n"
                + "        offset += 300.0\n";

        List<String> cmd = new ArrayList<>();
        cmd.add("python");
        cmd.add("-X");
        cmd.add("utf8");
        cmd.add("-c");
        cmd.add(pyBatchStt);
        for (File pf : partFiles) {
            cmd.add(pf.getAbsolutePath());
        }

        List<String> results = new ArrayList<>();
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.startsWith("INFO|")) {
                        System.out.println("   -> ⚡ " + line.substring(5));
                    } else if (line.startsWith("SEG|")) {
                        String segData = line.substring(4);
                        results.add(segData);
                        System.out.println("   -> 🎧 [" + results.size() + "] " + segData);
                    }
                }
            }

            int code = p.waitFor();
            if (code == 0 && !results.isEmpty()) {
                return results;
            }
        } catch (Exception ignored) {
        }

        // Dự phòng nếu máy chưa có faster-whisper: dùng Cloud Gemini Flash Audio
        double offset = 0.0;
        for (File pf : partFiles) {
            results.addAll(transcribeAudio(pf.getAbsolutePath(), offset));
            offset += 300.0;
        }
        return results;
    }

    public static List<String> transcribeAudio(String audioPath, double offsetSeconds) throws Exception {
        File singleFile = new File(audioPath);
        if (offsetSeconds == 0.0) {
            String pyQuick = "import faster_whisper";
            try {
                Process p = new ProcessBuilder("python", "-c", pyQuick).start();
                if (p.waitFor() == 0) {
                    List<String> res = transcribeAllAudioParts(new File[]{singleFile});
                    if (!res.isEmpty()) return res;
                }
            } catch (Exception ignored) {
            }
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

        String prompt = "Hãy nghe kỹ file âm thanh tiếng Trung này và bóc tách chính xác từng câu thoại kèm mốc thời gian thực tế lúc người nói bắt đầu phát âm (start) và kết thúc phát âm (end) tính bằng giây.\n"
                + "BẮT BUỘC trả về CHỈ MỘT JSON array theo cấu trúc:\n"
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

    /**
     * PHA 1 (STORY BIBLE & ASR HOMOPHONE CORRECTION):
     * Đọc toàn bộ kịch bản tiếng Trung để tự động xác định bối cảnh phim, lập bảng tên nhân vật Hán-Việt,
     * quy tắc đại từ xưng hô, và phát hiện các từ đồng âm do STT nghe nhầm (ví dụ: 苦恋 -> 苦练, 零峰 -> 林风).
     */
    public static String analyzeStoryBible(List<String> audioSegments, String genreHint, String userGlossary) {
        if (audioSegments == null || audioSegments.isEmpty()) {
            return userGlossary != null ? userGlossary : "";
        }

        try {
            String apiKey = getOrPromptApiKey();
            StringBuilder fullScript = new StringBuilder();
            int limit = Math.min(audioSegments.size(), 150);
            for (int i = 0; i < limit; i++) {
                String[] p = audioSegments.get(i).split("\\|");
                if (p.length >= 3) {
                    fullScript.append(i).append(": ").append(p[2]).append("\n");
                }
            }

            String prompt = "Bạn là Tổng đạo diễn biên dịch phim Trung Quốc sang tiếng Việt.\n"
                    + "Dưới đây là bản bóc băng âm thanh (ASR) của bộ phim"
                    + (genreHint != null && !genreHint.isEmpty() ? " (Thể loại gợi ý: " + genreHint + ")" : "") + ":\n\n"
                    + fullScript + "\n"
                    + "Hãy phân tích toàn cục và trả về bản HỒ SƠ DỊCH THUẬT ngắn gọn (dưới 250 từ) gồm 3 mục:\n"
                    + "1. BỐI CẢNH & XƯNG HÔ: Xác định thể loại (Cổ trang kiếm hiệp / Tu tiên / Ngôn tình hiện đại / Trinh thám...) và quy tắc đại từ nhân xưng chuẩn xuyên suốt.\n"
                    + "2. SỬA LỖI ĐỒNG ÂM STT: Liệt kê các từ tiếng Trung bị máy nghe nhầm do đồng âm (ví dụ: '苦恋' trong ngữ cảnh luyện kiếm phải hiểu là '苦练' = khổ luyện; '舞俠' = '武侠' = võ hiệp; sửa tên nhân vật bị lệch chữ Hán).\n"
                    + "3. BẢNG TÊN RIÊNG HÁN-VIỆT: Tên nhân vật, địa danh, chiêu thức thống nhất 100%.";

            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
            ObjectMapper mapper = new ObjectMapper();

            for (String model : getOrderedModelList()) {
                try {
                    ObjectNode root = mapper.createObjectNode();
                    ArrayNode contents = root.putArray("contents");
                    contents.addObject().put("role", "user").putArray("parts").addObject().put("text", prompt);
                    root.putObject("generationConfig").put("temperature", 0.1);

                    String url = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + apiKey;
                    HttpRequest req = HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .header("Content-Type", "application/json; charset=UTF-8")
                            .timeout(Duration.ofSeconds(30))
                            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(root), StandardCharsets.UTF_8))
                            .build();

                    HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                    if (resp.statusCode() == 200) {
                        activeModel = model;
                        JsonNode json = mapper.readTree(resp.body());
                        String bible = json.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText("").trim();
                        if (!bible.isEmpty()) {
                            System.out.println("   -> 🧠 [AI Story Bible] Đã lập hồ sơ ngữ cảnh & tự sửa lỗi đồng âm STT thành công!");
                            if (userGlossary != null && !userGlossary.isBlank()) {
                                return userGlossary.trim() + "\n\n" + bible;
                            }
                            return bible;
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        return userGlossary != null ? userGlossary : "";
    }

    /**
     * Tự động kiểm tra và dịch lại bất kỳ câu nào bị sót chữ Hán hoặc bị bỏ trống để đảm bảo 100% tiếng Việt sạch.
     */
    public static void autoRepairRemainingHanzi(List<String> audioSegments, Map<Integer, String> vietnameseMap, String storyBible) {
        Map<Integer, String> dirtyBatch = new LinkedHashMap<>();
        Map<Integer, Double> dirtyDurations = new HashMap<>();

        for (int i = 0; i < audioSegments.size(); i++) {
            String vi = vietnameseMap.getOrDefault(i, "").trim();
            if (vi.isEmpty() || vi.matches(".*[\\u4e00-\\u9fa5].*")) {
                String[] p = audioSegments.get(i).split("\\|");
                if (p.length >= 3) {
                    double dur = Math.max(0.5, Double.parseDouble(p[1]) - Double.parseDouble(p[0]));
                    dirtyBatch.put(i, p[2]);
                    dirtyDurations.put(i, dur);
                }
            }
        }

        if (dirtyBatch.isEmpty()) {
            return;
        }

        System.out.println("   -> 🔧 Phát hiện " + dirtyBatch.size() + " câu còn sót chữ Hán/trống, AI đang tự động hiệu đính lại...");
        try {
            Map<Integer, String> fixed = translateBatch(dirtyBatch, dirtyDurations, "", storyBible, MODEL_PRO);
            for (Map.Entry<Integer, String> entry : fixed.entrySet()) {
                String cleaned = entry.getValue().replaceAll("[\\u4e00-\\u9fa5]", "").replaceAll("\\s+", " ").trim();
                if (!cleaned.isEmpty()) {
                    vietnameseMap.put(entry.getKey(), cleaned);
                }
            }
        } catch (Exception ignored) {
        }

        // Đảm bảo tuyệt đối không còn ký tự chữ Hán nào lọt qua
        for (int i = 0; i < audioSegments.size(); i++) {
            String vi = vietnameseMap.getOrDefault(i, "").trim();
            if (vi.matches(".*[\\u4e00-\\u9fa5].*")) {
                vietnameseMap.put(i, vi.replaceAll("[\\u4e00-\\u9fa5]", "").replaceAll("\\s+", " ").trim());
            }
        }
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
            inputJson.put(String.valueOf(id), String.format(Locale.US, "[Thời lượng: %.1fs | ~%d âm tiết] %s", dur, targetWords, entry.getValue()));
        }

        StringBuilder systemInstruction = new StringBuilder();
        systemInstruction.append("Bạn là Đạo diễn Lồng tiếng & Biên dịch viên phim Trung Quốc sang tiếng Việt hàng đầu.\n")
                .append("QUY TẮC SỐNG CÒN ĐỂ BẢN DỊCH CHUẨN XÁC & KHỚP KHẨU HÌNH:\n")
                .append("1. Đầu ra CHỈ LÀ một JSON object phẳng hợp lệ (key là ID số nguyên dạng chuỗi, value là câu thoại tiếng Việt đã dịch — TUYỆT ĐỐI KHÔNG lặp lại nhãn [Thời lượng: ...] trong kết quả).\n")
                .append("2. TỰ ĐỘNG SỬA LỖI ĐỒNG ÂM STT: Văn bản đầu vào do máy nghe nên có thể bị nhầm từ đồng âm tiếng Trung (ví dụ: '苦恋' trong phim võ thuật phải dịch là 'khổ luyện' chứ không phải 'yêu khổ'; '零峰/临风' dịch là 'Lâm Phong'; '舞俠' là 'võ hiệp'). Hãy dựa vào mạch truyện để hiểu đúng chữ Hán gốc trước khi dịch!\n")
                .append("3. KHỚP ĐỘ DÀI CÂU NÓI: Mỗi câu có ghi chú [Thời lượng: Xs | ~N âm tiết]. Hãy viết câu tiếng Việt cô đọng, tự nhiên, có số tiếng (âm tiết) xấp xỉ ~N âm tiết để diễn viên đọc vừa khít thời gian.\n")
                .append("4. VĂN PHONG REVIEW PHIM CUỐN HÚT: Dịch thoát ý, mượt mà chuẩn văn phong thuyết minh phim Việt Nam, KHÔNG dịch word-by-word ngô nghê. TUYỆT ĐỐI KHÔNG để sót bất kỳ chữ Hán nào.\n")
                .append("5. Dịch ĐẦY ĐỦ tất cả các ID có trong đầu vào, không gộp câu, không bỏ sót ID nào.\n\n")
                .append("Ví dụ Input: {\"0\": \"[Thời lượng: 2.0s | ~7 âm tiết] 经过三年的苦恋\"}\n")
                .append("Ví dụ Output: {\"0\": \"Trải qua ba năm khổ luyện\"}\n");

        if (glossary != null && !glossary.isBlank()) {
            systemInstruction.append("\n=== HỒ SƠ CỐT TRUYỆN, XƯNG HÔ & TỪ ĐIỂN BẮT BUỘC TUÂN THỦ ===\n")
                    .append(glossary.trim()).append("\n");
        }

        if (previousContext != null && !previousContext.isBlank()) {
            systemInstruction.append("\nNgữ cảnh các câu ngay trước đó (tham khảo mạch truyện, KHÔNG dịch lại):\n")
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
        genConfig.put("temperature", 0.15);

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
