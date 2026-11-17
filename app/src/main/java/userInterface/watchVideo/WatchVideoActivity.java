package userInterface.watchVideo;

import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.MimeTypes;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.nextgen.R;
import com.nextgen.databinding.ActivityWatchVideo1Binding;

import java.io.File;
import java.util.Collections;
import java.util.Locale;

import coreUtils.base.BaseActivity;
import coreUtils.library.process.ShareHelper;
import coreUtils.library.process.TimeFormats;
import coreUtils.library.views.StylizedToastView;

/**
 * Built-in Media Player Activity powered by ExoPlayer.
 * Supports playing local speed-modified files (with auto-loaded synced .srt subtitles)
 * as well as live-streaming URLs at a requested preview playback speed.
 */
public class WatchVideoActivity extends BaseActivity<ActivityWatchVideo1Binding> {

    public static final String EXTRA_LOCAL_FILE_PATH = "EXTRA_LOCAL_FILE_PATH";
    public static final String EXTRA_STREAM_URL = "EXTRA_STREAM_URL";
    public static final String EXTRA_INITIAL_SPEED = "EXTRA_INITIAL_SPEED";
    public static final String EXTRA_MEDIA_TITLE = "EXTRA_MEDIA_TITLE";

    private static final float[] PLAYBACK_SPEEDS = {1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 0.75f};
    private int currentSpeedIndex = 0;

    private SimpleExoPlayer exoPlayer;
    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private boolean isFullscreen = false;
    private File currentFile;

    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            if (exoPlayer != null && binding != null) {
                long pos = Math.max(0L, exoPlayer.getCurrentPosition());
                long dur = Math.max(1L, exoPlayer.getDuration());
                float fraction = Math.max(0f, Math.min(1f, (float) pos / (float) dur));
                binding.playerTop.layoutPlaybackControls.seekPlaybackProgress.setProgress(fraction);
                String posStr = TimeFormats.formatDurationFromMillisecond(pos);
                String durStr = TimeFormats.formatDurationFromMillisecond(dur);
                binding.playerTop.layoutPlaybackControls.tvPlaybackTime.setText(posStr + " / " + durStr);
                progressHandler.postDelayed(this, 500L);
            }
        }
    };

    @Override
    protected boolean shouldLockOrientation() {
        return false;
    }

    @Override
    protected ActivityWatchVideo1Binding inflateBinding(LayoutInflater inflater) {
        return ActivityWatchVideo1Binding.inflate(inflater);
    }

    @Override
    protected void onLoadedLayout() {
        String filePath = getIntent().getStringExtra(EXTRA_LOCAL_FILE_PATH);
        String streamUrl = getIntent().getStringExtra(EXTRA_STREAM_URL);
        String title = getIntent().getStringExtra(EXTRA_MEDIA_TITLE);
        float initialSpeed = getIntent().getFloatExtra(EXTRA_INITIAL_SPEED, 1.0f);

        Uri playUri;
        File subtitleFile = null;

        if (streamUrl != null && !streamUrl.trim().isEmpty()) {
            playUri = Uri.parse(streamUrl.trim());
            binding.videoDetails.tvVideoTitle.setText(title != null && !title.isEmpty() ? title : streamUrl);
        } else if (filePath != null && !filePath.isEmpty()) {
            currentFile = new File(filePath);
            if (!currentFile.exists()) {
                StylizedToastView.showError(this, getString(R.string.toast_file_not_found_path_fmt, filePath));
                finish();
                return;
            }
            playUri = Uri.fromFile(currentFile);
            subtitleFile = findMatchingSubtitle(currentFile);
            binding.videoDetails.tvVideoTitle.setText(title != null && !title.isEmpty() ? title : currentFile.getName());
        } else {
            StylizedToastView.showError(this, getString(R.string.toast_no_file_to_play));
            finish();
            return;
        }

        binding.videoDetails.tvChannelName.setText(getString(R.string.label_local_file_brand));
        binding.playerTop.layoutQualityBadge.tvLiveIndicator.setVisibility(View.GONE);
        String spdLabel = String.format(Locale.US, "%.2fx", initialSpeed).replaceAll("0+x$", "x").replaceAll("\\.x$", "x");
        binding.playerTop.layoutQualityBadge.tvQuality.setText(spdLabel);
        binding.relatedVideos.getRoot().setVisibility(View.GONE);

        setupPlayer(playUri, subtitleFile, initialSpeed);
        setupControls();
    }

    @Nullable
    private File findMatchingSubtitle(@Nullable File videoFile) {
        if (videoFile == null || videoFile.getParentFile() == null) return null;
        String name = videoFile.getName();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        File exact = new File(videoFile.getParentFile(), base + ".srt");
        if (exact.exists()) return exact;
        File[] list = videoFile.getParentFile().listFiles((d, n) -> n.startsWith(base) && n.endsWith(".srt"));
        return (list != null && list.length > 0) ? list[0] : null;
    }

    private void setupPlayer(Uri playUri, @Nullable File subtitleFile, float initialSpeed) {
        exoPlayer = new SimpleExoPlayer.Builder(this).build();
        SurfaceView surfaceView = new SurfaceView(this);
        surfaceView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        binding.playerTop.playerContainer.removeAllViews();
        binding.playerTop.playerContainer.addView(surfaceView);
        exoPlayer.setVideoSurfaceView(surfaceView);

        exoPlayer.addListener(new Player.Listener() {
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                if (binding == null) return;
                int icon = isPlaying ? R.drawable.ic_playback_pause : R.drawable.ic_playback_play;
                binding.playerTop.layoutPlaybackControls.btnPlaybackToggle.setImageResource(icon);
                binding.playerTop.layoutQuickControlsOverlay.btnTogglePlayback.setImageResource(
                        isPlaying ? R.drawable.ic_pause_solid : R.drawable.ic_play_arrow
                );
            }
        });

        MediaItem.Builder itemBuilder = new MediaItem.Builder().setUri(playUri);
        if (subtitleFile != null && subtitleFile.exists()) {
            MediaItem.SubtitleConfiguration subConfig = new MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(subtitleFile))
                    .setMimeType(MimeTypes.APPLICATION_SUBRIP)
                    .setSelectionFlags(com.google.android.exoplayer2.C.SELECTION_FLAG_DEFAULT)
                    .build();
            itemBuilder.setSubtitleConfigurations(Collections.singletonList(subConfig));
        }

        exoPlayer.setMediaItem(itemBuilder.build());
        if (Math.abs(initialSpeed - 1.0f) >= 0.01f) {
            exoPlayer.setPlaybackParameters(new PlaybackParameters(initialSpeed, 1.0f));
        }
        exoPlayer.prepare();
        exoPlayer.setPlayWhenReady(true);
        progressHandler.post(progressRunnable);
    }

    private void setupControls() {
        View.OnClickListener playPauseClick = v -> {
            if (exoPlayer == null) return;
            if (exoPlayer.isPlaying()) {
                exoPlayer.pause();
            } else {
                exoPlayer.play();
            }
        };

        binding.playerTop.layoutPlaybackControls.seekPlaybackProgress.setOnProgressChangedListener(progress -> {
            if (exoPlayer != null && exoPlayer.getDuration() > 0) {
                long targetMs = (long) (progress * exoPlayer.getDuration());
                exoPlayer.seekTo(targetMs);
            }
        });
        binding.playerTop.layoutPlaybackControls.btnPlaybackToggle.setOnClickListener(playPauseClick);
        binding.playerTop.layoutQuickControlsOverlay.btnTogglePlayback.setOnClickListener(playPauseClick);

        binding.playerTop.layoutQuickControlsOverlay.btnPlayPrevious.setOnClickListener(v -> {
            if (exoPlayer != null) {
                exoPlayer.seekTo(Math.max(0L, exoPlayer.getCurrentPosition() - 10_000L));
            }
        });

        binding.playerTop.layoutQuickControlsOverlay.btnPlayNext.setOnClickListener(v -> {
            if (exoPlayer != null) {
                exoPlayer.seekTo(Math.min(exoPlayer.getDuration(), exoPlayer.getCurrentPosition() + 10_000L));
            }
        });

        binding.playerTop.layoutPlaybackControls.btnVolumeToggle.setOnClickListener(v -> {
            if (exoPlayer == null) return;
            boolean muted = exoPlayer.getVolume() == 0f;
            exoPlayer.setVolume(muted ? 1f : 0f);
            binding.playerTop.layoutPlaybackControls.btnVolumeToggle.setImageResource(
                    muted ? R.drawable.ic_volume_up : R.drawable.ic_volume_off
            );
        });

        binding.playerTop.layoutPlaybackControls.btnFullscreenToggle.setOnClickListener(v -> {
            isFullscreen = !isFullscreen;
            setRequestedOrientation(isFullscreen
                    ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            binding.videoDetails.getRoot().setVisibility(isFullscreen ? View.GONE : View.VISIBLE);
        });

        binding.playerTop.layoutQualityBadge.tvQuality.setOnClickListener(v -> {
            if (exoPlayer == null) return;
            currentSpeedIndex = (currentSpeedIndex + 1) % PLAYBACK_SPEEDS.length;
            float spd = PLAYBACK_SPEEDS[currentSpeedIndex];
            exoPlayer.setPlaybackParameters(new PlaybackParameters(spd, 1.0f));
            String label = String.format(Locale.US, "%.2fx", spd).replaceAll("0+x$", "x").replaceAll("\\.x$", "x");
            binding.playerTop.layoutQualityBadge.tvQuality.setText(label);
        });

        binding.videoDetails.shareBtnContainer.btnWhatsapp.setOnClickListener(v -> {
            if (currentFile != null && currentFile.exists()) {
                ShareHelper.shareMediaFile(this, currentFile, currentFile.getName());
            }
        });

        binding.videoDetails.shareBtnContainer.btnDownload.setOnClickListener(v -> {
            if (currentFile != null && currentFile.exists()) {
                ShareHelper.openFile(this, currentFile);
            }
        });
    }

    @Override
    protected void onDestroy() {
        progressHandler.removeCallbacks(progressRunnable);
        if (exoPlayer != null) {
            exoPlayer.release();
            exoPlayer = null;
        }
        super.onDestroy();
    }
}
