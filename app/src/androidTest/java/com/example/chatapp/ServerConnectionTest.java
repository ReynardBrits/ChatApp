package com.example.chatapp;

import android.content.Context;
import android.os.Build;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.chatapp.network.NetworkClient;
import com.example.chatapp.network.ServerConfig;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(AndroidJUnit4.class)
public class ServerConnectionTest {

    @Test
    public void emulatorCanReachServer() throws Exception {
        Context context = InstrumentationRegistry
                .getInstrumentation()
                .getTargetContext();

        if (Build.VERSION.SDK_INT >= 37) {
            InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .grantRuntimePermission(
                            context.getPackageName(),
                            "android.permission.ACCESS_LOCAL_NETWORK"
                    );
        }

        Request request = new Request.Builder()
                .url(ServerConfig.BASE_URL + "api/health")
                .get()
                .build();

        try (Response response = NetworkClient.getInstance()
                .getHttpClient()
                .newCall(request)
                .execute()) {

            assertEquals("Server should return HTTP 200", 200, response.code());

            ResponseBody body = response.body();
            assertNotNull("Server response should have a body", body);

            JSONObject json = new JSONObject(body.string());
            assertEquals("ok", json.getString("status"));
        }
    }
}