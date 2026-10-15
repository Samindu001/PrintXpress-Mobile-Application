package com.example.myapplication;

import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
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

public class AdminActivity extends AppCompatActivity {

    private static final String BASE_URL = "http://10.0.2.2:3000";

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    private LinearLayout layoutAdminLogin;
    private LinearLayout layoutAdminDashboard;
    private LinearLayout containerOrders;

    private EditText etAdminPassword;

    private Button btnAdminLogin;
    private Button btnRefreshOrders;
    private Button btnAdminLogout;

    private TextView tvTotalOrders;
    private TextView tvPendingOrders;
    private TextView tvProcessingOrders;
    private TextView tvCompletedOrders;
    private TextView tvNoOrders;

    private ProgressBar progressAdminLogin;
    private ProgressBar progressOrders;

    private String adminToken = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);

        layoutAdminLogin = findViewById(R.id.layoutAdminLogin);
        layoutAdminDashboard = findViewById(R.id.layoutAdminDashboard);
        containerOrders = findViewById(R.id.containerOrders);

        etAdminPassword = findViewById(R.id.etAdminPassword);

        btnAdminLogin = findViewById(R.id.btnAdminLogin);
        btnRefreshOrders = findViewById(R.id.btnRefreshOrders);
        btnAdminLogout = findViewById(R.id.btnAdminLogout);

        tvTotalOrders = findViewById(R.id.tvTotalOrders);
        tvPendingOrders = findViewById(R.id.tvPendingOrders);
        tvProcessingOrders = findViewById(R.id.tvProcessingOrders);
        tvCompletedOrders = findViewById(R.id.tvCompletedOrders);
        tvNoOrders = findViewById(R.id.tvNoOrders);

        progressAdminLogin = findViewById(R.id.progressAdminLogin);
        progressOrders = findViewById(R.id.progressOrders);

        btnAdminLogin.setOnClickListener(v -> loginAdmin());
        btnRefreshOrders.setOnClickListener(v -> loadOrders());
        btnAdminLogout.setOnClickListener(v -> logoutAdmin());
    }

    // ADMIN LOGIN
    private void loginAdmin() {
        String password = etAdminPassword.getText().toString();

        if (password.trim().isEmpty()) {
            etAdminPassword.setError("Enter admin password");
            return;
        }

        btnAdminLogin.setEnabled(false);
        progressAdminLogin.setVisibility(View.VISIBLE);

        executor.execute(() -> {
            HttpURLConnection connection = null;

            try {
                URL url = new URL(BASE_URL + "/api/admin/login");

                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                connection.setDoOutput(true);
                connection.setRequestProperty(
                        "Content-Type", "application/json; charset=UTF-8"
                );

                JSONObject body = new JSONObject();
                body.put("password", password);

                try (OutputStream os = connection.getOutputStream()) {
                    os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }

                int responseCode = connection.getResponseCode();
                String response = readResponse(connection, responseCode);
                JSONObject json = new JSONObject(response);

                if (responseCode >= 200 && responseCode < 300) {
                    String token = json.optString("token", "");

                    if (token.isEmpty()) {
                        throw new Exception(
                                "Server did not return an admin token."
                        );
                    }

                    runOnUiThread(() -> {
                        adminToken = token;

                        layoutAdminLogin.setVisibility(View.GONE);
                        layoutAdminDashboard.setVisibility(View.VISIBLE);

                        etAdminPassword.setText("");

                        Toast.makeText(
                                AdminActivity.this,
                                "Admin login successful!",
                                Toast.LENGTH_SHORT
                        ).show();

                        loadOrders();
                    });

                } else {
                    String message = json.optString(
                            "message", "Admin login failed."
                    );

                    runOnUiThread(() -> Toast.makeText(
                            AdminActivity.this,
                            message,
                            Toast.LENGTH_LONG
                    ).show());
                }

            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(
                        AdminActivity.this,
                        "Login error: " + e.getMessage(),
                        Toast.LENGTH_LONG
                ).show());

            } finally {
                if (connection != null) {
                    connection.disconnect();
                }

                runOnUiThread(() -> {
                    progressAdminLogin.setVisibility(View.GONE);
                    btnAdminLogin.setEnabled(true);
                });
            }
        });
    }

    // LOAD ORDERS
    private void loadOrders() {
        if (adminToken.isEmpty()) {
            return;
        }

        btnRefreshOrders.setEnabled(false);
        progressOrders.setVisibility(View.VISIBLE);
        tvNoOrders.setVisibility(View.GONE);

        executor.execute(() -> {
            HttpURLConnection connection = null;

            try {
                URL url = new URL(BASE_URL + "/api/admin/orders");

                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                connection.setRequestProperty(
                        "Authorization", "Bearer " + adminToken
                );

                int responseCode = connection.getResponseCode();
                String response = readResponse(connection, responseCode);
                JSONObject json = new JSONObject(response);

                if (responseCode == 401) {
                    runOnUiThread(() -> {
                        Toast.makeText(
                                AdminActivity.this,
                                "Admin session expired. Please log in again.",
                                Toast.LENGTH_LONG
                        ).show();
                        logoutAdmin();
                    });
                    return;
                }

                if (responseCode < 200 || responseCode >= 300) {
                    throw new Exception(
                            json.optString(
                                    "message", "Could not load orders."
                            )
                    );
                }

                JSONObject summary = json.optJSONObject("summary");
                JSONArray orders = json.optJSONArray("orders");

                if (summary == null) {
                    summary = new JSONObject();
                }

                if (orders == null) {
                    orders = new JSONArray();
                }

                final JSONObject finalSummary = summary;
                final JSONArray finalOrders = orders;

                runOnUiThread(() ->
                        displayOrders(finalSummary, finalOrders)
                );

            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(
                        AdminActivity.this,
                        "Orders error: " + e.getMessage(),
                        Toast.LENGTH_LONG
                ).show());

            } finally {
                if (connection != null) {
                    connection.disconnect();
                }

                runOnUiThread(() -> {
                    progressOrders.setVisibility(View.GONE);
                    btnRefreshOrders.setEnabled(true);
                });
            }
        });
    }

    // DISPLAY DASHBOARD SUMMARY AND ORDERS
    private void displayOrders(JSONObject summary, JSONArray orders) {
        tvTotalOrders.setText(
                String.valueOf(summary.optInt("total", orders.length()))
        );

        tvPendingOrders.setText(
                String.valueOf(summary.optInt("pending", 0))
        );

        tvProcessingOrders.setText(
                String.valueOf(summary.optInt("processing", 0))
        );

        tvCompletedOrders.setText(
                String.valueOf(summary.optInt("completed", 0))
        );

        containerOrders.removeAllViews();

        if (orders.length() == 0) {
            tvNoOrders.setText("No orders found.");
            tvNoOrders.setVisibility(View.VISIBLE);
            return;
        }

        tvNoOrders.setVisibility(View.GONE);

        for (int i = 0; i < orders.length(); i++) {
            JSONObject order = orders.optJSONObject(i);

            if (order != null) {
                addOrderCard(order);
            }
        }
    }

    // CREATE ORDER CARD
    private void addOrderCard(JSONObject order) {
        int orderId = order.optInt("id", -1);

        String customer = order.optString("customer_name", "Customer");
        String email = order.optString("customer_email", "");
        String product = order.optString("product", "Printing Product");
        String size = order.optString("card_size", "-");
        String paper = order.optString("paper_type", "-");
        String delivery = order.optString("delivery_method", "-");
        String address = order.optString("address", "");
        String status = order.optString("status", "Pending");
        int quantity = order.optInt("quantity", 0);
        double total = order.optDouble("total_price", 0);
        String createdAt = order.optString("created_at", "");

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackgroundColor(Color.WHITE);
        card.setElevation(dp(3));

        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        cardParams.bottomMargin = dp(14);
        card.setLayoutParams(cardParams);

        addText(card, "Order #" + orderId, 20, "#1746A2", true);
        addText(card, "Customer: " + customer, 16, "#172033", true);

        if (!email.isEmpty()) {
            addText(card, "Email: " + email, 14, "#687386", false);
        }

        addText(card, "Product: " + product, 15, "#172033", false);
        addText(card, "Quantity: " + quantity, 15, "#172033", false);
        addText(card, "Size: " + size, 14, "#687386", false);
        addText(card, "Material / Paper: " + paper, 14, "#687386", false);
        addText(card, "Delivery: " + delivery, 14, "#687386", false);

        if (!address.isEmpty()) {
            addText(card, "Address: " + address, 14, "#687386", false);
        }

        addText(
                card,
                String.format(
                        java.util.Locale.US,
                        "Total: LKR %.2f",
                        total
                ),
                17,
                "#1746A2",
                true
        );

        if (!createdAt.isEmpty()) {
            addText(card, "Date: " + createdAt, 12, "#687386", false);
        }

        // addText() already adds statusView to card.
        // Do not call card.addView(statusView) again.
        addText(
                card,
                "Status: " + status,
                16,
                String.format(
                        "#%06X",
                        (0xFFFFFF & statusColor(status))
                ),
                true
        );

        Button updateButton = new Button(this);
        updateButton.setText(nextStatus(status));
        updateButton.setAllCaps(false);
        updateButton.setEnabled(
                orderId > 0 && !"Completed".equalsIgnoreCase(status)
        );

        LinearLayout.LayoutParams buttonParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        buttonParams.topMargin = dp(10);
        updateButton.setLayoutParams(buttonParams);

        updateButton.setOnClickListener(v ->
                updateOrderStatus(
                        orderId,
                        nextStatus(status),
                        updateButton
                )
        );

        // Add only the button; status text is already in the card.
        card.addView(updateButton);
        containerOrders.addView(card);
    }

    // UPDATE ORDER STATUS
    private void updateOrderStatus(
            int orderId,
            String newStatus,
            Button button
    ) {
        if (orderId <= 0 || adminToken.isEmpty()) {
            Toast.makeText(
                    this,
                    "Please log in again.",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        button.setEnabled(false);

        executor.execute(() -> {
            HttpURLConnection connection = null;

            try {
                URL url = new URL(
                        BASE_URL + "/api/admin/orders/"
                                + orderId + "/status"
                );

                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("PATCH");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                connection.setDoOutput(true);

                connection.setRequestProperty(
                        "Content-Type", "application/json; charset=UTF-8"
                );

                connection.setRequestProperty(
                        "Authorization", "Bearer " + adminToken
                );

                JSONObject body = new JSONObject();
                body.put("status", newStatus);

                try (OutputStream os = connection.getOutputStream()) {
                    os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }

                int responseCode = connection.getResponseCode();
                String response = readResponse(connection, responseCode);
                JSONObject json = new JSONObject(response);

                if (responseCode >= 200 && responseCode < 300) {
                    runOnUiThread(() -> {
                        Toast.makeText(
                                AdminActivity.this,
                                "Order #" + orderId
                                        + " updated to " + newStatus,
                                Toast.LENGTH_SHORT
                        ).show();

                        loadOrders();
                    });

                } else if (responseCode == 401) {
                    runOnUiThread(() -> {
                        Toast.makeText(
                                AdminActivity.this,
                                "Admin session expired. Please log in again.",
                                Toast.LENGTH_LONG
                        ).show();

                        logoutAdmin();
                    });

                } else {
                    String message = json.optString(
                            "message", "Status update failed."
                    );

                    runOnUiThread(() -> Toast.makeText(
                            AdminActivity.this,
                            message,
                            Toast.LENGTH_LONG
                    ).show());
                }

            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(
                        AdminActivity.this,
                        "Update error: " + e.getMessage(),
                        Toast.LENGTH_LONG
                ).show());

            } finally {
                if (connection != null) {
                    connection.disconnect();
                }

                runOnUiThread(() -> button.setEnabled(true));
            }
        });
    }

    // NEXT ALLOWED STATUS
    private String nextStatus(String status) {
        if ("Pending".equalsIgnoreCase(status)) {
            return "Processing";
        }

        if ("Processing".equalsIgnoreCase(status)) {
            return "Completed";
        }

        return "Completed";
    }

    // STATUS COLORS
    private int statusColor(String status) {
        if ("Completed".equalsIgnoreCase(status)) {
            return Color.rgb(22, 131, 74);
        }

        if ("Processing".equalsIgnoreCase(status)) {
            return Color.rgb(107, 69, 181);
        }

        return Color.rgb(199, 123, 0);
    }

    // ADD TEXT TO ORDER CARD
    private TextView addText(
            LinearLayout parent,
            String text,
            int size,
            String color,
            boolean bold
    ) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(Color.parseColor(color));
        view.setPadding(0, dp(4), 0, dp(4));

        if (bold) {
            view.setTypeface(null, Typeface.BOLD);
        }

        parent.addView(view);
        return view;
    }

    private int dp(int value) {
        return (int) (
                value * getResources().getDisplayMetrics().density + 0.5f
        );
    }

    // READ SUCCESS OR ERROR RESPONSE
    private String readResponse(
            HttpURLConnection connection,
            int responseCode
    ) throws Exception {
        InputStream stream = responseCode >= 200 && responseCode < 400
                ? connection.getInputStream()
                : connection.getErrorStream();

        if (stream == null) {
            return "{}";
        }

        StringBuilder result = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        stream,
                        StandardCharsets.UTF_8
                ))) {
            String line;

            while ((line = reader.readLine()) != null) {
                result.append(line);
            }
        }

        return result.toString();
    }

    // ADMIN LOGOUT
    private void logoutAdmin() {
        adminToken = "";

        layoutAdminDashboard.setVisibility(View.GONE);
        layoutAdminLogin.setVisibility(View.VISIBLE);

        containerOrders.removeAllViews();
        etAdminPassword.setText("");

        tvTotalOrders.setText("0");
        tvPendingOrders.setText("0");
        tvProcessingOrders.setText("0");
        tvCompletedOrders.setText("0");

        tvNoOrders.setVisibility(View.GONE);
        progressOrders.setVisibility(View.GONE);
        progressAdminLogin.setVisibility(View.GONE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}