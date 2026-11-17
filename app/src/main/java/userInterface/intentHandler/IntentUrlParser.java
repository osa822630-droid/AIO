package userInterface.intentHandler;

import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.nextgen.R;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import coreUtils.library.networks.URLUtility;
import coreUtils.library.strings.StringHelper;

/**
 * Extracts, classifies (single video vs playlist), and normalizes media URLs from shared Intents or text.
 */
public final class IntentUrlParser {

    private static final Pattern HTTP_URL_PATTERN = Pattern.compile(
            "(https?://[^\\s\"'<>]+)",
            Pattern.CASE_INSENSITIVE
    );

    private IntentUrlParser() {}

    @Nullable
    public static String extractFirstUrl(@Nullable Intent intent) {
        if (intent == null) return null;
        String action = intent.getAction();
        if (Intent.ACTION_SEND.equals(action)) {
            String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (sharedText == null || sharedText.trim().isEmpty()) {
                CharSequence cs = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
                if (cs != null) sharedText = cs.toString();
            }
            return extractFirstUrlFromText(sharedText);
        } else if (Intent.ACTION_VIEW.equals(action)) {
            return extractFirstUrlFromText(intent.getDataString());
        }
        return extractFirstUrlFromText(intent.getStringExtra(Intent.EXTRA_TEXT));
    }

    @Nullable
    public static String extractFirstUrlFromText(@Nullable String rawText) {
        if (rawText == null || rawText.trim().isEmpty() || rawText.length() > 2048) return null;
        String lower = rawText.trim().toLowerCase(Locale.US);
        if (lower.startsWith("javascript:") || lower.startsWith("file:") || lower.startsWith("intent:") || lower.startsWith("content:")) return null;

        try {
            String[] links = URLUtility.extractLinks(rawText);
            if (links != null && links.length > 0 && links[0] != null && !links[0].isEmpty()) {
                return cleanTrailingPunctuation(links[0]);
            }
        } catch (Exception ignored) {}

        Matcher matcher = HTTP_URL_PATTERN.matcher(rawText);
        if (matcher.find()) {
            return cleanTrailingPunctuation(matcher.group(1));
        }
        return null;
    }

    public static boolean isPlaylistUrl(@Nullable String url) {
        if (url == null || url.trim().isEmpty()) return false;
        String lower = url.toLowerCase(Locale.US);
        return lower.contains("list=")
                || lower.contains("/playlist")
                || lower.contains("/sets/")
                || lower.contains("/album/")
                || lower.contains("/collection/");
    }

    @NonNull
    private static String cleanTrailingPunctuation(@NonNull String url) {
        String cleaned = url.trim();
        while (cleaned.endsWith(".") || cleaned.endsWith(",") || cleaned.endsWith(")") || cleaned.endsWith("]")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        return cleaned;
    }

    @NonNull
    public static String detectPlatformName(@Nullable String url) {
        if (url == null || url.isEmpty()) return StringHelper.getText(R.string.label_direct_link);
        String lower = url.toLowerCase(Locale.US);
        if (lower.contains("youtube.com") || lower.contains("youtu.be")) return "YouTube";
        if (lower.contains("tiktok.com")) return "TikTok";
        if (lower.contains("instagram.com")) return "Instagram";
        if (lower.contains("facebook.com") || lower.contains("fb.watch") || lower.contains("fb.com")) return "Facebook";
        if (lower.contains("twitter.com") || lower.contains("x.com")) return "X (Twitter)";
        if (lower.contains("soundcloud.com")) return "SoundCloud";
        if (lower.contains("vimeo.com")) return "Vimeo";
        if (lower.contains("dailymotion.com")) return "Dailymotion";
        if (lower.contains("reddit.com")) return "Reddit";
        if (lower.contains("twitch.tv")) return "Twitch";
        return StringHelper.getText(R.string.label_supported_web_link);
    }
}
