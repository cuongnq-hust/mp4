package com.example.tts;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

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
import java.util.function.IntConsumer;

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
            "        except Exception:\n" +
            "            await asyncio.sleep(1.0)\n" +
            "    sys.exit(1)\n" +
            "asyncio.run(main())";

    private static final String PYTHON_BATCH_ASYNC_TTS =
            "import sys, os, json, asyncio, edge_tts\n" +
            "from edge_tts.drm import DRM\n" +
            "sys.stdout.reconfigure(encoding='utf-8')\n" +
            "jobs_file = sys.argv[1]\n" +
            "voice = sys.argv[2]\n" +
            "rate = sys.argv[3]\n" +
            "pitch = sys.argv[4]\n" +
            "jobs = json.load(open(jobs_file, encoding='utf-8'))\n" +
            "sem = asyncio.Semaphore(4)\n" +
            "DRM.clock_skew_seconds = -42.0\n" +
            "async def worker(job):\n" +
            "    jid = job['id']\n" +
            "    txt = job['text']\n" +
            "    out = job['out']\n" +
            "    async with sem:\n" +
            "        for skew in [-42.0, 0.0, -300.0, 300.0, -60.0]:\n" +
            "            try:\n" +
            "                if os.path.exists(out) and os.path.getsize(out) > 100:\n" +
            "                    print(f'DONE|{jid}', flush=True)\n" +
            "                    return\n" +
            "                DRM.clock_skew_seconds = skew\n" +
            "                c = edge_tts.Communicate(txt, voice, rate=rate, pitch=pitch)\n" +
            "                await c.save(out)\n" +
            "                if os.path.exists(out) and os.path.getsize(out) > 100:\n" +
            "                    print(f'DONE|{jid}', flush=True)\n" +
            "                    return\n" +
            "            except Exception:\n" +
            "                await asyncio.sleep(0.8)\n" +
            "        print(f'FAIL|{jid}', flush=True)\n" +
            "async def main():\n" +
            "    await asyncio.gather(*(worker(j) for j in jobs))\n" +
            "asyncio.run(main())";

    public static String sanitizeTextForTTS(String vietnameseText) {
        if (vietnameseText == null) return "";
        return vietnameseText
                .replaceAll("[\\u4e00-\\u9fa5]", "")
                .replaceAll("[^\\p{L}\\p{N}\\s\\.,!\\?;:\\-]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * Tạo hàng loạt câu thoại song song trong 1 tiến trình Python Asyncio duy nhất (Tăng tốc gấp 5-6 lần).
     */
    public static List<Integer> generateBatchAudio(Map<Integer, String> vietnameseMap,
                                                   int totalSegments,
                                                   String chunksDirPath,
                                                   String voiceName,
                                                   String rate,
                                                   String pitch,
                                                   IntConsumer onProgressCount) {
        List<Integer> failedIds = new ArrayList<>();
        try {
            ObjectMapper mapper = new ObjectMapper();
            ArrayNode jobsArray = mapper.createArrayNode();

            for (int i = 0; i < totalSegments; i++) {
                String cleanText = sanitizeTextForTTS(vietnameseMap.get(i));
                if (!cleanText.matches(".*[\\p{L}\\p{N}].*")) {
                    continue;
                }
                String outPath = new File(chunksDirPath, "chunk_" + i + ".mp3").getAbsolutePath();
                ObjectNode job = jobsArray.addObject();
                job.put("id", i);
                job.put("text", cleanText);
                job.put("out", outPath);
            }

            if (jobsArray.isEmpty()) {
                return failedIds;
            }

            File jobsFile = new File(chunksDirPath, "tts_batch_jobs.json");
            Files.write(Paths.get(jobsFile.getAbsolutePath()),
                    mapper.writerWithDefaultPrettyPrinter().writeValueAsString(jobsArray).getBytes(StandardCharsets.UTF_8));

            List<String> command = new ArrayList<>();
            command.add("python");
            command.add("-X");
            command.add("utf8");
            command.add("-c");
            command.add(PYTHON_BATCH_ASYNC_TTS);
            command.add(jobsFile.getAbsolutePath());
            command.add(voiceName);
            command.add(rate);
            command.add(pitch);

            ProcessBuilder pb = new ProcessBuilder(command);
            Map<String, String> env = pb.environment();
            env.put("PYTHONUTF8", "1");
            env.put("PYTHONIOENCODING", "UTF-8");
            pb.redirectErrorStream(true);

            Process process = pb.start();
            int completed = 0;

            try (BufferedReader r = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    line = line.trim();
                    if (line.startsWith("DONE|")) {
                        completed++;
                        int id = Integer.parseInt(line.substring(5).trim());
                        System.out.println("   -> 🎤 [Studio Async TTS] Hoàn tất câu " + (id + 1) + "/" + totalSegments);
                        if (onProgressCount != null) {
                            onProgressCount.accept(completed);
                        }
                    } else if (line.startsWith("FAIL|")) {
                        int id = Integer.parseInt(line.substring(5).trim());
                        failedIds.add(id);
                    }
                }
            }

            process.waitFor(4, TimeUnit.MINUTES);
            if (jobsFile.exists()) {
                jobsFile.delete();
            }

            // Kiểm tra lại tất cả các file đầu ra xem có file nào bị thiếu không
            for (int i = 0; i < totalSegments; i++) {
                String cleanText = sanitizeTextForTTS(vietnameseMap.get(i));
                if (!cleanText.matches(".*[\\p{L}\\p{N}].*")) continue;
                File outFile = new File(chunksDirPath, "chunk_" + i + ".mp3");
                if ((!outFile.exists() || outFile.length() <= 100) && !failedIds.contains(i)) {
                    failedIds.add(i);
                }
            }
        } catch (Exception e) {
            System.out.println("   -> ⚠️ Batch TTS gặp sự cố (" + e.getMessage() + "), chuyển sang chế độ từng câu...");
            for (int i = 0; i < totalSegments; i++) {
                if (!failedIds.contains(i)) failedIds.add(i);
            }
        }
        return failedIds;
    }

    public static synchronized void generateAudio(String vietnameseText, String outputPath, String voiceName, String rate, String pitch) throws Exception {
        String cleanText = sanitizeTextForTTS(vietnameseText);
        if (!cleanText.matches(".*[\\p{L}\\p{N}].*")) {
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
                    Thread.sleep(1500L * attempt);
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
