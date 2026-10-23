package com.ai.apex;
import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;
import io.noties.markwon.Markwon;
import java.util.List;
import java.util.Objects;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ViewHolder> {
    private final List<String[]> messages;
    private final Markwon markwon;

    public ChatAdapter(Context context, List<String[]> messages) {
        this.messages = messages;
        this.markwon = Markwon.create(context);
    }

    public void updateMessages(List<String[]> newMessages) {
        DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return messages.size(); }
            @Override public int getNewListSize() { return newMessages.size(); }
            @Override public boolean areItemsTheSame(int o, int n) { return Objects.equals(messages.get(o)[0], newMessages.get(n)[0]); }
            @Override public boolean areContentsTheSame(int o, int n) { return Objects.equals(messages.get(o)[1], newMessages.get(n)[1]); }
        });
        messages.clear();
        messages.addAll(newMessages);
        result.dispatchUpdatesTo(this);
    }

    @NonNull @Override public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_message, parent, false));
    }
    @Override public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String[] msg = messages.get(position);
        boolean isUser = msg[0].equals("我");
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) holder.tvContent.getLayoutParams();
        params.gravity = isUser ? Gravity.END : Gravity.START;
        holder.tvContent.setLayoutParams(params);
        holder.tvContent.setBackgroundResource(isUser ? R.drawable.bubble_user : R.drawable.bubble_ai);
        if (isUser) {
            holder.tvContent.setText(msg[1]); holder.tvContent.setTextColor(Color.WHITE);
        } else {
            markwon.setMarkdown(holder.tvContent, msg[1]); holder.tvContent.setTextColor(0xFFE2E8F0);
        }
    }
    @Override public int getItemCount() { return messages.size(); }
    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvContent;
        ViewHolder(View v) { super(v); tvContent = v.findViewById(R.id.tvContent); }
    }
}
