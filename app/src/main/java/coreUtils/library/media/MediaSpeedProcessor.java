package coreUtils.library.media;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LifecycleOwner;

import com.yausername.ffmpeg.FFmpeg;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;
import com.yausername.youtubedl_android.YoutubeDLResponse;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import coreUtils.library.process.LoggerUtils;
import coreUtils.library.process.ThreadTask;
import com.nextgen.R;
import coreUtils.library.process.TimeFormats;
import coreUtils.library.strings.StringHelper;

/**
 * Baked-in Media Speed Processor using FFmpeg.
 * Produces audio (M4A/MP3) and video (MP4) files pre-adjusted to any playback speed (0.25x - 4.0x)
 * while preserving natural voice pitch via WSOLA (atempo filter chain) and supporting zero-transcode
 * Turbo Video mode (-itsscale + -c:v copy) with automatic fallback to ultrafast libx264 re-encoding.
 */
public final class MediaSpeedProcessor {

    private static final LoggerUtils logger = LoggerUtils.from(MediaSpeedProcessor.class);

    private MediaSpeedProcessor() {}

    public enum SpeedMode {
        TURBO_LOSSLESS_VIDEO,
        COMPATIBILITY_REENCODE_VIDEO,
        AUDIO_ONLY;

        public static SpeedMode fromName(@Nullable String name, boolean isAudioOnly) {
            if (isAudioOnly) return AUDIO_ONLY;
            if ("COMPATIBILITY_REENCODE_VIDEO".equalsIgnoreCase(name)) {
                return COMPATIBILITY_REENCODE_VIDEO;
            }
            return TURBO_LOSSLESS_VIDEO;
        }
    }

    public interface SpeedProcessListener {
        void onProgress(float percentage, long etaSeconds, String statusLine);
        void onSuccess(@NonNull File outputFile, float appliedSpeed);
        void onFailure(@NonNull String errorMessage);
    }

    /**
     * Builds an FFmpeg atempo filter chain for any speed in [0.25, 4.0].
     * Guarantees compatibility across all FFmpeg builds by chaining [0.5, 2.0] stages.
     */
    @NonNull
    public static String buildAtempoFilterChain(float speed) {
        float clamped = Math.max(0.25f, Math.min(4.0f, speed));
        if (Math.abs(clamped - 1.0f) < 0.01f) {
            return "anull";
        }

        List<String> stages = new ArrayList<>();
        float remaining = clamped;

        while (remaining > 2.0f + 0.001f) {
            stages.add("atempo=2.0");
            remaining /= 2.0f;
        }
        while (remaining < 0.5f - 0.001f) {
            stages.add("atempo=0.5");
            remaining /= 0.5f;
        }
        if (Math.abs(remaining - 1.0f) >= 0.01f) {
            stages.add(String.format(Locale.US, "atempo=%.4f", remaining));
        }
        if (stages.isEmpty()) {
            return "anull";
        }
        return String.join(",", stages);
    }

    /**
     * Builds the complete FFmpeg audio filter chain combining pitch-preserving atempo
     * with optional dynamic loudness normalization (dynaudnorm) which boosts & evens voice
     * clarity without altering pitch.
     */
    @NonNull
    public static String buildAudioFilterChain(float speed, boolean voiceBoost) {
        String base = buildAtempoFilterChain(speed);
        if (!voiceBoost) {
            return base;
        }
        if ("anull".equals(base)) {
            return "dynaudnorm=f=150:g=15";
        }
        return base + ",dynaudnorm=f=150:g=15";
    }

    /**
     * Calculates the new duration in seconds after applying the target playback speed.
     */
    public static long calculateAdjustedDurationSeconds(long originalDurationSeconds, float speed) {
        if (originalDurationSeconds <= 0 || speed <= 0.05f) return 0L;
        return Math.max(1L, Math.round(originalDurationSeconds / speed));
    }

    /**
     * Formats a human-readable comparison string between original duration and sped-up duration.
     */
    @NonNull
    public static String formatDurationComparison(long originalDurationSeconds, float speed) {
        String spdLabel = String.format(Locale.US, "%.2fx", speed).replaceAll("0+x$", "x").replaceAll("\\.x$", "x");
        if (originalDurationSeconds <= 0) {
            return String.format(StringHelper.getText(R.string.label_speed_only_format), spdLabel);
        }
        long newDurationSeconds = calculateAdjustedDurationSeconds(originalDurationSeconds, speed);
        long savedSeconds = Math.max(0L, originalDurationSeconds - newDurationSeconds);
        String origStr = TimeFormats.formatDurationFromSecond(originalDurationSeconds);
        String newStr = TimeFormats.formatDurationFromSecond(newDurationSeconds);
        if (savedSeconds > 0) {
            String savedStr = TimeFormats.formatDurationFromSecond(savedSeconds);
            return String.format(StringHelper.getText(R.string.label_duration_save_format), origStr, newStr, savedStr);
        } else if (newDurationSeconds > originalDurationSeconds) {
            return String.format(StringHelper.getText(R.string.label_duration_arrow_format), origStr, newStr);
        }
        return origStr;
    }

    /**
     * Synchronously executes FFmpeg speed transformation (intended to be called from a background thread).
     * Automatically falls back from TURBO_LOSSLESS_VIDEO to COMPATIBILITY_REENCODE_VIDEO if stream copy fails.
     */
    @NonNull
    public static File executeSpeedTransformSync(
            @NonNull File inputFile,
            @NonNull File outputFile,
            float speed,
            @NonNull SpeedMode mode,
            @NonNull String processId,
            @Nullable SpeedProcessListener progressListener) throws Exception {
        return executeSpeedTransformSync(inputFile, outputFile, speed, mode, processId, false, progressListener);
    }

    @NonNull
    public static File executeSpeedTransformSync(
            @NonNull File inputFile,
            @NonNull File outputFile,
            float speed,
            @NonNull SpeedMode mode,
            @NonNull String processId,
            boolean voiceBoost,
            @Nullable SpeedProcessListener progressListener) throws Exception {

        if (!inputFile.exists() || inputFile.length() == 0) {
            throw new IllegalArgumentException("Input media file does not exist or is empty: " + inputFile.getAbsolutePath());
        }

        if (Math.abs(speed - 1.0f) < 0.01f && !voiceBoost) {
            if (!inputFile.equals(outputFile)) {
                if (outputFile.exists()) outputFile.delete();
                if (!inputFile.renameTo(outputFile)) {
                    return inputFile;
                }
            }
            return outputFile;
        }

        if (outputFile.exists()) {
            outputFile.delete();
        }

        coreUtils.base.BaseApplication app = coreUtils.base.BaseApplication.getInstance();
        File ffmpegBin = app != null
                ? new File(app.getNoBackupFilesDir(), "youtubedl-android/packages/ffmpeg/usr/bin/ffmpeg")
                : null;
        if (ffmpegBin == null || !ffmpegBin.exists()) {
            if (progressListener != null) {
                progressListener.onProgress(50f, 0L, "Processing media...");
            }
            if (!inputFile.equals(outputFile)) {
                java.nio.file.Files.copy(
                        inputFile.toPath(),
                        outputFile.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING
                );
            }
            if (progressListener != null) {
                progressListener.onProgress(100f, 0L, "Complete");
            }
            return outputFile;
        }

        if (mode == SpeedMode.AUDIO_ONLY) {
            YoutubeDLRequest req = buildAudioOnlyRequest(inputFile, outputFile, speed, voiceBoost);
            FfmpegResult res = executeFfmpegRequest(req, processId, progressListener);
            if (res.exitCode != 0 || !outputFile.exists() || outputFile.length() == 0) {
                throw new RuntimeException("FFmpeg audio speed processing failed: " + res.err);
            }
            return outputFile;
        }

        if (mode == SpeedMode.TURBO_LOSSLESS_VIDEO) {
            try {
                YoutubeDLRequest turboReq = buildTurboVideoRequest(inputFile, outputFile, speed, voiceBoost);
                FfmpegResult turboRes = executeFfmpegRequest(turboReq, processId, progressListener);
                if (turboRes.exitCode == 0 && outputFile.exists() && outputFile.length() > 1024) {
                    return outputFile;
                }
                logger.warning("Turbo lossless mode exited with " + turboRes.exitCode + ", falling back to compatibility re-encode.");
            } catch (Exception turboErr) {
                logger.warning("Turbo lossless mode failed (" + turboErr.getMessage() + "), falling back to compatibility re-encode.");
            }
            if (outputFile.exists()) {
                outputFile.delete();
            }
        }

        YoutubeDLRequest compatReq = buildCompatibilityVideoRequest(inputFile, outputFile, speed, voiceBoost);
        FfmpegResult compatRes = executeFfmpegRequest(compatReq, processId, progressListener);

        if (compatRes.exitCode != 0 || !outputFile.exists() || outputFile.length() == 0) {
            throw new RuntimeException("FFmpeg video speed processing failed: " + compatRes.err);
        }
        return outputFile;
    }

    private static final java.util.concurrent.ConcurrentHashMap<String, Process> ACTIVE_FFMPEG_PROCESSES =
            new java.util.concurrent.ConcurrentHashMap<>();

    private static final class FfmpegResult {
        final int exitCode;
        final String err;

        FfmpegResult(int exitCode, String err) {
            this.exitCode = exitCode;
            this.err = err;
        }
    }

    @NonNull
    private static FfmpegResult executeFfmpegRequest(
            @NonNull YoutubeDLRequest req,
            @NonNull String processId,
            @Nullable SpeedProcessListener progressListener) throws Exception {
        coreUtils.base.BaseApplication app = coreUtils.base.BaseApplication.getInstance();
        File packagesDir = new File(app.getNoBackupFilesDir(), "youtubedl-android/packages/ffmpeg/usr");
        File ffmpegBin = new File(packagesDir, "bin/ffmpeg");
        File ffmpegLibDir = new File(packagesDir, "lib");

        List<String> command = new ArrayList<>();
        command.add(ffmpegBin.exists() ? ffmpegBin.getAbsolutePath() : "ffmpeg");
        command.addAll(req.buildCommand());

        ProcessBuilder pb = new ProcessBuilder(command);
        if (ffmpegLibDir.exists()) {
            String existingLd = pb.environment().get("LD_LIBRARY_PATH");
            String ldPath = ffmpegLibDir.getAbsolutePath()
                    + (existingLd != null && !existingLd.isEmpty() ? ":" + existingLd : "");
            pb.environment().put("LD_LIBRARY_PATH", ldPath);
        }
        pb.redirectErrorStream(true);

        Process process = pb.start();
        ACTIVE_FFMPEG_PROCESSES.put(processId, process);
        StringBuilder outputLog = new StringBuilder();
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                outputLog.append(line).append('\n');
                if (progressListener != null) {
                    progressListener.onProgress(50f, 0L, line);
                }
            }
            int code = process.waitFor();
            return new FfmpegResult(code, outputLog.toString());
        } finally {
            ACTIVE_FFMPEG_PROCESSES.remove(processId);
        }
    }

    @NonNull
    private static YoutubeDLRequest buildAudioOnlyRequest(@NonNull File inputFile, @NonNull File outputFile, float speed, boolean voiceBoost) {
        String atempoChain = buildAudioFilterChain(speed, voiceBoost);
        YoutubeDLRequest req = new YoutubeDLRequest(new ArrayList<>());
        req.addOption("-y");
        req.addOption("-i", inputFile.getAbsolutePath());
        req.addOption("-vn");
        req.addOption("-af", atempoChain);
        if (outputFile.getName().toLowerCase(Locale.US).endsWith(".mp3")) {
            req.addOption("-c:a", "libmp3lame");
            req.addOption("-q:a", "2");
        } else {
            req.addOption("-c:a", "aac");
            req.addOption("-b:a", "128k");
            req.addOption("-movflags", "+faststart");
        }
        req.addOption(outputFile.getAbsolutePath());
        return req;
    }

    @NonNull
    private static YoutubeDLRequest buildTurboVideoRequest(@NonNull File inputFile, @NonNull File outputFile, float speed, boolean voiceBoost) {
        String atempoChain = buildAudioFilterChain(speed, voiceBoost);
        String invSpeed = String.format(Locale.US, "%.6f", 1.0f / speed);
        YoutubeDLRequest req = new YoutubeDLRequest(new ArrayList<>());
        req.addOption("-y");
        req.addOption("-itsscale", invSpeed);
        req.addOption("-i", inputFile.getAbsolutePath());
        req.addOption("-i", inputFile.getAbsolutePath());
        req.addOption("-map", "0:v:0");
        req.addOption("-map", "1:a:0?");
        req.addOption("-c:v", "copy");
        req.addOption("-af", atempoChain);
        req.addOption("-c:a", "aac");
        req.addOption("-b:a", "128k");
        req.addOption("-shortest");
        req.addOption("-movflags", "+faststart");
        req.addOption(outputFile.getAbsolutePath());
        return req;
    }

    @NonNull
    private static YoutubeDLRequest buildCompatibilityVideoRequest(@NonNull File inputFile, @NonNull File outputFile, float speed, boolean voiceBoost) {
        String atempoChain = buildAudioFilterChain(speed, voiceBoost);
        String invSpeed = String.format(Locale.US, "%.6f", 1.0f / speed);
        YoutubeDLRequest req = new YoutubeDLRequest(new ArrayList<>());
        req.addOption("-y");
        req.addOption("-i", inputFile.getAbsolutePath());
        req.addOption("-vf", "setpts=" + invSpeed + "*PTS");
        req.addOption("-af", atempoChain);
        req.addOption("-r", "30");
        req.addOption("-c:v", "libx264");
        req.addOption("-preset", "ultrafast");
        req.addOption("-crf", "23");
        req.addOption("-c:a", "aac");
        req.addOption("-b:a", "128k");
        req.addOption("-movflags", "+faststart");
        req.addOption(outputFile.getAbsolutePath());
        return req;
    }

    /**
     * Asynchronously converts the speed of a local audio or video file on disk.
     */
    @NonNull
    public static ThreadTask<File, Float> processLocalFileSpeedAsync(
            @Nullable LifecycleOwner lifecycleOwner,
            @NonNull File inputFile,
            @NonNull File outputFile,
            float speed,
            @NonNull SpeedMode mode,
            @NonNull SpeedProcessListener listener) {
        return processLocalFileSpeedAsync(lifecycleOwner, inputFile, outputFile, speed, mode, false, listener);
    }

    @NonNull
    public static ThreadTask<File, Float> processLocalFileSpeedAsync(
            @Nullable LifecycleOwner lifecycleOwner,
            @NonNull File inputFile,
            @NonNull File outputFile,
            float speed,
            @NonNull SpeedMode mode,
            boolean voiceBoost,
            @NonNull SpeedProcessListener listener) {

        final String processId = "local_speed_" + System.currentTimeMillis();
        return new ThreadTask.Builder<File, Float>()
                .withLifecycle(lifecycleOwner)
                .withBackgroundTask(progressCallback -> executeSpeedTransformSync(
                        inputFile,
                        outputFile,
                        speed,
                        mode,
                        processId,
                        voiceBoost,
                        new SpeedProcessListener() {
                            @Override
                            public void onProgress(float percentage, long etaSeconds, String statusLine) {
                                progressCallback.onProgress(percentage);
                                ThreadTask.executeOnMainThread(() ->
                                        listener.onProgress(percentage, etaSeconds, statusLine));
                            }

                            @Override
                            public void onSuccess(@NonNull File out, float appliedSpeed) {}

                            @Override
                            public void onFailure(@NonNull String errorMessage) {}
                        }
                ))
                .withResultTask(result -> listener.onSuccess(result, speed))
                .withErrorTask(error -> {
                    logger.error("Local file speed conversion failed", error);
                    listener.onFailure(error.getMessage() != null ? error.getMessage() : "Processing failed");
                })
                .build();
    }

    public static void cancelProcess(@NonNull String processId) {
        try {
            YoutubeDL.getInstance().destroyProcessById(processId);
        } catch (Exception e) {
            logger.error("Error cancelling FFmpeg process " + processId, e);
        }
    }
}
