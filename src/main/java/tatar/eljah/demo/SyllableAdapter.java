package tatar.eljah.demo;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;

import tatar.eljah.R;
import tatar.eljah.model.VietnameseSyllable;

import java.util.List;

public class SyllableAdapter extends RecyclerView.Adapter<SyllableAdapter.ViewHolder> {

    public interface OnSyllableClickListener {
        void onSyllableClick(VietnameseSyllable syllable);
    }

    private final List<VietnameseSyllable> items;
    private final OnSyllableClickListener listener;

    public SyllableAdapter(List<VietnameseSyllable> items, OnSyllableClickListener listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_syllable, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        final VietnameseSyllable syllable = items.get(position);
        holder.textView.setText(syllable.getText() + " (" + syllable.getToneName() + ")");
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) {
                    listener.onSyllableClick(syllable);
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView textView;

        ViewHolder(View itemView) {
            super(itemView);
            textView = itemView.findViewById(R.id.tv_syllable);
        }
    }
}
