package com.example.myapplication;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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
import java.text.DecimalFormat;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MyOrdersActivity extends AppCompatActivity {

    private LinearLayout ordersContainer;
    private TextView tvMessage;
    private int userId;

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    private static final String BASE_URL =
            "http://10.0.2.2:3000";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        userId = getIntent().getIntExtra("USER_ID", -1);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(20, 20, 20, 20);
        root.setBackgroundColor(0xFFF5F6FA);

        TextView title = new TextView(this);
        title.setText("My Orders");
        title.setTextSize(25);
        title.setTextColor(0xFF4B00C8);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(0, 8, 0, 20);
        root.addView(title);

        tvMessage = new TextView(this);
        tvMessage.setText("Loading orders...");
        tvMessage.setTextSize(16);
        root.addView(tvMessage);

        ScrollView scrollView = new ScrollView(this);

        ordersContainer = new LinearLayout(this);
        ordersContainer.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(ordersContainer);

        root.addView(
                scrollView,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                        1
                )
        );

        setContentView(root);

        if (userId <= 0) {
            tvMessage.setText(
                    "User information missing. Please log in again."
            );
            return;
        }

        loadOrders();
    }

    private String readResponse(InputStream stream) throws Exception {
        if (stream == null) {
            return "";
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

    private JSONObject parseJson(String text) throws Exception {
        String response = text == null ? "" : text.trim();

        if (response.isEmpty()) {
            throw new Exception("The server returned an empty response.");
        }

        if (response.startsWith("<")) {
            throw new Exception(
                    "Server returned HTML instead of JSON. "
                            + "Check the API route."
            );
        }

        return new JSONObject(response);
    }

    private void loadOrders() {
        tvMessage.setText("Loading orders...");

        executor.execute(() -> {
            HttpURLConnection connection = null;

            try {
                URL url = new URL(
                        BASE_URL + "/api/orders/" + userId
                );

                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);

                int code = connection.getResponseCode();

                InputStream stream =
                        code >= 200 && code < 300
                                ? connection.getInputStream()
                                : connection.getErrorStream();

                String responseText = readResponse(stream);

                android.util.Log.d(
                        "MyOrdersActivity",
                        "LOAD URL: " + url
                                + " | HTTP: " + code
                                + " | Response: " + responseText
                );

                if (code < 200 || code >= 300) {
                    String message = "Could not load orders.";

                    try {
                        message = parseJson(responseText)
                                .optString("message", message);
                    } catch (Exception ignored) {
                    }

                    final String error = message;

                    runOnUiThread(() -> tvMessage.setText(error));
                    return;
                }

                JSONObject json = parseJson(responseText);
                JSONArray orders = json.optJSONArray("orders");

                if (orders == null) {
                    throw new Exception(
                            "Orders were not found in the server response."
                    );
                }

                runOnUiThread(() -> displayOrders(orders));

            } catch (Exception e) {
                android.util.Log.e(
                        "MyOrdersActivity",
                        "Load orders failed",
                        e
                );

                runOnUiThread(() ->
                        tvMessage.setText(
                                "Could not load orders. " + e.getMessage()
                        )
                );

            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private void displayOrders(JSONArray orders) {
        ordersContainer.removeAllViews();

        if (orders.length() == 0) {
            tvMessage.setText("You have not placed any orders yet.");
            return;
        }

        tvMessage.setText("Your orders: " + orders.length());

        DecimalFormat moneyFormat =
                new DecimalFormat("#,##0.00");

        for (int i = 0; i < orders.length(); i++) {
            JSONObject order = orders.optJSONObject(i);

            if (order == null) {
                continue;
            }

            final int orderId = order.optInt("id");

            String product = order.optString(
                    "product", "Business Cards"
            );
            String size = order.optString(
                    "card_size", "Not specified"
            );
            String paper = order.optString(
                    "paper_type", "Not specified"
            );
            int quantity = order.optInt("quantity");
            String method = order.optString(
                    "delivery_method", "Not specified"
            );
            double total = order.optDouble("total_price", 0);
            String status = order.optString("status", "Pending");
            String date = order.optString(
                    "created_at", "Not available"
            );

            LinearLayout cardLayout = new LinearLayout(this);
            cardLayout.setOrientation(LinearLayout.VERTICAL);
            cardLayout.setPadding(12, 12, 12, 12);
            cardLayout.setBackgroundColor(0xFFFFFFFF);
            cardLayout.setElevation(3);

            TextView card = new TextView(this);
            card.setText(
                    "Order #" + orderId
                            + "\nProduct: " + product
                            + "\nCard Size: " + size
                            + "\nPaper Type: " + paper
                            + "\nQuantity: " + quantity + " Cards"
                            + "\nDelivery: " + method
                            + "\nTotal: LKR "
                            + moneyFormat.format(total)
                            + "\nStatus: " + status
                            + "\nDate: " + date
            );
            card.setTextSize(14);
            card.setTextColor(0xFF222222);
            card.setPadding(8, 8, 8, 8);

            cardLayout.addView(card);

            if ("Pending".equalsIgnoreCase(status)) {
                Button cancelButton = new Button(this);
                cancelButton.setText("Cancel Order");

                cardLayout.addView(
                        cancelButton,
                        new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                );

                cancelButton.setOnClickListener(v ->
                        new AlertDialog.Builder(MyOrdersActivity.this)
                                .setTitle("Cancel Order")
                                .setMessage(
                                        "Are you sure you want to cancel "
                                                + "order #" + orderId + "?"
                                )
                                .setNegativeButton(
                                        "No",
                                        (dialog, which) -> dialog.dismiss()
                                )
                                .setPositiveButton(
                                        "Yes, Cancel",
                                        (dialog, which) -> {
                                            cancelButton.setEnabled(false);
                                            cancelOrder(orderId);
                                        }
                                )
                                .show()
                );
            }

            LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    );

            params.setMargins(0, 8, 0, 12);
            ordersContainer.addView(cardLayout, params);
        }
    }

    private void cancelOrder(int orderId) {
        executor.execute(() -> {
            HttpURLConnection connection = null;

            try {
                // IMPORTANT: This is the cancellation endpoint.
                URL url = new URL(
                        BASE_URL + "/api/orders/"
                                + orderId + "/cancel"
                );

                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                connection.setDoOutput(true);

                connection.setRequestProperty(
                        "Content-Type",
                        "application/json; charset=UTF-8"
                );
                connection.setRequestProperty(
                        "Accept",
                        "application/json"
                );

                JSONObject request = new JSONObject();
                request.put("user_id", userId);

                byte[] body = request.toString()
                        .getBytes(StandardCharsets.UTF_8);

                android.util.Log.d(
                        "CANCEL_DEBUG",
                        "URL: " + url + " | Method: POST"
                );

                try (OutputStream output = connection.getOutputStream()) {
                    output.write(body);
                }

                int code = connection.getResponseCode();

                InputStream stream =
                        code >= 200 && code < 300
                                ? connection.getInputStream()
                                : connection.getErrorStream();

                String responseText = readResponse(stream);

                android.util.Log.d(
                        "CANCEL_DEBUG",
                        "HTTP: " + code
                                + " | Response: " + responseText
                );

                JSONObject json = parseJson(responseText);

                String message = json.optString(
                        "message", "Unable to cancel order."
                );

                final boolean success = code >= 200 && code < 300;
                final String finalMessage = message;

                runOnUiThread(() -> {
                    Toast.makeText(
                            MyOrdersActivity.this,
                            finalMessage,
                            Toast.LENGTH_LONG
                    ).show();

                    if (success) {
                        loadOrders();
                    } else {
                        // Re-enable the button by refreshing the list.
                        loadOrders();
                    }
                });

            } catch (Exception e) {
                android.util.Log.e(
                        "CANCEL_DEBUG",
                        "Cancellation failed for order " + orderId,
                        e
                );

                runOnUiThread(() -> {
                    Toast.makeText(
                            MyOrdersActivity.this,
                            "Cancellation failed: " + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();

                    loadOrders();
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
        executor.shutdown();
    }
}