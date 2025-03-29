/*
 * This file is part of Tack Android.
 *
 * Tack Android is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Tack Android is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Tack Android. If not, see http://www.gnu.org/licenses/.
 *
 * Copyright (c) 2020-2025 by Patrick Zedler
 */

package xyz.zedler.patrick.tack.recyclerview.adapter;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView.ViewHolder;
import com.google.android.material.chip.Chip;
import xyz.zedler.patrick.tack.database.relations.SongWithParts;
import xyz.zedler.patrick.tack.databinding.RowSongChipBinding;

public class SongChipAdapter extends ListAdapter<SongWithParts, ViewHolder> {

  private final static String TAG = SongChipAdapter.class.getSimpleName();

  private final OnSongClickListener listener;
  private boolean clickable;

  public SongChipAdapter(@NonNull OnSongClickListener listener, boolean clickable) {
    super(new SongWithPartsDiffCallback());
    this.listener = listener;
    this.clickable = clickable;
  }

  @NonNull
  @Override
  public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    RowSongChipBinding binding = RowSongChipBinding.inflate(
        LayoutInflater.from(parent.getContext()), parent, false
    );
    return new SongChipViewHolder(binding);
  }

  @Override
  public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
    SongWithParts songWithParts = getItem(holder.getBindingAdapterPosition());
    SongChipViewHolder songHolder = (SongChipViewHolder) holder;
    songHolder.binding.chipRowSong.setText(songWithParts.getSong().getName());
    songHolder.binding.chipRowSong.setClickable(clickable);
    if (clickable) {
      songHolder.binding.chipRowSong.setOnClickListener(
          v -> listener.onSongClick(songHolder.binding.chipRowSong, songWithParts)
      );
    } else {
      songHolder.binding.chipRowSong.setOnClickListener(null);
    }
  }

  @SuppressLint("NotifyDataSetChanged")
  public void setClickable(boolean clickable) {
    if ((this.clickable && !clickable) || (!this.clickable && clickable)) {
      this.clickable = clickable;
      notifyDataSetChanged();
    }
  }

  public static class SongChipViewHolder extends ViewHolder {

    private final RowSongChipBinding binding;

    public SongChipViewHolder(RowSongChipBinding binding) {
      super(binding.getRoot());
      this.binding = binding;
    }
  }

  public interface OnSongClickListener {
    void onSongClick(Chip chip, @NonNull SongWithParts song);
  }

  static class SongWithPartsDiffCallback extends DiffUtil.ItemCallback<SongWithParts> {

    @Override
    public boolean areItemsTheSame(
        @NonNull SongWithParts oldItem,
        @NonNull SongWithParts newItem
    ) {
      return oldItem.getSong().getId().equals(newItem.getSong().getId());
    }

    @Override
    public boolean areContentsTheSame(
        @NonNull SongWithParts oldItem,
        @NonNull SongWithParts newItem
    ) {
      return oldItem.equals(newItem);
    }
  }
}