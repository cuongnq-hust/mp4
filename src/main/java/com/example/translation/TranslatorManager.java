package com.example.translation;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class TranslatorManager {

    public static String translateText(String pythonScriptPath, String chineseText) throws Exception {
        // Gọi Python và truyền câu tiếng Trung vào
        ProcessBuilder pb = new ProcessBuilder("python", pythonScriptPath, chineseText);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), "UTF-8"));
        StringBuilder vietnameseText = new StringBuilder();
        String line;

        while ((line = reader.readLine()) != null) {
            vietnameseText.append(line);
        }
        process.waitFor();

        return vietnameseText.toString().trim(); // Trả về kết quả tiếng Việt
    }

}