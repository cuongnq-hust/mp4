package com.example.ffmpeg;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class FFmpegManager {

    public static void extractAudio(String videoInput, String audioOutput) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("ffmpeg", "-y", "-i", videoInput, "-f", "wav", "-ar", "16000", "-ac",
                "1", audioOutput);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
        while (r.readLine() != null) {
        }
        p.waitFor();
    }

    public static double getAudioDuration(String filePath) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("ffprobe", "-v", "error", "-show_entries", "format=duration", "-of",
                "default=noprint_wrappers=1:nokey=1", filePath);
        Process p = pb.start();
        BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
        String line = r.readLine();
        p.waitFor();
        return line != null ? Double.parseDouble(line.trim()) : 0.0;
    }

    public static void createSilenceAudio(double durationSeconds, String outputPath) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("ffmpeg", "-y", "-f", "lavfi", "-i", "anullsrc=r=24000:cl=mono", "-t",
                String.valueOf(durationSeconds), "-c:a", "libmp3lame", "-b:a", "48k", outputPath);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
        while (r.readLine() != null) {
        }
        p.waitFor();
    }

    public static void concatAudioFiles(String listTxtPath, String outputAudioPath) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("ffmpeg", "-y", "-f", "concat", "-safe", "0", "-i", listTxtPath, "-c:a",
                "pcm_s16le", outputAudioPath);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
        String line;
        while ((line = r.readLine()) != null) {
            // [BẢN VÁ] In log ra để nếu FFmpeg lỗi thì mình còn biết đường sửa
            System.out.println("[Concat Audio] " + line);
        }
        p.waitFor();
    }

    public static void mergeFinalVideoWithSub(String inputVideo, String inputAudio, String srtFile, String outputMp4,
                                              boolean keepOriginalAudio, boolean showViSub, int cropPercent) throws Exception {

        String audioFilter;
        if (keepOriginalAudio) {
            audioFilter = "[0:a]volume=0.9[bg_vol];" + "[1:a]volume=1.5,asplit=2[voice][sc];" + "[bg_vol][sc]sidechaincompress=threshold=0.03:ratio=10:attack=50:release=1000[bg_ducked];" + "[bg_ducked][voice]amix=inputs=2:duration=longest:dropout_transition=0[outa]";
        } else {
            audioFilter = "[1:a]volume=1.5[outa]";
        }

        List<String> command = new ArrayList<>();
        command.add("ffmpeg");
        command.add("-y");
        command.add("-hwaccel");
        command.add("cuda");
        command.add("-i");
        command.add(inputVideo);
        command.add("-i");
        command.add(inputAudio);
        command.add("-filter_complex");
        command.add(audioFilter);

        String videoFilter = "tpad=stop_mode=clone:stop=-1";

        if (showViSub) {
            File srt = new File(srtFile);
            String srtFileName = srt.getName();

            String cropFilter = "";
            if (cropPercent > 0) {
                // Công thức an toàn tuyệt đối: Giữ lại (100 - cropPercent)% chiều cao từ phía trên cùng
                double remainingHeightRatio = (100.0 - cropPercent) / 100.0;
                cropFilter = "crop=iw:ih*" + remainingHeightRatio + ":0:0,";
            }

            videoFilter += "," + cropFilter +
                    "subtitles=" + srtFileName + ":force_style='FontSize=22,PrimaryColour=&H00FFFFFF,Outline=1,Shadow=0,Bold=1,MarginV=10'";
        }

        command.add("-vf");
        command.add(videoFilter);

        command.add("-map");
        command.add("0:v");
        command.add("-map");
        command.add("[outa]");
        command.add("-c:v");
        command.add("h264_nvenc");
        command.add("-preset");
        command.add("p6");
        command.add("-c:a");
        command.add("aac");
        command.add("-b:a");
        command.add("192k");
        command.add("-shortest");
        command.add(outputMp4);

        ProcessBuilder pb = new ProcessBuilder(command);

        if (showViSub) {
            pb.directory(new File(srtFile).getParentFile());
        }

        pb.redirectErrorStream(true);
        Process p = pb.start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), "UTF-8"));
        String line;
        while ((line = reader.readLine()) != null) {
            System.out.println("[Render] " + line);
        }
        p.waitFor();
    }

    // THÊM HÀM MỚI VÀO FFMPEG MANAGER
    public static void splitAudio(String inputAudio, String outputPattern, int segmentTimeSeconds) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("ffmpeg", "-y", "-i", inputAudio, "-f", "segment", "-segment_time",
                String.valueOf(segmentTimeSeconds), "-c", "copy", outputPattern);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), "UTF-8"));
        while (r.readLine() != null) {
        } // Đọc sạch log để FFmpeg không bị treo
        p.waitFor();
    }

}