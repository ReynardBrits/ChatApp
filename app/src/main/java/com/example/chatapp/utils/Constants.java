package com.example.chatapp.utils;

public final class Constants {

    public static final String DATABASE_NAME = "chat_database";

    public static final String PREFERENCES_NAME = "chat_session";
    public static final String CURRENT_USER = "current_user";
    public static final String CURRENT_USER_ID = "current_user_id";

    public static final String EXTRA_RECEIVER = "receiver";
    public static final String EXTRA_OPEN_CAMERA = "open_camera";

    public static final String STATE_PHOTO_PATH = "pending_photo_path";
    public static final String IMAGE_DIRECTORY = "chat_images";

    public static final int MIN_PASSWORD_LENGTH = 8;
    public static final int MAX_PASSWORD_LENGTH = 128;
    public static final int MAX_MESSAGE_LENGTH = 2000;

    private Constants() {
        // Prevents creating an instance of this utility class.
    }
}
