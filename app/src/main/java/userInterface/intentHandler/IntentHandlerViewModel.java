package userInterface.intentHandler;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.Collections;
import java.util.List;

import coreUtils.library.media.MediaSpeedProcessor.SpeedMode;
import coreUtils.library.process.ThreadTask;
import dataRepo.downloads.DownloadInfo;
import sysModules.downloadSys.YtDlpSpeedDownloadEngine;
import sysModules.downloadSys.YtDlpSpeedDownloadEngine.ExtractedMediaDetails;
import sysModules.downloadSys.YtDlpSpeedDownloadEngine.ExtractedPlaylistDetails;

/**
 * ViewModel for the Quick Share Speed-Downloader Bottom Sheet (IntentHandlerActivity).
 * Supports both single media links and full playlists.
 */
public class IntentHandlerViewModel extends ViewModel {

    private final MutableLiveData<String> currentUrl = new MutableLiveData<>("");
    private final MutableLiveData<ExtractedMediaDetails> mediaDetails = new MutableLiveData<>();
    private final MutableLiveData<ExtractedPlaylistDetails> playlistDetails = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoadingMetadata = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>("");

    private final MutableLiveData<Float> selectedSpeed = new MutableLiveData<>(1.5f);
    private final MutableLiveData<Boolean> isAudioOnly = new MutableLiveData<>(false);
    private final MutableLiveData<String> selectedQuality = new MutableLiveData<>("720p");
    private final MutableLiveData<SpeedMode> selectedSpeedMode = new MutableLiveData<>(SpeedMode.TURBO_LOSSLESS_VIDEO);

    private ThreadTask<ExtractedMediaDetails, Void> activeFetchTask;
    private ThreadTask<ExtractedPlaylistDetails, Void> activePlaylistTask;

    public LiveData<String> getCurrentUrl() { return currentUrl; }
    public LiveData<ExtractedMediaDetails> getMediaDetails() { return mediaDetails; }
    public LiveData<ExtractedPlaylistDetails> getPlaylistDetails() { return playlistDetails; }
    public LiveData<Boolean> getIsLoadingMetadata() { return isLoadingMetadata; }
    public LiveData<String> getErrorMessage() { return errorMessage; }
    public LiveData<Float> getSelectedSpeed() { return selectedSpeed; }
    public LiveData<Boolean> getIsAudioOnly() { return isAudioOnly; }
    public LiveData<String> getSelectedQuality() { return selectedQuality; }
    public LiveData<SpeedMode> getSelectedSpeedMode() { return selectedSpeedMode; }

    public void setSelectedSpeed(float speed) {
        float clamped = Math.round(Math.max(0.25f, Math.min(4.0f, speed)) * 100f) / 100f;
        selectedSpeed.setValue(clamped);
    }

    public void setFormatSelection(boolean audioOnly, @NonNull String qualityOrCodec) {
        isAudioOnly.setValue(audioOnly);
        selectedQuality.setValue(qualityOrCodec);
        if (audioOnly) {
            selectedSpeedMode.setValue(SpeedMode.AUDIO_ONLY);
        } else if (selectedSpeedMode.getValue() == SpeedMode.AUDIO_ONLY) {
            selectedSpeedMode.setValue(SpeedMode.TURBO_LOSSLESS_VIDEO);
        }
    }

    public void setSelectedSpeedMode(@NonNull SpeedMode mode) {
        selectedSpeedMode.setValue(mode);
    }

    public void loadMetadataForUrl(@NonNull LifecycleOwner owner, @NonNull String url) {
        currentUrl.setValue(url);
        errorMessage.setValue("");
        isLoadingMetadata.setValue(true);

        if (activeFetchTask != null) activeFetchTask.cancel();
        if (activePlaylistTask != null) activePlaylistTask.cancel();

        if (IntentUrlParser.isPlaylistUrl(url)) {
            mediaDetails.setValue(null);
            activePlaylistTask = YtDlpSpeedDownloadEngine.extractPlaylistAsync(
                    owner,
                    url,
                    new YtDlpSpeedDownloadEngine.PlaylistCallback() {
                        @Override
                        public void onPlaylistReady(@NonNull ExtractedPlaylistDetails playlist) {
                            isLoadingMetadata.setValue(false);
                            playlistDetails.setValue(playlist);
                        }

                        @Override
                        public void onPlaylistError(@NonNull String err) {
                            isLoadingMetadata.setValue(false);
                            errorMessage.setValue(err);
                        }
                    }
            );
            return;
        }

        playlistDetails.setValue(null);
        activeFetchTask = YtDlpSpeedDownloadEngine.extractMediaMetadataAsync(
                owner,
                url,
                new YtDlpSpeedDownloadEngine.MetadataCallback() {
                    @Override
                    public void onMetadataReady(@NonNull ExtractedMediaDetails details) {
                        isLoadingMetadata.setValue(false);
                        mediaDetails.setValue(details);
                    }

                    @Override
                    public void onMetadataError(@NonNull String err) {
                        isLoadingMetadata.setValue(false);
                        errorMessage.setValue(err);
                        mediaDetails.setValue(new ExtractedMediaDetails(
                                url,
                                IntentUrlParser.detectPlatformName(url) + "_" + System.currentTimeMillis(),
                                IntentUrlParser.detectPlatformName(url),
                                "",
                                0L
                        ));
                    }
                }
        );
    }

    @NonNull
    public List<DownloadInfo> startConfiguredPlaylistDownload(@Nullable YtDlpSpeedDownloadEngine.EngineDownloadListener listener) {
        ExtractedPlaylistDetails playlist = playlistDetails.getValue();
        if (playlist == null || playlist.items.isEmpty()) return Collections.emptyList();

        float speed = selectedSpeed.getValue() != null ? selectedSpeed.getValue() : 1.5f;
        boolean audioOnly = Boolean.TRUE.equals(isAudioOnly.getValue());
        String quality = selectedQuality.getValue() != null ? selectedQuality.getValue() : "720p";
        SpeedMode mode = selectedSpeedMode.getValue() != null
                ? selectedSpeedMode.getValue()
                : (audioOnly ? SpeedMode.AUDIO_ONLY : SpeedMode.TURBO_LOSSLESS_VIDEO);

        return YtDlpSpeedDownloadEngine.enqueuePlaylistDownloads(
                playlist.playlistTitle, playlist.items, speed, audioOnly, mode, quality, false, false, listener
        );
    }

    @Nullable
    public DownloadInfo startConfiguredDownload(@Nullable YtDlpSpeedDownloadEngine.EngineDownloadListener listener) {
        String url = currentUrl.getValue();
        if (url == null || url.trim().isEmpty()) return null;

        ExtractedMediaDetails details = mediaDetails.getValue();
        String title = details != null ? details.title : ("Media_" + System.currentTimeMillis());
        String thumb = details != null ? details.thumbnailUrl : "";
        long origDuration = details != null ? details.durationSeconds : 0L;

        float speed = selectedSpeed.getValue() != null ? selectedSpeed.getValue() : 1.5f;
        boolean audioOnly = Boolean.TRUE.equals(isAudioOnly.getValue());
        String quality = selectedQuality.getValue() != null ? selectedQuality.getValue() : "720p";
        SpeedMode mode = selectedSpeedMode.getValue() != null
                ? selectedSpeedMode.getValue()
                : (audioOnly ? SpeedMode.AUDIO_ONLY : SpeedMode.TURBO_LOSSLESS_VIDEO);

        return YtDlpSpeedDownloadEngine.enqueueAndStartDownload(
                url, title, thumb, origDuration, speed, audioOnly, mode, quality, listener
        );
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (activeFetchTask != null) activeFetchTask.cancel();
        if (activePlaylistTask != null) activePlaylistTask.cancel();
    }
}
