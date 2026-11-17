package userInterface.fragmentsUIs.downloads;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.nextgen.R;
import com.nextgen.databinding.ItemDownloadCard1Binding;

import java.util.ArrayList;
import java.util.List;

import dataRepo.downloads.DownloadInfo;

/**
 * RecyclerView Adapter for displaying active and completed speed-modified downloads.
 */
public final class DownloadsAdapter extends RecyclerView.Adapter<DownloadsAdapter.DownloadViewHolder> {

    public interface ItemActionListener {
        void onPlayInternal(@NonNull DownloadInfo info);
        void onOpenExternal(@NonNull DownloadInfo info);
        void onShareFile(@NonNull DownloadInfo info);
        void onDeleteOrCancel(@NonNull DownloadInfo info);
    }

    private final List<DownloadInfo> items = new ArrayList<>();
    private final ItemActionListener listener;

    public DownloadsAdapter(@NonNull ItemActionListener listener) {
        this.listener = listener;
    }

    public void submitList(@NonNull List<DownloadInfo> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    public void updateSingleItem(@NonNull DownloadInfo updated) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).downloadId != null && items.get(i).downloadId.equals(updated.downloadId)) {
                items.set(i, updated);
                notifyItemChanged(i);
                return;
            }
        }
    }

    @NonNull
    @Override
    public DownloadViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemDownloadCard1Binding binding = ItemDownloadCard1Binding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new DownloadViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull DownloadViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    final class DownloadViewHolder extends RecyclerView.ViewHolder {
        private final ItemDownloadCard1Binding binding;

        DownloadViewHolder(ItemDownloadCard1Binding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull DownloadInfo info) {
            Context ctx = binding.getRoot().getContext();
            binding.tvItemTitle.setText(info.fileName != null && !info.fileName.isEmpty()
                    ? info.fileName : info.fileUrl);
            binding.tvItemSpeedBadge.setText(info.getFormattedSpeedBadge());
            binding.tvItemStatusInfo.setText(info.downloadStatusInfo);

            String durStr = (info.mediaPlaybackDurationInFormat != null && !info.mediaPlaybackDurationInFormat.isEmpty())
                    ? ctx.getString(R.string.label_duration_prefix, info.mediaPlaybackDurationInFormat) : "";
            String sizeStr = (info.fileSizeInFormat != null && !info.fileSizeInFormat.isEmpty())
                    ? ctx.getString(R.string.label_size_prefix, info.fileSizeInFormat) : "";
            String typePrefix = ctx.getString(info.isAudioOnlyDownload() ? R.string.label_audio_prefix : R.string.label_video_prefix);
            binding.tvItemDurationAndSize.setText(typePrefix + durStr + sizeStr);

            if (info.thumbnailUrl != null && !info.thumbnailUrl.isEmpty()) {
                Glide.with(binding.imgItemThumb.getContext())
                        .load(info.thumbnailUrl)
                        .placeholder(R.drawable.bg_media_placeholder)
                        .into(binding.imgItemThumb);
            } else {
                binding.imgItemThumb.setImageResource(R.drawable.bg_media_placeholder);
            }

            if (info.isComplete) {
                binding.pbItemProgress.setVisibility(View.GONE);
                binding.btnItemPlayInternal.setVisibility(View.VISIBLE);
                binding.btnItemOpenExternal.setVisibility(View.VISIBLE);
                binding.btnItemShare.setVisibility(View.VISIBLE);
                binding.btnItemDeleteOrCancel.setText(R.string.btn_delete);
            } else {
                binding.pbItemProgress.setVisibility(View.VISIBLE);
                binding.pbItemProgress.setProgress((int) info.progressPercentage);
                binding.btnItemPlayInternal.setVisibility(View.GONE);
                binding.btnItemOpenExternal.setVisibility(View.GONE);
                binding.btnItemShare.setVisibility(View.GONE);
                binding.btnItemDeleteOrCancel.setText(info.isRunning ? R.string.btn_stop : R.string.btn_delete);
            }

            binding.btnItemPlayInternal.setOnClickListener(v -> listener.onPlayInternal(info));
            binding.btnItemOpenExternal.setOnClickListener(v -> listener.onOpenExternal(info));
            binding.btnItemShare.setOnClickListener(v -> listener.onShareFile(info));
            binding.btnItemDeleteOrCancel.setOnClickListener(v -> listener.onDeleteOrCancel(info));
        }
    }
}
