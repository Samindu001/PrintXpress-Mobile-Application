package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String LOGIN_URL =
            "http://10.0.2.2:3000/api/auth/login";

    private EditText etEmail, etPassword;

    private Button btnLogin;
    private Button btnOpenAdmin;
    private Button btnOpenRegister;

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);

        btnLogin = findViewById(R.id.btnLogin);
        btnOpenAdmin = findViewById(R.id.btnOpenAdmin);
        btnOpenRegister = findViewById(R.id.btnOpenRegister);

        // Customer Login
        btnLogin.setOnClickListener(v -> loginUser());

        // Admin Login
        if (btnOpenAdmin != null) {
            btnOpenAdmin.setOnClickListener(v -> {
                Intent intent = new Intent(
                        MainActivity.this,
                        AdminActivity.class
                );
                startActivity(intent);
            });
        }

        // Customer Registration
        if (btnOpenRegister != null) {
            btnOpenRegister.setOnClickListener(v -> {
                Intent intent = new Intent(
                        MainActivity.this,
                        RegisterActivity.class
                );
                startActivity(intent);
            });
        }
    }

    private void loginUser() {

        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(
                    this,
                    "Enter email and password",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        btnLogin.setEnabled(false);
        btnLogin.setText("Logging in...");

        executor.execute(() -> {

            HttpURLConnection connection = null;

            try {
                URL url = new URL(LOGIN_URL);

                connection = (HttpURLConnection)
                        url.openConnection();

                connection.setRequestMethod("POST");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                connection.setDoOutput(true);

                connection.setRequestProperty(
                        "Content-Type",
                        "application/json; charset=UTF-8"
                );

                JSONObject request = new JSONObject();
                request.put("email", email);
                request.put("password", password);

                byte[] requestData = request.toString()
                        .getBytes(StandardCharsets.UTF_8);

                try (OutputStream output =
                             connection.getOutputStream()) {
                    output.write(requestData);
                }

                int responseCode = connection.getResponseCode();

                InputStream stream =
                        responseCode >= 200 && responseCode < 300
                                ? connection.getInputStream()
                                : connection.getErrorStream();

                StringBuilder response = new StringBuilder();

                if (stream != null) {
                    try (BufferedReader reader =
                                 new BufferedReader(
                                         new InputStreamReader(
                                                 stream,
                                                 StandardCharsets.UTF_8
                                         ))) {

                        String line;

                        while ((line = reader.readLine()) != null) {
                            response.append(line);
                        }
                    }
                }

                JSONObject json =
                        new JSONObject(response.toString());

                if (responseCode == HttpURLConnection.HTTP_OK) {

                    JSONObject user = json.getJSONObject("user");

                    int userId = user.getInt("id");
                    String userName = user.getString("name");
                    String userEmail = user.getString("email");

                    runOnUiThread(() -> {

                        if (isFinishing() || isDestroyed()) {
                            return;
                        }

                        btnLogin.setEnabled(true);
                        btnLogin.setText("Login");

                        Intent intent = new Intent(
                                MainActivity.this,
                                HomeActivity.class
                        );

                        intent.putExtra("USER_ID", userId);
                        intent.putExtra("USER_NAME", userName);
                        intent.putExtra("USER_EMAIL", userEmail);

                        startActivity(intent);
                        finish();
                    });

                } else {

                    String message = json.optString(
                            "message",
                            "Login failed"
                    );

                    runOnUiThread(() -> {

                        if (isFinishing() || isDestroyed()) {
                            return;
                        }

                        btnLogin.setEnabled(true);
                        btnLogin.setText("Login");

                        Toast.makeText(
                                MainActivity.this,
                                message,
                                Toast.LENGTH_LONG
                        ).show();
                    });
                }

            } catch (Exception e) {

                Log.e("PrintXpress", "Login error", e);

                runOnUiThread(() -> {

                    if (isFinishing() || isDestroyed()) {
                        return;
                    }

                    btnLogin.setEnabled(true);
                    btnLogin.setText("Login");

                    Toast.makeText(
                            MainActivity.this,
                            "Could not connect to server",
                            Toast.LENGTH_LONG
                    ).show();
                });

            } finally {

                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}