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
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.signature.ObjectKey;
import com.example.chatapp.R;
import com.example.chatapp.database.Message;
import com.example.chatapp.network.ServerConfig;
import com.example.chatapp.utils.SessionManager;
import com.google.android.material.card.MaterialCardView;

import java.io.File;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

import okhttp3.HttpUrl;

public class MessageAdapter
        extends RecyclerView.Adapter<MessageAdapter.MessageViewHolder> {

    private static final Pattern PHOTO_NAME = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-"
                    + "[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png)"
    );

    private final String currentUser;
    private final List<Message> messages = new ArrayList<>();

    private final DateFormat dateFormat = DateFormat.getDateTimeInstance(
            DateFormat.SHORT,
            DateFormat.SHORT
    );

    public MessageAdapter(String currentUser) {
        this.currentUser = currentUser;
        setHasStableIds(true);
    }

    public void setMessages(List<Message> updatedMessages) {
        messages.clear();

        if (updatedMessages != null) {
            messages.addAll(updatedMessages);
        }

        notifyDataSetChanged();
    }

    @Override
    public long getItemId(int position) {
        return messages.get(position).getId();
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

        holder.row.setGravity(
                sentByMe ? Gravity.END : Gravity.START
        );

        holder.bubble.setCardBackgroundColor(
                ContextCompat.getColor(
                        context,
                        sentByMe
                                ? R.color.chat_sent
                                : R.color.chat_received
                )
        );

        holder.sender.setText(
                sentByMe
                        ? context.getString(R.string.you)
                        : message.getSender()
        );

        holder.text.setText(message.getContent());
        holder.text.setVisibility(
                message.getContent().isEmpty()
                        ? View.GONE
                        : View.VISIBLE
        );

        holder.time.setText(
                dateFormat.format(new Date(message.getTimestamp()))
        );

        bindPhoto(holder, message, context);
    }

    private void bindPhoto(
            MessageViewHolder holder,
            Message message,
            Context context
    ) {
        Glide.with(holder.image).clear(holder.image);
        holder.image.setImageDrawable(null);

        String source = message.getImagePath();

        if (source == null || source.isEmpty()) {
            holder.image.setVisibility(View.GONE);
            return;
        }

        holder.image.setVisibility(View.VISIBLE);

        SessionManager session = new SessionManager(context);

        if (!session.isLoggedIn()
                || !currentUser.equals(session.getUsername())) {
            holder.image.setImageResource(
                    android.R.drawable.ic_menu_gallery
            );
            return;
        }

        Object model = createPhotoModel(source);

        if (model == null) {
            holder.image.setImageResource(
                    android.R.drawable.ic_menu_gallery
            );
            return;
        }

        String cacheIdentity = ServerConfig.BASE_URL
                + "|" + session.getUserId()
                + "|" + currentUser;

        Glide.with(holder.image)
                .load(model)
                .signature(new ObjectKey(cacheIdentity))
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_gallery)
                .centerCrop()
                .into(holder.image);
    }

    @Nullable
    private Object createPhotoModel(String source) {
        HttpUrl imageUrl = HttpUrl.parse(source);

        if (imageUrl == null) {
            File file = new File(source);
            return file.isAbsolute() ? file : null;
        }

        HttpUrl server = HttpUrl.get(ServerConfig.BASE_URL);

        if (!imageUrl.scheme().equals(server.scheme())
                || !imageUrl.host().equals(server.host())
                || imageUrl.port() != server.port()
                || !imageUrl.username().isEmpty()
                || !imageUrl.password().isEmpty()
                || imageUrl.query() != null
                || imageUrl.fragment() != null) {
            return null;
        }

        String prefix = server.encodedPath() + "api/photos/";
        String path = imageUrl.encodedPath();

        if (!path.startsWith(prefix)) {
            return null;
        }

        String filename = path.substring(prefix.length());

        if (!PHOTO_NAME.matcher(filename).matches()) {
            return null;
        }

        return new GlideUrl(imageUrl.toString());
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