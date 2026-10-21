package com.example.chatapp.adapters;

import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.chatapp.R;
import com.example.chatapp.database.Message;
import com.google.android.material.card.MaterialCardView;

import java.io.File;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class MessageAdapter
        extends RecyclerView.Adapter<MessageAdapter.MessageViewHolder> {

    private final String currentUser;
    private final List<Message> messages = new ArrayList<>();

    private final DateFormat dateFormat = DateFormat.getDateTimeInstance(
            DateFormat.SHORT,
            DateFormat.SHORT
    );

    public MessageAdapter(String currentUser) {
        this.currentUser = currentUser;
    }

    public void setMessages(List<Message> updatedMessages) {
        messages.clear();

        if (updatedMessages != null) {
            messages.addAll(updatedMessages);
        }

        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_message, parent, false);

        return new MessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(
            @NonNull MessageViewHolder holder,
            int position
    ) {
        Message message = messages.get(position);
        Context context = holder.itemView.getContext();

        boolean sentByMe = currentUser.equals(message.getSender());

        // Sent messages appear on the right.
        holder.row.setGravity(sentByMe ? Gravity.END : Gravity.START);

        int bubbleColor = sentByMe
                ? R.color.chat_sent
                : R.color.chat_received;

        holder.bubble.setCardBackgroundColor(
                ContextCompat.getColor(context, bubbleColor)
        );

        holder.sender.setText(
                sentByMe
                        ? context.getString(R.string.you)
                        : message.getSender()
        );

        // Photo messages can have no text.
        holder.text.setText(message.getContent());
        holder.text.setVisibility(
                message.getContent().isEmpty() ? View.GONE : View.VISIBLE
        );

        holder.time.setText(
                dateFormat.format(new Date(message.getTimestamp()))
        );

        String imagePath = message.getImagePath();

        if (imagePath == null || imagePath.isEmpty()) {
            Glide.with(holder.image).clear(holder.image);
            holder.image.setImageDrawable(null);
            holder.image.setVisibility(View.GONE);
        } else {
            holder.image.setVisibility(View.VISIBLE);

            Glide.with(holder.image)
                    .load(new File(imagePath))
                    .centerCrop()
                    .into(holder.image);
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    @Override
    public void onViewRecycled(@NonNull MessageViewHolder holder) {
        Glide.with(holder.image).clear(holder.image);
        holder.image.setImageDrawable(null);
        super.onViewRecycled(holder);
    }

    static class MessageViewHolder extends RecyclerView.ViewHolder {

        final LinearLayout row;
        final MaterialCardView bubble;
        final TextView sender;
        final TextView text;
        final TextView time;
        final ImageView image;

        MessageViewHolder(@NonNull View itemView) {
            super(itemView);

            row = itemView.findViewById(R.id.message_row);
            bubble = itemView.findViewById(R.id.message_bubble);
            sender = itemView.findViewById(R.id.message_sender);
            text = itemView.findViewById(R.id.message_text);
            time = itemView.findViewById(R.id.message_time);
            image = itemView.findViewById(R.id.message_image);
        }
    }
}