package com.example.chatapp;

import android.content.Context;
import android.os.Build;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.chatapp.network.ApiAccount;
import com.example.chatapp.network.ApiException;
import com.example.chatapp.network.AuthApi;
import com.example.chatapp.network.NetworkClient;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(AndroidJUnit4.class)
public class AuthApiTest {

    @Test
    public void loginKeepsSessionAndLogoutEndsIt() throws Exception {
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

        NetworkClient.getInstance().clearSession();

        AuthApi auth = new AuthApi();

        // Incorrect credentials must be rejected.
        try {
            auth.login("server_test", "DefinitelyWrong123!");
            fail("An incorrect password should be rejected.");
        } catch (ApiException exception) {
            assertEquals(401, exception.getStatusCode());
        }

        // Use the test account we created on the server earlier.
        ApiAccount signedIn = auth.login(
                "server_test",
                "ChatTest123!"
        );

        try {
            assertEquals("server_test", signedIn.getUsername());
            assertTrue(signedIn.getId() > 0);

            // This separate request must reuse the login cookie.
            ApiAccount current = auth.getCurrentAccount();

            assertEquals(signedIn.getId(), current.getId());
            assertEquals(
                    signedIn.getUsername(),
                    current.getUsername()
            );
        } finally {
            auth.logout();
        }

        // After logout, the account endpoint must reject access.
        try {
            auth.getCurrentAccount();
            fail("Access should be rejected after logout.");
        } catch (ApiException exception) {
            assertEquals(401, exception.getStatusCode());
        }
    }
}