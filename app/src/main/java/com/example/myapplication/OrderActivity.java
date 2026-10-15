package com.example.myapplication;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

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

public class OrderActivity extends AppCompatActivity {

    private static final String API_URL =
            "http://10.0.2.2:3000/api/orders";

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    private Spinner spSize, spPaper, spQuantity;
    private Button btnUpload, btnConfirm;
    private TextView tvFileName, tvTotal;
    private TextView tvOrderTitle, tvOrderSubtitle;
    private TextView tvSizeLabel, tvPaperLabel;
    private RadioGroup rgDelivery;
    private RadioButton rbDelivery, rbPickup;
    private EditText etAddress, etCustomQuantity;

    private ActivityResultLauncher<String[]> filePickerLauncher;
    private Uri selectedFileUri;

    private int userId;
    private int productId;
    private double productPrice;

    private String productName;
    private String categoryName;

    private int[] quantityOptions;
    private boolean isBusinessCard;
    private boolean isTShirt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order);

        spSize = findViewById(R.id.spSize);
        spPaper = findViewById(R.id.spPaper);
        spQuantity = findViewById(R.id.spQuantity);

        btnUpload = findViewById(R.id.btnUpload);
        btnConfirm = findViewById(R.id.btnConfirm);

        tvFileName = findViewById(R.id.tvFileName);
        tvTotal = findViewById(R.id.tvTotal);

        tvOrderTitle = findViewById(R.id.tvOrderTitle);
        tvOrderSubtitle = findViewById(R.id.tvOrderSubtitle);
        tvSizeLabel = findViewById(R.id.tvSizeLabel);
        tvPaperLabel = findViewById(R.id.tvPaperLabel);

        rgDelivery = findViewById(R.id.rgDelivery);
        rbDelivery = findViewById(R.id.rbDelivery);
        rbPickup = findViewById(R.id.rbPickup);
        etAddress = findViewById(R.id.etAddress);

        userId = getIntent().getIntExtra("USER_ID", -1);
        productId = getIntent().getIntExtra("PRODUCT_ID", -1);
        productPrice = getIntent().getDoubleExtra("PRODUCT_PRICE", 0.0);

        productName = getIntent().getStringExtra("PRODUCT_NAME");
        categoryName = getIntent().getStringExtra("CATEGORY_NAME");

        if (productName == null || productName.trim().isEmpty()) {
            productName = "Printing Product";
        }

        if (categoryName == null) {
            categoryName = "";
        }

        isBusinessCard =
                categoryName.equalsIgnoreCase("Business Cards")
                        || productName.toLowerCase().contains("business card");

        isTShirt =
                categoryName.equalsIgnoreCase("T-Shirts")
                        || productName.toLowerCase().contains("t-shirt");

        setupScreenTitle();
        setupProductOptions();
        setupQuantityOptions();
        setupCustomQuantityField();
        setupQuantitySpinner();
        setupDeliveryOptions();
        setupFilePicker();

        TextView tvBack = findViewById(R.id.tvBack);
        tvBack.setOnClickListener(v -> finish());

        btnConfirm.setOnClickListener(v -> confirmOrder());

        updateTotal();
    }

    private void setupScreenTitle() {
        tvOrderTitle.setText(productName + " Order");
        tvOrderSubtitle.setText("Customise your " + productName + " order.");

        if (isBusinessCard) {
            tvOrderTitle.setText("Business Card Order");
            tvSizeLabel.setText("Card Size");
            tvPaperLabel.setText("Paper Type");

        } else if (isTShirt) {
            tvOrderTitle.setText("T-Shirt Order");
            tvSizeLabel.setText("T-Shirt Size");
            tvPaperLabel.setText("Material / Print Option");

        } else if (categoryName.equalsIgnoreCase("Flyers")) {
            tvOrderTitle.setText("Flyer Order");
            tvSizeLabel.setText("Flyer Size");
            tvPaperLabel.setText("Paper Type");

        } else if (categoryName.equalsIgnoreCase("Posters")) {
            tvOrderTitle.setText("Poster Order");
            tvSizeLabel.setText("Poster Size");
            tvPaperLabel.setText("Paper / Finish");

        } else if (categoryName.equalsIgnoreCase("Banners")) {
            tvOrderTitle.setText("Banner Order");
            tvSizeLabel.setText("Banner Size");
            tvPaperLabel.setText("Material");

        } else if (categoryName.equalsIgnoreCase("Stickers")) {
            tvOrderTitle.setText("Sticker Order");
            tvSizeLabel.setText("Sticker Size");
            tvPaperLabel.setText("Finish");
        }
    }

    private void setupProductOptions() {
        String[] sizes;
        String[] materials;

        if (isBusinessCard) {
            sizes = new String[]{
                    "Standard (90 x 54 mm)",
                    "Custom Size"
            };
            materials = new String[]{
                    "Matte",
                    "Glossy",
                    "Premium Matte"
            };

        } else if (isTShirt) {
            sizes = new String[]{
                    "S", "M", "L", "XL", "XXL"
            };
            materials = new String[]{
                    "Cotton",
                    "Front Print",
                    "Front and Back Print"
            };

        } else if (categoryName.equalsIgnoreCase("Flyers")) {
            sizes = new String[]{
                    "A4", "A5", "A6", "Custom Size"
            };
            materials = new String[]{
                    "Matte",
                    "Glossy"
            };

        } else if (categoryName.equalsIgnoreCase("Posters")) {
            sizes = new String[]{
                    "A4", "A3", "A2", "Custom Size"
            };
            materials = new String[]{
                    "Matte",
                    "Glossy"
            };

        } else if (categoryName.equalsIgnoreCase("Banners")) {
            sizes = new String[]{
                    "Custom Size",
                    "Small",
                    "Medium",
                    "Large"
            };
            materials = new String[]{
                    "Vinyl",
                    "Outdoor Banner Material"
            };

        } else if (categoryName.equalsIgnoreCase("Stickers")) {
            sizes = new String[]{
                    "Small",
                    "Medium",
                    "Large",
                    "Custom Size"
            };
            materials = new String[]{
                    "Glossy",
                    "Matte"
            };

        } else {
            sizes = new String[]{
                    "Standard",
                    "Custom Size"
            };
            materials = new String[]{
                    "Standard",
                    "Premium"
            };
        }

        setSpinnerItems(spSize, sizes);
        setSpinnerItems(spPaper, materials);
    }

    private void setSpinnerItems(Spinner spinner, String[] items) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                items
        );

        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        spinner.setAdapter(adapter);
    }

    private void setupQuantityOptions() {
        if (isBusinessCard) {
            quantityOptions = new int[]{100, 250, 500, 1000};
        } else {
            quantityOptions = new int[]{1, 5, 10, 25, 50};
        }
    }

    private void setupCustomQuantityField() {
        etCustomQuantity = new EditText(this);
        etCustomQuantity.setHint("Enter custom quantity");
        etCustomQuantity.setInputType(InputType.TYPE_CLASS_NUMBER);
        etCustomQuantity.setSingleLine(true);
        etCustomQuantity.setVisibility(View.GONE);

        ViewGroup parent = (ViewGroup) spQuantity.getParent();
        int index = parent.indexOfChild(spQuantity);
        parent.addView(etCustomQuantity, index + 1);

        etCustomQuantity.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(
                    CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(
                    CharSequence s, int start, int before, int count) {
                updateTotal();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void setupQuantitySpinner() {
        String unit = isBusinessCard ? "Cards" : "Units";

        // Create one spinner option for each available quantity,
        // followed by the custom quantity option.
        String[] quantities = new String[quantityOptions.length + 1];

        for (int i = 0; i < quantityOptions.length; i++) {
            quantities[i] = quantityOptions[i] + " " + unit;
        }
        quantities[quantityOptions.length] = "Custom Quantity";

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                quantities
        );

        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        spQuantity.setAdapter(adapter);

        spQuantity.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(
                            AdapterView<?> parent,
                            View view,
                            int position,
                            long id) {

                        boolean custom = position == quantityOptions.length;

                        etCustomQuantity.setVisibility(
                                custom ? View.VISIBLE : View.GONE
                        );

                        if (!custom) {
                            etCustomQuantity.setText("");
                        }

                        updateTotal();
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                    }
                }
        );
    }

    private void setupDeliveryOptions() {
        rgDelivery.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbPickup) {
                etAddress.setText("");
                etAddress.setError(null);
                etAddress.setVisibility(View.GONE);

            } else if (checkedId == R.id.rbDelivery) {
                etAddress.setVisibility(View.VISIBLE);
                etAddress.setHint("Delivery address");
            }
        });

        if (rbPickup.isChecked()) {
            etAddress.setVisibility(View.GONE);
        } else if (rbDelivery.isChecked()) {
            etAddress.setVisibility(View.VISIBLE);
        } else {
            rbPickup.setChecked(true);
            etAddress.setVisibility(View.GONE);
        }
    }

    private void setupFilePicker() {
        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri != null) {
                        selectedFileUri = uri;

                        try {
                            getContentResolver().takePersistableUriPermission(
                                    uri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                            );
                        } catch (Exception ignored) {
                        }

                        String fileName = uri.getLastPathSegment();

                        tvFileName.setText(
                                fileName != null
                                        ? fileName
                                        : "Design file selected"
                        );
                    }
                }
        );

        btnUpload.setOnClickListener(v ->
                filePickerLauncher.launch(new String[]{
                        "application/pdf",
                        "image/png",
                        "image/jpeg"
                })
        );
    }

    private int getSelectedQuantity() {
        int position = spQuantity.getSelectedItemPosition();

        if (position >= 0 && position < quantityOptions.length) {
            return quantityOptions[position];
        }

        if (position == quantityOptions.length) {
            String value = etCustomQuantity.getText()
                    .toString().trim();

            if (value.isEmpty()) {
                return -1;
            }

            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                return -1;
            }
        }

        return -1;
    }

    private double calculateTotal(int quantity) {
        return productPrice * quantity;
    }

    private void updateTotal() {
        if (tvTotal == null || spQuantity == null
                || spQuantity.getSelectedItem() == null) {
            return;
        }

        int quantity = getSelectedQuantity();

        if (productPrice <= 0) {
            tvTotal.setText("Price unavailable");
            return;
        }

        if (quantity <= 0) {
            tvTotal.setText("Estimated Total: Enter quantity");
            return;
        }

        tvTotal.setText(
                "Estimated Total: LKR "
                        + formatPrice(calculateTotal(quantity))
        );
    }

    private void confirmOrder() {
        if (userId <= 0) {
            showMessage("User ID missing. Please log in again.");
            return;
        }

        if (productId <= 0) {
            showMessage("Product ID missing. Please select the product again.");
            return;
        }

        if (productPrice <= 0) {
            showMessage("Product price is invalid. Please select the product again.");
            return;
        }

        final int quantity = getSelectedQuantity();

        if (quantity <= 0 || quantity > 100000) {
            etCustomQuantity.setError(
                    "Enter a quantity from 1 to 100000"
            );
            etCustomQuantity.requestFocus();
            return;
        }

        if (rbDelivery.isChecked()
                && etAddress.getText().toString().trim().isEmpty()) {
            etAddress.setError("Please enter delivery address");
            etAddress.requestFocus();
            return;
        }

        if (!rbDelivery.isChecked() && !rbPickup.isChecked()) {
            showMessage("Please select Pickup or Delivery.");
            return;
        }

        final String size = spSize.getSelectedItem() == null
                ? ""
                : spSize.getSelectedItem().toString();

        final String paper = spPaper.getSelectedItem() == null
                ? ""
                : spPaper.getSelectedItem().toString();

        final String method =
                rbPickup.isChecked() ? "Pickup" : "Delivery";

        final String address = rbPickup.isChecked()
                ? ""
                : etAddress.getText().toString().trim();

        final double estimatedTotal = calculateTotal(quantity);

        String unit = isBusinessCard ? "Cards" : "Units";

        String summary =
                "Product: " + productName
                        + "\nSize: " + size
                        + "\nMaterial / Print Option: " + paper
                        + "\nQuantity: " + quantity + " " + unit
                        + "\nUnit Price: LKR " + formatPrice(productPrice)
                        + "\nDelivery: " + method
                        + (address.isEmpty() ? "" : "\nAddress: " + address)
                        + "\nEstimated Total: LKR "
                        + formatPrice(estimatedTotal)
                        + "\n\nPlace this order?";

        new AlertDialog.Builder(this)
                .setTitle("Confirm Order")
                .setMessage(summary)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Place Order",
                        (dialog, which) ->
                                submitOrder(
                                        size,
                                        paper,
                                        quantity,
                                        method,
                                        address
                                ))
                .show();
    }

    private void submitOrder(
            String size,
            String paper,
            int quantity,
            String method,
            String address) {

        btnConfirm.setEnabled(false);
        btnConfirm.setText("Submitting...");

        executor.execute(() -> {
            HttpURLConnection connection = null;

            try {
                URL url = new URL(API_URL);
                connection = (HttpURLConnection) url.openConnection();

                connection.setRequestMethod("POST");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                connection.setDoOutput(true);

                connection.setRequestProperty(
                        "Content-Type",
                        "application/json; charset=UTF-8"
                );
                connection.setRequestProperty("Accept", "application/json");

                JSONObject body = new JSONObject();
                body.put("user_id", userId);
                body.put("product_id", productId);
                body.put("quantity", quantity);
                body.put("delivery_method", method);
                body.put("address", address);
                body.put("card_size", size);
                body.put("paper_type", paper);

                // Sends a file URI reference, not the actual file upload.
                body.put(
                        "design_file",
                        selectedFileUri == null
                                ? JSONObject.NULL
                                : selectedFileUri.toString()
                );

                byte[] data = body.toString()
                        .getBytes(StandardCharsets.UTF_8);

                try (OutputStream output = connection.getOutputStream()) {
                    output.write(data);
                }

                int responseCode = connection.getResponseCode();

                InputStream stream =
                        responseCode >= 200 && responseCode < 300
                                ? connection.getInputStream()
                                : connection.getErrorStream();

                StringBuilder response = new StringBuilder();

                if (stream != null) {
                    try (BufferedReader reader = new BufferedReader(
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

                JSONObject responseJson =
                        new JSONObject(response.toString());

                boolean success =
                        responseCode == HttpURLConnection.HTTP_CREATED;

                String message = responseJson.optString(
                        "message",
                        "Unexpected server response."
                );

                JSONObject order = responseJson.optJSONObject("order");

                final double serverTotal = order != null
                        ? order.optDouble(
                        "total_price",
                        calculateTotal(quantity)
                )
                        : calculateTotal(quantity);

                final int orderId = order != null
                        ? order.optInt("id", -1)
                        : -1;

                runOnUiThread(() -> {
                    btnConfirm.setEnabled(true);
                    btnConfirm.setText("Confirm Order");

                    if (success) {
                        new AlertDialog.Builder(OrderActivity.this)
                                .setTitle("Order Placed Successfully!")
                                .setMessage(
                                        "Order ID: " + orderId
                                                + "\nProduct: " + productName
                                                + "\nQuantity: " + quantity
                                                + "\nTotal: LKR "
                                                + formatPrice(serverTotal)
                                                + "\nStatus: Pending"
                                )
                                .setPositiveButton("OK", (d, w) -> finish())
                                .show();
                    } else {
                        Toast.makeText(
                                OrderActivity.this,
                                message,
                                Toast.LENGTH_LONG
                        ).show();
                    }
                });

            } catch (Exception e) {
                android.util.Log.e(
                        "PrintXpress",
                        "Order submission failed",
                        e
                );

                runOnUiThread(() -> {
                    btnConfirm.setEnabled(true);
                    btnConfirm.setText("Confirm Order");

                    Toast.makeText(
                            OrderActivity.this,
                            "Could not connect to server. "
                                    + "Check that the backend is running.",
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

    private String formatPrice(double price) {
        return new DecimalFormat("#,##0.00").format(price);
    }

    private void showMessage(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
