package com.example.stt;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class WhisperManager {

    // NÂNG CẤP: Thêm tham số bù giờ (offsetSeconds)
    public static List<String> runSTT(String scriptPath, String audioPath, double offsetSeconds) throws Exception {
        List<String> segments = new ArrayList<>();
        ProcessBuilder pb = new ProcessBuilder("python", scriptPath, audioPath);
        pb.redirectErrorStream(true);

        Process p = pb.start();
        BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), "UTF-8"));
        String line;

        while ((line = r.readLine()) != null) {
            if (line.contains("|") && ! line.toLowerCase().contains("traceback") && ! line.toLowerCase()
                    .contains("error")) {
                String[] parts = line.split("\\|");
                if (parts.length >= 3) {
                    try {
                        // Toán học bù giờ: Cộng thêm thời gian của các file trước đó
                        double start = Double.parseDouble(parts[ 0 ].trim()) + offsetSeconds;
                        double end = Double.parseDouble(parts[ 1 ].trim()) + offsetSeconds;
                        String text = parts[ 2 ].trim();

                        String newLine = String.format(Locale.US, "%.2f|%.2f|%s", start, end, text);
                        segments.add(newLine);
                        System.out.println("AI nghe được: " + newLine);
                    } catch (Exception e) {
                        System.out.println("⚠️ Lỗi định dạng thời gian: " + line);
                    }
                }
            } else {
                System.out.println("[Whisper Log] " + line);
            }
        }
        p.waitFor();
        return segments;
    }

}