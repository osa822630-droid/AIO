package userInterface.fragmentsUIs.homepage;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.nextgen.R;

import java.util.Collections;
import java.util.List;

import coreUtils.library.media.MediaSpeedProcessor.SpeedMode;
import coreUtils.library.process.SettingsPrefs;
import coreUtils.library.process.ThreadTask;
import coreUtils.library.strings.StringHelper;
import dataRepo.downloads.DownloadInfo;
import sysModules.downloadSys.YtDlpSpeedDownloadEngine;
import sysModules.downloadSys.YtDlpSpeedDownloadEngine.ExtractedMediaDetails;
import sysModules.downloadSys.YtDlpSpeedDownloadEngine.ExtractedPlaylistDetails;
import sysModules.downloadSys.YtDlpSpeedDownloadEngine.ExtractedPlaylistItem;
import userInterface.intentHandler.IntentUrlParser;

/**
 * ViewModel for HomepageFragment managing URL analysis (single media & playlists),
 * speed presets, format/quality selection, and dispatching speed-modified downloads.
 */
public class HomepageViewModel extends ViewModel {

    private final MutableLiveData<ExtractedMediaDetails> currentDetails = new MutableLiveData<>();
    private final MutableLiveData<ExtractedPlaylistDetails> currentPlaylist = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isAnalyzingUrl = new MutableLiveData<>(false);
    private final MutableLiveData<String> statusMessage = new MutableLiveData<>("");

    private final MutableLiveData<Float> targetSpeed = new MutableLiveData<>(SettingsPrefs.getDefaultSpeed());
    private final MutableLiveData<Boolean> audioOnly = new MutableLiveData<>(false);
    private final MutableLiveData<String> qualityOrCodec = new MutableLiveData<>(SettingsPrefs.getDefaultQuality());
    private final MutableLiveData<SpeedMode> speedMode = new MutableLiveData<>(SettingsPrefs.isDefaultTurboMode() ? SpeedMode.TURBO_LOSSLESS_VIDEO : SpeedMode.COMPATIBILITY_REENCODE_VIDEO);
    private final MutableLiveData<Boolean> voiceBoost = new MutableLiveData<>(SettingsPrefs.isDefaultVoiceBoost());
    private final MutableLiveData<Boolean> syncSubtitles = new MutableLiveData<>(SettingsPrefs.isDefaultSyncSubtitles());

    private ThreadTask<ExtractedMediaDetails, Void> extractTask;
    private ThreadTask<ExtractedPlaylistDetails, Void> extractPlaylistTask;

    public LiveData<ExtractedMediaDetails> getCurrentDetails() { return currentDetails; }
    public LiveData<ExtractedPlaylistDetails> getCurrentPlaylist() { return currentPlaylist; }
    public LiveData<Boolean> getIsAnalyzingUrl() { return isAnalyzingUrl; }
    public LiveData<String> getStatusMessage() { return statusMessage; }
    public LiveData<Float> getTargetSpeed() { return targetSpeed; }
    public LiveData<Boolean> getAudioOnly() { return audioOnly; }
    public LiveData<String> getQualityOrCodec() { return qualityOrCodec; }
    public LiveData<SpeedMode> getSpeedMode() { return speedMode; }
    public LiveData<Boolean> getVoiceBoost() { return voiceBoost; }
    public LiveData<Boolean> getSyncSubtitles() { return syncSubtitles; }

    public void toggleVoiceBoost() {
        voiceBoost.setValue(!Boolean.TRUE.equals(voiceBoost.getValue()));
    }

    public void toggleSyncSubtitles() {
        syncSubtitles.setValue(!Boolean.TRUE.equals(syncSubtitles.getValue()));
    }

    public void setTargetSpeed(float speed) {
        float rounded = Math.round(Math.max(0.25f, Math.min(4.0f, speed)) * 100f) / 100f;
        targetSpeed.setValue(rounded);
    }

    public void setFormat(boolean isAudio, @NonNull String qualityTag) {
        audioOnly.setValue(isAudio);
        qualityOrCodec.setValue(qualityTag);
        if (isAudio) {
            speedMode.setValue(SpeedMode.AUDIO_ONLY);
        } else if (speedMode.getValue() == SpeedMode.AUDIO_ONLY) {
            speedMode.setValue(SpeedMode.TURBO_LOSSLESS_VIDEO);
        }
    }

    public void setVideoSpeedMode(@NonNull SpeedMode mode) {
        speedMode.setValue(mode);
    }

    public void clearPreview() {
        currentDetails.setValue(null);
        currentPlaylist.setValue(null);
    }

    public void analyzeUrl(@NonNull LifecycleOwner owner, @NonNull String rawInput) {
        String cleanUrl = IntentUrlParser.extractFirstUrlFromText(rawInput);
        if (cleanUrl == null || cleanUrl.isEmpty()) {
            statusMessage.setValue(StringHelper.getText(R.string.err_invalid_url));
            return;
        }

        if (extractTask != null) extractTask.cancel();
        if (extractPlaylistTask != null) extractPlaylistTask.cancel();
        isAnalyzingUrl.setValue(true);

        if (IntentUrlParser.isPlaylistUrl(cleanUrl)) {
            currentDetails.setValue(null);
            extractPlaylistTask = YtDlpSpeedDownloadEngine.extractPlaylistAsync(
                    owner,
                    cleanUrl,
                    new YtDlpSpeedDownloadEngine.PlaylistCallback() {
                        @Override
                        public void onPlaylistReady(@NonNull ExtractedPlaylistDetails playlist) {
                            isAnalyzingUrl.setValue(false);
                            currentPlaylist.setValue(playlist);
                        }

                        @Override
                        public void onPlaylistError(@NonNull String errorMessage) {
                            isAnalyzingUrl.setValue(false);
                            statusMessage.setValue(errorMessage);
                        }
                    }
            );
            return;
        }

        currentPlaylist.setValue(null);
        extractTask = YtDlpSpeedDownloadEngine.extractMediaMetadataAsync(
                owner,
                cleanUrl,
                new YtDlpSpeedDownloadEngine.MetadataCallback() {
                    @Override
                    public void onMetadataReady(@NonNull ExtractedMediaDetails details) {
                        isAnalyzingUrl.setValue(false);
                        currentDetails.setValue(details);
                    }

                    @Override
                    public void onMetadataError(@NonNull String errorMessage) {
                        isAnalyzingUrl.setValue(false);
                        statusMessage.setValue(errorMessage);
                        currentDetails.setValue(new ExtractedMediaDetails(
                                cleanUrl,
                                IntentUrlParser.detectPlatformName(cleanUrl) + "_" + System.currentTimeMillis(),
                                IntentUrlParser.detectPlatformName(cleanUrl),
                                "",
                                0L
                        ));
                    }
                }
        );
    }

    @Nullable
    public DownloadInfo startDownload(
            @NonNull String rawUrlInput,
            @Nullable YtDlpSpeedDownloadEngine.EngineDownloadListener listener) {

        String cleanUrl = IntentUrlParser.extractFirstUrlFromText(rawUrlInput);
        if (cleanUrl == null || cleanUrl.isEmpty()) {
            statusMessage.setValue(StringHelper.getText(R.string.err_paste_url_first));
            return null;
        }

        ExtractedMediaDetails details = currentDetails.getValue();
        boolean matchesCurrent = details != null && cleanUrl.equals(details.url);
        String title = matchesCurrent ? details.title : (IntentUrlParser.detectPlatformName(cleanUrl) + "_" + System.currentTimeMillis());
        String thumb = matchesCurrent ? details.thumbnailUrl : "";
        long origSeconds = matchesCurrent ? details.durationSeconds : 0L;

        float speed = targetSpeed.getValue() != null ? targetSpeed.getValue() : 1.5f;
        boolean isAudio = Boolean.TRUE.equals(audioOnly.getValue());
        String qTag = qualityOrCodec.getValue() != null ? qualityOrCodec.getValue() : "720p";
        SpeedMode mode = speedMode.getValue() != null
                ? speedMode.getValue()
                : (isAudio ? SpeedMode.AUDIO_ONLY : SpeedMode.TURBO_LOSSLESS_VIDEO);

        boolean boost = Boolean.TRUE.equals(voiceBoost.getValue());
        boolean subs = Boolean.TRUE.equals(syncSubtitles.getValue());

        return YtDlpSpeedDownloadEngine.enqueueAndStartDownload(
                cleanUrl, title, thumb, origSeconds, speed, isAudio, mode, qTag, boost, subs, null, listener
        );
    }

    @NonNull
    public List<DownloadInfo> startPlaylistDownload(
            @NonNull List<ExtractedPlaylistItem> selectedItems,
            @Nullable YtDlpSpeedDownloadEngine.EngineDownloadListener listener) {
        if (selectedItems.isEmpty()) {
            statusMessage.setValue(StringHelper.getText(R.string.err_playlist_empty));
            return Collections.emptyList();
        }
        float speed = targetSpeed.getValue() != null ? targetSpeed.getValue() : 1.5f;
        boolean isAudio = Boolean.TRUE.equals(audioOnly.getValue());
        String qTag = qualityOrCodec.getValue() != null ? qualityOrCodec.getValue() : "720p";
        SpeedMode mode = speedMode.getValue() != null
                ? speedMode.getValue()
                : (isAudio ? SpeedMode.AUDIO_ONLY : SpeedMode.TURBO_LOSSLESS_VIDEO);
        boolean boost = Boolean.TRUE.equals(voiceBoost.getValue());
        boolean subs = Boolean.TRUE.equals(syncSubtitles.getValue());
        ExtractedPlaylistDetails pl = currentPlaylist.getValue();
        String plTitle = pl != null ? pl.playlistTitle : "Playlist";

        return YtDlpSpeedDownloadEngine.enqueuePlaylistDownloads(
                plTitle, selectedItems, speed, isAudio, mode, qTag, boost, subs, listener
        );
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (extractTask != null) extractTask.cancel();
        if (extractPlaylistTask != null) extractPlaylistTask.cancel();
    }
}
