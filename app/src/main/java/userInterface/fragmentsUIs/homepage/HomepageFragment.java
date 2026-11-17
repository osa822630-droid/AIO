package userInterface.fragmentsUIs.homepage;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.nextgen.R;
import com.nextgen.databinding.FragHomepage1Binding;

import java.io.File;
import java.util.List;
import java.util.Locale;

import coreUtils.base.BaseActivity;
import coreUtils.base.BaseFragment;
import coreUtils.library.media.MediaSpeedProcessor;
import coreUtils.library.media.MediaSpeedProcessor.SpeedMode;
import coreUtils.library.process.LocaleHelper;
import coreUtils.library.process.ThemeManager;
import coreUtils.library.strings.ClipboardHelper;
import coreUtils.library.views.StylizedToastView;
import dataRepo.appConfigs.AppConfigsRepo;
import dataRepo.downloads.DownloadInfo;
import sysModules.downloadSys.YtDlpSpeedDownloadEngine;
import sysModules.downloadSys.YtDlpSpeedDownloadEngine.ExtractedMediaDetails;
import sysModules.downloadSys.YtDlpSpeedDownloadEngine.ExtractedPlaylistItem;
import userInterface.settings.SettingsActivity;
import userInterface.intentHandler.IntentUrlParser;
import userInterface.languagePicker.LanguageActivity;
import userInterface.mainScreen.MainActivity;
import userInterface.mainScreen.MainViewModel;
import userInterface.watchVideo.WatchVideoActivity;

/**
 * Full Speed-Downloader Studio Homepage Fragment.
 * Supports single media + full playlist downloads, Day/Night theme toggle,
 * instant Arabic/English switching, and custom speed baking (0.5x - 3.0x).
 */
public class HomepageFragment extends BaseFragment<FragHomepage1Binding> {

    private HomepageViewModel viewModel;
    private PlaylistAdapter playlistAdapter;
    private boolean allPlaylistSelected = true;

    @Override
    protected FragHomepage1Binding inflateBinding(LayoutInflater inflater, ViewGroup container) {
        return FragHomepage1Binding.inflate(inflater, container, false);
    }

    @Override
    protected void onLoadedLayout() {
        viewModel = getHomepageViewModel();
        if (getActivity() instanceof MainActivity mainAct) {
            mainAct.setupNestedScrollListener(binding.NestedScrollView);
        }
        setupPlaylistRecycler();
        setupListeners();
        observeState();
    }

    public HomepageViewModel getHomepageViewModel() {
        return new ViewModelProvider(this).get(HomepageViewModel.class);
    }

    public MainViewModel getMainViewModel() {
        FragmentActivity owner = requireActivity();
        return new ViewModelProvider(owner).get(MainViewModel.class);
    }

    private void setupPlaylistRecycler() {
        playlistAdapter = new PlaylistAdapter((selectedCount, totalCount) -> updatePlaylistButtons(selectedCount, totalCount));
        binding.rvPlaylistItems.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvPlaylistItems.setAdapter(playlistAdapter);
    }

    private void setupListeners() {
        binding.btnToggleTheme.setOnClickListener(v -> {
            if (getActivity() instanceof BaseActivity<?> baseAct) {
                boolean nowDark = !ThemeManager.isDarkModeActive(baseAct);
                ThemeManager.toggleTheme(baseAct);
                showToast(getString(nowDark ? R.string.msg_theme_switched_dark : R.string.msg_theme_switched_light));
            }
        });

        binding.btnToggleLang.setOnClickListener(v -> {
            if (getActivity() instanceof BaseActivity<?> baseAct) {
                String current = LocaleHelper.getEffectiveLanguage(baseAct);
                String target = "ar".equalsIgnoreCase(current) ? "en" : "ar";
                AppConfigsRepo.getConfig().selectedLanguageCode = target;
                AppConfigsRepo.getConfig().isLocaleConfigured = true;
                AppConfigsRepo.getConfig().save();
                LocaleHelper.toggleArabicEnglish(baseAct);
            }
        });

        binding.btnOpenLanguage.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), LanguageActivity.class)));

        binding.btnOpenSettings.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), SettingsActivity.class)));

        binding.btnUpdateYtDlp.setOnClickListener(v -> {
            showToast(getString(R.string.msg_updating_engine));
            binding.btnUpdateYtDlp.setEnabled(false);
            YtDlpSpeedDownloadEngine.updateYtDlpEngineAsync(getViewLifecycleOwner(), new YtDlpSpeedDownloadEngine.UpdateCallback() {
                @Override
                public void onUpdateResult(boolean updated, @NonNull String statusMessage) {
                    if (!isFragmentAlive() || binding == null) return;
                    binding.btnUpdateYtDlp.setEnabled(true);
                    showToast(statusMessage);
                }

                @Override
                public void onUpdateError(@NonNull String errorMessage) {
                    if (!isFragmentAlive() || binding == null) return;
                    binding.btnUpdateYtDlp.setEnabled(true);
                    showToast(getString(R.string.msg_engine_update_failed_fmt, errorMessage));
                }
            });
        });

        binding.btnPreviewStreamSpeed.setOnClickListener(v -> {
            ExtractedMediaDetails details = viewModel.getCurrentDetails().getValue();
            if (details == null || details.url.isEmpty()) return;
            showToast(getString(R.string.msg_fetching_preview_stream));
            binding.btnPreviewStreamSpeed.setEnabled(false);
            boolean isAudio = Boolean.TRUE.equals(viewModel.getAudioOnly().getValue());
            float speed = viewModel.getTargetSpeed().getValue() != null ? viewModel.getTargetSpeed().getValue() : 1.5f;

            YtDlpSpeedDownloadEngine.extractDirectStreamUrlAsync(getViewLifecycleOwner(), details.url, isAudio,
                    new YtDlpSpeedDownloadEngine.StreamUrlCallback() {
                        @Override
                        public void onStreamUrlReady(@NonNull String streamUrl) {
                            if (!isFragmentAlive() || binding == null) return;
                            binding.btnPreviewStreamSpeed.setEnabled(true);
                            Intent intent = new Intent(requireContext(), WatchVideoActivity.class);
                            intent.putExtra(WatchVideoActivity.EXTRA_STREAM_URL, streamUrl);
                            intent.putExtra(WatchVideoActivity.EXTRA_INITIAL_SPEED, speed);
                            intent.putExtra(WatchVideoActivity.EXTRA_MEDIA_TITLE, details.title);
                            startActivity(intent);
                        }

                        @Override
                        public void onStreamUrlError(@NonNull String errorMessage) {
                            if (!isFragmentAlive() || binding == null) return;
                            binding.btnPreviewStreamSpeed.setEnabled(true);
                            showToast(getString(R.string.msg_preview_stream_failed_fmt, errorMessage));
                        }
                    });
        });

        binding.btnVoiceBoost.setOnClickListener(v -> viewModel.toggleVoiceBoost());
        binding.btnSyncSubtitles.setOnClickListener(v -> viewModel.toggleSyncSubtitles());

        binding.btnClearUrl.setOnClickListener(v -> {
            binding.editMediaUrl.setText("");
            viewModel.clearPreview();
            binding.cardMediaPreview.setVisibility(View.GONE);
            binding.cardPlaylistPreview.setVisibility(View.GONE);
        });

        binding.btnPasteUrl.setOnClickListener(v -> {
            String clip = ClipboardHelper.getTextFromClipboard(requireContext());
            String extracted = IntentUrlParser.extractFirstUrlFromText(clip);
            if (extracted != null && !extracted.isEmpty()) {
                binding.editMediaUrl.setText(extracted);
                viewModel.analyzeUrl(getViewLifecycleOwner(), extracted);
            } else if (clip != null && !clip.trim().isEmpty()) {
                binding.editMediaUrl.setText(clip.trim());
            } else {
                showToast(getString(R.string.toast_clipboard_empty));
            }
        });

        binding.btnFetchUrlInfo.setOnClickListener(v -> {
            String input = binding.editMediaUrl.getText() != null
                    ? binding.editMediaUrl.getText().toString().trim() : "";
            viewModel.analyzeUrl(getViewLifecycleOwner(), input);
        });

        binding.btnPlaylistSelectAll.setOnClickListener(v -> {
            allPlaylistSelected = !allPlaylistSelected;
            playlistAdapter.setAllSelected(allPlaylistSelected);
        });

        binding.btnDownloadPlaylist.setOnClickListener(v -> {
            List<ExtractedPlaylistItem> chosen = playlistAdapter.getSelectedItems();
            List<DownloadInfo> queued = viewModel.startPlaylistDownload(chosen, createDownloadListener());
            if (!queued.isEmpty()) {
                DownloadInfo first = queued.get(0);
                binding.cardActiveTaskBanner.setVisibility(View.VISIBLE);
                binding.tvActiveTaskTitle.setText(first.fileName);
                binding.tvActiveTaskStatus.setText(first.downloadStatusInfo);
                binding.pbActiveTaskProgress.setProgress(0);
                showToast(getString(R.string.msg_playlist_started_fmt, queued.size(), first.getFormattedSpeedBadge()));
            }
        });

        // Speed Preset Chips
        binding.chipSpeed075.setOnClickListener(v -> selectSpeedPreset(0.75f));
        binding.chipSpeed100.setOnClickListener(v -> selectSpeedPreset(1.00f));
        binding.chipSpeed125.setOnClickListener(v -> selectSpeedPreset(1.25f));
        binding.chipSpeed150.setOnClickListener(v -> selectSpeedPreset(1.50f));
        binding.chipSpeed175.setOnClickListener(v -> selectSpeedPreset(1.75f));
        binding.chipSpeed200.setOnClickListener(v -> selectSpeedPreset(2.00f));
        binding.chipSpeed250.setOnClickListener(v -> selectSpeedPreset(2.50f));
        binding.chipSpeed300.setOnClickListener(v -> selectSpeedPreset(3.00f));

        binding.seekSpeedControl.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    float speed = 0.50f + (progress * 0.05f);
                    viewModel.setTargetSpeed(speed);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Format Type Selection
        binding.btnTypeVideoMp4.setOnClickListener(v -> {
            viewModel.setFormat(false, "720p");
            updateFormatUI(false, "720p");
        });
        binding.btnTypeAudioM4a.setOnClickListener(v -> {
            viewModel.setFormat(true, "m4a");
            updateFormatUI(true, "m4a");
        });
        binding.btnTypeAudioMp3.setOnClickListener(v -> {
            viewModel.setFormat(true, "mp3");
            updateFormatUI(true, "mp3");
        });

        // Video Quality Chips
        binding.chipQ1080.setOnClickListener(v -> selectVideoQuality("1080p"));
        binding.chipQ720.setOnClickListener(v -> selectVideoQuality("720p"));
        binding.chipQ480.setOnClickListener(v -> selectVideoQuality("480p"));
        binding.chipQ360.setOnClickListener(v -> selectVideoQuality("360p"));

        // Video Processing Mode
        binding.btnTurboLosslessMode.setOnClickListener(v -> {
            viewModel.setVideoSpeedMode(SpeedMode.TURBO_LOSSLESS_VIDEO);
            setChipBg(binding.btnTurboLosslessMode, true);
            setChipBg(binding.btnCompatReencodeMode, false);
        });
        binding.btnCompatReencodeMode.setOnClickListener(v -> {
            viewModel.setVideoSpeedMode(SpeedMode.COMPATIBILITY_REENCODE_VIDEO);
            setChipBg(binding.btnCompatReencodeMode, true);
            setChipBg(binding.btnTurboLosslessMode, false);
        });

        // Start Single Download Button
        binding.btnDownloadWithSpeed.setOnClickListener(v -> {
            String rawUrl = binding.editMediaUrl.getText() != null
                    ? binding.editMediaUrl.getText().toString().trim() : "";
            if (rawUrl.isEmpty()) {
                String clipUrl = IntentUrlParser.extractFirstUrlFromText(
                        ClipboardHelper.getTextFromClipboard(requireContext()));
                if (clipUrl != null && !clipUrl.isEmpty()) {
                    binding.editMediaUrl.setText(clipUrl);
                    rawUrl = clipUrl;
                }
            }

            if (IntentUrlParser.isPlaylistUrl(rawUrl) && playlistAdapter.getItemCount() > 0) {
                binding.btnDownloadPlaylist.performClick();
                return;
            }

            DownloadInfo started = viewModel.startDownload(rawUrl, createDownloadListener());
            if (started != null) {
                binding.cardActiveTaskBanner.setVisibility(View.VISIBLE);
                binding.tvActiveTaskTitle.setText(started.fileName);
                binding.tvActiveTaskStatus.setText(started.downloadStatusInfo);
                binding.pbActiveTaskProgress.setProgress(0);
                showToast(getString(R.string.toast_download_started_badge, started.getFormattedSpeedBadge()));
            }
        });

        binding.btnViewAllDownloads.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity mainAct) {
                mainAct.openDownloadsTab();
            }
        });
    }

    @NonNull
    private YtDlpSpeedDownloadEngine.EngineDownloadListener createDownloadListener() {
        return new YtDlpSpeedDownloadEngine.EngineDownloadListener() {
            @Override
            public void onProgress(long downloadId, float totalPercent, String statusText) {
                if (!isFragmentAlive() || binding == null) return;
                binding.cardActiveTaskBanner.setVisibility(View.VISIBLE);
                binding.pbActiveTaskProgress.setProgress(Math.round(totalPercent));
                binding.tvActiveTaskStatus.setText(statusText);
            }

            @Override
            public void onCompleted(long downloadId, @NonNull File outputFile) {
                if (!isFragmentAlive() || binding == null) return;
                binding.pbActiveTaskProgress.setProgress(100);
                binding.tvActiveTaskStatus.setText(getString(R.string.toast_download_success_named, outputFile.getName()));
                showToast(getString(R.string.toast_speed_download_complete, outputFile.getName()));
            }

            @Override
            public void onError(long downloadId, @NonNull String errorMessage) {
                if (!isFragmentAlive() || binding == null) return;
                binding.tvActiveTaskStatus.setText(getString(R.string.toast_download_error_fmt, errorMessage));
                showToast(getString(R.string.toast_cannot_download_fmt, errorMessage));
            }
        };
    }

    private void selectSpeedPreset(float speed) {
        viewModel.setTargetSpeed(speed);
        int progress = Math.round((speed - 0.50f) / 0.05f);
        binding.seekSpeedControl.setProgress(Math.max(0, Math.min(50, progress)));
    }

    private void selectVideoQuality(@NonNull String quality) {
        viewModel.setFormat(false, quality);
        setChipBg(binding.chipQ1080, "1080p".equals(quality));
        setChipBg(binding.chipQ720, "720p".equals(quality));
        setChipBg(binding.chipQ480, "480p".equals(quality));
        setChipBg(binding.chipQ360, "360p".equals(quality));
    }

    private void updateFormatUI(boolean isAudio, @NonNull String qualityOrCodec) {
        setChipBg(binding.btnTypeVideoMp4, !isAudio);
        setChipBg(binding.btnTypeAudioM4a, isAudio && "m4a".equals(qualityOrCodec));
        setChipBg(binding.btnTypeAudioMp3, isAudio && "mp3".equals(qualityOrCodec));
        binding.layoutVideoQualities.setVisibility(isAudio ? View.GONE : View.VISIBLE);
        binding.layoutVideoSpeedModes.setVisibility(isAudio ? View.GONE : View.VISIBLE);
    }

    private void observeState() {
        viewModel.getIsAnalyzingUrl().observe(getViewLifecycleOwner(), loading ->
                binding.pbFetchLoading.setVisibility(Boolean.TRUE.equals(loading) ? View.VISIBLE : View.GONE));

        viewModel.getCurrentDetails().observe(getViewLifecycleOwner(), details -> {
            if (details == null) {
                binding.cardMediaPreview.setVisibility(View.GONE);
                return;
            }
            binding.cardPlaylistPreview.setVisibility(View.GONE);
            binding.cardMediaPreview.setVisibility(View.VISIBLE);
            binding.tvPreviewTitle.setText(details.title);
            binding.tvPreviewUploader.setText(details.uploader);
            if (!details.thumbnailUrl.isEmpty()) {
                Glide.with(this)
                        .load(details.thumbnailUrl)
                        .placeholder(R.drawable.bg_media_placeholder)
                        .into(binding.imgPreviewThumb);
            }
            refreshDurationAndSpeedUI();
        });

        viewModel.getCurrentPlaylist().observe(getViewLifecycleOwner(), playlist -> {
            if (playlist == null) {
                binding.cardPlaylistPreview.setVisibility(View.GONE);
                return;
            }
            binding.cardMediaPreview.setVisibility(View.GONE);
            binding.cardPlaylistPreview.setVisibility(View.VISIBLE);
            binding.tvPlaylistTitle.setText(playlist.playlistTitle);
            allPlaylistSelected = true;
            playlistAdapter.submitList(playlist.items);
        });

        viewModel.getTargetSpeed().observe(getViewLifecycleOwner(), speed -> {
            if (speed == null) return;
            String speedStr = formatSpeedLabel(speed);
            binding.tvCurrentSpeedBadge.setText(speedStr);
            binding.btnDownloadWithSpeed.setText(getString(R.string.btn_download_with_speed_fmt, speedStr));
            highlightSpeedChip(speed);
            refreshDurationAndSpeedUI();
            updatePlaylistButtons(playlistAdapter.getSelectedCount(), playlistAdapter.getItemCount());
        });

        viewModel.getVoiceBoost().observe(getViewLifecycleOwner(), enabled ->
                setChipBg(binding.btnVoiceBoost, Boolean.TRUE.equals(enabled)));

        viewModel.getSyncSubtitles().observe(getViewLifecycleOwner(), enabled ->
                setChipBg(binding.btnSyncSubtitles, Boolean.TRUE.equals(enabled)));

        viewModel.getStatusMessage().observe(getViewLifecycleOwner(), msg -> {
            if (msg != null && !msg.isEmpty()) {
                showToast(msg);
            }
        });
    }

    private void updatePlaylistButtons(int selectedCount, int totalCount) {
        if (binding == null) return;
        binding.tvPlaylistCount.setText(getString(R.string.playlist_items_count_fmt, selectedCount, totalCount));
        binding.btnPlaylistSelectAll.setText(getString(allPlaylistSelected ? R.string.btn_deselect_all : R.string.btn_select_all));
        Float speedObj = viewModel.getTargetSpeed().getValue();
        float speed = speedObj != null ? speedObj : 1.5f;
        binding.btnDownloadPlaylist.setText(getString(R.string.btn_download_playlist_fmt, selectedCount, formatSpeedLabel(speed)));
    }

    @NonNull
    private String formatSpeedLabel(float speed) {
        return String.format(Locale.US, "%.2fx", speed).replaceAll("0+x$", "x").replaceAll("\\.x$", "x");
    }

    private void highlightSpeedChip(float s) {
        setChipBg(binding.chipSpeed075, Math.abs(s - 0.75f) < 0.02f);
        setChipBg(binding.chipSpeed100, Math.abs(s - 1.00f) < 0.02f);
        setChipBg(binding.chipSpeed125, Math.abs(s - 1.25f) < 0.02f);
        setChipBg(binding.chipSpeed150, Math.abs(s - 1.50f) < 0.02f);
        setChipBg(binding.chipSpeed175, Math.abs(s - 1.75f) < 0.02f);
        setChipBg(binding.chipSpeed200, Math.abs(s - 2.00f) < 0.02f);
        setChipBg(binding.chipSpeed250, Math.abs(s - 2.50f) < 0.02f);
        setChipBg(binding.chipSpeed300, Math.abs(s - 3.00f) < 0.02f);
    }

    private void setChipBg(TextView view, boolean selected) {
        view.setBackgroundResource(selected ? R.drawable.bg_button_accent : R.drawable.bg_button_negative);
        view.setTextColor(getResources().getColor(
                selected ? R.color.style_color_text_inverse : R.color.style_color_text_primary,
                requireContext().getTheme()));
    }

    private void refreshDurationAndSpeedUI() {
        ExtractedMediaDetails details = viewModel.getCurrentDetails().getValue();
        Float speedObj = viewModel.getTargetSpeed().getValue();
        float speed = speedObj != null ? speedObj : 1.5f;
        long origSeconds = details != null ? details.durationSeconds : 0L;
        binding.tvPreviewDurationCalc.setText(MediaSpeedProcessor.formatDurationComparison(origSeconds, speed));
    }

    private void showToast(String msg) {
        if (getActivity() instanceof BaseActivity<?> baseAct) {
            StylizedToastView.show(baseAct, msg);
        }
    }

    @Override
    public void onDestroyView() {
        if (getActivity() instanceof MainActivity mainAct && binding != null) {
            mainAct.cleanupScrollListener(binding.NestedScrollView);
        }
        super.onDestroyView();
    }
}
