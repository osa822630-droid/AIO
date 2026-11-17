package sysModules.downloadSys;

import android.media.MediaPlayer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LifecycleOwner;

import com.nextgen.R;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;
import com.yausername.youtubedl_android.YoutubeDLResponse;
import com.yausername.youtubedl_android.mapper.VideoInfo;

import org.json.JSONArray;
import org.json.JSONObject;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.playlist.PlaylistInfo;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import coreUtils.base.BaseApplication;
import coreUtils.library.media.MediaSpeedProcessor;
import coreUtils.library.media.SubtitleSpeedScaler;
import coreUtils.library.media.MediaSpeedProcessor.SpeedMode;
import coreUtils.library.process.DownloaderUtils;
import coreUtils.library.process.SettingsPrefs;
import coreUtils.library.process.LoggerUtils;
import coreUtils.library.process.ThreadTask;
import coreUtils.library.process.TimeFormats;
import coreUtils.library.storage.FileStorageUtility;
import coreUtils.library.strings.StringHelper;
import dataRepo.appConfigs.AppConfigs;
import dataRepo.appConfigs.AppConfigsRepo;
import dataRepo.downloads.DownloadInfo;
import dataRepo.downloads.DownloadsInfoRepo;
import sysModules.newPipeLib.parsers.YtSteamExtractor;
import sysModules.ytdlpWrapper.YtDlpLibraryManager;

/**
 * Production Download, Playlist Extraction, & Speed Post-Processing Engine.
 * Supports single media & full playlists (YouTube via NewPipeExtractor + 1,000+ sites via yt-dlp),
 * configurable 1-to-4 worker parallel execution (via SettingsPrefs), aria2c acceleration, and FFmpeg speed baking.
 */
public final class YtDlpSpeedDownloadEngine {

    private static final LoggerUtils logger = LoggerUtils.from(YtDlpSpeedDownloadEngine.class);
    private static final Map<Long, String> ACTIVE_PROCESS_IDS = new ConcurrentHashMap<>();
    private static final ExecutorService DOWNLOAD_QUEUE_EXECUTOR = Executors.newFixedThreadPool(4);
    private static final AtomicInteger ACTIVE_TASK_COUNT = new AtomicInteger(0);
    private static final AtomicInteger RUNNING_WORKERS = new AtomicInteger(0);
    private static final Object CONCURRENCY_LOCK = new Object();

    private YtDlpSpeedDownloadEngine() {}

    public static final class ExtractedMediaDetails {
        public final String url;
        public final String title;
        public final String uploader;
        public final String thumbnailUrl;
        public final long durationSeconds;

        public ExtractedMediaDetails(String url, String title, String uploader, String thumbnailUrl, long durationSeconds) {
            this.url = url != null ? url : "";
            this.title = (title != null && !title.trim().isEmpty()) ? title.trim() : "Media_" + System.currentTimeMillis();
            this.uploader = (uploader != null && !uploader.trim().isEmpty()) ? uploader.trim() : "TubeAIO";
            this.thumbnailUrl = thumbnailUrl != null ? thumbnailUrl : "";
            this.durationSeconds = Math.max(0L, durationSeconds);
        }
    }

    public static final class ExtractedPlaylistItem {
        public final String url;
        public final String title;
        public final String uploader;
        public final String thumbnailUrl;
        public final long durationSeconds;
        public boolean isSelected = true;

        public ExtractedPlaylistItem(String url, String title, String uploader, String thumbnailUrl, long durationSeconds) {
            this.url = url != null ? url : "";
            this.title = (title != null && !title.trim().isEmpty()) ? title.trim() : "Track";
            this.uploader = uploader != null ? uploader : "";
            this.thumbnailUrl = thumbnailUrl != null ? thumbnailUrl : "";
            this.durationSeconds = Math.max(0L, durationSeconds);
        }
    }

    public static final class ExtractedPlaylistDetails {
        public final String playlistUrl;
        public final String playlistTitle;
        public final String uploader;
        public final List<ExtractedPlaylistItem> items;

        public ExtractedPlaylistDetails(String playlistUrl, String playlistTitle, String uploader, List<ExtractedPlaylistItem> items) {
            this.playlistUrl = playlistUrl != null ? playlistUrl : "";
            this.playlistTitle = (playlistTitle != null && !playlistTitle.trim().isEmpty()) ? playlistTitle.trim() : "Playlist";
            this.uploader = uploader != null ? uploader : "";
            this.items = items != null ? items : new ArrayList<>();
        }
    }

    public interface MetadataCallback {
        void onMetadataReady(@NonNull ExtractedMediaDetails details);
        void onMetadataError(@NonNull String errorMessage);
    }

    public interface PlaylistCallback {
        void onPlaylistReady(@NonNull ExtractedPlaylistDetails playlist);
        void onPlaylistError(@NonNull String errorMessage);
    }

    public interface EngineDownloadListener {
        default void onProgress(long downloadId, float totalPercent, String statusText) {}
        default void onCompleted(long downloadId, @NonNull File outputFile) {}
        default void onError(long downloadId, @NonNull String errorMessage) {}
    }

    public interface UpdateCallback {
        void onUpdateResult(boolean updated, @NonNull String statusMessage);
        void onUpdateError(@NonNull String errorMessage);
    }

    public interface StreamUrlCallback {
        void onStreamUrlReady(@NonNull String streamUrl);
        void onStreamUrlError(@NonNull String errorMessage);
    }

    /**
     * Updates the embedded yt-dlp binary in-app without needing a new APK release.
     */
    public static ThreadTask<YoutubeDL.UpdateStatus, Void> updateYtDlpEngineAsync(
            @Nullable LifecycleOwner lifecycleOwner,
            @NonNull UpdateCallback callback) {

        ThreadTask<YoutubeDL.UpdateStatus, Void> task = new ThreadTask.Builder<YoutubeDL.UpdateStatus, Void>()
                .withLifecycle(lifecycleOwner)
                .withBackgroundTask(progress -> YoutubeDL.getInstance().updateYoutubeDL(
                        BaseApplication.getInstance(),
                        YoutubeDL.UpdateChannel._STABLE
                ))
                .withResultTask(status -> {
                    if (status == YoutubeDL.UpdateStatus.DONE) {
                        callback.onUpdateResult(true, StringHelper.getText(R.string.msg_engine_updated));
                    } else {
                        callback.onUpdateResult(false, StringHelper.getText(R.string.msg_engine_up_to_date));
                    }
                })
                .withErrorTask(err -> callback.onUpdateError(
                        err.getMessage() != null ? err.getMessage() : "Update failed"))
                .build();
        task.execute();
        return task;
    }

    /**
     * Extracts a direct playable stream URL for live speed preview in ExoPlayer before downloading.
     */
    public static ThreadTask<String, Void> extractDirectStreamUrlAsync(
            @Nullable LifecycleOwner lifecycleOwner,
            @NonNull String url,
            boolean audioOnly,
            @NonNull StreamUrlCallback callback) {

        ThreadTask<String, Void> task = new ThreadTask.Builder<String, Void>()
                .withLifecycle(lifecycleOwner)
                .withBackgroundTask(progress -> {
                    if (YtSteamExtractor.isYoutubeUrl(url)) {
                        try {
                            StreamInfo info = StreamInfo.getInfo(ServiceList.YouTube, url);
                            if (audioOnly && !info.getAudioStreams().isEmpty()) {
                                return info.getAudioStreams().get(0).getContent();
                            }
                            if (!info.getVideoStreams().isEmpty()) {
                                return info.getVideoStreams().get(0).getContent();
                            }
                        } catch (Exception ignored) {}
                    }
                    YtDlpLibraryManager.awaitReady(20);
                    YoutubeDLRequest req = new YoutubeDLRequest(url);
                    req.addOption("--extractor-args", "youtube:player_client=android,web");
                    req.addOption("--no-playlist");
                    req.addOption("-g");
                    req.addOption("-f", audioOnly ? "bestaudio[ext=m4a]/bestaudio" : "best[ext=mp4]/best");
                    YoutubeDLResponse res = YoutubeDL.getInstance().execute(req, "preview_" + System.currentTimeMillis(), null);
                    String out = res.getOut() != null ? res.getOut().trim() : "";
                    if (out.isEmpty()) {
                        throw new RuntimeException(res.getErr() != null ? res.getErr() : "No stream URL found");
                    }
                    return out.split("\\r?\\n")[0].trim();
                })
                .withResultTask(callback::onStreamUrlReady)
                .withErrorTask(err -> callback.onStreamUrlError(
                        err.getMessage() != null ? err.getMessage() : "Stream extraction failed"))
                .build();
        task.execute();
        return task;
    }

    /**
     * Extracts metadata for a single video/audio URL.
     */
    public static ThreadTask<ExtractedMediaDetails, Void> extractMediaMetadataAsync(
            @Nullable LifecycleOwner lifecycleOwner,
            @NonNull String url,
            @NonNull MetadataCallback callback) {

        ThreadTask<ExtractedMediaDetails, Void> task = new ThreadTask.Builder<ExtractedMediaDetails, Void>()
                .withLifecycle(lifecycleOwner)
                .withBackgroundTask(progress -> {
                    String cleanUrl = url.trim();
                    boolean isYouTube = cleanUrl.contains("youtube.com") || cleanUrl.contains("youtu.be");

                    if (isYouTube) {
                        try {
                            StreamInfo streamInfo = YtSteamExtractor.getStreamInfo(cleanUrl);
                            if (streamInfo != null && streamInfo.getName() != null) {
                                String thumb = YtSteamExtractor.getBestImageQuality(streamInfo.getThumbnails());
                                return new ExtractedMediaDetails(
                                        cleanUrl,
                                        streamInfo.getName(),
                                        streamInfo.getUploaderName(),
                                        thumb,
                                        streamInfo.getDuration()
                                );
                            }
                        } catch (Exception npErr) {
                            logger.warning("NewPipe extraction failed, falling back to yt-dlp: " + npErr.getMessage());
                        }
                    }

                    YtDlpLibraryManager.awaitReady(20);
                    YoutubeDLRequest infoReq = new YoutubeDLRequest(cleanUrl);
                    infoReq.addOption("--extractor-args", "youtube:player_client=android,web");
                    infoReq.addOption("--no-playlist");
                    VideoInfo videoInfo = YoutubeDL.getInstance().getInfo(infoReq);
                    if (videoInfo == null) {
                        throw new RuntimeException(StringHelper.getText(R.string.msg_playlist_fetch_failed));
                    }
                    return new ExtractedMediaDetails(
                            cleanUrl,
                            videoInfo.getTitle(),
                            videoInfo.getUploader(),
                            videoInfo.getThumbnail(),
                            videoInfo.getDuration()
                    );
                })
                .withResultTask(callback::onMetadataReady)
                .withErrorTask(err -> callback.onMetadataError(
                        err.getMessage() != null ? err.getMessage() : "Error"))
                .build();

        task.start();
        return task;
    }

    /**
     * Extracts all items in a Playlist URL (YouTube via NewPipe PlaylistInfo, or any site via yt-dlp --flat-playlist).
     */
    public static ThreadTask<ExtractedPlaylistDetails, Void> extractPlaylistAsync(
            @Nullable LifecycleOwner lifecycleOwner,
            @NonNull String playlistUrl,
            @NonNull PlaylistCallback callback) {

        ThreadTask<ExtractedPlaylistDetails, Void> task = new ThreadTask.Builder<ExtractedPlaylistDetails, Void>()
                .withLifecycle(lifecycleOwner)
                .withBackgroundTask(progress -> {
                    String cleanUrl = playlistUrl.trim();
                    boolean isYouTube = cleanUrl.contains("youtube.com") || cleanUrl.contains("youtu.be");

                    if (isYouTube) {
                        try {
                            PlaylistInfo plInfo = PlaylistInfo.getInfo(ServiceList.YouTube, cleanUrl);
                            List<ExtractedPlaylistItem> items = new ArrayList<>();
                            if (plInfo.getRelatedItems() != null) {
                                for (StreamInfoItem item : plInfo.getRelatedItems()) {
                                    String thumb = YtSteamExtractor.getBestImageQuality(item.getThumbnails());
                                    items.add(new ExtractedPlaylistItem(
                                            item.getUrl(),
                                            item.getName(),
                                            item.getUploaderName(),
                                            thumb,
                                            item.getDuration()
                                    ));
                                }
                            }
                            if (!items.isEmpty()) {
                                return new ExtractedPlaylistDetails(
                                        cleanUrl,
                                        plInfo.getName(),
                                        plInfo.getUploaderName(),
                                        items
                                );
                            }
                        } catch (Exception npErr) {
                            logger.warning("NewPipe playlist extraction failed, falling back to yt-dlp: " + npErr.getMessage());
                        }
                    }

                    // Universal yt-dlp flat-playlist JSON fallback
                    YtDlpLibraryManager.awaitReady(20);
                    YoutubeDLRequest req = new YoutubeDLRequest(cleanUrl);
                    req.addOption("--extractor-args", "youtube:player_client=android,web");
                    req.addOption("--flat-playlist");
                    req.addOption("--dump-single-json");
                    YoutubeDLResponse res = YoutubeDL.getInstance().execute(req, "pl_" + System.currentTimeMillis(), null);
                    String out = res.getOut();
                    if (out == null || out.trim().isEmpty()) {
                        throw new RuntimeException(StringHelper.getText(R.string.msg_playlist_fetch_failed));
                    }

                    JSONObject json = new JSONObject(out);
                    String plTitle = json.optString("title", "Playlist");
                    String plUploader = json.optString("uploader", "");
                    JSONArray entries = json.optJSONArray("entries");
                    List<ExtractedPlaylistItem> parsedItems = new ArrayList<>();

                    if (entries != null) {
                        for (int i = 0; i < entries.length(); i++) {
                            JSONObject entry = entries.optJSONObject(i);
                            if (entry == null) continue;
                            String itemUrl = entry.optString("webpage_url", "");
                            if (itemUrl.isEmpty()) itemUrl = entry.optString("url", "");
                            if (itemUrl.isEmpty() && entry.has("id") && isYouTube) {
                                itemUrl = "https://www.youtube.com/watch?v=" + entry.optString("id");
                            }
                            if (itemUrl.isEmpty()) continue;

                            String itemTitle = entry.optString("title", "Item " + (i + 1));
                            String itemThumb = entry.optString("thumbnail", "");
                            long dur = entry.optLong("duration", 0L);
                            parsedItems.add(new ExtractedPlaylistItem(itemUrl, itemTitle, plUploader, itemThumb, dur));
                        }
                    }

                    if (parsedItems.isEmpty()) {
                        throw new RuntimeException(StringHelper.getText(R.string.msg_playlist_fetch_failed));
                    }

                    return new ExtractedPlaylistDetails(cleanUrl, plTitle, plUploader, parsedItems);
                })
                .withResultTask(callback::onPlaylistReady)
                .withErrorTask(err -> callback.onPlaylistError(
                        err.getMessage() != null ? err.getMessage() : StringHelper.getText(R.string.msg_playlist_fetch_failed)))
                .build();

        task.start();
        return task;
    }

    /**
     * Enqueues all selected playlist items for sequential/bounded parallel downloading at the target speed.
     */
    @NonNull
    public static List<DownloadInfo> enqueuePlaylistDownloads(
            @NonNull List<ExtractedPlaylistItem> items,
            float targetSpeed,
            boolean audioOnly,
            @NonNull SpeedMode speedMode,
            @NonNull String qualityTag,
            @Nullable EngineDownloadListener listener) {
        return enqueuePlaylistDownloads("", items, targetSpeed, audioOnly, speedMode, qualityTag, false, false, listener);
    }

    @NonNull
    public static List<DownloadInfo> enqueuePlaylistDownloads(
            @Nullable String playlistTitle,
            @NonNull List<ExtractedPlaylistItem> items,
            float targetSpeed,
            boolean audioOnly,
            @NonNull SpeedMode speedMode,
            @NonNull String qualityTag,
            boolean voiceBoost,
            boolean includeSubtitles,
            @Nullable EngineDownloadListener listener) {

        String safeFolder = FileStorageUtility.sanitizeFileNameNormal(playlistTitle);
        if (safeFolder == null || safeFolder.trim().isEmpty()) {
            safeFolder = "Playlist_" + System.currentTimeMillis();
        }
        if (safeFolder.length() > 50) {
            safeFolder = safeFolder.substring(0, 50).trim();
        }

        List<DownloadInfo> queued = new ArrayList<>();
        int index = 1;
        for (ExtractedPlaylistItem item : items) {
            if (!item.isSelected || item.url.isEmpty()) continue;
            String numberedTitle = String.format(Locale.US, "%02d - %s", index, item.title);
            queued.add(enqueueAndStartDownload(
                    item.url,
                    numberedTitle,
                    item.thumbnailUrl,
                    item.durationSeconds,
                    targetSpeed,
                    audioOnly,
                    speedMode,
                    qualityTag,
                    voiceBoost,
                    includeSubtitles,
                    safeFolder,
                    listener
            ));
            index++;
        }
        return queued;
    }

    @NonNull
    public static DownloadInfo enqueueAndStartDownload(
            @NonNull String url,
            @NonNull String title,
            @Nullable String thumbnailUrl,
            long originalDurationSeconds,
            float targetSpeed,
            boolean audioOnly,
            @NonNull SpeedMode speedMode,
            @NonNull String qualityTag,
            @Nullable EngineDownloadListener listener) {
        return enqueueAndStartDownload(
                url, title, thumbnailUrl, originalDurationSeconds,
                targetSpeed, audioOnly, speedMode, qualityTag,
                false, false, null, listener
        );
    }

    @NonNull
    public static DownloadInfo enqueueAndStartDownload(
            @NonNull String url,
            @NonNull String title,
            @Nullable String thumbnailUrl,
            long originalDurationSeconds,
            float targetSpeed,
            boolean audioOnly,
            @NonNull SpeedMode speedMode,
            @NonNull String qualityTag,
            boolean voiceBoost,
            boolean includeSubtitles,
            @Nullable String customSubfolder,
            @Nullable EngineDownloadListener listener) {

        DownloadInfo info = new DownloadInfo();
        info.fileUrl = url.trim();
        info.thumbnailUrl = thumbnailUrl != null ? thumbnailUrl : "";

        float clampedSpeed = Math.max(0.25f, Math.min(4.0f, targetSpeed));
        boolean needsSpeedChange = Math.abs(clampedSpeed - 1.0f) >= 0.01f;

        String sanitizedTitle = FileStorageUtility.sanitizeFileNameNormal(title);
        if (sanitizedTitle == null || sanitizedTitle.trim().isEmpty()) {
            sanitizedTitle = "TubeAIO_" + System.currentTimeMillis();
        }
        if (sanitizedTitle.length() > 80) {
            sanitizedTitle = sanitizedTitle.substring(0, 80).trim();
        }

        String speedSuffix = needsSpeedChange
                ? "_" + String.format(Locale.US, "%.2fx", clampedSpeed).replaceAll("0+x$", "x").replaceAll("\\.x$", "x")
                : "";
        String ext = audioOnly
                ? ("mp3".equalsIgnoreCase(qualityTag) ? ".mp3" : ".m4a")
                : ".mp4";

        info.fileName = sanitizedTitle + speedSuffix + ext;
        info.fileExtension = ext;
        info.fileDirectory = SettingsPrefs.resolveBaseDownloadDirectory(BaseApplication.getInstance()).getAbsolutePath();
        info.isSmartDirEnabled = SettingsPrefs.isSmartSubfoldersEnabled();
        DownloaderUtils.applySmartCatalogDirectory(info);
        if (customSubfolder != null && !customSubfolder.trim().isEmpty()) {
            File subDir = new File(info.fileDirectory, customSubfolder.trim());
            if (!subDir.exists()) {
                subDir.mkdirs();
            }
            info.fileDirectory = subDir.getAbsolutePath();
        }
        DownloaderUtils.resolveFileNameConflict(info);

        info.setSpeedDownloadConfig(clampedSpeed, audioOnly, speedMode.name(), qualityTag, voiceBoost, includeSubtitles);
        info.startTimeDate = System.currentTimeMillis();
        info.startTimeDateInFormat = TimeFormats.getCurrentDateTime();
        info.downloadStatus = DownloadInfo.DOWNLOAD_STATUS_PENDING;
        info.downloadStatusInfo = String.format(
                StringHelper.getText(R.string.status_queued_with_speed),
                info.getFormattedSpeedBadge()
        );
        info.isRunning = true;
        info.isComplete = false;

        long adjustedDurationSec = MediaSpeedProcessor.calculateAdjustedDurationSeconds(originalDurationSeconds, clampedSpeed);
        if (adjustedDurationSec > 0) {
            info.mediaPlaybackDuration = adjustedDurationSec * 1000L;
            info.mediaPlaybackDurationInFormat = TimeFormats.formatDurationFromSecond(adjustedDurationSec);
        }

        DownloadsInfoRepo.save(info);
        executeDownloadPipeline(info, listener);
        return info;
    }

    public static void executeDownloadPipeline(
            @NonNull DownloadInfo downloadInfo,
            @Nullable EngineDownloadListener listener) {

        int active = ACTIVE_TASK_COUNT.incrementAndGet();
        SpeedDownloadForegroundService.startOrUpdate(
                downloadInfo.fileName,
                downloadInfo.downloadStatusInfo,
                0,
                active,
                true
        );

        DOWNLOAD_QUEUE_EXECUTOR.execute(() -> {
            synchronized (CONCURRENCY_LOCK) {
                while (RUNNING_WORKERS.get() >= SettingsPrefs.getMaxConcurrentDownloads()) {
                    try {
                        CONCURRENCY_LOCK.wait(500L);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                RUNNING_WORKERS.incrementAndGet();
            }
            final long downloadId = downloadInfo.downloadId;
            final String dlProcessId = "ytdlp_dl_" + downloadId + "_" + System.currentTimeMillis();
            ACTIVE_PROCESS_IDS.put(downloadId, dlProcessId);

            float targetSpeed = downloadInfo.getTargetPlaybackSpeed();
            boolean audioOnly = downloadInfo.isAudioOnlyDownload();
            SpeedMode speedMode = SpeedMode.fromName(downloadInfo.getSpeedModeName(), audioOnly);
            String qualityTag = downloadInfo.getRequestedQuality();
            boolean voiceBoost = downloadInfo.isVoiceBoostEnabled();
            boolean includeSubtitles = downloadInfo.isSubtitlesEnabled() && !audioOnly;
            boolean needsSpeedChange = Math.abs(targetSpeed - 1.0f) >= 0.01f || voiceBoost;
            String rawTempPrefix = ".tmp_raw_" + downloadId;

            File destDir = new File(downloadInfo.fileDirectory);
            if (!destDir.exists()) {
                destDir.mkdirs();
            }

            File finalOutputFile = downloadInfo.getDestinationFile();
            String rawExt = audioOnly ? ".m4a" : ".mp4";
            File rawTempFile = needsSpeedChange
                    ? new File(destDir, rawTempPrefix + rawExt)
                    : finalOutputFile;

            try {
                AppConfigs netCfg = AppConfigsRepo.getConfig();
                if (netCfg != null && netCfg.wifiOnlyDownloadEnabled && !SettingsPrefs.isWifiConnected(BaseApplication.getInstance())) {
                    throw new RuntimeException(StringHelper.getText(R.string.err_wifi_only_enabled));
                }
                downloadInfo.downloadStatus = DownloadInfo.DOWNLOAD_STATUS_DOWNLOADING;
                downloadInfo.isRunning = true;
                downloadInfo.downloadStatusInfo = StringHelper.getText(R.string.status_downloading);
                DownloadsInfoRepo.save(downloadInfo);

                YtDlpLibraryManager.awaitReady(20);
                YoutubeDLRequest request = new YoutubeDLRequest(downloadInfo.fileUrl);
                request.addOption("--extractor-args", "youtube:player_client=android,web");
                request.addOption("--no-mtime");
                request.addOption("--no-playlist");
                request.addOption("-o", rawTempFile.getAbsolutePath());
                if (includeSubtitles) {
                    request.addOption("--write-subs");
                    request.addOption("--write-auto-subs");
                    request.addOption("--sub-langs", "ar.*,en.*");
                    request.addOption("--convert-subs", "srt");
                }
                request.addOption("--downloader", "libaria2c.so");
                request.addOption("--external-downloader-args", "aria2c:-x 8 -s 8 -k 1M");

                if (audioOnly) {
                    request.addOption("-f", "bestaudio[ext=m4a]/bestaudio");
                    if (!needsSpeedChange && "mp3".equalsIgnoreCase(qualityTag)) {
                        request.addOption("-x");
                        request.addOption("--audio-format", "mp3");
                    }
                } else {
                    String formatSelector = switch (qualityTag) {
                        case "1080p" -> "bestvideo[height<=1080][ext=mp4]+bestaudio[ext=m4a]/best[height<=1080][ext=mp4]/best[height<=1080]";
                        case "720p" -> "bestvideo[height<=720][ext=mp4]+bestaudio[ext=m4a]/best[height<=720][ext=mp4]/best[height<=720]";
                        case "480p" -> "bestvideo[height<=480][ext=mp4]+bestaudio[ext=m4a]/best[height<=480][ext=mp4]/best[height<=480]";
                        case "360p" -> "bestvideo[height<=360][ext=mp4]+bestaudio[ext=m4a]/best[height<=360][ext=mp4]/best[height<=360]";
                        default -> "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best";
                    };
                    request.addOption("-f", formatSelector);
                    request.addOption("--merge-output-format", "mp4");
                }

                boolean downloadedViaYtDlp = false;
                try {
                    YoutubeDLResponse response = YoutubeDL.getInstance().execute(
                            request,
                            dlProcessId,
                            (progress, etaInSeconds, line) -> {
                                float cleanProgress = Math.max(0f, Math.min(100f, progress));
                                float overallProgress = needsSpeedChange ? (cleanProgress * 0.75f) : cleanProgress;
                                downloadInfo.progressPercentage = Math.round(overallProgress);
                                downloadInfo.progressPercentageInFormat = DownloaderUtils.getFormatted(overallProgress) + "%";
                                downloadInfo.remainingTimeInSec = Math.max(0L, etaInSeconds);
                                downloadInfo.remainingTimeInFormat = etaInSeconds > 0
                                        ? TimeFormats.toHumanReadableTime(etaInSeconds)
                                        : "--:--";
                                downloadInfo.downloadStatusInfo = String.format(
                                        Locale.US,
                                        StringHelper.getText(R.string.status_downloading_pct),
                                        overallProgress,
                                        downloadInfo.getFormattedSpeedBadge()
                                );
                                DownloadsInfoRepo.updateProgressOnly(downloadInfo);
                                SpeedDownloadForegroundService.startOrUpdate(downloadInfo.fileName, downloadInfo.downloadStatusInfo, Math.round(overallProgress), ACTIVE_TASK_COUNT.get(), false);
                                if (listener != null) {
                                    ThreadTask.executeOnMainThread(() ->
                                            listener.onProgress(downloadId, overallProgress, downloadInfo.downloadStatusInfo));
                                }
                                return null;
                            }
                    );
                    downloadedViaYtDlp = (response.getExitCode() == 0 && rawTempFile.exists() && rawTempFile.length() > 0);
                } catch (Exception ytdlpErr) {
                    logger.warning("yt-dlp execution unavailable, falling back to NewPipe/HTTP stream download: " + ytdlpErr.getMessage());
                }

                if (!downloadedViaYtDlp) {
                    downloadStreamViaNewPipeOrHttp(downloadInfo.fileUrl, rawTempFile, audioOnly, needsSpeedChange, downloadInfo, downloadId, listener);
                }

                if (needsSpeedChange) {
                    String speedProcessId = "ffmpeg_speed_" + downloadId + "_" + System.currentTimeMillis();
                    ACTIVE_PROCESS_IDS.put(downloadId, speedProcessId);
                    downloadInfo.downloadStatusInfo = String.format(
                            StringHelper.getText(R.string.status_adjusting_speed),
                            downloadInfo.getFormattedSpeedBadge()
                    );
                    downloadInfo.progressPercentage = 76L;
                    DownloadsInfoRepo.save(downloadInfo);

                    MediaSpeedProcessor.executeSpeedTransformSync(
                            rawTempFile,
                            finalOutputFile,
                            targetSpeed,
                            speedMode,
                            speedProcessId,
                            voiceBoost,
                            new MediaSpeedProcessor.SpeedProcessListener() {
                                @Override
                                public void onProgress(float percentage, long etaSeconds, String statusLine) {
                                    float totalPct = 75.0f + (Math.max(0f, Math.min(100f, percentage)) * 0.25f);
                                    downloadInfo.progressPercentage = Math.round(totalPct);
                                    downloadInfo.progressPercentageInFormat = DownloaderUtils.getFormatted(totalPct) + "%";
                                    downloadInfo.downloadStatusInfo = String.format(
                                            Locale.US,
                                            StringHelper.getText(R.string.status_processing_speed_pct),
                                            downloadInfo.getFormattedSpeedBadge(),
                                            totalPct
                                    );
                                    DownloadsInfoRepo.updateProgressOnly(downloadInfo);
                                    if (listener != null) {
                                        ThreadTask.executeOnMainThread(() ->
                                                listener.onProgress(downloadId, totalPct, downloadInfo.downloadStatusInfo));
                                    }
                                }

                                @Override public void onSuccess(@NonNull File outputFile, float appliedSpeed) {}
                                @Override public void onFailure(@NonNull String errorMessage) {}
                            }
                    );

                    if (rawTempFile.exists() && !rawTempFile.equals(finalOutputFile)) {
                        rawTempFile.delete();
                    }
                }

                if (includeSubtitles) {
                    String subPrefix = needsSpeedChange ? rawTempPrefix : FileStorageUtility.getFileNameWithoutExtension(finalOutputFile.getName());
                    if (subPrefix != null) {
                        SubtitleSpeedScaler.findAndScaleSubtitles(destDir, subPrefix, finalOutputFile, targetSpeed);
                    }
                }

                ACTIVE_PROCESS_IDS.remove(downloadId);
                downloadInfo.finalFilePath = finalOutputFile.getAbsolutePath();
                downloadInfo.fileSize = finalOutputFile.length();
                downloadInfo.fileSizeInFormat = DownloaderUtils.getHumanReadableFormat(downloadInfo.fileSize);
                downloadInfo.downloadedByte = downloadInfo.fileSize;
                downloadInfo.downloadedByteInFormat = downloadInfo.fileSizeInFormat;
                downloadInfo.progressPercentage = 100L;
                downloadInfo.progressPercentageInFormat = "100%";
                downloadInfo.isRunning = false;
                downloadInfo.isComplete = true;
                downloadInfo.downloadStatus = DownloadInfo.DOWNLOAD_STATUS_COMPLETED;
                downloadInfo.completedTimeDate = System.currentTimeMillis();
                downloadInfo.completedTimeDateInFormat = TimeFormats.getCurrentDateTime();
                downloadInfo.downloadStatusInfo = String.format(
                        StringHelper.getText(R.string.status_completed_summary),
                        downloadInfo.getFormattedSpeedBadge(),
                        downloadInfo.fileSizeInFormat
                );

                String fetchedDur = DownloaderUtils.fetchMediaDuration(downloadInfo);
                if (fetchedDur != null && !fetchedDur.isEmpty()) {
                    downloadInfo.mediaPlaybackDurationInFormat = fetchedDur.replace("(", "").replace(")", "");
                }

                DownloadsInfoRepo.save(downloadInfo);

                FileStorageUtility.addToMediaStore(finalOutputFile, (path, uri) ->
                        logger.debug("Indexed speed-modified media in MediaStore: " + path));

                AppConfigs configs = AppConfigsRepo.getConfig();
                if (configs != null) {
                    configs.totalSuccessfulDownloadCount += 1;
                    configs.save();
                    if (configs.isDownloadCompletionSoundEnabled) {
                        playCompletionSound();
                    }
                }

                if (listener != null) {
                    ThreadTask.executeOnMainThread(() -> listener.onCompleted(downloadId, finalOutputFile));
                }

            } catch (Exception error) {
                ACTIVE_PROCESS_IDS.remove(downloadId);
                if (needsSpeedChange && rawTempFile.exists() && !rawTempFile.equals(finalOutputFile)) {
                    rawTempFile.delete();
                }
                logger.error("Download pipeline failed for ID " + downloadId, error);
                downloadInfo.isRunning = false;
                downloadInfo.isComplete = false;
                downloadInfo.downloadStatus = DownloadInfo.DOWNLOAD_STATUS_FAILED;
                String errMsg = error.getMessage() != null ? error.getMessage() : "Error";
                downloadInfo.ytdlpErrorMessage = errMsg;
                String shortErr = errMsg.length() > 45 ? errMsg.substring(0, 45) + "..." : errMsg;
                downloadInfo.downloadStatusInfo = String.format(StringHelper.getText(R.string.status_failed_prefix), shortErr);
                DownloadsInfoRepo.save(downloadInfo);

                if (listener != null) {
                    ThreadTask.executeOnMainThread(() -> listener.onError(downloadId, errMsg));
                }
            } finally {
                synchronized (CONCURRENCY_LOCK) {
                    RUNNING_WORKERS.decrementAndGet();
                    CONCURRENCY_LOCK.notifyAll();
                }
                if (ACTIVE_TASK_COUNT.decrementAndGet() <= 0) {
                    ACTIVE_TASK_COUNT.set(0);
                    SpeedDownloadForegroundService.stopIfIdle();
                }
            }
        });
    }

    public static void cancelAllActiveDownloads() {
        for (Long id : new ArrayList<>(ACTIVE_PROCESS_IDS.keySet())) {
            cancelDownload(id);
        }
        ACTIVE_TASK_COUNT.set(0);
        SpeedDownloadForegroundService.stopIfIdle();
    }

    public static void cancelDownload(long downloadId) {
        String processId = ACTIVE_PROCESS_IDS.remove(downloadId);
        if (processId != null) {
            try {
                YoutubeDL.getInstance().destroyProcessById(processId);
            } catch (Exception e) {
                logger.error("Failed destroying process " + processId, e);
            }
        }
        DownloadInfo info = DownloadsInfoRepo.getById(downloadId);
        if (info != null && !info.isComplete) {
            info.isRunning = false;
            info.downloadStatus = DownloadInfo.DOWNLOAD_STATUS_PAUSED;
            info.downloadStatusInfo = StringHelper.getText(R.string.status_paused);
            DownloadsInfoRepo.save(info);
        }
    }

    private static void downloadStreamViaNewPipeOrHttp(
            @NonNull String sourceUrl,
            @NonNull File targetFile,
            boolean audioOnly,
            boolean needsSpeedChange,
            @NonNull DownloadInfo downloadInfo,
            long downloadId,
            @Nullable EngineDownloadListener listener) throws Exception {

        String directUrl = sourceUrl;
        if (YtSteamExtractor.isYoutubeUrl(sourceUrl)) {
            StreamInfo info = StreamInfo.getInfo(ServiceList.YouTube, sourceUrl);
            if (audioOnly && !info.getAudioStreams().isEmpty()) {
                directUrl = info.getAudioStreams().get(0).getContent();
            } else if (!info.getVideoStreams().isEmpty()) {
                directUrl = info.getVideoStreams().get(0).getContent();
            } else if (!info.getVideoOnlyStreams().isEmpty()) {
                directUrl = info.getVideoOnlyStreams().get(0).getContent();
            }
        }

        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(directUrl).openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124.0.0.0 Mobile Safari/537.36");
        conn.connect();

        int responseCode = conn.getResponseCode();
        if (responseCode < 200 || responseCode >= 300) {
            throw new RuntimeException("HTTP " + responseCode + " when downloading stream");
        }

        long totalBytes = conn.getContentLengthLong();
        try (java.io.InputStream in = conn.getInputStream();
             java.io.FileOutputStream out = new java.io.FileOutputStream(targetFile)) {
            byte[] buffer = new byte[64 * 1024];
            long downloaded = 0L;
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                downloaded += read;
                if (totalBytes > 0) {
                    float pct = Math.min(100f, (downloaded * 100f) / totalBytes);
                    float overallProgress = needsSpeedChange ? (pct * 0.75f) : pct;
                    downloadInfo.progressPercentage = Math.round(overallProgress);
                    downloadInfo.progressPercentageInFormat = DownloaderUtils.getFormatted(overallProgress) + "%";
                    downloadInfo.downloadStatusInfo = String.format(
                            Locale.US,
                            StringHelper.getText(R.string.status_downloading_pct),
                            overallProgress,
                            downloadInfo.getFormattedSpeedBadge()
                    );
                    DownloadsInfoRepo.updateProgressOnly(downloadInfo);
                    if (listener != null) {
                        ThreadTask.executeOnMainThread(() ->
                                listener.onProgress(downloadId, overallProgress, downloadInfo.downloadStatusInfo));
                    }
                }
            }
        } finally {
            conn.disconnect();
        }

        if (!targetFile.exists() || targetFile.length() == 0) {
            throw new RuntimeException("Downloaded stream file is empty");
        }
    }

    private static void playCompletionSound() {
        ThreadTask.executeOnMainThread(() -> {
            try {
                MediaPlayer mp = MediaPlayer.create(BaseApplication.getInstance(), R.raw.sound_download_finished);
                if (mp != null) {
                    mp.setOnCompletionListener(MediaPlayer::release);
                    mp.start();
                }
            } catch (Exception ignored) {}
        });
    }
}
