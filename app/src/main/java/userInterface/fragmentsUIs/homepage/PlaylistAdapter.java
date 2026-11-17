package userInterface.fragmentsUIs.homepage;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.nextgen.R;
import com.nextgen.databinding.ItemPlaylistEntry1Binding;

import java.util.ArrayList;
import java.util.List;

import coreUtils.library.process.TimeFormats;
import sysModules.downloadSys.YtDlpSpeedDownloadEngine.ExtractedPlaylistItem;

/**
 * Adapter for selecting individual items in an extracted playlist before batch speed downloading.
 */
public final class PlaylistAdapter extends RecyclerView.Adapter<PlaylistAdapter.PlaylistViewHolder> {

    public interface SelectionChangedListener {
        void onSelectionChanged(int selectedCount, int totalCount);
    }

    private final List<ExtractedPlaylistItem> items = new ArrayList<>();
    private final SelectionChangedListener listener;

    public PlaylistAdapter(@NonNull SelectionChangedListener listener) {
        this.listener = listener;
    }

    public void submitList(@NonNull List<ExtractedPlaylistItem> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
        notifyCount();
    }

    public void setAllSelected(boolean selected) {
        for (ExtractedPlaylistItem item : items) {
            item.isSelected = selected;
        }
        notifyDataSetChanged();
        notifyCount();
    }

    public int getSelectedCount() {
        int count = 0;
        for (ExtractedPlaylistItem item : items) {
            if (item.isSelected) count++;
        }
        return count;
    }

    @NonNull
    public List<ExtractedPlaylistItem> getSelectedItems() {
        List<ExtractedPlaylistItem> out = new ArrayList<>();
        for (ExtractedPlaylistItem item : items) {
            if (item.isSelected) out.add(item);
        }
        return out;
    }

    private void notifyCount() {
        listener.onSelectionChanged(getSelectedCount(), items.size());
    }

    @NonNull
    @Override
    public PlaylistViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemPlaylistEntry1Binding binding = ItemPlaylistEntry1Binding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new PlaylistViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull PlaylistViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    final class PlaylistViewHolder extends RecyclerView.ViewHolder {
        private final ItemPlaylistEntry1Binding binding;

        PlaylistViewHolder(ItemPlaylistEntry1Binding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull ExtractedPlaylistItem item) {
            binding.tvPlaylistItemTitle.setText(item.title);
            binding.tvPlaylistItemDuration.setText(item.durationSeconds > 0
                    ? TimeFormats.formatDurationFromSecond(item.durationSeconds)
                    : "--:--");

            if (!item.thumbnailUrl.isEmpty()) {
                Glide.with(binding.imgPlaylistThumb.getContext())
                        .load(item.thumbnailUrl)
                        .placeholder(R.drawable.bg_media_placeholder)
                        .into(binding.imgPlaylistThumb);
            } else {
                binding.imgPlaylistThumb.setImageResource(R.drawable.bg_media_placeholder);
            }

            binding.cbPlaylistItem.setOnCheckedChangeListener(null);
            binding.cbPlaylistItem.setChecked(item.isSelected);
            binding.cbPlaylistItem.setOnCheckedChangeListener((buttonView, isChecked) -> {
                item.isSelected = isChecked;
                notifyCount();
            });
            binding.getRoot().setOnClickListener(v -> binding.cbPlaylistItem.performClick());
        }
    }
}
