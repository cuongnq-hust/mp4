package com.example.tts;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class EdgeTTSManager {

    private static final String PYTHON_INLINE_TTS =
            "import sys, os, asyncio, edge_tts\n" +
            "from edge_tts.drm import DRM\n" +
            "txt = open(sys.argv[1], encoding='utf-8').read().strip()\n" +
            "out = sys.argv[2]\n" +
            "voice = sys.argv[3]\n" +
            "rate = sys.argv[4]\n" +
            "pitch = sys.argv[5]\n" +
            "async def main():\n" +
            "    for skew in [-42.0, 0.0, -300.0, 300.0, -60.0]:\n" +
            "        try:\n" +
            "            if os.path.exists(out): os.remove(out)\n" +
            "            DRM.clock_skew_seconds = skew\n" +
            "            c = edge_tts.Communicate(txt, voice, rate=rate, pitch=pitch)\n" +
            "            await c.save(out)\n" +
            "            if os.path.exists(out) and os.path.getsize(out) > 100:\n" +
            "                return\n" +
            "        except Exception as e:\n" +
            "            await asyncio.sleep(1.2)\n" +
            "    sys.exit(1)\n" +
            "asyncio.run(main())";

    /**
     * Sinh âm thanh bằng Microsoft Edge-TTS có tự động đồng bộ lệch múi giờ 5 phút (Sec-MS-GEC v2)
     * và truyền văn bản qua file UTF-8 để đảm bảo không bao giờ bị rớt câu (0 byte).
     */
    public static synchronized void generateAudio(String vietnameseText, String outputPath, String voiceName, String rate, String pitch) throws Exception {
        String cleanText = vietnameseText
                .replaceAll("[\\u4e00-\\u9fa5]", "")
                .replaceAll("[^\\p{L}\\p{N}\\s\\.,!\\?;:\\-]", " ")
                .replaceAll("\\s+", " ")
                .trim();

        if (!cleanText.matches(".*[\\p{L}\\p{N}].*")) {
            System.out.println("   -> ⏩ Bỏ qua vì câu không có chữ/số hợp lệ.");
            return;
        }

        File audioFile = new File(outputPath);
        File tempTextFile = new File(outputPath + ".txt");
        Files.write(Paths.get(tempTextFile.getAbsolutePath()), cleanText.getBytes(StandardCharsets.UTF_8));

        int maxAttempts = 4;
        String lastError = "";

        try {
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                if (audioFile.exists()) {
                    audioFile.delete();
                }

                List<String> command = new ArrayList<>();
                command.add("python");
                command.add("-X");
                command.add("utf8");
                command.add("-c");
                command.add(PYTHON_INLINE_TTS);
                command.add(tempTextFile.getAbsolutePath());
                command.add(audioFile.getAbsolutePath());
                command.add(voiceName);
                command.add(rate);
                command.add(pitch);

                ProcessBuilder pb = new ProcessBuilder(command);
                Map<String, String> env = pb.environment();
                env.put("PYTHONUTF8", "1");
                env.put("PYTHONIOENCODING", "UTF-8");

                pb.redirectErrorStream(true);
                Process process = pb.start();

                StringBuilder logBuilder = new StringBuilder();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        logBuilder.append(line).append(" ");
                    }
                }

                boolean finished = process.waitFor(45, TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    lastError = "Timeout quá 45s";
                } else if (audioFile.exists() && audioFile.length() > 100) {
                    return;
                } else {
                    lastError = logBuilder.toString().trim();
                }

                if (attempt < maxAttempts) {
                    long backoffMs = 2000L * attempt;
                    System.out.println("   -> ⚠️ Edge-TTS đang đồng bộ lại kết nối (Lần " + attempt + "/" + maxAttempts + ")...");
                    Thread.sleep(backoffMs);
                }
            }
        } finally {
            if (tempTextFile.exists()) {
                tempTextFile.delete();
            }
        }

        throw new Exception("Edge-TTS không thể tạo file sau " + maxAttempts + " lần thử: " + lastError);
    }
}
