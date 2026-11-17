package userInterface.settings;

import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;

import com.nextgen.R;
import com.nextgen.databinding.ActivitySettings1Binding;

import coreUtils.base.BaseActivity;
import coreUtils.library.process.DownloaderUtils;
import coreUtils.library.process.LocaleHelper;
import coreUtils.library.process.SettingsPrefs;
import coreUtils.library.process.ThemeManager;
import coreUtils.library.views.StylizedToastView;
import dataRepo.appConfigs.AppConfigs;
import dataRepo.appConfigs.AppConfigsRepo;
import sysModules.downloadSys.YtDlpSpeedDownloadEngine;
import userInterface.aboutUs.AboutActivity;
import userInterface.userFeedback.FeedbackActivity;
import userInterface.userPrivacy.PrivacyPolicyActivity;

/**
 * Full Settings Activity for configuring download folder, max concurrent downloads (1-4),
 * default speed/quality/turbo/audio/subtitle options, Wi-Fi-only mode, Day/Night theme,
 * Arabic/English language, cache cleanup, and yt-dlp engine updates.
 */
public final class SettingsActivity extends BaseActivity<ActivitySettings1Binding> {

    private ActivityResultLauncher<Uri> folderPickerLauncher;

    @Override
    protected boolean shouldLockOrientation() {
        return true;
    }

    @Override
    protected ActivitySettings1Binding inflateBinding(LayoutInflater inflater) {
        return ActivitySettings1Binding.inflate(inflater);
    }

    @Override
    protected void onLoadedLayout() {
        folderPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocumentTree(),
                uri -> {
                    if (uri == null) return;
                    try {
                        getContentResolver().takePersistableUriPermission(
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        );
                    } catch (Exception ignored) {}

                    String resolved = SettingsPrefs.resolveTreeUriToFilePath(uri);
                    AppConfigs cfg = AppConfigsRepo.getConfig();
                    if (cfg != null) {
                        if (resolved != null && !resolved.isEmpty()) {
                            cfg.selectedDownloadDirectoryPath = resolved;
                            cfg.save();
                            StylizedToastView.showSuccess(this, getString(R.string.toast_folder_updated));
                        } else {
                            cfg.selectedDownloadDirectoryPath = "";
                            cfg.defaultDownloadLocationType = AppConfigs.PRIVATE_FOLDER;
                            cfg.save();
                            StylizedToastView.show(this, getString(R.string.toast_folder_uri_fallback));
                        }
                    }
                    refreshUI();
                }
        );

        setupListeners();
        refreshUI();
    }

    private void setupListeners() {
        binding.btnSettingsBack.setOnClickListener(v -> finish());

        // Download Location
        binding.btnChooseCustomFolder.setOnClickListener(v -> folderPickerLauncher.launch(null));
        binding.btnUsePublicDownloads.setOnClickListener(v -> {
            AppConfigs cfg = AppConfigsRepo.getConfig();
            if (cfg != null) {
                cfg.selectedDownloadDirectoryPath = "";
                cfg.defaultDownloadLocationType = AppConfigs.SYSTEM_GALLERY;
                cfg.save();
            }
            StylizedToastView.showSuccess(this, getString(R.string.toast_folder_updated));
            refreshUI();
        });
        binding.btnUsePrivateFolder.setOnClickListener(v -> {
            AppConfigs cfg = AppConfigsRepo.getConfig();
            if (cfg != null) {
                cfg.selectedDownloadDirectoryPath = "";
                cfg.defaultDownloadLocationType = AppConfigs.PRIVATE_FOLDER;
                cfg.save();
            }
            StylizedToastView.showSuccess(this, getString(R.string.toast_folder_updated));
            refreshUI();
        });

        binding.switchSmartSubfolders.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (buttonView.isPressed()) {
                SettingsPrefs.setSmartSubfoldersEnabled(isChecked);
            }
        });

        // Max Concurrent Downloads (1..4)
        binding.btnConc1.setOnClickListener(v -> setConcurrency(1));
        binding.btnConc2.setOnClickListener(v -> setConcurrency(2));
        binding.btnConc3.setOnClickListener(v -> setConcurrency(3));
        binding.btnConc4.setOnClickListener(v -> setConcurrency(4));

        // Default Speed
        binding.btnDefSpd100.setOnClickListener(v -> setDefaultSpeed(1.00f));
        binding.btnDefSpd125.setOnClickListener(v -> setDefaultSpeed(1.25f));
        binding.btnDefSpd150.setOnClickListener(v -> setDefaultSpeed(1.50f));
        binding.btnDefSpd175.setOnClickListener(v -> setDefaultSpeed(1.75f));
        binding.btnDefSpd200.setOnClickListener(v -> setDefaultSpeed(2.00f));

        // Default Quality
        binding.btnDefQ1080.setOnClickListener(v -> setDefaultQuality("1080p"));
        binding.btnDefQ720.setOnClickListener(v -> setDefaultQuality("720p"));
        binding.btnDefQ480.setOnClickListener(v -> setDefaultQuality("480p"));
        binding.btnDefQ360.setOnClickListener(v -> setDefaultQuality("360p"));

        binding.switchDefaultTurbo.setOnCheckedChangeListener((btn, checked) -> {
            if (btn.isPressed()) SettingsPrefs.setDefaultTurboMode(checked);
        });
        binding.switchDefaultVoiceBoost.setOnCheckedChangeListener((btn, checked) -> {
            if (btn.isPressed()) SettingsPrefs.setDefaultVoiceBoost(checked);
        });
        binding.switchDefaultSubtitles.setOnCheckedChangeListener((btn, checked) -> {
            if (btn.isPressed()) SettingsPrefs.setDefaultSyncSubtitles(checked);
        });

        // Network & Sound
        binding.switchWifiOnly.setOnCheckedChangeListener((btn, checked) -> {
            if (btn.isPressed()) {
                AppConfigs cfg = AppConfigsRepo.getConfig();
                if (cfg != null) {
                    cfg.wifiOnlyDownloadEnabled = checked;
                    cfg.save();
                }
            }
        });
        binding.switchCompletionSound.setOnCheckedChangeListener((btn, checked) -> {
            if (btn.isPressed()) {
                AppConfigs cfg = AppConfigsRepo.getConfig();
                if (cfg != null) {
                    cfg.isDownloadCompletionSoundEnabled = checked;
                    cfg.save();
                }
            }
        });

        // Theme Mode
        binding.btnThemeSystem.setOnClickListener(v -> applyThemeMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM));
        binding.btnThemeLight.setOnClickListener(v -> applyThemeMode(AppCompatDelegate.MODE_NIGHT_NO));
        binding.btnThemeDark.setOnClickListener(v -> applyThemeMode(AppCompatDelegate.MODE_NIGHT_YES));

        // Language Toggle
        binding.btnSettingsLanguage.setOnClickListener(v -> {
            String current = LocaleHelper.getEffectiveLanguage(this);
            String target = "ar".equalsIgnoreCase(current) ? "en" : "ar";
            AppConfigs cfg = AppConfigsRepo.getConfig();
            if (cfg != null) {
                cfg.selectedLanguageCode = target;
                cfg.isLocaleConfigured = true;
                cfg.save();
            }
            LocaleHelper.toggleArabicEnglish(this);
        });

        // Maintenance & Links
        binding.btnClearCache.setOnClickListener(v -> {
            SettingsPrefs.clearTempCache(this);
            StylizedToastView.showSuccess(this, getString(R.string.toast_cache_cleared));
            refreshCacheSize();
        });

        binding.btnSettingsUpdateEngine.setOnClickListener(v -> {
            StylizedToastView.show(this, getString(R.string.msg_updating_engine));
            binding.btnSettingsUpdateEngine.setEnabled(false);
            YtDlpSpeedDownloadEngine.updateYtDlpEngineAsync(this, new YtDlpSpeedDownloadEngine.UpdateCallback() {
                @Override
                public void onUpdateResult(boolean updated, @NonNull String statusMessage) {
                    binding.btnSettingsUpdateEngine.setEnabled(true);
                    StylizedToastView.showSuccess(SettingsActivity.this, statusMessage);
                }

                @Override
                public void onUpdateError(@NonNull String errorMessage) {
                    binding.btnSettingsUpdateEngine.setEnabled(true);
                    StylizedToastView.showError(SettingsActivity.this, getString(R.string.msg_engine_update_failed_fmt, errorMessage));
                }
            });
        });

        binding.btnSettingsAbout.setOnClickListener(v -> startActivity(new Intent(this, AboutActivity.class)));
        binding.btnSettingsPrivacy.setOnClickListener(v -> startActivity(new Intent(this, PrivacyPolicyActivity.class)));
        binding.btnSettingsFeedback.setOnClickListener(v -> startActivity(new Intent(this, FeedbackActivity.class)));
    }

    private void setConcurrency(int limit) {
        SettingsPrefs.setMaxConcurrentDownloads(limit);
        StylizedToastView.show(this, getString(R.string.toast_max_concurrent_updated, limit));
        refreshConcurrencyChips();
    }

    private void setDefaultSpeed(float speed) {
        SettingsPrefs.setDefaultSpeed(speed);
        refreshSpeedChips();
    }

    private void setDefaultQuality(@NonNull String quality) {
        SettingsPrefs.setDefaultQuality(quality);
        refreshQualityChips();
    }

    private void applyThemeMode(int mode) {
        ThemeManager.setThemeMode(this, mode);
        refreshThemeChips();
    }

    private void refreshUI() {
        binding.tvCurrentDownloadPath.setText(SettingsPrefs.resolveBaseDownloadDirectory(this).getAbsolutePath());
        AppConfigs cfg = AppConfigsRepo.getConfig();
        boolean isCustom = cfg != null && cfg.selectedDownloadDirectoryPath != null && !cfg.selectedDownloadDirectoryPath.trim().isEmpty();
        boolean isPrivate = !isCustom && cfg != null && cfg.defaultDownloadLocationType == AppConfigs.PRIVATE_FOLDER;
        setChip(binding.btnChooseCustomFolder, isCustom);
        setChip(binding.btnUsePublicDownloads, !isCustom && !isPrivate);
        setChip(binding.btnUsePrivateFolder, isPrivate);

        binding.switchSmartSubfolders.setChecked(SettingsPrefs.isSmartSubfoldersEnabled());
        binding.switchDefaultTurbo.setChecked(SettingsPrefs.isDefaultTurboMode());
        binding.switchDefaultVoiceBoost.setChecked(SettingsPrefs.isDefaultVoiceBoost());
        binding.switchDefaultSubtitles.setChecked(SettingsPrefs.isDefaultSyncSubtitles());
        binding.switchWifiOnly.setChecked(cfg != null && cfg.wifiOnlyDownloadEnabled);
        binding.switchCompletionSound.setChecked(cfg == null || cfg.isDownloadCompletionSoundEnabled);

        refreshConcurrencyChips();
        refreshSpeedChips();
        refreshQualityChips();
        refreshThemeChips();
        refreshCacheSize();
    }

    private void refreshConcurrencyChips() {
        int c = SettingsPrefs.getMaxConcurrentDownloads();
        setChip(binding.btnConc1, c == 1);
        setChip(binding.btnConc2, c == 2);
        setChip(binding.btnConc3, c == 3);
        setChip(binding.btnConc4, c == 4);
    }

    private void refreshSpeedChips() {
        float s = SettingsPrefs.getDefaultSpeed();
        setChip(binding.btnDefSpd100, Math.abs(s - 1.00f) < 0.02f);
        setChip(binding.btnDefSpd125, Math.abs(s - 1.25f) < 0.02f);
        setChip(binding.btnDefSpd150, Math.abs(s - 1.50f) < 0.02f);
        setChip(binding.btnDefSpd175, Math.abs(s - 1.75f) < 0.02f);
        setChip(binding.btnDefSpd200, Math.abs(s - 2.00f) < 0.02f);
    }

    private void refreshQualityChips() {
        String q = SettingsPrefs.getDefaultQuality();
        setChip(binding.btnDefQ1080, "1080p".equals(q));
        setChip(binding.btnDefQ720, "720p".equals(q));
        setChip(binding.btnDefQ480, "480p".equals(q));
        setChip(binding.btnDefQ360, "360p".equals(q));
    }

    private void refreshThemeChips() {
        int m = ThemeManager.getSavedThemeMode(this);
        setChip(binding.btnThemeSystem, m == AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        setChip(binding.btnThemeLight, m == AppCompatDelegate.MODE_NIGHT_NO);
        setChip(binding.btnThemeDark, m == AppCompatDelegate.MODE_NIGHT_YES);
    }

    private void refreshCacheSize() {
        long bytes = SettingsPrefs.calculateTempCacheBytes(this);
        String formatted = DownloaderUtils.getHumanReadableFormat(bytes);
        binding.tvCacheSizeInfo.setText(getString(R.string.label_temp_cache_fmt, formatted));
    }

    private void setChip(TextView view, boolean selected) {
        view.setBackgroundResource(selected ? R.drawable.bg_button_accent : R.drawable.bg_button_negative);
        view.setTextColor(getResources().getColor(
                selected ? R.color.style_color_text_inverse : R.color.style_color_text_primary,
                getTheme()));
    }
}
