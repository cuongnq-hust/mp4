package com.example.ffmpeg;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
     * Cắt khoảng lặng thừa ở đầu & cuối tất cả các câu thoại song song bằng đa luồng CPU (Nhanh gấp 5 lần).
     */
    public static void preTrimSilenceParallel(String chunksDirPath, int totalSegments) {
        int threads = Math.max(2, Math.min(6, Runtime.getRuntime().availableProcessors()));
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        String trimFilter = "silenceremove=start_periods=1:start_silence=0.02:start_threshold=-45dB,"
                + "areverse,silenceremove=start_periods=1:start_silence=0.04:start_threshold=-45dB,areverse";

        for (int i = 0; i < totalSegments; i++) {
            final int idx = i;
            pool.submit(() -> {
                try {
                    File raw = new File(chunksDirPath, "chunk_" + idx + ".mp3");
                    if (!raw.exists() || raw.length() <= 100) return;
                    File trimmed = new File(chunksDirPath, "chunk_" + idx + "_trimmed.wav");
                    ProcessBuilder pb = new ProcessBuilder(
                            "ffmpeg", "-y", "-i", raw.getAbsolutePath(),
                            "-filter:a", trimFilter,
                            "-ar", "24000", "-ac", "1", "-c:a", "pcm_s16le",
                            trimmed.getAbsolutePath()
                    );
                    pb.redirectErrorStream(true);
                    Process p = pb.start();
                    try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                        while (r.readLine() != null) {
                        }
                    }
                    p.waitFor(20, TimeUnit.SECONDS);
                } catch (Exception ignored) {
                }
            });
        }
        pool.shutdown();
        try {
            pool.awaitTermination(2, TimeUnit.MINUTES);
        } catch (InterruptedException ignored) {
        }
    }

    /**
     * Bước 1: Sử dụng file đã cắt khoảng lặng (hoặc cắt trực tiếp nếu chưa có).
     * Bước 2: Co giãn tốc độ 2 chiều thông minh (0.90x -> 1.50x) để khớp khẩu hình tự nhiên nhất.
     */
    public static double trimAndFitAudioToWav(String inputAudioPath, String outputWavPath, double origDuration, double maxSlotDuration) throws Exception {
        String trimmedWavPath = outputWavPath.replace(".wav", "_trimmed.wav");
        File trimmedFile = new File(trimmedWavPath);

        if (!trimmedFile.exists() || trimmedFile.length() <= 100) {
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
        }

        double currentDur = (trimmedFile.exists() && trimmedFile.length() > 100)
                ? getAudioDuration(trimmedWavPath)
                : getAudioDuration(inputAudioPath);
        String sourceForSpeed = (trimmedFile.exists() && trimmedFile.length() > 100 && currentDur > 0.15)
                ? trimmedWavPath
                : inputAudioPath;

        if (!sourceForSpeed.equals(trimmedWavPath)) {
            currentDur = getAudioDuration(inputAudioPath);
        }

        // Cho phép mượn tối đa 0.35s khoảng nghỉ sau câu nếu câu hơi dài để giữ giọng đọc tự nhiên không bị tua gấp
        double comfortableTarget = Math.min(maxSlotDuration, origDuration + 0.35);
        double tempo = 1.0;

        if (currentDur > maxSlotDuration && maxSlotDuration > 0.25) {
            tempo = currentDur / maxSlotDuration;
        } else if (currentDur > comfortableTarget * 1.04 && comfortableTarget > 0.25) {
            tempo = currentDur / comfortableTarget;
        } else if (currentDur < origDuration * 0.90 && origDuration > 0.4) {
            tempo = currentDur / origDuration;
        }

        double safeTempo = Math.min(1.50, Math.max(0.90, tempo));

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
            while (r.readLine() != null) {
            }
        }
        p.waitFor();
    }

    /**
     * Tự động trích xuất các khung hình mẫu và quét biên độ tương phản nét chữ + viền đen (Stroke Contrast Detection)
     * ở nửa dưới video để tính toán chính xác chiều cao thanh che phụ đề Trung Quốc gốc (kể cả phụ đề 2 dòng).
     */
    public static double detectSubtitleCoverRatio(String inputVideo, double videoDuration, File workDir) {
        double defaultCoverRatio = 0.26;
        if (videoDuration <= 0.5) {
            return defaultCoverRatio;
        }

        double[] sampleRatios = {0.12, 0.28, 0.44, 0.60, 0.74, 0.88};
        double minTopYRatio = 1.0 - defaultCoverRatio; // 0.74

        for (int i = 0; i < sampleRatios.length; i++) {
            double t = videoDuration * sampleRatios[i];
            File probePng = new File(workDir, "sub_probe_" + i + ".png");
            try {
                ProcessBuilder pb = new ProcessBuilder(
                        "ffmpeg", "-y",
                        "-ss", String.format(Locale.US, "%.2f", t),
                        "-i", inputVideo,
                        "-frames:v", "1",
                        "-vf", "scale=640:-1",
                        probePng.getAbsolutePath()
                );
                pb.redirectErrorStream(true);
                Process p = pb.start();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                    while (r.readLine() != null) {
                    }
                }
                p.waitFor();

                if (probePng.exists() && probePng.length() > 500) {
                    BufferedImage img = ImageIO.read(probePng);
                    if (img != null) {
                        double detectedTop = scanFrameForSubtitleTopRatio(img);
                        if (detectedTop < minTopYRatio) {
                            minTopYRatio = detectedTop;
                        }
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (probePng.exists()) {
                    try {
                        probePng.delete();
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        double safeTopRatio = Math.max(0.64, Math.min(0.75, minTopYRatio - 0.035));
        double coverHeightRatio = 1.0 - safeTopRatio;
        System.out.println(String.format(Locale.US,
                "   -> 🔍 [Auto-Detect Subtitle] Tự động tính toán vùng che phụ đề gốc: che %.1f%% chiều cao đáy (y = ih*%.3f, h = ih*%.3f)",
                coverHeightRatio * 100.0, safeTopRatio, coverHeightRatio));
        return coverHeightRatio;
    }

    private static double scanFrameForSubtitleTopRatio(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        int startY = (int) (h * 0.62);
        int endY = (int) (h * 0.96);
        int startX = (int) (w * 0.14);
        int endX = (int) (w * 0.86);

        boolean[] rowHasSubtitle = new boolean[h];
        for (int y = startY; y < endY; y++) {
            int outlinePairs = 0;
            int transitions = 0;
            boolean prevBright = false;

            for (int x = startX; x < endX; x++) {
                int lum = getLuminance(img.getRGB(x, y));
                boolean isBright = lum > 175;
                if (isBright != prevBright) {
                    transitions++;
                    prevBright = isBright;
                }

                if (lum < 36) {
                    boolean nearBrightText = false;
                    for (int dx = -3; dx <= 3; dx += 2) {
                        int nx = x + dx;
                        if (nx >= startX && nx < endX && getLuminance(img.getRGB(nx, y)) > 175) {
                            nearBrightText = true;
                            break;
                        }
                    }
                    if (!nearBrightText && y + 2 < endY && y - 2 >= startY) {
                        if (getLuminance(img.getRGB(x, y - 2)) > 175 || getLuminance(img.getRGB(x, y + 2)) > 175) {
                            nearBrightText = true;
                        }
                    }
                    if (nearBrightText) {
                        outlinePairs++;
                    }
                }
            }

            if (outlinePairs >= 14 && transitions >= 12) {
                rowHasSubtitle[y] = true;
            }
        }

        for (int y = startY; y < endY - 6; y++) {
            int activeCount = 0;
            for (int dy = 0; dy < 8 && (y + dy) < endY; dy++) {
                if (rowHasSubtitle[y + dy]) {
                    activeCount++;
                }
            }
            if (activeCount >= 5) {
                return (double) y / (double) h;
            }
        }

        return 0.75;
    }

    private static int getLuminance(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return (299 * r + 587 * g + 114 * b) / 1000;
    }

    public static void mergeFinalVideoWithSub(String inputVideo, String inputAudio, String srtFile, String outputMp4,
                                              boolean keepOriginalAudio, boolean showViSub, String studioEqMode) throws Exception {
        mergeFinalVideoStudio(inputVideo, inputAudio, srtFile, outputMp4,
                keepOriginalAudio ? "vocal_cut" : "mute_orig",
                "black_bar",
                showViSub,
                studioEqMode);
    }

    /**
     * Hòa âm phối khí Studio (EQ + Compressor + Lọc giọng Trung giữ Nhạc nền/SFX)
     * & Render Video (Hỗ trợ cả Dải băng đen tuyệt đối và Kính mờ Điện ảnh Smart Blur).
     */
    public static void mergeFinalVideoStudio(String inputVideo, String inputAudio, String srtFile, String outputMp4,
                                             String bgmMode, String coverStyle, boolean showViSub, String studioEqMode) throws Exception {

        double videoDuration = getAudioDuration(inputVideo);
        String durStr = videoDuration > 0 ? String.format(Locale.US, "%.3f", videoDuration) : "";

        // Bộ lọc âm thanh Phòng thu (Studio EQ + Compressor)
        String studioVoiceFilter;
        if ("deep".equalsIgnoreCase(studioEqMode)) {
            studioVoiceFilter = "highpass=f=65,equalizer=f=115:width_type=o:width=1.4:g=4.0,equalizer=f=3600:width_type=o:width=1.5:g=2.5,acompressor=threshold=-18dB:ratio=3:attack=15:release=150:makeup=2";
        } else {
            studioVoiceFilter = "highpass=f=85,equalizer=f=200:width_type=o:width=1.3:g=2.0,equalizer=f=4200:width_type=o:width=1.5:g=3.5,acompressor=threshold=-18dB:ratio=3:attack=10:release=120:makeup=2";
        }

        String padClause = videoDuration > 0 ? ",apad=whole_dur=" + durStr : ",apad";
        String audioFilter;

        if ("vocal_cut".equalsIgnoreCase(bgmMode)) {
            // Khử dải tần giọng nói tiếng Trung gốc (350Hz - 2800Hz), giữ lại nhạc nền trầm (<280Hz) & tiếng động/kiếm hiệp (>3200Hz)
            String vocalCutFilter = "bandreject=f=850:width_type=h:w=1100,bandreject=f=2100:width_type=h:w=1300,volume=0.38";
            audioFilter = "[0:a]" + vocalCutFilter + "[bg_clean];"
                    + "[1:a]" + studioVoiceFilter + ",volume=1.55" + padClause + ",asplit=2[voice][sc];"
                    + "[bg_clean][sc]sidechaincompress=threshold=0.025:ratio=12:attack=35:release=650[bg_ducked];"
                    + "[bg_ducked][voice]amix=inputs=2:duration=first:dropout_transition=0[outa]";
        } else if ("keep_soft".equalsIgnoreCase(bgmMode)) {
            audioFilter = "[0:a]volume=0.22[bg_vol];"
                    + "[1:a]" + studioVoiceFilter + ",volume=1.55" + padClause + ",asplit=2[voice][sc];"
                    + "[bg_vol][sc]sidechaincompress=threshold=0.025:ratio=10:attack=40:release=700[bg_ducked];"
                    + "[bg_ducked][voice]amix=inputs=2:duration=first:dropout_transition=0[outa]";
        } else {
            String singlePad = videoDuration > 0 ? ",apad=whole_dur=" + durStr : "";
            audioFilter = "[1:a]" + studioVoiceFilter + ",volume=1.45" + singlePad + "[outa]";
        }

        File srt = new File(srtFile);
        File workDir = srt.getParentFile() != null ? srt.getParentFile() : new File(".");

        // Tự động nhận diện chiều cao vùng chữ Trung gốc
        double coverRatio = detectSubtitleCoverRatio(inputVideo, videoDuration, workDir);
        double topYRatio = 1.0 - coverRatio;

        boolean hasValidSrt = showViSub && srt.exists() && srt.length() > 5;
        int marginV = Math.max(18, (int) Math.round(coverRatio * 95));

        String videoFilter;
        if ("none".equalsIgnoreCase(coverStyle)) {
            // Chế độ Không che nền (chỉ gắn phụ đề Tiếng Việt nếu có)
            if (hasValidSrt) {
                String srtFileName = srt.getName();
                videoFilter = "subtitles=" + srtFileName
                        + ":force_style='FontSize=20,PrimaryColour=&H00FFFFFF,OutlineColour=&H00000000,Outline=2,Shadow=1,Bold=1,Alignment=2,MarginV=24'";
            } else {
                videoFilter = "";
            }
        } else if ("smart_blur".equalsIgnoreCase(coverStyle)) {
            // Chế độ Kính mờ Điện ảnh (Smart Frosted Glass Blur): Làm mờ sâu vùng chữ gốc + phủ kính tối dịu giữ nguyên cảnh phim
            String blurChain = String.format(Locale.US,
                    "split[main][subcrop];[subcrop]crop=iw:ih*%.4f:0:ih*%.4f,boxblur=24:4,drawbox=x=0:y=0:w=iw:h=ih:color=black@0.62:t=fill[blurred];[main][blurred]overlay=0:H*%.4f",
                    coverRatio, topYRatio, topYRatio);
            if (hasValidSrt) {
                String srtFileName = srt.getName();
                videoFilter = blurChain + ",subtitles=" + srtFileName
                        + ":force_style='FontSize=20,PrimaryColour=&H00FFFFFF,OutlineColour=&H00000000,Outline=2,Shadow=1,Bold=1,Alignment=2,MarginV=" + marginV + "'";
            } else {
                videoFilter = blurChain;
            }
        } else {
            // Chế độ Dải băng đen tuyệt đối 100% (Solid Black Bar)
            String drawboxFilter = String.format(Locale.US,
                    "drawbox=x=0:y=ih*%.4f:w=iw:h=ih*%.4f:color=black@1.0:t=fill",
                    topYRatio, coverRatio);
            if (hasValidSrt) {
                String srtFileName = srt.getName();
                videoFilter = drawboxFilter + ",subtitles=" + srtFileName
                        + ":force_style='FontSize=20,PrimaryColour=&H00FFFFFF,OutlineColour=&H00000000,Outline=1,Shadow=0,Bold=1,Alignment=2,MarginV=" + marginV + "'";
            } else {
                videoFilter = drawboxFilter;
            }
        }

        String bestEncoder = detectBestVideoEncoder();
        System.out.println("   -> ⚡ Chế độ Render phần cứng: " + getHardwareEncoderDescription(bestEncoder));

        boolean renderedOk = runRenderProcess(inputVideo, inputAudio, srtFile, outputMp4,
                audioFilter, videoFilter, hasValidSrt, durStr, bestEncoder);

        if (!renderedOk && !"libx264".equals(bestEncoder)) {
            System.out.println("   -> ⚠️ Bộ mã hóa GPU (" + bestEncoder + ") bận hoặc không tương thích filter, tự động chuyển sang Đa nhân CPU (libx264)...");
            runRenderProcess(inputVideo, inputAudio, srtFile, outputMp4,
                    audioFilter, videoFilter, hasValidSrt, durStr, "libx264");
        }
    }

    private static volatile String cachedBestEncoder = null;

    /**
     * Tự động dò tìm bộ mã hóa phần cứng tốt nhất trên máy:
     * 1. NVIDIA GPU (h264_nvenc)
     * 2. Intel QuickSync iGPU / Arc (h264_qsv)
     * 3. AMD Radeon GPU / Ryzen iGPU (h264_amf)
     * 4. Đa nhân CPU (libx264)
     */
    public static synchronized String detectBestVideoEncoder() {
        if (cachedBestEncoder != null) {
            return cachedBestEncoder;
        }
        String[] candidates = {"h264_nvenc", "h264_qsv", "h264_amf"};
        for (String enc : candidates) {
            try {
                ProcessBuilder pb = new ProcessBuilder(
                        "ffmpeg", "-hide_banner", "-loglevel", "error",
                        "-f", "lavfi", "-i", "color=c=black:s=128x128:d=0.1",
                        "-frames:v", "1", "-c:v", enc, "-f", "null", "-"
                );
                pb.redirectErrorStream(true);
                Process p = pb.start();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                    while (r.readLine() != null) {
                    }
                }
                boolean finished = p.waitFor(4, TimeUnit.SECONDS);
                if (finished && p.exitValue() == 0) {
                    cachedBestEncoder = enc;
                    return cachedBestEncoder;
                }
            } catch (Exception ignored) {
            }
        }
        cachedBestEncoder = "libx264";
        return cachedBestEncoder;
    }

    public static String getHardwareEncoderDescription(String encoder) {
        if ("h264_nvenc".equals(encoder)) {
            return "NVIDIA CUDA GPU (h264_nvenc - Siêu tốc)";
        } else if ("h264_qsv".equals(encoder)) {
            return "Intel QuickSync GPU tích hợp (h264_qsv - Tăng tốc phần cứng)";
        } else if ("h264_amf".equals(encoder)) {
            return "AMD Radeon GPU (h264_amf - Tăng tốc phần cứng)";
        } else {
            int threads = Runtime.getRuntime().availableProcessors();
            return "Đa nhân CPU (" + threads + " luồng - libx264 veryfast)";
        }
    }

    private static boolean runRenderProcess(String inputVideo, String inputAudio, String srtFile, String outputMp4,
                                            String audioFilter, String videoFilter, boolean hasValidSrt,
                                            String durStr, String encoder) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("ffmpeg");
        command.add("-y");
        if ("h264_nvenc".equals(encoder)) {
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

        if ("h264_nvenc".equals(encoder)) {
            command.add("-c:v");
            command.add("h264_nvenc");
            command.add("-preset");
            command.add("p5");
            command.add("-cq");
            command.add("20");
        } else if ("h264_qsv".equals(encoder)) {
            command.add("-c:v");
            command.add("h264_qsv");
            command.add("-preset");
            command.add("fast");
            command.add("-global_quality");
            command.add("20");
        } else if ("h264_amf".equals(encoder)) {
            command.add("-c:v");
            command.add("h264_amf");
            command.add("-quality");
            command.add("balanced");
            command.add("-rc");
            command.add("cqp");
            command.add("-qp_i");
            command.add("20");
            command.add("-qp_p");
            command.add("20");
        } else {
            command.add("-c:v");
            command.add("libx264");
            command.add("-preset");
            command.add("veryfast");
            command.add("-crf");
            command.add("20");
            command.add("-threads");
            command.add("0");
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
