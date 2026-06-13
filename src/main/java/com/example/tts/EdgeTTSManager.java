package com.example.tts;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class EdgeTTSManager {

    // [VŨ KHÍ MỚI] Hệ thống Quản lý Mạng lưới Proxy
    private static List<String> proxyList = new ArrayList<>();
    private static String currentProxy = null;
    private static boolean directConnectionFailed = false;
    private static boolean forceGoogleFallback = false;

    // Hàm bí mật: Lẻn lên mạng hái Proxy miễn phí
    private static void loadFreeProxies() {
        try {
            System.out.println("   -> 🌐 Đang săn lùng Proxy miễn phí từ Internet...");
            URL url = new URL("https://api.proxyscrape.com/v2/?request=displayproxies&protocol=http&timeout=5000&country=all&ssl=all&anonymity=all");
            BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream()));
            String line;
            while ((line = in.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    proxyList.add(line.trim());
                }
            }
            in.close();
            System.out.println("   -> ✅ Đã thu thập được " + proxyList.size() + " mặt nạ Proxy.");
        } catch (Exception e) {
            System.out.println("   -> ❌ Lỗi tải proxy: Không có kết nối mạng.");
        }
    }

    // Hàm lấy mặt nạ mới, lấy xong vứt luôn để không xài lại đồ hỏng
    private static String getNextProxy() {
        if (proxyList.isEmpty()) {
            loadFreeProxies();
        }
        if (!proxyList.isEmpty()) {
            return proxyList.remove(0);
        }
        return null;
    }

    public static void generateAudio(String vietnameseText, String outputPath, String voiceName, String rate, String pitch) throws Exception {
        String cleanText = vietnameseText.replaceAll("[\\u4e00-\\u9fa5]", "")
                .replaceAll("[^\\p{L}\\p{N}\\s\\.,!\\?]", "")
                .replace("\n", " ").trim();

        if (!cleanText.matches(".*[\\p{L}\\p{N}].*")) {
            System.out.println("   -> ⏩ Bỏ qua vì câu không có chữ/số hợp lệ.");
            return;
        }

        if (forceGoogleFallback) {
            fallbackToGoogleTTS(cleanText, outputPath, rate);
            return;
        }

        int maxRetries = 5; // Cho phép thử tối đa 5 lần cho 1 câu (1 lần IP thật + 4 lần đổi Proxy)

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            List<String> command = new ArrayList<>();
            command.add("edge-tts");
            command.add("--text");
            command.add(cleanText);
            command.add("--voice");
            command.add(voiceName);
            command.add("--rate=" + rate);
            command.add("--pitch=" + pitch);
            command.add("--write-media");
            command.add(outputPath);

            // Tự động mang mặt nạ nếu IP thật đã tử trận
            if (directConnectionFailed && currentProxy == null) {
                currentProxy = getNextProxy();
            }

            if (currentProxy != null) {
                command.add("--proxy");
                command.add("http://" + currentProxy);
            }

            ProcessBuilder pb = new ProcessBuilder(command);
            Map<String, String> env = pb.environment();
            env.put("PYTHONUTF8", "1");
            env.put("PYTHONIOENCODING", "UTF-8");

            pb.redirectErrorStream(true);
            Process process = pb.start();

            BufferedReader r = new BufferedReader(new InputStreamReader(process.getInputStream(), "UTF-8"));
            while (r.readLine() != null) {
            }
            process.waitFor();

            File audioFile = new File(outputPath);
            if (audioFile.exists() && audioFile.length() > 100) {
                return; // Tạo file thành công!
            } else {
                if (!directConnectionFailed) {
                    System.out.println("   -> ⚠️ IP thật bị chặn! Kích hoạt Mạng lưới Proxy ẩn danh...");
                    directConnectionFailed = true;
                } else {
                    System.out.println("   -> ⚠️ Proxy [" + currentProxy + "] từ chối kết nối. Đang đổi mặt nạ mới...");
                    currentProxy = getNextProxy(); // Vứt proxy cũ, xoay sang proxy mới cho vòng lặp tiếp theo
                }
            }
        }

        // Nếu đen đủi đến mức thử 5 Proxy liên tiếp đều xịt, mới gọi Google ra cứu giá
        System.out.println("   -> 🛑 Mạng lưới Proxy quá tải. Kích hoạt Google TTS tạm thời.");
        forceGoogleFallback = true;
        fallbackToGoogleTTS(cleanText, outputPath, rate);
    }

    // [BẢN VÁ] Ép xung Google TTS bằng FFmpeg
    private static void fallbackToGoogleTTS(String text, String outputPath, String rateStr) {
        try {
            String tempPath = outputPath.replace(".mp3", "_temp.mp3");
            ProcessBuilder pb = new ProcessBuilder("gtts-cli", text, "-l", "vi", "-o", tempPath);
            pb.redirectErrorStream(true);
            Process p = pb.start();

            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), "UTF-8"));
            while (r.readLine() != null) {
            }
            p.waitFor();

            File tempFile = new File(tempPath);
            if (tempFile.exists() && tempFile.length() > 50) {
                double atempo = 1.25;
                try {
                    int ratePercent = Integer.parseInt(rateStr.replace("+", "").replace("%", "").trim());
                    atempo = atempo * (1.0 + (ratePercent / 100.0));
                } catch (Exception ignored) {
                }

                if (atempo > 2.0) atempo = 2.0;

                ProcessBuilder pbFFmpeg = new ProcessBuilder("ffmpeg", "-y", "-i", tempPath,
                        "-filter:a", "atempo=" + atempo, outputPath);
                pbFFmpeg.start().waitFor();

                tempFile.delete();
                System.out.println("   -> 🟢 Đã lồng Google TTS (Ép xung tốc độ " + String.format("%.2f", atempo) + "x)");
            } else {
                System.out.println("   -> ❌ LỖI NẶNG: Google TTS cũng thất bại.");
            }
        } catch (Exception e) {
            System.out.println("   -> ❌ Không thể xử lý Google TTS.");
        }
    }
}