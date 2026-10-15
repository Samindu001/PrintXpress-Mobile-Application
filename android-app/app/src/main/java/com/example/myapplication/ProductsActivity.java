package com.example.myapplication;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
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
import java.text.DecimalFormat;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProductsActivity extends AppCompatActivity {

    private static final String API_URL =
            "http://10.0.2.2:3000/api/products";

    private LinearLayout productsContainer;
    private TextView tvMessage;
    private ProgressBar progressBar;

    private ExecutorService executor;

    private int categoryId;
    private int userId = -1;

    private String categoryName = "Products";
    private String userName;
    private String userEmail;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        categoryId = getIntent().getIntExtra(
                "CATEGORY_ID", -1
        );

        userId = getIntent().getIntExtra(
                "USER_ID", -1
        );

        categoryName = getIntent().getStringExtra(
                "CATEGORY_NAME"
        );

        userName = getIntent().getStringExtra(
                "USER_NAME"
        );

        userEmail = getIntent().getStringExtra(
                "USER_EMAIL"
        );

        if (categoryName == null || categoryName.trim().isEmpty()) {
            categoryName = "Products";
        }

        executor = Executors.newSingleThreadExecutor();

        createLayout();

        if (categoryId <= 0) {
            progressBar.setVisibility(View.GONE);
            tvMessage.setText("Invalid product category.");
            return;
        }

        loadProducts();
    }

    // =========================================
    // CREATE SCREEN
    // =========================================

    private void createLayout() {

        LinearLayout root = new LinearLayout(this);

        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(16, 16, 16, 16);
        root.setBackgroundColor(0xFFF5F6FA);

        // Screen title
        TextView title = new TextView(this);

        title.setText(categoryName);
        title.setTextSize(25);
        title.setTextColor(0xFF4B00C8);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(0, 8, 0, 12);

        root.addView(title);

        // Information message
        tvMessage = new TextView(this);

        tvMessage.setText("Loading products...");
        tvMessage.setTextSize(14);
        tvMessage.setTextColor(0xFF555555);
        tvMessage.setPadding(0, 0, 0, 8);

        root.addView(tvMessage);

        // Loading indicator
        progressBar = new ProgressBar(this);

        progressBar.setVisibility(View.GONE);

        LinearLayout.LayoutParams progressParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        progressParams.setMargins(0, 4, 0, 12);

        root.addView(progressBar, progressParams);

        // Scrollable products list
        ScrollView scrollView = new ScrollView(this);

        scrollView.setFillViewport(false);

        productsContainer = new LinearLayout(this);

        productsContainer.setOrientation(
                LinearLayout.VERTICAL
        );

        scrollView.addView(productsContainer);

        root.addView(
                scrollView,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                        1
                )
        );

        setContentView(root);
    }

    // =========================================
    // LOAD PRODUCTS FROM BACKEND
    // =========================================

    private void loadProducts() {

        progressBar.setVisibility(View.VISIBLE);
        tvMessage.setText("Loading products...");

        executor.execute(() -> {

            HttpURLConnection connection = null;

            try {
                URL url = new URL(
                        API_URL + "?categoryId=" + categoryId
                );

                connection = (HttpURLConnection)
                        url.openConnection();

                connection.setRequestMethod("GET");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);

                int responseCode =
                        connection.getResponseCode();

                InputStream stream;

                if (responseCode >= 200 && responseCode < 300) {
                    stream = connection.getInputStream();
                } else {
                    stream = connection.getErrorStream();
                }

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
                            "Server returned HTTP " + responseCode
                    );
                }

                JSONObject json = new JSONObject(
                        response.toString()
                );

                JSONArray products =
                        json.getJSONArray("products");

                runOnUiThread(() ->
                        displayProducts(products)
                );

            } catch (Exception e) {

                android.util.Log.e(
                        "ProductsActivity",
                        "Product loading failed",
                        e
                );

                runOnUiThread(() -> {

                    progressBar.setVisibility(View.GONE);

                    tvMessage.setText(
                            "Could not load products. "
                                    + "Please check your backend."
                    );

                    Toast.makeText(
                            ProductsActivity.this,
                            "Unable to load products.",
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

    // =========================================
    // DISPLAY PRODUCT CARDS
    // =========================================

    private void displayProducts(JSONArray products) {

        progressBar.setVisibility(View.GONE);

        productsContainer.removeAllViews();

        if (products.length() == 0) {

            tvMessage.setText(
                    "No products available in this category."
            );

            return;
        }

        tvMessage.setText(
                products.length() + " product(s) available"
        );

        for (int i = 0; i < products.length(); i++) {

            JSONObject product = products.optJSONObject(i);

            if (product == null) {
                continue;
            }

            int productId = product.optInt("id");

            String name = product.optString(
                    "name", "Product"
            );

            String description = product.optString(
                    "description", ""
            );

            String imageKey = product.optString(
                    "image", ""
            );

            double price = product.optDouble(
                    "price", 0
            );

            String size = product.optString(
                    "size", "Not specified"
            );

            String paperType = product.optString(
                    "paper_type", "Not specified"
            );

            // Product card
            LinearLayout card = new LinearLayout(this);

            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(16, 16, 16, 16);
            card.setBackgroundColor(0xFFFFFFFF);
            card.setElevation(4);

            // -------------------------------------
            // PRODUCT IMAGE
            // -------------------------------------

            ImageView imageView = new ImageView(this);

            LinearLayout.LayoutParams imageParams =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            180
                    );

            imageParams.setMargins(0, 0, 0, 12);

            imageView.setLayoutParams(imageParams);

            imageView.setScaleType(
                    ImageView.ScaleType.FIT_CENTER
            );

            imageView.setBackgroundColor(0xFFF0F0F0);

            // Find image in res/drawable
            int imageResource = 0;

            if (!imageKey.trim().isEmpty()) {

                imageResource = getResources().getIdentifier(
                        imageKey.trim(),
                        "drawable",
                        getPackageName()
                );
            }

            if (imageResource != 0) {

                imageView.setImageResource(imageResource);

            } else {

                imageView.setImageResource(
                        android.R.drawable.ic_menu_gallery
                );
            }

            // -------------------------------------
            // PRODUCT NAME
            // -------------------------------------

            TextView productName = new TextView(this);

            productName.setText(name);
            productName.setTextSize(19);
            productName.setTextColor(0xFF4B00C8);

            productName.setTypeface(
                    null,
                    Typeface.BOLD
            );

            productName.setPadding(0, 4, 0, 8);

            // -------------------------------------
            // PRODUCT DESCRIPTION
            // -------------------------------------

            TextView productDescription = new TextView(this);

            productDescription.setText(description);
            productDescription.setTextSize(14);
            productDescription.setTextColor(0xFF555555);

            productDescription.setPadding(
                    0, 0, 0, 10
            );

            // -------------------------------------
            // PRODUCT DETAILS
            // -------------------------------------

            TextView productDetails = new TextView(this);

            productDetails.setText(
                    "Size: " + size
                            + "\nPaper / Material: " + paperType
            );

            productDetails.setTextSize(14);
            productDetails.setTextColor(0xFF333333);

            // -------------------------------------
            // PRODUCT PRICE
            // -------------------------------------

            TextView productPrice = new TextView(this);

            productPrice.setText(
                    "Price: LKR "
                            + new DecimalFormat("#,##0.00")
                            .format(price)
            );

            productPrice.setTextSize(17);
            productPrice.setTextColor(0xFF194DA8);

            productPrice.setTypeface(
                    null,
                    Typeface.BOLD
            );

            productPrice.setPadding(0, 12, 0, 12);

            // -------------------------------------
            // ORDER BUTTON
            // -------------------------------------

            Button orderButton = new Button(this);

            orderButton.setText("Order This Product");
            orderButton.setAllCaps(false);

            orderButton.setOnClickListener(v -> {

                if (userId <= 0) {

                    Toast.makeText(
                            ProductsActivity.this,
                            "Please log in again.",
                            Toast.LENGTH_LONG
                    ).show();

                    return;
                }

                Intent intent = new Intent(
                        ProductsActivity.this,
                        OrderActivity.class
                );

                intent.putExtra("USER_ID", userId);
                intent.putExtra("PRODUCT_ID", productId);
                intent.putExtra("PRODUCT_NAME", name);
                intent.putExtra("PRODUCT_PRICE", price);
                intent.putExtra("CATEGORY_NAME", categoryName);

                intent.putExtra("USER_NAME", userName);
                intent.putExtra("USER_EMAIL", userEmail);

                startActivity(intent);
            });

            // Add views in correct order
            card.addView(imageView);
            card.addView(productName);
            card.addView(productDescription);
            card.addView(productDetails);
            card.addView(productPrice);
            card.addView(orderButton);

            LinearLayout.LayoutParams cardParams =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    );

            cardParams.setMargins(0, 0, 0, 18);

            productsContainer.addView(card, cardParams);
        }
    }

    // =========================================
    // CLEAN UP
    // =========================================

    @Override
    protected void onDestroy() {

        super.onDestroy();

        if (executor != null) {
            executor.shutdown();
        }
    }
}