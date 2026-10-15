package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class HomeActivity extends AppCompatActivity {

    private TextView tvWelcome;
    private Button btnBrowseProducts;
    private Button btnMyOrders;

    private int userId;
    private String userName;
    private String userEmail;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        // Receive customer details from MainActivity
        Intent receivedIntent = getIntent();

        userId = receivedIntent.getIntExtra("USER_ID", -1);
        userName = receivedIntent.getStringExtra("USER_NAME");
        userEmail = receivedIntent.getStringExtra("USER_EMAIL");

        // Initialize views
        tvWelcome = findViewById(R.id.tvWelcome);
        btnBrowseProducts = findViewById(R.id.btnBrowseProducts);
        btnMyOrders = findViewById(R.id.btnMyOrders);

        // Display customer name
        if (userName != null && !userName.trim().isEmpty()) {
            tvWelcome.setText("Welcome, " + userName + "!");
        } else {
            tvWelcome.setText("Welcome!");
        }

        // Browse Products
        btnBrowseProducts.setOnClickListener(v -> {
            Intent intent = new Intent(
                    HomeActivity.this,
                    ProductCategoriesActivity.class
            );

            intent.putExtra("USER_ID", userId);
            intent.putExtra("USER_NAME", userName);
            intent.putExtra("USER_EMAIL", userEmail);

            startActivity(intent);
        });

        // My Orders
        btnMyOrders.setOnClickListener(v -> {
            Intent intent = new Intent(
                    HomeActivity.this,
                    MyOrdersActivity.class
            );

            intent.putExtra("USER_ID", userId);
            intent.putExtra("USER_NAME", userName);
            intent.putExtra("USER_EMAIL", userEmail);

            startActivity(intent);
        });
    }
}
