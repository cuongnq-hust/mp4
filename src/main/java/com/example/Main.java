package com.example;

import com.example.ffmpeg.FFmpegManager;
import com.example.googleAI.GeminiTranslator;
import com.example.tts.EdgeTTSManager;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class Main {

    public static void main(String[] args) {
        long programStartTime = System.currentTimeMillis();

        System.out.println("=== BẮT ĐẦU KIẾN TRÚC REVIEW PHIM TỰ ĐỘNG (GEMINI PRO + STUDIO VOICE) ===");

        // ==========================================
        // 0. CHỌN CHẾ ĐỘ CHẠY
        // ==========================================
        Object[] modeOptions = {"1. Chạy từ A-Z (Tải -> Bóc -> Dịch Gemini -> Lồng tiếng)", "2. Chỉ Lồng tiếng (Dùng bản dịch có sẵn)"};
        int modeChoice = JOptionPane.showOptionDialog(null, "Bạn muốn chạy hệ thống ở chế độ nào?", "Chế độ Khởi động",
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, modeOptions, modeOptions[0]);

        if (modeChoice == JOptionPane.CLOSED_OPTION) return;
        boolean isResumeMode = (modeChoice == 1);

        if (!isResumeMode) {
            try {
                GeminiTranslator.getOrPromptApiKey();
            } catch (Exception e) {
                JOptionPane.showMessageDialog(null, "Chưa có Gemini API Key. Dừng chương trình.", "Lỗi", JOptionPane.ERROR_MESSAGE);
                return;
            }
        }

        String relativeWorkspacePath = "";
        String inputVideoPath = "";
        String reviewFilePath = "";

        if (isResumeMode) {
            JOptionPane.showMessageDialog(null, "Bước 1: Hãy chọn VIDEO GỐC cần lồng tiếng.");
            JFileChooser videoChooser = new JFileChooser();
            if (videoChooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return;
            inputVideoPath = videoChooser.getSelectedFile().getAbsolutePath();

            JOptionPane.showMessageDialog(null, "Bước 2: Hãy chọn file 'review_translation.txt' đã dịch.");
            JFileChooser txtChooser = new JFileChooser();
            txtChooser.setFileFilter(new FileNameExtensionFilter("Text Files", "txt"));
            if (txtChooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return;
            File txtFile = txtChooser.getSelectedFile();
            reviewFilePath = txtFile.getAbsolutePath();

            relativeWorkspacePath = txtFile.getParent() + File.separator;
            System.out.println("⏩ CHẾ ĐỘ FAST-TRACK: Sử dụng Workspace: " + relativeWorkspacePath);

        } else {
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            relativeWorkspacePath = "Workspaces/Video_" + timestamp + "/";
            File workspaceDir = new File(relativeWorkspacePath);
            if (!workspaceDir.exists()) workspaceDir.mkdirs();

            Object[] inputOptions = {"Dán Link YouTube", "Chọn File từ Máy tính"};
            int inputChoice = JOptionPane.showOptionDialog(null, "Bước 1: Nguồn video?", "Nguồn Video",
                    JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, inputOptions, inputOptions[0]);

            if (inputChoice == JOptionPane.CLOSED_OPTION) return;

            if (inputChoice == 1) {
                JFileChooser videoChooser = new JFileChooser();
                if (videoChooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return;
                inputVideoPath = videoChooser.getSelectedFile().getAbsolutePath();
            } else {
                String ytUrl = JOptionPane.showInputDialog(null, "Dán link YouTube:", "Tải YouTube", JOptionPane.PLAIN_MESSAGE);
                if (ytUrl == null || ytUrl.isEmpty()) return;
                inputVideoPath = relativeWorkspacePath + "YT_Source.mp4";
                System.out.println("⏳ Bắt đầu kết nối và tải video từ YouTube... (Quá trình này tùy thuộc vào tốc độ mạng)");
                try {
                    ProcessBuilder pb = new ProcessBuilder("yt-dlp", "--no-check-certificate", "-f",
                            "bestvideo[vcodec^=avc1]+bestaudio[ext=m4a]/mp4", "--merge-output-format", "mp4", "-o",
                            inputVideoPath, ytUrl);
                    pb.redirectErrorStream(true);
                    Process p = pb.start();

                    try (java.io.BufferedReader reader = new java.io.BufferedReader(
                            new java.io.InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (line.contains("[download]") || line.contains("[Merger]")) {
                                System.out.println("   -> " + line);
                            }
                        }
                    }

                    p.waitFor();
                    System.out.println("✅ Tải video YouTube thành công!");
                } catch (Exception e) {
                    System.out.println("❌ LỖI TẢI YOUTUBE: " + e.getMessage());
                    e.printStackTrace();
                    return;
                }
            }
            reviewFilePath = relativeWorkspacePath + "review_translation.txt";
        }

        String extractedAudio = relativeWorkspacePath + "audio_temp.wav";
        String outputMp4 = relativeWorkspacePath + "output_final.mp4";
        String srtFilePath = relativeWorkspacePath + "vi_sub.srt";
        String concatListPath = relativeWorkspacePath + "concat_list.txt";
        String finalAudioPath = relativeWorkspacePath + "final_vietnamese_track.wav";

        File audioChunksDir = new File(relativeWorkspacePath + "audio_chunks");
        if (!audioChunksDir.exists()) audioChunksDir.mkdir();

        String glossary = "";
        if (!isResumeMode) {
            int dictChoice = JOptionPane.showConfirmDialog(null, "Bước 2: Bạn có muốn chọn file TỪ ĐIỂN (.txt)?",
                    "Tùy chọn Từ điển", JOptionPane.YES_NO_OPTION);
            if (dictChoice == JOptionPane.YES_OPTION) {
                JFileChooser dictChooser = new JFileChooser();
                if (dictChooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                    try {
                        glossary = new String(Files.readAllBytes(Paths.get(dictChooser.getSelectedFile().getAbsolutePath())), StandardCharsets.UTF_8);
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        boolean keepOriginalAudio = (JOptionPane.showConfirmDialog(null, "GIỮ lại âm thanh gốc?", "Âm thanh",
                JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION);
        boolean showViSub = (JOptionPane.showConfirmDialog(null, "XUẤT KÈM PHỤ ĐỀ Tiếng Việt?\n(Hệ thống sẽ tự động che kín 100% phụ đề tiếng Trung gốc)", "Phụ đề",
                JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION);

        List<String> audioSegments = new ArrayList<>();
        Map<Integer, String> vietnameseTranslatedMap = new HashMap<>();

        try {
            if (!isResumeMode) {
                FFmpegManager.extractAudio(inputVideoPath, extractedAudio);

                System.out.println("\n[1.5/5] Chia nhỏ âm thanh...");
                File audioPartsDir = new File(relativeWorkspacePath + "audio_parts");
                if (!audioPartsDir.exists()) audioPartsDir.mkdirs();
                FFmpegManager.splitAudio(extractedAudio, relativeWorkspacePath + "audio_parts/part_%03d.wav", 300);

                System.out.println("\n[2/5] Bóc tách âm thanh sang văn bản bằng Google Gemini Audio...");
                File[] partFiles = audioPartsDir.listFiles((dir, name) -> name.startsWith("part_") && name.endsWith(".wav"));
                if (partFiles != null) {
                    java.util.Arrays.sort(partFiles);
                    double currentOffset = 0.0;
                    for (File part : partFiles) {
                        audioSegments.addAll(GeminiTranslator.transcribeAudio(part.getAbsolutePath(), currentOffset));
                        currentOffset += FFmpegManager.getAudioDuration(part.getAbsolutePath());
                    }
                }

                System.out.println("\n[3/5] Dịch thuật khớp khẩu hình bằng Google Gemini...");
                int batchSize = 30;
                for (int i = 0; i < audioSegments.size(); i += batchSize) {
                    Map<Integer, String> currentChunk = new LinkedHashMap<>();
                    Map<Integer, Double> currentDurations = new HashMap<>();
                    int endIdx = Math.min(i + batchSize, audioSegments.size());
                    for (int j = i; j < endIdx; j++) {
                        String[] segParts = audioSegments.get(j).split("\\|");
                        double dur = Math.max(0.5, Double.parseDouble(segParts[1]) - Double.parseDouble(segParts[0]));
                        currentChunk.put(j, segParts[2]);
                        currentDurations.put(j, dur);
                    }

                    StringBuilder prevContext = new StringBuilder();
                    for (int k = Math.max(0, i - 5); k < i; k++) {
                        String orig = audioSegments.get(k).split("\\|")[2];
                        String trans = vietnameseTranslatedMap.getOrDefault(k, "");
                        if (!trans.isEmpty()) {
                            prevContext.append(orig).append(" -> ").append(trans).append("\n");
                        }
                    }

                    boolean success = false;
                    while (!success) {
                        try {
                            System.out.println("   -> 🌐 Gemini đang dịch cụm câu " + i + " -> " + (endIdx - 1) + " / " + audioSegments.size() + "...");
                            vietnameseTranslatedMap.putAll(GeminiTranslator.translateBatch(
                                    currentChunk, currentDurations, prevContext.toString(), glossary, GeminiTranslator.MODEL_PRO));
                            success = true;
                        } catch (Exception e) {
                            System.out.println("   -> ⚠️ Lỗi kết nối Gemini API. Đang thử lại sau 5 giây... (" + e.getMessage() + ")");
                            Thread.sleep(5000);
                        }
                    }
                    Thread.sleep(1200);
                }

                try (FileWriter rw = new FileWriter(reviewFilePath, StandardCharsets.UTF_8)) {
                    for (int i = 0; i < audioSegments.size(); i++) {
                        String[] p = audioSegments.get(i).split("\\|");
                        rw.write(i + "|||" + p[0] + "|||" + p[1] + "|||" + p[2] + "|||" + vietnameseTranslatedMap.getOrDefault(i, "") + "\n");
                    }
                }
                JOptionPane.showMessageDialog(null, "Đã dịch xong! Bạn có thể kiểm tra/sửa file review_translation.txt rồi bấm OK.");
            }

            if (isResumeMode) {
                List<String> lines = Files.readAllLines(Paths.get(reviewFilePath), StandardCharsets.UTF_8);
                for (String line : lines) {
                    String[] p = line.split("\\|\\|\\|", -1);
                    if (p.length >= 5) {
                        audioSegments.add(p[1].trim() + "|" + p[2].trim() + "|" + p[3].trim());
                    }
                }
            }

            // ==============================================================
            // BƯỚC 3.6: TRẠM KIỂM SOÁT DỮ LIỆU CÓ VÒNG LẶP SỬA LỖI
            // ==============================================================
            boolean dataIsValid = false;
            while (!dataIsValid) {
                System.out.println("\n[3.6/5] Đang nạp và kiểm tra tính hợp lệ của bản dịch...");

                vietnameseTranslatedMap.clear();
                List<String> lines = Files.readAllLines(Paths.get(reviewFilePath), StandardCharsets.UTF_8);
                for (String line : lines) {
                    String[] p = line.split("\\|\\|\\|", -1);
                    if (p.length >= 5) {
                        int id = Integer.parseInt(p[0].trim());
                        vietnameseTranslatedMap.put(id, p[4].trim());
                    }
                }

                boolean hasError = false;
                StringBuilder errorLog = new StringBuilder();
                for (int i = 0; i < audioSegments.size(); i++) {
                    String text = vietnameseTranslatedMap.getOrDefault(i, "").trim();
                    if (text.matches(".*[\\u4e00-\\u9fa5].*")) {
                        errorLog.append("❌ Câu ").append(i).append(": Còn sót chữ Hán -> \"").append(text).append("\"\n");
                        hasError = true;
                    }
                }

                if (hasError) {
                    JTextArea textArea = new JTextArea(errorLog.toString() +
                            "\n\n👉 VUI LÒNG MỞ FILE:\n" + reviewFilePath +
                            "\n\n1. Sửa hết các câu có chữ Trung Quốc ở trên.\n2. Bấm Ctrl+S để LƯU FILE.\n3. Quay lại đây bấm OK để tiếp tục.");
                    textArea.setEditable(false);
                    int userAction = JOptionPane.showConfirmDialog(null, new JScrollPane(textArea), "Phát hiện lỗi - Cần sửa để tiếp tục!", JOptionPane.OK_CANCEL_OPTION, JOptionPane.ERROR_MESSAGE);

                    if (userAction != JOptionPane.OK_OPTION) {
                        System.out.println("❌ Người dùng đã hủy quá trình sửa lỗi. Dừng chương trình.");
                        return;
                    }
                } else {
                    System.out.println("✅ Bản dịch sạch sẽ, 100% tiếng Việt!");
                    dataIsValid = true;
                }
            }

            // ==============================================================
            // CHỌN PRESET GIỌNG ĐỌC PHÒNG THU YOUTUBE / TIKTOK / GEMINI AI
            // ==============================================================
            double videoDuration = FFmpegManager.getAudioDuration(inputVideoPath);
            String[] studioPresets = {
                    "1. Nam Trầm Điện Ảnh (YouTube Review Kiếm Hiệp / Hành Động - Bass Ấm)",
                    "2. Nữ TikTok / CapCut Sáng (Review Ngôn Tình / Drama / Tổng Tài)",
                    "3. Nam TikTok Tốc Độ (Tóm Tắt Phim Nhanh, Rõ Nét)",
                    "4. Gemini AI Nam Truyền Cảm (Giọng Charon - Đa Cảm Xúc Thế Hệ Mới)",
                    "5. Gemini AI Nữ Truyền Cảm (Giọng Kore - Tự Nhiên Như Người Thật)"
            };
            String selectedPreset = (String) JOptionPane.showInputDialog(
                    null,
                    "Chọn phong cách Giọng Đọc Phòng Thu (YouTube / TikTok):\n(Tự động lọc âm Studio EQ + Compressor và khớp thời gian từng câu)",
                    "Bộ sưu tập Giọng Đọc YouTube & TikTok",
                    JOptionPane.QUESTION_MESSAGE,
                    null,
                    studioPresets,
                    studioPresets[0]
            );
            if (selectedPreset == null) selectedPreset = studioPresets[0];

            boolean useGeminiTTS = false;
            String geminiVoice = "Charon";
            String edgeVoice = "vi-VN-NamMinhNeural";
            String baseRate = "+5%";
            String basePitch = "-8Hz";
            String studioEqMode = "deep";

            if (selectedPreset.startsWith("1")) {
                edgeVoice = "vi-VN-NamMinhNeural";
                baseRate = "+5%";
                basePitch = "-8Hz";
                studioEqMode = "deep";
            } else if (selectedPreset.startsWith("2")) {
                edgeVoice = "vi-VN-HoaiMyNeural";
                baseRate = "+8%";
                basePitch = "+6Hz";
                studioEqMode = "bright";
            } else if (selectedPreset.startsWith("3")) {
                edgeVoice = "vi-VN-NamMinhNeural";
                baseRate = "+12%";
                basePitch = "+2Hz";
                studioEqMode = "bright";
            } else if (selectedPreset.startsWith("4")) {
                useGeminiTTS = true;
                geminiVoice = "Charon";
                edgeVoice = "vi-VN-NamMinhNeural";
                baseRate = "+5%";
                basePitch = "-8Hz";
                studioEqMode = "deep";
            } else if (selectedPreset.startsWith("5")) {
                useGeminiTTS = true;
                geminiVoice = "Kore";
                edgeVoice = "vi-VN-HoaiMyNeural";
                baseRate = "+8%";
                basePitch = "+6Hz";
                studioEqMode = "bright";
            }

            // ==============================================================
            // 4. SINH ÂM THANH CHUẨN PHÒNG THU (KHÔNG RỚT CÂU)
            // ==============================================================
            System.out.println("\n[4/5] 🚀 Đang tạo giọng đọc theo Preset: " + selectedPreset + "...");

            for (int i = 0; i < audioSegments.size(); i++) {
                String text = vietnameseTranslatedMap.get(i);
                String chunkRawPath = relativeWorkspacePath + "audio_chunks/chunk_" + i + ".mp3";

                if (text == null || !text.matches(".*[\\p{L}\\p{N}].*")) {
                    System.out.println("   -> ⏩ Bỏ qua ID " + i + " (Không chứa văn bản hợp lệ)");
                    continue;
                }
                try {
                    if (useGeminiTTS) {
                        try {
                            GeminiTranslator.generateGeminiTTS(text, chunkRawPath, geminiVoice);
                        } catch (Exception geminiEx) {
                            System.out.println("   -> ⚡ Gemini TTS chạm giới hạn tốc độ, tự động dùng Preset Studio tương đương cho câu " + i + "...");
                            EdgeTTSManager.generateAudio(text, chunkRawPath, edgeVoice, baseRate, basePitch);
                        }
                    } else {
                        try {
                            EdgeTTSManager.generateAudio(text, chunkRawPath, edgeVoice, baseRate, basePitch);
                        } catch (Exception edgeEx) {
                            System.out.println("   -> ⚡ Edge-TTS bận, tự động dùng Gemini TTS dự phòng cho câu " + i + "...");
                            GeminiTranslator.generateGeminiTTS(text, chunkRawPath, geminiVoice);
                        }
                    }
                    System.out.println("   -> 🎤 [Hoàn thành] Câu " + i + " / " + (audioSegments.size() - 1));
                } catch (Exception e) {
                    System.out.println("   -> ❌ Lỗi tạo giọng câu " + i + ": " + e.getMessage());
                }
            }

            // ==============================================================
            // 5. CẮT KHOẢNG LẶNG ĐẦU/CUỐI, KHỚP KHẨU HÌNH 2 CHIỀU & HÒA ÂM WAV
            // ==============================================================
            System.out.println("\n[5/5] Cắt khoảng lặng thừa, khớp thời lượng từng câu & Hòa âm Phòng thu...");
            try (FileWriter srt = new FileWriter(srtFilePath, StandardCharsets.UTF_8);
                 FileWriter con = new FileWriter(concatListPath, StandardCharsets.UTF_8)) {

                long timeMs = 0;
                int srtIdx = 1;

                for (int i = 0; i < audioSegments.size(); i++) {
                    String[] p = audioSegments.get(i).split("\\|");
                    File rawAudio = new File(relativeWorkspacePath + "audio_chunks/chunk_" + i + ".mp3");

                    if (rawAudio.exists() && rawAudio.length() > 100) {
                        double origStartSec = Double.parseDouble(p[0]);
                        double origEndSec = Double.parseDouble(p[1]);
                        long startMs = (long) (origStartSec * 1000);

                        if (startMs > timeMs + 15) {
                            String silenceWavPath = relativeWorkspacePath + "audio_chunks/s_" + i + ".wav";
                            FFmpegManager.createSilenceWav((startMs - timeMs) / 1000.0, silenceWavPath);

                            String absoluteSilencePath = new File(silenceWavPath).getAbsolutePath().replace("\\", "/");
                            con.write("file '" + absoluteSilencePath + "'\n");
                            timeMs = startMs;
                        }

                        double currentCursorSec = timeMs / 1000.0;
                        double nextOrigStartSec = (i + 1 < audioSegments.size())
                                ? Double.parseDouble(audioSegments.get(i + 1).split("\\|")[0])
                                : (videoDuration > 0 ? videoDuration : origEndSec + 2.0);

                        double maxSlotDuration = Math.max(0.35, nextOrigStartSec - currentCursorSec - 0.02);
                        double origDuration = Math.max(0.35, origEndSec - origStartSec);

                        String fittedWavPath = relativeWorkspacePath + "audio_chunks/chunk_" + i + ".wav";
                        double actualDurSec = FFmpegManager.trimAndFitAudioToWav(
                                rawAudio.getAbsolutePath(),
                                fittedWavPath,
                                origDuration,
                                maxSlotDuration
                        );

                        String absoluteChunkWavPath = new File(fittedWavPath).getAbsolutePath().replace("\\", "/");
                        con.write("file '" + absoluteChunkWavPath + "'\n");

                        long durMs = (long) (actualDurSec * 1000);
                        srt.write(srtIdx++ + "\n"
                                + formatSrtTime(timeMs / 1000.0) + " --> " + formatSrtTime((timeMs + durMs) / 1000.0) + "\n"
                                + vietnameseTranslatedMap.get(i) + "\n\n");
                        timeMs += durMs;
                    }
                }

                long totalVideoMs = (long) (videoDuration * 1000);
                if (totalVideoMs > timeMs + 50) {
                    String tailSilencePath = relativeWorkspacePath + "audio_chunks/s_tail.wav";
                    FFmpegManager.createSilenceWav((totalVideoMs - timeMs) / 1000.0, tailSilencePath);
                    String absoluteTailPath = new File(tailSilencePath).getAbsolutePath().replace("\\", "/");
                    con.write("file '" + absoluteTailPath + "'\n");
                }
            }

            FFmpegManager.concatAudioFiles(concatListPath, finalAudioPath);
            FFmpegManager.mergeFinalVideoWithSub(
                    new File(inputVideoPath).getAbsolutePath(),
                    new File(finalAudioPath).getAbsolutePath(),
                    new File(srtFilePath).getAbsolutePath(),
                    new File(outputMp4).getAbsolutePath(),
                    keepOriginalAudio, showViSub, studioEqMode
            );

            long programEndTime = System.currentTimeMillis();
            long totalSeconds = (programEndTime - programStartTime) / 1000;
            long minutes = totalSeconds / 60;
            long seconds = totalSeconds % 60;

            System.out.println("\n🎉 HOÀN THÀNH XUẤT SẮC! Sản phẩm được lưu tại:\n👉 " + new File(outputMp4).getAbsolutePath());
            System.out.println("⏱️ TỔNG THỜI GIAN XỬ LÝ: " + minutes + " phút " + seconds + " giây.");

        } catch (Exception e) {
            System.out.println("❌ LỖI HỆ THỐNG: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static String formatSrtTime(double s) {
        return String.format(Locale.US, "%02d:%02d:%02d,%03d",
                (int) (s / 3600), (int) ((s % 3600) / 60), (int) (s % 60), (int) ((s % 1) * 1000));
    }
}
