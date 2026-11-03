package com.example.ffmpeg;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class FFmpegManager {

    public static void extractAudio(String videoInput, String audioOutput) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("ffmpeg", "-y", "-i", videoInput, "-f", "wav", "-ar", "16000", "-ac",
                "1", audioOutput);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            while (r.readLine() != null) {
            }
        }
        p.waitFor();
    }

    public static double getAudioDuration(String filePath) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("ffprobe", "-v", "error", "-show_entries", "format=duration", "-of",
                "default=noprint_wrappers=1:nokey=1", filePath);
        Process p = pb.start();
        String line;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            line = r.readLine();
        }
        p.waitFor();
        return (line != null && !line.trim().isEmpty() && !line.contains("N/A")) ? Double.parseDouble(line.trim()) : 0.0;
    }

    /**
     * Tạo khoảng lặng chuẩn WAV (pcm_s16le, 24000Hz, mono) chính xác đến từng mili-giây.
     */
    public static void createSilenceWav(double durationSeconds, String outputWavPath) throws Exception {
        if (durationSeconds <= 0.005) {
            durationSeconds = 0.01;
        }
        ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg", "-y", "-f", "lavfi", "-i", "anullsrc=r=24000:cl=mono",
                "-t", String.format(Locale.US, "%.3f", durationSeconds),
                "-c:a", "pcm_s16le", outputWavPath
        );
        pb.redirectErrorStream(true);
        Process p = pb.start();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            while (r.readLine() != null) {
            }
        }
        p.waitFor();
    }

    /**
     * Bước 1: Cắt bỏ khoảng lặng thừa ở đầu và cuối mỗi file âm thanh do TTS sinh ra.
     * Bước 2: Co giãn tốc độ 2 chiều (0.88x -> 1.55x) để thời lượng câu tiếng Việt khớp khít với câu tiếng Trung gốc.
     */
    public static double trimAndFitAudioToWav(String inputAudioPath, String outputWavPath, double origDuration, double maxSlotDuration) throws Exception {
        String trimmedWavPath = outputWavPath.replace(".wav", "_trimmed.wav");

        String trimFilter = "silenceremove=start_periods=1:start_silence=0.02:start_threshold=-45dB,"
                + "areverse,silenceremove=start_periods=1:start_silence=0.04:start_threshold=-45dB,areverse";

        ProcessBuilder pbTrim = new ProcessBuilder(
                "ffmpeg", "-y", "-i", inputAudioPath,
                "-filter:a", trimFilter,
                "-ar", "24000", "-ac", "1", "-c:a", "pcm_s16le",
                trimmedWavPath
        );
        pbTrim.redirectErrorStream(true);
        Process pTrim = pbTrim.start();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(pTrim.getInputStream(), StandardCharsets.UTF_8))) {
            while (r.readLine() != null) {
            }
        }
        pTrim.waitFor();

        File trimmedFile = new File(trimmedWavPath);
        double currentDur = (trimmedFile.exists() && trimmedFile.length() > 100)
                ? getAudioDuration(trimmedWavPath)
                : getAudioDuration(inputAudioPath);
        String sourceForSpeed = (trimmedFile.exists() && trimmedFile.length() > 100 && currentDur > 0.15)
                ? trimmedWavPath
                : inputAudioPath;

        if (!sourceForSpeed.equals(trimmedWavPath)) {
            currentDur = getAudioDuration(inputAudioPath);
        }

        double targetDuration = Math.min(maxSlotDuration, origDuration);
        double tempo = 1.0;

        if (currentDur > maxSlotDuration && maxSlotDuration > 0.25) {
            tempo = currentDur / maxSlotDuration;
        } else if (currentDur > targetDuration * 1.05 && targetDuration > 0.25) {
            tempo = currentDur / targetDuration;
        } else if (currentDur < targetDuration * 0.92 && targetDuration > 0.4) {
            tempo = currentDur / targetDuration;
        }

        double safeTempo = Math.min(1.55, Math.max(0.88, tempo));

        if (Math.abs(safeTempo - 1.0) > 0.03) {
            ProcessBuilder pbSpeed = new ProcessBuilder(
                    "ffmpeg", "-y", "-i", sourceForSpeed,
                    "-filter:a", String.format(Locale.US, "atempo=%.3f", safeTempo),
                    "-ar", "24000", "-ac", "1", "-c:a", "pcm_s16le",
                    outputWavPath
            );
            pbSpeed.redirectErrorStream(true);
            Process pSpeed = pbSpeed.start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(pSpeed.getInputStream(), StandardCharsets.UTF_8))) {
                while (r.readLine() != null) {
                }
            }
            pSpeed.waitFor();
            if (trimmedFile.exists()) {
                trimmedFile.delete();
            }
        } else {
            if (sourceForSpeed.equals(trimmedWavPath)) {
                File outWav = new File(outputWavPath);
                if (outWav.exists()) outWav.delete();
                trimmedFile.renameTo(outWav);
            } else {
                ProcessBuilder pbCopy = new ProcessBuilder(
                        "ffmpeg", "-y", "-i", inputAudioPath,
                        "-ar", "24000", "-ac", "1", "-c:a", "pcm_s16le",
                        outputWavPath
                );
                pbCopy.redirectErrorStream(true);
                Process pCopy = pbCopy.start();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(pCopy.getInputStream(), StandardCharsets.UTF_8))) {
                    while (r.readLine() != null) {
                    }
                }
                pCopy.waitFor();
            }
        }

        return getAudioDuration(outputWavPath);
    }

    public static void concatAudioFiles(String listTxtPath, String outputAudioPath) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("ffmpeg", "-y", "-f", "concat", "-safe", "0", "-i", listTxtPath, "-c:a",
                "pcm_s16le", outputAudioPath);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                System.out.println("[Concat Audio] " + line);
            }
        }
        p.waitFor();
    }

    public static void mergeFinalVideoWithSub(String inputVideo, String inputAudio, String srtFile, String outputMp4,
                                              boolean keepOriginalAudio, boolean showViSub, String studioEqMode) throws Exception {

        double videoDuration = getAudioDuration(inputVideo);
        String durStr = videoDuration > 0 ? String.format(Locale.US, "%.3f", videoDuration) : "";

        // Bộ lọc âm thanh Phòng thu (Studio EQ + Compressor) tạo chất giọng ấm, dày, chuẩn YouTube/TikTok Review
        String studioVoiceFilter;
        if ("deep".equalsIgnoreCase(studioEqMode)) {
            // Tăng ấm trầm (Bass 115Hz) + Độ nét (3.6kHz) + Nén giọng phòng thu
            studioVoiceFilter = "highpass=f=65,equalizer=f=115:width_type=o:width=1.4:g=4.0,equalizer=f=3600:width_type=o:width=1.5:g=2.5,acompressor=threshold=-18dB:ratio=3:attack=15:release=150:makeup=2";
        } else {
            // Giọng sáng, trong trẻo, bắt tai chuẩn TikTok / CapCut
            studioVoiceFilter = "highpass=f=85,equalizer=f=200:width_type=o:width=1.3:g=2.0,equalizer=f=4200:width_type=o:width=1.5:g=3.5,acompressor=threshold=-18dB:ratio=3:attack=10:release=120:makeup=2";
        }

        String audioFilter;
        if (keepOriginalAudio) {
            String padClause = videoDuration > 0 ? ",apad=whole_dur=" + durStr : ",apad";
            audioFilter = "[0:a]volume=0.80[bg_vol];"
                    + "[1:a]" + studioVoiceFilter + ",volume=1.5" + padClause + ",asplit=2[voice][sc];"
                    + "[bg_vol][sc]sidechaincompress=threshold=0.03:ratio=10:attack=50:release=800[bg_ducked];"
                    + "[bg_ducked][voice]amix=inputs=2:duration=first:dropout_transition=0[outa]";
        } else {
            String padClause = videoDuration > 0 ? ",apad=whole_dur=" + durStr : "";
            audioFilter = "[1:a]" + studioVoiceFilter + ",volume=1.4" + padClause + "[outa]";
        }

        // Mặc định luôn che kín hoàn toàn phần chữ Trung ở đáy (20% chiều cao, màu đen đặc 100% black@1.0)
        String drawboxFilter = "drawbox=x=0:y=ih*0.80:w=iw:h=ih*0.20:color=black@1.0:t=fill";
        String videoFilter = drawboxFilter;

        File srt = new File(srtFile);
        boolean hasValidSrt = showViSub && srt.exists() && srt.length() > 5;
        if (hasValidSrt) {
            String srtFileName = srt.getName();
            videoFilter = drawboxFilter + ","
                    + "subtitles=" + srtFileName
                    + ":force_style='FontSize=20,PrimaryColour=&H00FFFFFF,OutlineColour=&H00000000,Outline=1,Shadow=0,Bold=1,Alignment=2,MarginV=16'";
        }

        boolean renderedWithGpu = runRenderProcess(inputVideo, inputAudio, srtFile, outputMp4,
                audioFilter, videoFilter, hasValidSrt, durStr, true);

        if (!renderedWithGpu) {
            System.out.println("   -> ⚠️ Render bằng GPU (NVENC) không khả dụng, tự động chuyển sang CPU (libx264)...");
            runRenderProcess(inputVideo, inputAudio, srtFile, outputMp4,
                    audioFilter, videoFilter, hasValidSrt, durStr, false);
        }
    }

    private static boolean runRenderProcess(String inputVideo, String inputAudio, String srtFile, String outputMp4,
                                            String audioFilter, String videoFilter, boolean hasValidSrt,
                                            String durStr, boolean useNvenc) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("ffmpeg");
        command.add("-y");
        if (useNvenc) {
            command.add("-hwaccel");
            command.add("cuda");
        }
        command.add("-i");
        command.add(inputVideo);
        command.add("-i");
        command.add(inputAudio);
        command.add("-filter_complex");
        command.add(audioFilter);

        if (!videoFilter.isEmpty()) {
            command.add("-vf");
            command.add(videoFilter);
        }

        command.add("-map");
        command.add("0:v");
        command.add("-map");
        command.add("[outa]");

        if (useNvenc) {
            command.add("-c:v");
            command.add("h264_nvenc");
            command.add("-preset");
            command.add("p6");
        } else {
            command.add("-c:v");
            command.add("libx264");
            command.add("-preset");
            command.add("fast");
            command.add("-crf");
            command.add("20");
        }

        command.add("-c:a");
        command.add("aac");
        command.add("-b:a");
        command.add("192k");

        if (!durStr.isEmpty()) {
            command.add("-t");
            command.add(durStr);
        } else {
            command.add("-shortest");
        }

        command.add(outputMp4);

        ProcessBuilder pb = new ProcessBuilder(command);
        if (hasValidSrt) {
            pb.directory(new File(srtFile).getParentFile());
        }

        pb.redirectErrorStream(true);
        Process p = pb.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("[Render] " + line);
            }
        }
        int exitCode = p.waitFor();
        File out = new File(outputMp4);
        return exitCode == 0 && out.exists() && out.length() > 1000;
    }

    public static void splitAudio(String inputAudio, String outputPattern, int segmentTimeSeconds) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("ffmpeg", "-y", "-i", inputAudio, "-f", "segment", "-segment_time",
                String.valueOf(segmentTimeSeconds), "-c", "copy", outputPattern);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            while (r.readLine() != null) {
            }
        }
        p.waitFor();
    }

}
