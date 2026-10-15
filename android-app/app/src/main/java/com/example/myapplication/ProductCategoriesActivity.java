package com.example.myapplication;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
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
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProductCategoriesActivity extends AppCompatActivity {

    private static final String API_URL =
            "http://10.0.2.2:3000/api/products/categories";

    private LinearLayout categoriesContainer;
    private TextView tvMessage;
    private ProgressBar progressBar;

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    private int userId = -1;
    private String userName;
    private String userEmail;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_product_categories);

        categoriesContainer =
                findViewById(R.id.categoriesContainer);

        tvMessage = findViewById(R.id.tvMessage);
        progressBar = findViewById(R.id.progressBar);

        Intent receivedIntent = getIntent();

        userId = receivedIntent.getIntExtra("USER_ID", -1);
        userName = receivedIntent.getStringExtra("USER_NAME");
        userEmail = receivedIntent.getStringExtra("USER_EMAIL");

        loadCategories();
    }

    private void loadCategories() {

        progressBar.setVisibility(View.VISIBLE);
        tvMessage.setVisibility(View.VISIBLE);
        tvMessage.setText("Loading categories...");

        executor.execute(() -> {

            HttpURLConnection connection = null;

            try {
                URL url = new URL(API_URL);

                connection =
                        (HttpURLConnection) url.openConnection();

                connection.setRequestMethod("GET");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);

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

                if (responseCode != HttpURLConnection.HTTP_OK) {
                    throw new Exception(
                            "Server error: HTTP " + responseCode
                    );
                }

                JSONObject json =
                        new JSONObject(response.toString());

                JSONArray categories =
                        json.getJSONArray("categories");

                runOnUiThread(() ->
                        displayCategories(categories)
                );

            } catch (Exception e) {

                android.util.Log.e(
                        "ProductCategories",
                        "Failed to load categories",
                        e
                );

                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    tvMessage.setText(
                            "Unable to load categories."
                    );

                    Toast.makeText(
                            ProductCategoriesActivity.this,
                            "Check your backend connection.",
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

    private void displayCategories(JSONArray categories) {

        progressBar.setVisibility(View.GONE);
        categoriesContainer.removeAllViews();

        if (categories.length() == 0) {
            tvMessage.setText("No categories available.");
            return;
        }

        tvMessage.setText(
                "Available categories: " + categories.length()
        );

        for (int i = 0; i < categories.length(); i++) {

            JSONObject category = categories.optJSONObject(i);

            if (category == null) {
                continue;
            }

            int categoryId = category.optInt("id");
            String categoryName =
                    category.optString("name", "Category");

            String description =
                    category.optString(
                            "description",
                            "Explore our printing products."
                    );

            // Category card
            LinearLayout card = new LinearLayout(this);

            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(20, 18, 20, 18);
            card.setBackgroundColor(0xFFFFFFFF);
            card.setElevation(4);
            card.setClickable(true);
            card.setFocusable(true);

            // Category name
            TextView title = new TextView(this);

            title.setText(categoryName);
            title.setTextSize(19);
            title.setTextColor(0xFF4B00C8);
            title.setTypeface(null, Typeface.BOLD);

            // Category description
            TextView details = new TextView(this);

            details.setText(description);
            details.setTextSize(14);
            details.setTextColor(0xFF555555);
            details.setPadding(0, 8, 0, 12);

            // View products text
            TextView action = new TextView(this);

            action.setText("View Products  →");
            action.setTextSize(14);
            action.setTextColor(0xFF194DA8);
            action.setTypeface(null, Typeface.BOLD);

            card.addView(title);
            card.addView(details);
            card.addView(action);

            LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    );

            params.setMargins(0, 0, 0, 14);

            categoriesContainer.addView(card, params);

            // Open selected category
            card.setOnClickListener(v -> {

                Intent intent = new Intent(
                        ProductCategoriesActivity.this,
                        ProductsActivity.class
                );

                intent.putExtra("CATEGORY_ID", categoryId);
                intent.putExtra("CATEGORY_NAME", categoryName);
                intent.putExtra("USER_ID", userId);
                intent.putExtra("USER_NAME", userName);
                intent.putExtra("USER_EMAIL", userEmail);

                startActivity(intent);
            });
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}