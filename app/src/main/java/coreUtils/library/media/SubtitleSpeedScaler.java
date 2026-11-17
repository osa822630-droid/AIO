package coreUtils.library.media;

import androidx.annotation.NonNull;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import coreUtils.library.process.LoggerUtils;

/**
 * Scales SRT and WebVTT subtitle timestamps by (1 / speed) so downloaded subtitles
 * stay 100% synchronized with speed-modified video files.
 */
public final class SubtitleSpeedScaler {

    private static final LoggerUtils logger = LoggerUtils.from(SubtitleSpeedScaler.class);
    private static final Pattern TIME_LINE_PATTERN = Pattern.compile(
            "((?:\\d{2}:)?\\d{2}:\\d{2}[.,]\\d{3})\\s*-->\\s*((?:\\d{2}:)?\\d{2}:\\d{2}[.,]\\d{3})(.*)"
    );

    private SubtitleSpeedScaler() {}

    /**
     * Finds any downloaded subtitle files matching tempPrefix in searchDir, scales their timestamps
     * by targetSpeed, writes them beside targetVideoFile (e.g. Video_1.5x.srt), and cleans up temp files.
     */
    public static int findAndScaleSubtitles(
            @NonNull File searchDir,
            @NonNull String tempPrefix,
            @NonNull File targetVideoFile,
            float targetSpeed) {

        if (!searchDir.exists() || !searchDir.isDirectory()) return 0;
        File[] matches = searchDir.listFiles((dir, name) ->
                name.startsWith(tempPrefix) && (name.endsWith(".srt") || name.endsWith(".vtt")));
        if (matches == null || matches.length == 0) return 0;

        String videoName = targetVideoFile.getName();
        int dotIdx = videoName.lastIndexOf('.');
        String videoBase = dotIdx > 0 ? videoName.substring(0, dotIdx) : videoName;
        File outDir = targetVideoFile.getParentFile() != null ? targetVideoFile.getParentFile() : searchDir;

        int convertedCount = 0;
        for (File rawSub : matches) {
            try {
                // Extract optional language tag e.g. ".tmp_raw_123.ar.srt" -> ".ar"
                String subName = rawSub.getName();
                String withoutExt = subName.substring(0, subName.lastIndexOf('.'));
                String suffixPart = withoutExt.length() > tempPrefix.length()
                        ? withoutExt.substring(tempPrefix.length())
                        : "";
                String outSrtName = videoBase + suffixPart + ".srt";
                File outSrt = new File(outDir, outSrtName);

                if (scaleSubtitleFile(rawSub, outSrt, targetSpeed)) {
                    convertedCount++;
                }
            } catch (Exception e) {
                logger.error("Failed scaling subtitle " + rawSub.getName(), e);
            } finally {
                if (rawSub.exists()) {
                    rawSub.delete();
                }
            }
        }
        return convertedCount;
    }

    public static boolean scaleSubtitleFile(@NonNull File inputSub, @NonNull File outputSrt, float speed) {
        if (!inputSub.exists() || inputSub.length() == 0 || speed <= 0.05f) return false;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(inputSub), StandardCharsets.UTF_8));
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputSrt), StandardCharsets.UTF_8))) {

            String line;
            boolean skipHeader = false;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.startsWith("WEBVTT") || trimmed.startsWith("Kind:") || trimmed.startsWith("Language:")) {
                    skipHeader = true;
                    continue;
                }
                if (skipHeader && trimmed.isEmpty()) {
                    skipHeader = false;
                    continue;
                }
                skipHeader = false;

                Matcher m = TIME_LINE_PATTERN.matcher(line);
                if (m.matches()) {
                    long startMs = parseTimestampMs(m.group(1));
                    long endMs = parseTimestampMs(m.group(2));
                    long scaledStart = Math.max(0L, Math.round(startMs / (double) speed));
                    long scaledEnd = Math.max(scaledStart + 10L, Math.round(endMs / (double) speed));
                    writer.write(formatSrtTimestamp(scaledStart) + " --> " + formatSrtTimestamp(scaledEnd));
                    writer.newLine();
                } else {
                    writer.write(line);
                    writer.newLine();
                }
            }
            writer.flush();
            return outputSrt.exists() && outputSrt.length() > 0;
        } catch (Exception e) {
            logger.error("Subtitle scaling error", e);
            return false;
        }
    }

    private static long parseTimestampMs(@NonNull String raw) {
        String normalized = raw.trim().replace(',', '.');
        String[] parts = normalized.split(":");
        long hours = 0;
        long minutes;
        String secPart;
        if (parts.length == 3) {
            hours = Long.parseLong(parts[0]);
            minutes = Long.parseLong(parts[1]);
            secPart = parts[2];
        } else if (parts.length == 2) {
            minutes = Long.parseLong(parts[0]);
            secPart = parts[1];
        } else {
            return 0L;
        }
        String[] secMs = secPart.split("\\.");
        long seconds = Long.parseLong(secMs[0]);
        long millis = secMs.length > 1 ? Long.parseLong(secMs[1]) : 0L;
        return ((hours * 3600L) + (minutes * 60L) + seconds) * 1000L + millis;
    }

    @NonNull
    private static String formatSrtTimestamp(long totalMs) {
        long hours = totalMs / 3_600_000L;
        long rem = totalMs % 3_600_000L;
        long minutes = rem / 60_000L;
        rem %= 60_000L;
        long seconds = rem / 1_000L;
        long millis = rem % 1_000L;
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", hours, minutes, seconds, millis);
    }
}
