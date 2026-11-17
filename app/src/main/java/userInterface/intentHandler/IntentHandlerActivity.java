package userInterface.intentHandler;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.nextgen.R;
import com.nextgen.databinding.ActivityIntentHandler1Binding;

import java.util.List;
import java.util.Locale;

import coreUtils.base.BaseActivity;
import coreUtils.library.media.MediaSpeedProcessor;
import coreUtils.library.views.StylizedToastView;
import dataRepo.downloads.DownloadInfo;
import sysModules.downloadSys.YtDlpSpeedDownloadEngine.ExtractedMediaDetails;
import sysModules.downloadSys.YtDlpSpeedDownloadEngine.ExtractedPlaylistDetails;
import userInterface.mainScreen.MainActivity;

/**
 * Overlay Bottom-Sheet Activity that intercepts shared links (ACTION_SEND / ACTION_VIEW)
 * and lets the user immediately download single media or full playlists at a custom baked-in speed.
 */
public class IntentHandlerActivity extends BaseActivity<ActivityIntentHandler1Binding> {

    private IntentHandlerViewModel viewModel;

    @Override
    protected boolean shouldLockOrientation() {
        return true;
    }

    @Override
    protected ActivityIntentHandler1Binding inflateBinding(LayoutInflater inflater) {
        return ActivityIntentHandler1Binding.inflate(inflater);
    }

    @Override
    protected void onLoadedLayout() {
        viewModel = new ViewModelProvider(this).get(IntentHandlerViewModel.class);
        setupControls();
        observeViewModel();
        handleIncomingIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingIntent(intent);
    }

    private void handleIncomingIntent(Intent intent) {
        String extractedUrl = IntentUrlParser.extractFirstUrl(intent);
        if (extractedUrl == null || extractedUrl.isEmpty()) {
            StylizedToastView.showError(this, getString(R.string.toast_invalid_shared_url));
            finish();
            return;
        }
        binding.tvPlatformBadge.setText(IntentUrlParser.detectPlatformName(extractedUrl));
        viewModel.loadMetadataForUrl(this, extractedUrl);
    }

    private void setupControls() {
        binding.rootOverlay.setOnClickListener(v -> finish());
        binding.bottomSheetCard.setOnClickListener(v -> { /* consume click */ });
        binding.btnCloseSheet.setOnClickListener(v -> finish());

        binding.btnSpeed100.setOnClickListener(v -> setSpeedPreset(1.0f));
        binding.btnSpeed125.setOnClickListener(v -> setSpeedPreset(1.25f));
        binding.btnSpeed150.setOnClickListener(v -> setSpeedPreset(1.5f));
        binding.btnSpeed175.setOnClickListener(v -> setSpeedPreset(1.75f));
        binding.btnSpeed200.setOnClickListener(v -> setSpeedPreset(2.0f));
        binding.btnSpeed250.setOnClickListener(v -> setSpeedPreset(2.5f));

        binding.seekCustomSpeed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    float speed = 0.50f + (progress * 0.05f);
                    viewModel.setSelectedSpeed(speed);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        binding.btnFormatVideo.setOnClickListener(v -> {
            viewModel.setFormatSelection(false, "720p");
            highlightFormatButtons(false, "720p");
        });
        binding.btnFormatAudioM4a.setOnClickListener(v -> {
            viewModel.setFormatSelection(true, "m4a");
            highlightFormatButtons(true, "m4a");
        });
        binding.btnFormatAudioMp3.setOnClickListener(v -> {
            viewModel.setFormatSelection(true, "mp3");
            highlightFormatButtons(true, "mp3");
        });

        binding.btnQuality1080.setOnClickListener(v -> selectVideoQuality("1080p"));
        binding.btnQuality720.setOnClickListener(v -> selectVideoQuality("720p"));
        binding.btnQuality480.setOnClickListener(v -> selectVideoQuality("480p"));
        binding.btnQuality360.setOnClickListener(v -> selectVideoQuality("360p"));

        binding.btnStartSpeedDownload.setOnClickListener(v -> {
            buttonVibrate();
            ExtractedPlaylistDetails playlist = viewModel.getPlaylistDetails().getValue();
            if (playlist != null && !playlist.items.isEmpty()) {
                List<DownloadInfo> queued = viewModel.startConfiguredPlaylistDownload(null);
                if (!queued.isEmpty()) {
                    StylizedToastView.showSuccess(
                            this,
                            getString(R.string.msg_playlist_started_fmt, queued.size(), queued.get(0).getFormattedSpeedBadge())
                    );
                    openDownloadsAndFinish();
                }
                return;
            }

            DownloadInfo started = viewModel.startConfiguredDownload(null);
            if (started != null) {
                StylizedToastView.showSuccess(
                        this,
                        getString(R.string.toast_download_started_badge, started.getFormattedSpeedBadge()) + " • " + started.fileName
                );
                openDownloadsAndFinish();
            }
        });
    }

    private void openDownloadsAndFinish() {
        Intent mainIntent = new Intent(this, MainActivity.class);
        mainIntent.putExtra(MainActivity.EXTRA_OPEN_TAB, "DOWNLOADS");
        mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(mainIntent);
        finish();
    }

    private void setSpeedPreset(float speed) {
        buttonVibrate();
        viewModel.setSelectedSpeed(speed);
        int progress = Math.round((speed - 0.50f) / 0.05f);
        binding.seekCustomSpeed.setProgress(Math.max(0, Math.min(50, progress)));
    }

    private void selectVideoQuality(@NonNull String q) {
        buttonVibrate();
        viewModel.setFormatSelection(false, q);
        highlightQualityButtons(q);
    }

    private void highlightFormatButtons(boolean audioOnly, @NonNull String codecOrQuality) {
        setChipHighlight(binding.btnFormatVideo, !audioOnly);
        setChipHighlight(binding.btnFormatAudioM4a, audioOnly && "m4a".equals(codecOrQuality));
        setChipHighlight(binding.btnFormatAudioMp3, audioOnly && "mp3".equals(codecOrQuality));
        binding.layoutQualityRow.setVisibility(audioOnly ? View.GONE : View.VISIBLE);
    }

    private void highlightQualityButtons(@NonNull String q) {
        setChipHighlight(binding.btnQuality1080, "1080p".equals(q));
        setChipHighlight(binding.btnQuality720, "720p".equals(q));
        setChipHighlight(binding.btnQuality480, "480p".equals(q));
        setChipHighlight(binding.btnQuality360, "360p".equals(q));
    }

    private void highlightSpeedButtons(float speed) {
        setChipHighlight(binding.btnSpeed100, Math.abs(speed - 1.0f) < 0.02f);
        setChipHighlight(binding.btnSpeed125, Math.abs(speed - 1.25f) < 0.02f);
        setChipHighlight(binding.btnSpeed150, Math.abs(speed - 1.50f) < 0.02f);
        setChipHighlight(binding.btnSpeed175, Math.abs(speed - 1.75f) < 0.02f);
        setChipHighlight(binding.btnSpeed200, Math.abs(speed - 2.00f) < 0.02f);
        setChipHighlight(binding.btnSpeed250, Math.abs(speed - 2.50f) < 0.02f);
    }

    private void setChipHighlight(TextView view, boolean active) {
        view.setBackgroundResource(active ? R.drawable.bg_button_accent : R.drawable.bg_button_negative);
        view.setTextColor(getResources().getColor(
                active ? R.color.style_color_text_inverse : R.color.style_color_text_primary,
                getTheme()));
    }

    private void observeViewModel() {
        viewModel.getIsLoadingMetadata().observe(this, loading ->
                binding.pbMetadataLoading.setVisibility(Boolean.TRUE.equals(loading) ? View.VISIBLE : View.GONE));

        viewModel.getMediaDetails().observe(this, details -> {
            if (details == null) return;
            binding.tvMediaTitle.setText(details.title);
            if (!details.thumbnailUrl.isEmpty()) {
                Glide.with(this)
                        .load(details.thumbnailUrl)
                        .placeholder(R.drawable.bg_media_placeholder)
                        .into(binding.imgMediaThumb);
            }
            updateSpeedSummaryUI();
        });

        viewModel.getPlaylistDetails().observe(this, playlist -> {
            if (playlist == null) return;
            binding.tvMediaTitle.setText(playlist.playlistTitle);
            if (!playlist.items.isEmpty() && !playlist.items.get(0).thumbnailUrl.isEmpty()) {
                Glide.with(this)
                        .load(playlist.items.get(0).thumbnailUrl)
                        .placeholder(R.drawable.bg_media_placeholder)
                        .into(binding.imgMediaThumb);
            }
            updateSpeedSummaryUI();
        });

        viewModel.getSelectedSpeed().observe(this, speed -> {
            if (speed == null) return;
            String speedStr = formatSpeedLabel(speed);
            binding.tvSelectedSpeedValue.setText(speedStr);
            highlightSpeedButtons(speed);
            updateSpeedSummaryUI();
        });
    }

    @NonNull
    private String formatSpeedLabel(float speed) {
        return String.format(Locale.US, "%.2fx", speed).replaceAll("0+x$", "x").replaceAll("\\.x$", "x");
    }

    private void updateSpeedSummaryUI() {
        Float speedObj = viewModel.getSelectedSpeed().getValue();
        float speed = speedObj != null ? speedObj : 1.5f;
        String speedStr = formatSpeedLabel(speed);

        ExtractedPlaylistDetails playlist = viewModel.getPlaylistDetails().getValue();
        if (playlist != null && !playlist.items.isEmpty()) {
            binding.tvDurationComparison.setText(getString(R.string.playlist_sheet_summary_fmt, playlist.items.size()));
            binding.btnStartSpeedDownload.setText(getString(R.string.btn_download_playlist_sheet_fmt, playlist.items.size(), speedStr));
            return;
        }

        ExtractedMediaDetails details = viewModel.getMediaDetails().getValue();
        long origSec = details != null ? details.durationSeconds : 0L;
        binding.tvDurationComparison.setText(MediaSpeedProcessor.formatDurationComparison(origSec, speed));
        binding.btnStartSpeedDownload.setText(getString(R.string.btn_start_speed_download_fmt, speedStr));
    }
}
