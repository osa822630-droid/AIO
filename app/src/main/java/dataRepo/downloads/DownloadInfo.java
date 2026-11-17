package dataRepo.downloads;

import static coreUtils.library.storage.FileStorageUtility.saveStringToInternalStorage;

import androidx.annotation.Nullable;

import com.nextgen.R;

import java.io.File;
import java.io.Serializable;
import java.util.Map;

import coreUtils.library.process.DownloaderUtils;
import coreUtils.library.process.LoggerUtils;
import coreUtils.library.storage.FileExtensions;
import coreUtils.library.storage.FileStorageUtility;
import coreUtils.library.strings.StringHelper;
import dataRepo.appConfigs.AppConfigs;
import dataRepo.appConfigs.AppConfigsRepo;
import io.objectbox.annotation.Entity;
import io.objectbox.annotation.Id;
import io.objectbox.annotation.Transient;

@Entity
public class DownloadInfo implements Serializable {
    @Transient
    private final LoggerUtils logger = LoggerUtils.from(getClass());

    @Id
    public Long downloadId = 0L;

    public int downloadStatus = DOWNLOAD_STATUS_PAUSED;
    public String downloadStatusInfo = "--";
    public String userAlertMessage = "";

    public boolean isRunning = false;
    public boolean isComplete = false;
    public boolean isDeleted = false;
    public boolean isRemoved = false;
    public boolean hasUserOpenedTheFile = false;

    public boolean isWaitingForNetwork = false;
    public boolean isResumeSupported = false;
    public boolean isMultiThreadSupported = false;
    public boolean isFileSizeUnknown = false;
    public boolean isDownloadFromBrowser = false;

    public boolean isFileUrlExpired = false;
    public boolean expiredUrlDialogShown = false;
    public boolean isDestinationFileMissing = false;
    public boolean isChecksumInvalid = false;
    public boolean isFileCorrupted = false;
    public boolean isSmartDirInitialized = false;

    public boolean isSmartDirEnabled = true;
    public boolean isThumbnailDisabled = false;
    public boolean isCompletionAudioEnabled = true;
    public boolean isNotificationDisabled = false;
    public boolean isAutoResumeEnabled = true;
    public int autoResumeRetryLimit = 35;
    public long downloadSpeedCap = 0L;
    public boolean isWifiRequired = false;
    public String customUserAgent = "";
    public String proxyServer = "";

    public String ytdlpExecutionCommand = "";
    public boolean isYtdlpInitialized = false;
    public boolean isYtdlpErrorFound = false;

    public String ytdlpErrorMessage = "";
    public String ytdlpTempOutputPath = "";
    public String ytdlpStatusInfo = "";

    public String fileName = "";
    public String fileMimeType = "";
    public String fileCategoryName = "";
    public String fileDirectory = "";
    public String fileDirectoryURI = "";
    public String fileContentDisposition = "";
    public String fileChecksum = "";
    public String fileSiteReferrer = "";
    public String fileExtension = "";
    public String fileUrl = "";
    public String finalFilePath = "";

    public long fileSize = 0L;
    public String fileSizeInFormat = "";
    public long downloadedByte = 0L;
    public String downloadedByteInFormat = "--";
    public long remainingBytes = 0L;

    public long progressPercentage = 0L;
    public String progressPercentageInFormat = "";

    public String thumbPath = "";
    public String thumbnailUrl = "";

    public String siteCookieString = "";
    public Map<String, String> extraWebHeaders = null;

    public long startTimeDate = 0L;
    public String startTimeDateInFormat = "";

    public long lastModifiedTimeDate = 0L;
    public String lastModifiedTimeDateInFormat = "";

    public long completedTimeDate = 0L;
    public String completedTimeDateInFormat = "";

    public long averageSpeed = 0L;
    public String averageSpeedInFormat = "--";

    public long maxSpeed = 0L;
    public String maxSpeedInFormat = "--";

    public long realtimeSpeed = 0L;
    public String realtimeSpeedInFormat = "--";

    public long timeSpentInMilliSec = 0L;
    public String timeSpentInFormat = "--:--";

    public long remainingTimeInSec = 0L;
    public String remainingTimeInFormat = "--:--";

    public int resumeSessionRetryCount = 0;
    public int totalTrackedConnectionRetries = 0;
    public int failedChunkCount = 0;
    public int activeThreadCount = 0;

    public long[] partStartingPoint = new long[18];
    public long[] partEndingPoint = new long[18];
    public long[] partChunkSizes = new long[18];
    public long[] partsDownloadedByte = new long[18];
    public int[] partProgressPercentage = new int[18];

    @Transient
    public VideoMetaInfo videoMetaInfo = null;
    public long videoInfoId = -1L;

    @Transient
    public VideoFormateInfo videoFormatInfo = null;
    public long videoFormatId = -1L;

    @Transient
    public RemoteFileInfo remoteFileInfo = null;
    public long remoteFileInfoId = -1L;

    public Long mediaPlaybackDuration = 0L;
    public String mediaPlaybackDurationInFormat = "--:--";

    public static final String ID_DOWNLOAD_KEY = "Download_ID_KEY";
    public static final String DOWNLOAD_THUMBNAIL_FILE_EXTENSION = ".download_thub.jpg";
    public static final String DOWNLOAD_COOKIE_FILE_EXTENSION = ".download_cookie.jpg";
    public static final String TEMP_DOWNLOAD_FILE_EXTENSION = ".tmp_download";

    public static final int DOWNLOAD_STATUS_PENDING = 0;
    public static final int DOWNLOAD_STATUS_DOWNLOADING = 1;
    public static final int DOWNLOAD_STATUS_PAUSED = 2;
    public static final int DOWNLOAD_STATUS_FAILED = 3;
    public static final int DOWNLOAD_STATUS_COMPLETED = 4;

    public DownloadInfo() {
        initStorageLocation();
    }

    private void initStorageLocation() {
        AppConfigs appConfig = AppConfigsRepo.getConfig();
        if (appConfig == null) return;

        if (appConfig.defaultDownloadLocationType == AppConfigs.PRIVATE_FOLDER) {
            File appsInternalDataFolder = FileStorageUtility.getInternalDataFolder();
            String internalDataFolderPath = appsInternalDataFolder.getAbsolutePath();
            String appDownloadDirPath = internalDataFolderPath + "/Downloaded_Files";
            fileDirectory = StringHelper.removeDuplicateSlashes(appDownloadDirPath);

        } else if (appConfig.defaultDownloadLocationType == AppConfigs.SYSTEM_GALLERY) {
            File publicDownloadFolder = FileStorageUtility.getPublicDownloadFolder();
            String publicDownloadFolderPath = publicDownloadFolder.getAbsolutePath();
            String fullDownloadPath = publicDownloadFolderPath + "/" + StringHelper.getText(R.string.title_default_app_folder);
            fileDirectory = StringHelper.removeDuplicateSlashes(fullDownloadPath);
        }
    }

    public String getFileCategoryName() {
        return getFileCategoryName(false);
    }

    public String getFileCategoryName(boolean omitBranding) {
        if (omitBranding) {
            if (FileStorageUtility.endsWithExtension(fileName, FileExtensions.IMAGE_EXTENSIONS)) {
                return StringHelper.getText(R.string.label_images);
            } else if (FileStorageUtility.endsWithExtension(fileName, FileExtensions.VIDEO_EXTENSIONS)) {
                return StringHelper.getText(R.string.label_videos);
            } else if (FileStorageUtility.endsWithExtension(fileName, FileExtensions.MUSIC_EXTENSIONS)) {
                return StringHelper.getText(R.string.label_sounds);
            } else if (FileStorageUtility.endsWithExtension(fileName, FileExtensions.DOCUMENT_EXTENSIONS)) {
                return StringHelper.getText(R.string.label_documents);
            } else if (FileStorageUtility.endsWithExtension(fileName, FileExtensions.PROGRAM_EXTENSIONS)) {
                return StringHelper.getText(R.string.label_programs);
            } else if (FileStorageUtility.endsWithExtension(fileName, FileExtensions.ARCHIVE_EXTENSIONS)) {
                return StringHelper.getText(R.string.label_archives);
            } else {
                return StringHelper.getText(R.string.title_tubeaio_others);
            }
        } else {
            if (FileStorageUtility.endsWithExtension(fileName, FileExtensions.IMAGE_EXTENSIONS)) {
                return StringHelper.getText(R.string.title_tubeaio_images);
            } else if (FileStorageUtility.endsWithExtension(fileName, FileExtensions.VIDEO_EXTENSIONS)) {
                return StringHelper.getText(R.string.title_tubeaio_videos);
            } else if (FileStorageUtility.endsWithExtension(fileName, FileExtensions.MUSIC_EXTENSIONS)) {
                return StringHelper.getText(R.string.title_tubeaio_sounds);
            } else if (FileStorageUtility.endsWithExtension(fileName, FileExtensions.DOCUMENT_EXTENSIONS)) {
                return StringHelper.getText(R.string.title_tubeaio_documents);
            } else if (FileStorageUtility.endsWithExtension(fileName, FileExtensions.PROGRAM_EXTENSIONS)) {
                return StringHelper.getText(R.string.title_tubeaio_programs);
            } else if (FileStorageUtility.endsWithExtension(fileName, FileExtensions.ARCHIVE_EXTENSIONS)) {
                return StringHelper.getText(R.string.title_tubeaio_archives);
            } else {
                return StringHelper.getText(R.string.title_tubeaio_others);
            }
        }
    }

    @Nullable
    public String getCookiesFilePathIfAvailable() {
        if (siteCookieString == null || siteCookieString.isEmpty()) return null;

        String cookieFileName = downloadId + DOWNLOAD_COOKIE_FILE_EXTENSION;
        File appsInternalDataFolder = FileStorageUtility.getInternalDataFolder();
        File cookieFile = new File(appsInternalDataFolder, cookieFileName);
        if (cookieFile.exists() && cookieFile.isFile()) {
            return cookieFile.getAbsolutePath();
        } else {
            File savedCookieFile = saveCookiesIfAvailable(false);
            if (savedCookieFile == null) {
                return null;
            } else {
                return savedCookieFile.getAbsolutePath();
            }
        }
    }

    @Nullable
    public File saveCookiesIfAvailable(boolean shouldOverride) {
        if (siteCookieString == null || siteCookieString.isEmpty()) {
            return null;
        }

        String cookieFileName = downloadId + DOWNLOAD_COOKIE_FILE_EXTENSION;
        File appsInternalDataFolder = FileStorageUtility.getInternalDataFolder();
        File cookieFile = new File(appsInternalDataFolder, cookieFileName);
        if (!shouldOverride && cookieFile.exists()) {
            return cookieFile;
        }

        String formattedCookies = DownloaderUtils.convertToNetscapeCookies(siteCookieString);
        boolean isSaved = saveStringToInternalStorage(cookieFile, formattedCookies);
        if (isSaved && cookieFile.exists()) {
            return cookieFile;
        } else {
            return null;
        }
    }

    public File getDestinationFile() {
        return new File(fileDirectory, fileName);
    }

    // ─── Speed & Format Metadata Helpers (Persisted via ytdlpExecutionCommand) ───

    @Transient
    private float cachedTargetSpeed = 0f;

    public void setSpeedDownloadConfig(float targetSpeed, boolean audioOnly, String speedMode, String qualityFormat) {
        setSpeedDownloadConfig(targetSpeed, audioOnly, speedMode, qualityFormat, false, false);
    }

    public void setSpeedDownloadConfig(float targetSpeed, boolean audioOnly, String speedMode, String qualityFormat,
                                       boolean voiceBoost, boolean includeSubtitles) {
        this.cachedTargetSpeed = targetSpeed;
        String safeMode = (speedMode == null || speedMode.isEmpty()) ? "TURBO_LOSSLESS_VIDEO" : speedMode;
        String safeQuality = (qualityFormat == null || qualityFormat.isEmpty()) ? "best" : qualityFormat;
        this.ytdlpExecutionCommand = String.format(
                java.util.Locale.US,
                "SPEED=%.2f;AUDIO=%b;MODE=%s;QUALITY=%s;BOOST=%b;SUBS=%b",
                targetSpeed, audioOnly, safeMode, safeQuality, voiceBoost, includeSubtitles
        );
    }

    public boolean isVoiceBoostEnabled() {
        if (ytdlpExecutionCommand != null && ytdlpExecutionCommand.contains("BOOST=")) {
            for (String part : ytdlpExecutionCommand.split(";")) {
                if (part.startsWith("BOOST=")) {
                    return Boolean.parseBoolean(part.substring(6));
                }
            }
        }
        return false;
    }

    public boolean isSubtitlesEnabled() {
        if (ytdlpExecutionCommand != null && ytdlpExecutionCommand.contains("SUBS=")) {
            for (String part : ytdlpExecutionCommand.split(";")) {
                if (part.startsWith("SUBS=")) {
                    return Boolean.parseBoolean(part.substring(5));
                }
            }
        }
        return false;
    }

    public float getTargetPlaybackSpeed() {
        if (cachedTargetSpeed > 0.1f) return cachedTargetSpeed;
        if (ytdlpExecutionCommand != null && ytdlpExecutionCommand.contains("SPEED=")) {
            try {
                for (String part : ytdlpExecutionCommand.split(";")) {
                    if (part.startsWith("SPEED=")) {
                        cachedTargetSpeed = Float.parseFloat(part.substring(6));
                        return cachedTargetSpeed;
                    }
                }
            } catch (Exception ignored) {}
        }
        return 1.0f;
    }

    public boolean isAudioOnlyDownload() {
        if (ytdlpExecutionCommand != null && ytdlpExecutionCommand.contains("AUDIO=")) {
            for (String part : ytdlpExecutionCommand.split(";")) {
                if (part.startsWith("AUDIO=")) {
                    return Boolean.parseBoolean(part.substring(6));
                }
            }
        }
        return fileName != null && (fileName.endsWith(".m4a") || fileName.endsWith(".mp3") || fileName.endsWith(".opus"));
    }

    public String getSpeedModeName() {
        if (ytdlpExecutionCommand != null && ytdlpExecutionCommand.contains("MODE=")) {
            for (String part : ytdlpExecutionCommand.split(";")) {
                if (part.startsWith("MODE=")) {
                    return part.substring(5);
                }
            }
        }
        return isAudioOnlyDownload() ? "AUDIO_ONLY" : "TURBO_LOSSLESS_VIDEO";
    }

    public String getRequestedQuality() {
        if (ytdlpExecutionCommand != null && ytdlpExecutionCommand.contains("QUALITY=")) {
            for (String part : ytdlpExecutionCommand.split(";")) {
                if (part.startsWith("QUALITY=")) {
                    return part.substring(8);
                }
            }
        }
        return "best";
    }

    public String getFormattedSpeedBadge() {
        float speed = getTargetPlaybackSpeed();
        if (speed == (long) speed) {
            return String.format(java.util.Locale.US, "%dx", (long) speed);
        }
        return String.format(java.util.Locale.US, "%.2fx", speed).replaceAll("0+x$", "x").replaceAll("\\.x$", "x");
    }
}