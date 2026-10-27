package com.example.chatapp.database;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "messages",
        foreignKeys = {
                @ForeignKey(
                        entity = User.class,
                        parentColumns = "username",
                        childColumns = "sender",
                        onDelete = ForeignKey.RESTRICT
                ),
                @ForeignKey(
                        entity = User.class,
                        parentColumns = "username",
                        childColumns = "receiver",
                        onDelete = ForeignKey.RESTRICT
                )
        },
        indices = {
                @Index("sender"),
                @Index("receiver"),
                @Index("timestamp")
        }
)
public class Message {

    @PrimaryKey(autoGenerate = true)
    private long id;

    @NonNull
    private String sender = "";

    @NonNull
    private String receiver = "";

    @NonNull
    private String content = "";

    private long timestamp;

    @Nullable
    private String imagePath;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    @NonNull
    public String getSender() {
        return sender;
    }

    public void setSender(@NonNull String sender) {
        this.sender = sender;
    }

    @NonNull
    public String getReceiver() {
        return receiver;
    }

    public void setReceiver(@NonNull String receiver) {
        this.receiver = receiver;
    }

    @NonNull
    public String getContent() {
        return content;
    }

    public void setContent(@NonNull String content) {
        this.content = content;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    @Nullable
    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(@Nullable String imagePath) {
        this.imagePath = imagePath;
    }
}
