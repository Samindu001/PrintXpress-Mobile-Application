
package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
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

public class RegisterActivity extends AppCompatActivity {

    private static final String REGISTER_URL =
            "http://10.0.2.2:3000/api/auth/register";

    private EditText etName;
    private EditText etEmail;
    private EditText etPhone;
    private EditText etPassword;
    private EditText etConfirmPassword;
    private Button btnRegister;
    private TextView tvBack;

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        etName = findViewById(R.id.etName);
        etEmail = findViewById(R.id.etEmail);
        etPhone = findViewById(R.id.etPhone);
        etPassword = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        btnRegister = findViewById(R.id.btnRegister);
        tvBack = findViewById(R.id.tvBack);

        btnRegister.setOnClickListener(v -> registerUser());

        if (tvBack != null) {
            tvBack.setOnClickListener(v -> finish());
        }
    }

    private void registerUser() {

        String name = etName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();
        String password = etPassword.getText().toString();
        String confirmPassword =
                etConfirmPassword.getText().toString();

        // Validate required fields
        if (name.isEmpty() || email.isEmpty()
                || phone.isEmpty() || password.isEmpty()
                || confirmPassword.isEmpty()) {

            Toast.makeText(this,
                    "Please fill in all fields.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        // Validate email
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError("Enter a valid email address");
            etEmail.requestFocus();
            return;
        }

        // Validate password
        if (password.length() < 8) {
            etPassword.setError(
                    "Password must contain at least 8 characters");
            etPassword.requestFocus();
            return;
        }

        // Confirm password
        if (!password.equals(confirmPassword)) {
            etConfirmPassword.setError("Passwords do not match");
            etConfirmPassword.requestFocus();
            return;
        }

        // Prevent repeated clicks while registering
        btnRegister.setEnabled(false);
        btnRegister.setText("Creating Account...");

        executor.execute(() -> {

            HttpURLConnection connection = null;
            int responseCode = -1;
            String responseBody = "";

            try {
                URL url = new URL(REGISTER_URL);

                connection =
                        (HttpURLConnection) url.openConnection();

                connection.setRequestMethod("POST");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                connection.setDoOutput(true);

                connection.setRequestProperty(
                        "Content-Type",
                        "application/json; charset=UTF-8");

                connection.setRequestProperty(
                        "Accept",
                        "application/json");

                JSONObject requestBody = new JSONObject();
                requestBody.put("name", name);
                requestBody.put("email", email);
                requestBody.put("phone", phone);
                requestBody.put("password", password);

                byte[] data = requestBody.toString()
                        .getBytes(StandardCharsets.UTF_8);

                try (OutputStream outputStream =
                             connection.getOutputStream()) {
                    outputStream.write(data);
                    outputStream.flush();
                }

                responseCode = connection.getResponseCode();

                InputStream inputStream =
                        (responseCode >= 200 && responseCode < 400)
                                ? connection.getInputStream()
                                : connection.getErrorStream();

                if (inputStream != null) {
                    StringBuilder response = new StringBuilder();

                    try (BufferedReader reader =
                                 new BufferedReader(
                                         new InputStreamReader(
                                                 inputStream,
                                                 StandardCharsets.UTF_8))) {

                        String line;

                        while ((line = reader.readLine()) != null) {
                            response.append(line);
                        }
                    }

                    responseBody = response.toString();
                }

                String finalResponseBody = responseBody;
                int finalResponseCode = responseCode;

                runOnUiThread(() -> {

                    btnRegister.setEnabled(true);
                    btnRegister.setText("Create Account");

                    try {
                        JSONObject json =
                                new JSONObject(finalResponseBody);

                        if (finalResponseCode == 201
                                || finalResponseCode == 200) {

                            Toast.makeText(
                                    RegisterActivity.this,
                                    "Account created successfully!",
                                    Toast.LENGTH_LONG
                            ).show();

                            Intent intent = new Intent(
                                    RegisterActivity.this,
                                    MainActivity.class
                            );

                            intent.addFlags(
                                    Intent.FLAG_ACTIVITY_CLEAR_TOP
                                            | Intent.FLAG_ACTIVITY_SINGLE_TOP
                            );

                            startActivity(intent);
                            finish();

                        } else {
                            String message = json.optString(
                                    "message",
                                    json.optString(
                                            "error",
                                            "Registration failed. Please try again."
                                    )
                            );

                            Toast.makeText(
                                    RegisterActivity.this,
                                    message,
                                    Toast.LENGTH_LONG
                            ).show();
                        }

                    } catch (Exception e) {

                        String message =
                                (finalResponseCode == 201
                                        || finalResponseCode == 200)
                                        ? "Account created successfully!"
                                        : "Registration failed. Please try again.";

                        Toast.makeText(
                                RegisterActivity.this,
                                message,
                                Toast.LENGTH_LONG
                        ).show();

                        if (finalResponseCode == 201
                                || finalResponseCode == 200) {
                            startActivity(new Intent(
                                    RegisterActivity.this,
                                    MainActivity.class
                            ));
                            finish();
                        }
                    }
                });

            } catch (Exception e) {

                runOnUiThread(() -> {
                    btnRegister.setEnabled(true);
                    btnRegister.setText("Create Account");

                    Toast.makeText(
                            RegisterActivity.this,
                            "Cannot connect to server. Please check that the backend is running.",
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
