package userInterface.fragmentsUIs.localSpeed;

import android.content.ContentResolver;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;

import com.nextgen.R;
import com.nextgen.databinding.FragLocalSpeed1Binding;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Locale;

import coreUtils.base.BaseActivity;
import coreUtils.base.BaseFragment;
import coreUtils.library.media.MediaSpeedProcessor;
import coreUtils.library.media.MediaSpeedProcessor.SpeedMode;
import coreUtils.library.process.DownloaderUtils;
import coreUtils.library.process.ThreadTask;
import coreUtils.library.process.TimeFormats;
import coreUtils.library.storage.FileStorageUtility;
import coreUtils.library.views.StylizedToastView;
import dataRepo.downloads.DownloadInfo;
import dataRepo.downloads.DownloadsInfoRepo;

/**
 * Fragment allowing users to select any existing video or audio file on their phone
 * and produce a baked-in speed-adjusted copy (e.g., 1.25x, 1.5x, 2.0x) without re-downloading.
 */
public class LocalSpeedFragment extends BaseFragment<FragLocalSpeed1Binding> {

    private ActivityResultLauncher<String[]> mediaPickerLauncher;
    private Uri selectedUri;
    private String selectedDisplayName = "";
    private long selectedDurationSeconds = 0L;
    private float targetSpeed = 1.5f;
    private boolean extractAudioOnly = false;
    private boolean voiceBoostEnabled = false;

    @Override
    protected FragLocalSpeed1Binding inflateBinding(LayoutInflater inflater, ViewGroup container) {
        return FragLocalSpeed1Binding.inflate(inflater, container, false);
    }

    @Override
    protected void onLoadedLayout() {
        mediaPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                this::onLocalMediaPicked
        );

        binding.btnPickLocalMedia.setOnClickListener(v ->
                mediaPickerLauncher.launch(new String[]{"video/*", "audio/*"}));

        binding.btnLocalSpd125.setOnClickListener(v -> updateTargetSpeed(1.25f, true));
        binding.btnLocalSpd150.setOnClickListener(v -> updateTargetSpeed(1.50f, true));
        binding.btnLocalSpd175.setOnClickListener(v -> updateTargetSpeed(1.75f, true));
        binding.btnLocalSpd200.setOnClickListener(v -> updateTargetSpeed(2.00f, true));
        binding.btnLocalSpd250.setOnClickListener(v -> updateTargetSpeed(2.50f, true));

        binding.seekLocalSpeed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    updateTargetSpeed(0.50f + (progress * 0.05f), false);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        binding.btnLocalModeTurboVideo.setOnClickListener(v -> {
            extractAudioOnly = false;
            setChipStyle(binding.btnLocalModeTurboVideo, true);
            setChipStyle(binding.btnLocalModeAudioOnly, false);
        });

        binding.btnLocalModeAudioOnly.setOnClickListener(v -> {
            extractAudioOnly = true;
            setChipStyle(binding.btnLocalModeAudioOnly, true);
            setChipStyle(binding.btnLocalModeTurboVideo, false);
        });

        binding.btnLocalVoiceBoost.setOnClickListener(v -> {
            voiceBoostEnabled = !voiceBoostEnabled;
            setChipStyle(binding.btnLocalVoiceBoost, voiceBoostEnabled);
        });

        binding.btnStartLocalConversion.setOnClickListener(v -> startLocalSpeedConversion());
        refreshDurationPreview();
    }

    private void onLocalMediaPicked(Uri uri) {
        if (uri == null || !isFragmentAlive()) return;
        selectedUri = uri;
        String name = FileStorageUtility.getFileNameFromUri(requireContext(), uri);
        selectedDisplayName = (name != null && !name.isEmpty()) ? name : ("LocalMedia_" + System.currentTimeMillis() + ".mp4");
        binding.tvSelectedLocalFileName.setText(selectedDisplayName);

        try (MediaMetadataRetriever retriever = new MediaMetadataRetriever()) {
            retriever.setDataSource(requireContext(), uri);
            String durMsStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            selectedDurationSeconds = durMsStr != null ? (Long.parseLong(durMsStr) / 1000L) : 0L;
        } catch (Exception ignored) {
            selectedDurationSeconds = 0L;
        }

        if (FileStorageUtility.isAudioByName(selectedDisplayName)) {
            extractAudioOnly = true;
            setChipStyle(binding.btnLocalModeAudioOnly, true);
            setChipStyle(binding.btnLocalModeTurboVideo, false);
        }
        refreshDurationPreview();
    }

    private void updateTargetSpeed(float speed, boolean updateSeekBar) {
        targetSpeed = Math.round(Math.max(0.25f, Math.min(4.0f, speed)) * 100f) / 100f;
        String spdStr = String.format(Locale.US, "%.2fx", targetSpeed).replaceAll("0+x$", "x").replaceAll("\\.x$", "x");
        binding.tvLocalSpeedValue.setText(spdStr);
        if (updateSeekBar) {
            int progress = Math.round((targetSpeed - 0.50f) / 0.05f);
            binding.seekLocalSpeed.setProgress(Math.max(0, Math.min(50, progress)));
        }
        setChipStyle(binding.btnLocalSpd125, Math.abs(targetSpeed - 1.25f) < 0.02f);
        setChipStyle(binding.btnLocalSpd150, Math.abs(targetSpeed - 1.50f) < 0.02f);
        setChipStyle(binding.btnLocalSpd175, Math.abs(targetSpeed - 1.75f) < 0.02f);
        setChipStyle(binding.btnLocalSpd200, Math.abs(targetSpeed - 2.00f) < 0.02f);
        setChipStyle(binding.btnLocalSpd250, Math.abs(targetSpeed - 2.50f) < 0.02f);
        refreshDurationPreview();
    }

    private void setChipStyle(TextView view, boolean selected) {
        view.setBackgroundResource(selected ? R.drawable.bg_button_accent : R.drawable.bg_button_negative);
        view.setTextColor(getResources().getColor(
                selected ? R.color.style_color_text_inverse : R.color.style_color_text_primary,
                requireContext().getTheme()));
    }

    private void refreshDurationPreview() {
        binding.tvLocalDurationInfo.setText(MediaSpeedProcessor.formatDurationComparison(selectedDurationSeconds, targetSpeed));
    }

    private void startLocalSpeedConversion() {
        if (selectedUri == null) {
            showToast(getString(R.string.toast_pick_local_media_first));
            return;
        }

        binding.pbLocalConvertProgress.setVisibility(View.VISIBLE);
        binding.pbLocalConvertProgress.setProgress(0);
        binding.tvLocalConvertStatus.setText(R.string.status_preparing_local_file);
        binding.btnStartLocalConversion.setEnabled(false);

        final Uri inputUri = selectedUri;
        final float speed = targetSpeed;
        final boolean isAudio = extractAudioOnly;
        final boolean boost = voiceBoostEnabled;

        ThreadTask.executeInBackground(() -> {
            File tempInput = null;
            try {
                ContentResolver resolver = requireContext().getContentResolver();
                String inExt = isAudio ? ".m4a" : ".mp4";
                tempInput = new File(requireContext().getCacheDir(), "local_in_" + System.currentTimeMillis() + inExt);
                try (InputStream is = resolver.openInputStream(inputUri);
                     FileOutputStream fos = new FileOutputStream(tempInput)) {
                    if (is == null) throw new RuntimeException(getString(R.string.err_cannot_read_selected_file));
                    byte[] buf = new byte[64 * 1024];
                    int read;
                    while ((read = is.read(buf)) != -1) {
                        fos.write(buf, 0, read);
                    }
                }

                DownloadInfo record = new DownloadInfo();
                String baseName = FileStorageUtility.getFileNameWithoutExtension(selectedDisplayName);
                if (baseName == null || baseName.isEmpty()) baseName = "LocalMedia";
                String spdSuffix = "_" + String.format(Locale.US, "%.2fx", speed).replaceAll("0+x$", "x").replaceAll("\\.x$", "x");
                String outExt = isAudio ? ".m4a" : ".mp4";
                record.fileName = FileStorageUtility.sanitizeFileNameNormal(baseName) + spdSuffix + outExt;
                record.fileExtension = outExt;
                DownloaderUtils.applySmartCatalogDirectory(record);
                DownloaderUtils.resolveFileNameConflict(record);

                File outputFile = record.getDestinationFile();
                SpeedMode mode = isAudio ? SpeedMode.AUDIO_ONLY : SpeedMode.TURBO_LOSSLESS_VIDEO;

                File finalResult = MediaSpeedProcessor.executeSpeedTransformSync(
                        tempInput,
                        outputFile,
                        speed,
                        mode,
                        "local_conv_" + System.currentTimeMillis(),
                        boost,
                        new MediaSpeedProcessor.SpeedProcessListener() {
                            @Override
                            public void onProgress(float percentage, long etaSeconds, String statusLine) {
                                ThreadTask.executeOnMainThread(() -> {
                                    if (!isFragmentAlive() || binding == null) return;
                                    int pct = Math.round(percentage);
                                    binding.pbLocalConvertProgress.setProgress(pct);
                                    binding.tvLocalConvertStatus.setText(getString(R.string.status_local_speed_progress_fmt, pct));
                                });
                            }
                            @Override public void onSuccess(@NonNull File out, float appliedSpeed) {}
                            @Override public void onFailure(@NonNull String errorMessage) {}
                        }
                );

                record.setSpeedDownloadConfig(speed, isAudio, mode.name(), "local");
                record.finalFilePath = finalResult.getAbsolutePath();
                record.fileSize = finalResult.length();
                record.fileSizeInFormat = DownloaderUtils.getHumanReadableFormat(record.fileSize);
                record.isComplete = true;
                record.isRunning = false;
                record.progressPercentage = 100L;
                record.downloadStatus = DownloadInfo.DOWNLOAD_STATUS_COMPLETED;
                record.completedTimeDate = System.currentTimeMillis();
                record.completedTimeDateInFormat = TimeFormats.getCurrentDateTime();
                record.downloadStatusInfo = getString(R.string.status_local_convert_complete_fmt, record.getFormattedSpeedBadge(), record.fileSizeInFormat);
                DownloadsInfoRepo.save(record);

                FileStorageUtility.addToMediaStore(finalResult, null);

                ThreadTask.executeOnMainThread(() -> {
                    if (!isFragmentAlive() || binding == null) return;
                    binding.btnStartLocalConversion.setEnabled(true);
                    binding.pbLocalConvertProgress.setProgress(100);
                    binding.tvLocalConvertStatus.setText(getString(R.string.status_saved_success_fmt, finalResult.getName()));
                    showToast(getString(R.string.toast_saved_to_downloads_gallery));
                });

            } catch (Exception e) {
                ThreadTask.executeOnMainThread(() -> {
                    if (!isFragmentAlive() || binding == null) return;
                    binding.btnStartLocalConversion.setEnabled(true);
                    binding.tvLocalConvertStatus.setText(getString(R.string.status_convert_failed_fmt, e.getMessage()));
                    showToast(getString(R.string.toast_download_error_fmt, e.getMessage()));
                });
            } finally {
                if (tempInput != null && tempInput.exists()) {
                    tempInput.delete();
                }
            }
        });
    }

    private void showToast(String msg) {
        if (getActivity() instanceof BaseActivity<?> baseAct) {
            StylizedToastView.show(baseAct, msg);
        }
    }
}
