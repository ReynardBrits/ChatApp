package com.example.chatapp.adapters;

import android.content.Context;
import android.graphics.Typeface;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.chatapp.R;
import com.example.chatapp.network.ApiAccount;
import com.example.chatapp.network.ApiConversation;
import com.example.chatapp.network.ApiMessage;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class ConversationAdapter extends ListAdapter<
        ApiConversation,
        ConversationAdapter.ConversationViewHolder
        > {

    public interface OnConversationClickListener {
        void onConversationClick(ApiAccount user);
    }

    private static final DiffUtil.ItemCallback<ApiConversation> DIFF =
            new DiffUtil.ItemCallback<ApiConversation>() {
                @Override
                public boolean areItemsTheSame(
                        @NonNull ApiConversation oldItem,
                        @NonNull ApiConversation newItem
                ) {
                    return oldItem.getUser().getId()
                            == newItem.getUser().getId();
                }

                @Override
                public boolean areContentsTheSame(
                        @NonNull ApiConversation oldItem,
                        @NonNull ApiConversation newItem
                ) {
                    ApiMessage oldMessage = oldItem.getLastMessage();
                    ApiMessage newMessage = newItem.getLastMessage();

                    return oldItem.getUser().getUsername().equals(
                            newItem.getUser().getUsername()
                    )
                            && oldItem.getUnreadCount() == newItem.getUnreadCount()
                            && oldMessage.getId() == newMessage.getId()
                            && oldMessage.getTimestamp() == newMessage.getTimestamp()
                            && oldMessage.getSender().equals(newMessage.getSender())
                            && oldMessage.getReceiver().equals(newMessage.getReceiver())
                            && oldMessage.getContent().equals(newMessage.getContent())
                            && Objects.equals(
                            oldMessage.getImageName(),
                            newMessage.getImageName()
                    );
                }
            };

    private final String currentUsername;
    private final OnConversationClickListener listener;

    private final NumberFormat countFormat =
            NumberFormat.getIntegerInstance();

    public ConversationAdapter(
            String currentUsername,
            OnConversationClickListener listener
    ) {
        super(DIFF);

        if (currentUsername == null
                || !currentUsername.matches("[a-z0-9_]{3,24}")) {
            throw new IllegalArgumentException(
                    "Invalid signed-in username."
            );
        }

        this.currentUsername = currentUsername;
        this.listener = Objects.requireNonNull(listener);

        setHasStableIds(true);
    }

    public void setConversations(
            @Nullable List<ApiConversation> conversations
    ) {
        submitList(conversations == null
                ? Collections.emptyList()
                : new ArrayList<>(conversations));
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).getUser().getId();
    }

    @NonNull
    @Override
    public ConversationViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_conversation, parent, false);

        ConversationViewHolder holder =
                new ConversationViewHolder(view);

        view.setOnClickListener(clicked -> {
            int position = holder.getBindingAdapterPosition();

            if (position != RecyclerView.NO_POSITION) {
                listener.onConversationClick(
                        getItem(position).getUser()
                );
            }
        });

        return holder;
    }

    @Override
    public void onBindViewHolder(
            @NonNull ConversationViewHolder holder,
            int position
    ) {
        ApiConversation conversation = getItem(position);
        ApiMessage message = conversation.getLastMessage();
        Context context = holder.itemView.getContext();

        String username = conversation.getUser().getUsername();

        String preview = message.hasPhoto()
                ? context.getString(R.string.shared_photo)
                : message.getContent()
                .replaceAll("\\s+", " ")
                .trim();

        if (currentUsername.equals(message.getSender())) {
            preview = context.getString(R.string.you)
                    + ": " + preview;
        }

        String time = formatTime(
                context,
                message.getTimestamp()
        );

        boolean unread = conversation.hasUnreadMessages();
        long unreadCount = conversation.getUnreadCount();

        holder.initials.setText(makeInitials(username));
        holder.username.setText(username);
        holder.preview.setText(preview);
        holder.time.setText(time);

        holder.username.setTypeface(
                null,
                unread ? Typeface.BOLD : Typeface.NORMAL
        );

        holder.preview.setTypeface(
                null,
                unread ? Typeface.BOLD : Typeface.NORMAL
        );

        holder.preview.setTextColor(
                ContextCompat.getColor(
                        context,
                        unread
                                ? R.color.chat_text_primary
                                : R.color.chat_text_secondary
                )
        );

        holder.time.setTextColor(
                ContextCompat.getColor(
                        context,
                        unread
                                ? R.color.chat_primary
                                : R.color.chat_text_secondary
                )
        );

        holder.unreadBadge.setVisibility(
                unread ? View.VISIBLE : View.GONE
        );

        holder.unreadCount.setText(
                unread
                        ? (unreadCount > 99
                        ? "99+"
                        : countFormat.format(unreadCount))
                        : ""
        );

        String description = context.getString(
                R.string.chat_with,
                username
        ) + ". " + preview + ". " + time;

        if (unread) {
            description += ". Unread: "
                    + countFormat.format(unreadCount);
        }

        holder.itemView.setContentDescription(description);
    }

    private static String formatTime(
            Context context,
            long timestamp
    ) {
        if (DateUtils.isToday(timestamp)) {
            return DateFormat.getTimeFormat(context)
                    .format(new Date(timestamp));
        }

        return DateUtils.formatDateTime(
                context,
                timestamp,
                DateUtils.FORMAT_SHOW_DATE
                        | DateUtils.FORMAT_ABBREV_MONTH
        );
    }

    private static String makeInitials(String username) {
        StringBuilder initials = new StringBuilder(2);

        for (String part : username.split("_+")) {
            if (!part.isEmpty()) {
                initials.append(part.charAt(0));
            }

            if (initials.length() == 2) {
                break;
            }
        }

        return initials.length() == 0
                ? "?"
                : initials.toString().toUpperCase(Locale.ROOT);
    }

    public static final class ConversationViewHolder
            extends RecyclerView.ViewHolder {

        final TextView initials;
        final TextView username;
        final TextView preview;
        final TextView time;
        final View unreadBadge;
        final TextView unreadCount;

        ConversationViewHolder(@NonNull View itemView) {
            super(itemView);

            initials = itemView.findViewById(
                    R.id.conversation_initials
            );

            username = itemView.findViewById(
                    R.id.conversation_username
            );

            preview = itemView.findViewById(
                    R.id.conversation_preview
            );

            time = itemView.findViewById(
                    R.id.conversation_time
            );

            unreadBadge = itemView.findViewById(
                    R.id.conversation_unread_badge
            );

            unreadCount = itemView.findViewById(
                    R.id.conversation_unread_count
            );
        }
    }
}