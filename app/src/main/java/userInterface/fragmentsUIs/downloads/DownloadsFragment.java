package userInterface.fragmentsUIs.downloads;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.nextgen.R;
import com.nextgen.databinding.FragDownloads1Binding;

import java.io.File;
import java.util.List;

import coreUtils.base.BaseActivity;
import coreUtils.base.BaseFragment;
import coreUtils.library.process.ShareHelper;
import coreUtils.library.views.StylizedToastView;
import dataRepo.downloads.DownloadInfo;
import dataRepo.downloads.DownloadsInfoRepo;
import sysModules.downloadSys.YtDlpSpeedDownloadEngine;
import userInterface.watchVideo.WatchVideoActivity;

/**
 * Fragment displaying all active and completed downloads with real-time progress updates.
 */
public class DownloadsFragment extends BaseFragment<FragDownloads1Binding>
        implements DownloadsInfoRepo.DownloadsObserver {

    private static final int FILTER_ALL = 0;
    private static final int FILTER_ACTIVE = 1;
    private static final int FILTER_COMPLETED = 2;

    private int currentFilter = FILTER_ALL;
    private DownloadsAdapter adapter;

    @Override
    protected FragDownloads1Binding inflateBinding(LayoutInflater inflater, ViewGroup container) {
        return FragDownloads1Binding.inflate(inflater, container, false);
    }

    @Override
    protected void onLoadedLayout() {
        adapter = new DownloadsAdapter(new DownloadsAdapter.ItemActionListener() {
            @Override
            public void onPlayInternal(@NonNull DownloadInfo info) {
                File f = info.getDestinationFile();
                if (!f.exists() && info.finalFilePath != null && !info.finalFilePath.isEmpty()) {
                    f = new File(info.finalFilePath);
                }
                if (!f.exists()) {
                    showToast(getString(R.string.toast_file_missing_storage));
                    return;
                }
                Intent intent = new Intent(requireContext(), WatchVideoActivity.class);
                intent.putExtra(WatchVideoActivity.EXTRA_LOCAL_FILE_PATH, f.getAbsolutePath());
                intent.putExtra(WatchVideoActivity.EXTRA_MEDIA_TITLE, info.fileName);
                startActivity(intent);
            }

            @Override
            public void onOpenExternal(@NonNull DownloadInfo info) {
                File f = info.getDestinationFile();
                if (f.exists()) {
                    ShareHelper.openFile(requireContext(), f);
                } else {
                    showToast(getString(R.string.toast_file_missing));
                }
            }

            @Override
            public void onShareFile(@NonNull DownloadInfo info) {
                File f = info.getDestinationFile();
                if (f.exists()) {
                    ShareHelper.shareMediaFile(requireContext(), f, info.fileName);
                } else {
                    showToast(getString(R.string.toast_file_missing));
                }
            }

            @Override
            public void onDeleteOrCancel(@NonNull DownloadInfo info) {
                if (info.isRunning && !info.isComplete) {
                    YtDlpSpeedDownloadEngine.cancelDownload(info.downloadId);
                    showToast(getString(R.string.toast_download_cancelled));
                } else {
                    DownloadsInfoRepo.removeDownload(info.downloadId, true);
                    showToast(getString(R.string.toast_file_deleted));
                }
                refreshList();
            }
        });

        binding.rvDownloadsList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvDownloadsList.setAdapter(adapter);

        binding.btnFilterAll.setOnClickListener(v -> applyFilter(FILTER_ALL));
        binding.btnFilterActive.setOnClickListener(v -> applyFilter(FILTER_ACTIVE));
        binding.btnFilterCompleted.setOnClickListener(v -> applyFilter(FILTER_COMPLETED));

        refreshList();
    }

    private void applyFilter(int filter) {
        currentFilter = filter;
        setFilterStyle(binding.btnFilterAll, filter == FILTER_ALL);
        setFilterStyle(binding.btnFilterActive, filter == FILTER_ACTIVE);
        setFilterStyle(binding.btnFilterCompleted, filter == FILTER_COMPLETED);
        refreshList();
    }

    private void setFilterStyle(TextView view, boolean active) {
        view.setBackgroundResource(active ? R.drawable.bg_button_accent : R.drawable.bg_button_negative);
        view.setTextColor(getResources().getColor(
                active ? R.color.style_color_text_inverse : R.color.style_color_text_primary,
                requireContext().getTheme()));
    }

    private void refreshList() {
        if (!isFragmentAlive() || binding == null) return;
        List<DownloadInfo> list = switch (currentFilter) {
            case FILTER_ACTIVE -> DownloadsInfoRepo.getActiveDownloads();
            case FILTER_COMPLETED -> DownloadsInfoRepo.getCompletedDownloads();
            default -> DownloadsInfoRepo.getAllDownloads();
        };
        adapter.submitList(list);
        binding.tvDownloadsSummary.setText(getString(R.string.summary_files_count_fmt, list.size()));
        binding.layoutEmptyState.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        binding.rvDownloadsList.setVisibility(list.isEmpty() ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onResume() {
        super.onResume();
        DownloadsInfoRepo.registerObserver(this);
        refreshList();
    }

    @Override
    public void onPause() {
        super.onPause();
        DownloadsInfoRepo.unregisterObserver(this);
    }

    @Override
    public void onDownloadsUpdated(@NonNull List<DownloadInfo> allDownloads) {
        refreshList();
    }

    @Override
    public void onSingleDownloadProgress(@NonNull DownloadInfo info) {
        if (!isFragmentAlive() || binding == null) return;
        adapter.updateSingleItem(info);
    }

    private void showToast(String msg) {
        if (getActivity() instanceof BaseActivity<?> baseAct) {
            StylizedToastView.show(baseAct, msg);
        }
    }
}
