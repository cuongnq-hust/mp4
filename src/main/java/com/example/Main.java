package com.example;

import com.example.ffmpeg.FFmpegManager;
import com.example.googleAI.GroqTranslator;
import com.example.localAI.OllamaTranslator;
import com.example.stt.WhisperManager;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Main {

    public static void main(String[] args) {
        // [TÍNH NĂNG MỚI] Bắt đầu bấm giờ
        long programStartTime = System.currentTimeMillis();

        System.out.println("=== BẮT ĐẦU KIẾN TRÚC REVIEW PHIM TỰ ĐỘNG ===");

        // ==========================================
        // 0. CHỌN CHẾ ĐỘ CHẠY (TỐI ƯU HÓA TOKEN)
        // ==========================================
        Object[] modeOptions = {"1. Chạy từ A-Z (Tải -> Bóc -> Dịch -> Lồng tiếng)", "2. Chỉ Lồng tiếng (Dùng bản dịch có sẵn)"};
        int modeChoice = JOptionPane.showOptionDialog(null, "Bạn muốn chạy hệ thống ở chế độ nào?", "Chế độ Khởi động",
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, modeOptions, modeOptions[0]);

        if (modeChoice == JOptionPane.CLOSED_OPTION) return;
        boolean isResumeMode = (modeChoice == 1);
        String aiEngine = "1";
        String ollamaIp = "100.72.146.92"; // 👉 Gắn cứng IP máy phụ của bạn vào đây cho nhàn

        if (!isResumeMode) {
            Object[] aiOptions = {"1. Groq API (Mỹ - Siêu tốc)", "2. Máy phụ Local (Qwen - Chậm mà chất)"};
            int aiChoice = JOptionPane.showOptionDialog(null, "Bạn muốn hệ thống Dịch thuật bằng Động cơ nào?", "Chọn Não bộ AI",
                    JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, aiOptions, aiOptions[0]);

            if (aiChoice == 1) { // 1 tương đương với Option 2 (Máy phụ)
                aiEngine = "2";
                ollamaIp = JOptionPane.showInputDialog(null, "Xác nhận IPv4 của Máy Phụ (Nhấn OK để đi tiếp):", ollamaIp);
                if (ollamaIp == null || ollamaIp.isEmpty()) return;
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
                    ProcessBuilder pb = new ProcessBuilder("yt-dlp", "-f",
                            "bestvideo[vcodec^=avc1]+bestaudio[ext=m4a]/mp4", "--merge-output-format", "mp4", "-o",
                            inputVideoPath, ytUrl);
                    pb.redirectErrorStream(true);
                    Process p = pb.start();

                    // [BẢN VÁ LỖI] Liên tục đọc log tiến trình của yt-dlp và in ra Console
                    java.io.BufferedReader reader = new java.io.BufferedReader(
                            new java.io.InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        // Rút gọn log để Console không bị trôi quá nhanh, chỉ in các dòng báo % tải
                        if (line.contains("[download]") || line.contains("[Merger]")) {
                            System.out.println("   -> " + line);
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
        boolean showViSub = (JOptionPane.showConfirmDialog(null, "XUẤT KÈM PHỤ ĐỀ Tiếng Việt?", "Phụ đề",
                JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION);

        // --- [GIAO DIỆN MỚI] HỎI MỨC ĐỘ CẮT XÉN ---
        int cropPercent = 0;
        if (showViSub) {
            String[] cropOptions = {"1. Không cắt (0%)", "2. Cắt mỏng (15%)", "3. Cắt vừa (20%)", "4. Cắt sâu (25%)", "5. Tự nhập %"};
            String selectedCrop = (String) JOptionPane.showInputDialog(null,
                    "Bạn muốn cắt bỏ bao nhiêu % phần đáy video để xóa chữ cũ?",
                    "Kỹ thuật Cắt xén (Crop)", JOptionPane.QUESTION_MESSAGE, null, cropOptions, cropOptions[2]);

            if (selectedCrop != null) {
                if (selectedCrop.startsWith("1")) cropPercent = 0;
                else if (selectedCrop.startsWith("2")) cropPercent = 15;
                else if (selectedCrop.startsWith("3")) cropPercent = 20;
                else if (selectedCrop.startsWith("4")) cropPercent = 25;
                else {
                    try {
                        String customCrop = JOptionPane.showInputDialog("Nhập số % muốn cắt (VD: 18, 22, 30):");
                        cropPercent = Integer.parseInt(customCrop.trim());
                    } catch (Exception e) {
                        cropPercent = 20;
                    }
                }
            } else {
                cropPercent = 20;
            }
        }

        List<String> audioSegments = new ArrayList<>();
        Map<Integer, String> vietnameseTranslatedMap = new HashMap<>();

        try {
            if (!isResumeMode) {
                FFmpegManager.extractAudio(inputVideoPath, extractedAudio);

                System.out.println("\n[1.5/5] Chia nhỏ âm thanh...");
                File audioPartsDir = new File(relativeWorkspacePath + "audio_parts");
                if (!audioPartsDir.exists()) audioPartsDir.mkdirs();
                FFmpegManager.splitAudio(extractedAudio, relativeWorkspacePath + "audio_parts/part_%03d.wav", 600);

                System.out.println("\n[2/5] Bóc tách Whisper...");
                File[] partFiles = audioPartsDir.listFiles((dir, name) -> name.startsWith("part_") && name.endsWith(".wav"));
                if (partFiles != null) {
                    java.util.Arrays.sort(partFiles);
                    double currentOffset = 0.0;
                    for (File part : partFiles) {
                        audioSegments.addAll(WhisperManager.runSTT("transcribe.py", part.getAbsolutePath(), currentOffset));
                        currentOffset += FFmpegManager.getAudioDuration(part.getAbsolutePath());
                    }
                }

                System.out.println("\n[3/5] Dịch thuật AI...");
                for (int i = 0; i < audioSegments.size(); i += 10) {
                    Map<Integer, String> currentChunk = new HashMap<>();
                    for (int j = i; j < Math.min(i + 10, audioSegments.size()); j++)
                        currentChunk.put(j, audioSegments.get(j).split("\\|")[2]);

                    boolean success = false;
                    while (!success) {
                        try {
                            // CÔNG TẮC ĐIỀU HƯỚNG DỮ LIỆU
                            if (aiEngine.equals("2")) {
                                vietnameseTranslatedMap.putAll(OllamaTranslator.translateBatch(currentChunk, glossary, ollamaIp));
                            } else {
                                vietnameseTranslatedMap.putAll(GroqTranslator.translateBatch(currentChunk, glossary));
                            }
                            success = true;
                        } catch (Exception e) {
                            System.out.println("   -> ⚠️ Lỗi đường truyền. Đang kết nối lại sau 5 giây... (" + e.getMessage() + ")");
                            Thread.sleep(5000);
                        }
                    }
                    Thread.sleep(2000); // Nhịp thở cho AI
                }

                try (FileWriter rw = new FileWriter(reviewFilePath, StandardCharsets.UTF_8)) {
                    for (int i = 0; i < audioSegments.size(); i++) {
                        String[] p = audioSegments.get(i).split("\\|");
                        rw.write(i + "|||" + p[0] + "|||" + p[1] + "|||" + p[2] + "|||" + vietnameseTranslatedMap.getOrDefault(i, "") + "\n");
                    }
                }
                JOptionPane.showMessageDialog(null, "Đã dịch xong! Sửa file review rồi bấm OK.");
            }

            // Nếu nạp từ file có sẵn, cấu trúc mảng âm thanh ban đầu
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
            // BƯỚC 3.6: TRẠM KIỂM SOÁT DỮ LIỆU CÓ VÒNG LẶP SỬA LỖI (BẢN GỐC)
            // ==============================================================
            boolean dataIsValid = false;
            while (!dataIsValid) {
                System.out.println("\n[3.6/5] Đang nạp và kiểm tra tính hợp lệ của bản dịch...");

                // Nạp lại toàn bộ file text mỗi vòng lặp
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
            // TÍNH TOÁN TỐC ĐỘ (SMART RATE)
            // ==============================================================
            double videoDuration = FFmpegManager.getAudioDuration(inputVideoPath);
            double simTime = 0;
            for (String t : vietnameseTranslatedMap.values()) {
                if (t != null && !t.trim().isEmpty()) {
                    simTime += (t.trim().split("\\s+").length / 2.6 * 1000) + 400;
                }
            }
            int recommendedRate = (int) Math.min(60,
                    Math.max(0, Math.ceil(((simTime / 1000.0 / videoDuration) - 1.0) * 100.0) + 8));

            String recommendMsg = String.format(
                    "Thời lượng gốc: %.1fs\nThời lượng ước tính: %.1fs\nĐề xuất tốc độ: +%d%%\nChọn tốc độ:",
                    videoDuration, simTime / 1000.0, recommendedRate);
            String[] voiceOptions = {"1. Khớp Hình (Nam, +" + recommendedRate + "%)", "2. Khớp Hình (Nữ, +" + recommendedRate + "%)", "3. Tự nhập (Nam)", "4. Tự nhập (Nữ)"};
            String selectedOption = (String) JOptionPane.showInputDialog(null, recommendMsg, "Gợi ý",
                    JOptionPane.WARNING_MESSAGE, null, voiceOptions, voiceOptions[0]);

            String rateStr = "+" + recommendedRate + "%";
            String voiceName;

            if (selectedOption != null) {
                if (selectedOption.startsWith("2") || selectedOption.startsWith("4")) voiceName = "vi-VN-HoaiMyNeural";
                else {
                    voiceName = "vi-VN-NamMinhNeural";
                }
                if (selectedOption.startsWith("3") || selectedOption.startsWith("4")) {
                    try {
                        int customRate = Integer.parseInt(JOptionPane.showInputDialog("Nhập % tốc độ (VD: 15 hoặc -5):").trim());
                        rateStr = (customRate >= 0 ? "+" : "") + customRate + "%";
                    } catch (Exception e) {
                        rateStr = "+0%";
                    }
                }
            } else {
                voiceName = "vi-VN-NamMinhNeural";
            }

// ==============================================================
            // 4. BƠM TURBO: SINH ÂM THANH ĐA LUỒNG TỐC ĐỘ CAO
            // ==============================================================
            System.out.println("\n[4/5] 🚀 Khởi động Turbo Lồng tiếng (Tải song song 3 luồng)...");

            // Nâng cấp: Dùng 3 thợ làm việc cùng lúc thay vì 1
            ExecutorService executor = Executors.newFixedThreadPool(3);
            Map<Integer, String> finalTextToSpeak = new HashMap<>(vietnameseTranslatedMap);

            for (int i = 0; i < audioSegments.size(); i++) {
                final int idx = i;
                final String text = finalTextToSpeak.get(i);
                final String chunkPath = relativeWorkspacePath + "audio_chunks/chunk_" + idx + ".mp3";
                final String finalRateStr = rateStr;

                executor.submit(() -> {
                    int maxRetries = 3;
                    int currentRetry = 0;
                    boolean success = false;

                    while (currentRetry < maxRetries && !success) {
                        try {
                            // Lọc bỏ các câu trống hoặc chỉ chứa số/ký tự đặc biệt
                            if (text == null || !text.matches(".*[\\p{L}\\p{N}].*")) {
                                System.out.println("   -> ⏩ Bỏ qua ID " + idx + " (Không chứa văn bản hợp lệ)");
                                break;
                            }

                            EdgeTTSManager.generateAudio(text, chunkPath, voiceName, finalRateStr, "+0Hz");
                            System.out.println("   -> 🎤 [Hoàn thành] Câu " + idx);
                            success = true;

                            // Nghỉ ngơi ngẫu nhiên (1s - 2s) để ngụy trang thao tác con người
                            Thread.sleep(1000 + (long) (Math.random() * 1000));

                        } catch (Exception e) {
                            currentRetry++;
                            System.out.println("   -> ⚠️ Lỗi kết nối ID " + idx + " (Thử lại lần " + currentRetry + "): " + e.getMessage());

                            if (currentRetry < maxRetries) {
                                try {
                                    // Cơ chế Exponential Backoff: Chờ 3s, 6s... để Server Microsoft bớt "giận"
                                    Thread.sleep(3000L * currentRetry);
                                } catch (InterruptedException ignored) {
                                }
                            } else {
                                System.out.println("   -> ❌ BỎ QUA ID " + idx + " do lỗi mạng kéo dài!");
                            }
                        }
                    }
                });
            }
            executor.shutdown();
            executor.awaitTermination(2, TimeUnit.HOURS);

            // ==============================================================
            // 4.2 LẮP RÁP & HÒA ÂM
            // ==============================================================
            System.out.println("\n[5/5] Hòa âm phối khí...");
            FileWriter srt = new FileWriter(srtFilePath, StandardCharsets.UTF_8);
            FileWriter con = new FileWriter(concatListPath, StandardCharsets.UTF_8);
            long timeMs = 0;
            int srtIdx = 1;

            for (int i = 0; i < audioSegments.size(); i++) {
                String[] p = audioSegments.get(i).split("\\|");
                File f = new File(relativeWorkspacePath + "audio_chunks/chunk_" + i + ".mp3");

                if (f.exists() && f.length() > 0) {
                    long start = (long) (Double.parseDouble(p[0]) * 1000);

                    if (start > timeMs) {
                        String silencePath = relativeWorkspacePath + "audio_chunks/s_" + i + ".mp3";
                        FFmpegManager.createSilenceAudio((start - timeMs) / 1000.0, silencePath);

                        // [VÁ LỖI TẬN GỐC] Dùng đường dẫn tuyệt đối (Absolute Path) và đổi \ thành /
                        String absoluteSilencePath = new File(silencePath).getAbsolutePath().replace("\\", "/");
                        con.write("file '" + absoluteSilencePath + "'\n");
                        timeMs = start;
                    }

                    String absoluteChunkPath = f.getAbsolutePath().replace("\\", "/");
                    con.write("file '" + absoluteChunkPath + "'\n");

                    long dur = (long) (FFmpegManager.getAudioDuration(f.getAbsolutePath()) * 1000);
                    srt.write(srtIdx++ + "\n" + formatSrtTime(timeMs / 1000.0) + " --> " + formatSrtTime((timeMs + dur) / 1000.0) + "\n" + vietnameseTranslatedMap.get(i) + "\n\n");
                    timeMs += dur;
                }
            }
            srt.close();
            con.close();

            FFmpegManager.concatAudioFiles(concatListPath, finalAudioPath);
            FFmpegManager.mergeFinalVideoWithSub(
                    new File(inputVideoPath).getAbsolutePath(),
                    new File(finalAudioPath).getAbsolutePath(),
                    new File(srtFilePath).getAbsolutePath(),
                    new File(outputMp4).getAbsolutePath(),
                    keepOriginalAudio, showViSub, cropPercent
            );
            // [TÍNH TOÁN VÀ IN RA TỔNG THỜI GIAN]
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
        return String.format("%02d:%02d:%02d,%03d", (int) (s / 3600), (int) ((s % 3600) / 60), (int) (s % 60), (int) ((s % 1) * 1000));
    }
}