package com.example.chatapp.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface MessageDao {

    @Insert
    long insertMessage(Message message);

    @Query("SELECT * FROM messages ORDER BY timestamp ASC, id ASC")
    List<Message> getAllMessages();

    @Query(
            "SELECT * FROM messages WHERE " +
                    "(sender = :currentUser AND receiver = :otherUser) OR " +
                    "(sender = :otherUser AND receiver = :currentUser) " +
                    "ORDER BY timestamp ASC, id ASC"
    )
    LiveData<List<Message>> getConversation(
            String currentUser,
            String otherUser
    );
}