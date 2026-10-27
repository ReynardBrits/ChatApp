package com.example.chatapp.utils;

import android.content.Context;
import android.graphics.Bitmap;

import androidx.annotation.WorkerThread;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy;
import com.bumptech.glide.request.FutureTarget;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class PhotoPreparer {

    private static final int MAX_EDGE = 1600;
    private static final long MAX_BYTES = 5L * 1024 * 1024;

    private PhotoPreparer() {
    }

    @WorkerThread
    public static File prepare(Context context, File original)
            throws IOException {

        if (original == null
                || !original.isFile()
                || original.length() == 0) {
            throw new IOException(
                    "The captured photo could not be found."
            );
        }

        Context applicationContext = context.getApplicationContext();

        FutureTarget<Bitmap> target = Glide.with(applicationContext)
                .asBitmap()
                .load(original)
                .downsample(DownsampleStrategy.AT_MOST)
                .disallowHardwareConfig()
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(true)
                .submit(MAX_EDGE, MAX_EDGE);

        File preparedPhoto = null;
        boolean success = false;

        try {
            Bitmap bitmap = target.get(30, TimeUnit.SECONDS);

            File directory = new File(
                    applicationContext.getCacheDir(),
                    "photo_uploads"
            );

            if (!directory.isDirectory() && !directory.mkdirs()) {
                throw new IOException(
                        "Could not create the photo upload folder."
                );
            }

            preparedPhoto = File.createTempFile(
                    "upload_",
                    ".jpg",
                    directory
            );

            for (int quality = 85; quality >= 55; quality -= 15) {
                try (FileOutputStream output =
                             new FileOutputStream(preparedPhoto)) {

                    if (!bitmap.compress(
                            Bitmap.CompressFormat.JPEG,
                            quality,
                            output
                    )) {
                        throw new IOException(
                                "Could not prepare the photo."
                        );
                    }
                }

                long size = preparedPhoto.length();

                if (size > 0 && size <= MAX_BYTES) {
                    success = true;
                    return preparedPhoto;
                }
            }

            throw new IOException(
                    "Could not reduce the photo to the upload limit."
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();

            throw new IOException(
                    "Photo preparation was interrupted.",
                    exception
            );
        } catch (ExecutionException | TimeoutException exception) {
            throw new IOException(
                    "Could not read the captured photo.",
                    exception
            );
        } finally {
            if (!success && preparedPhoto != null) {
                preparedPhoto.delete();
            }

            Glide.with(applicationContext).clear(target);
        }
    }
}