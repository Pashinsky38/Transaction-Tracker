package com.example.transactiontracker;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;
import java.io.FileOutputStream;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements TransactionAdapter.OnTransactionClickListener {
    private RecyclerView recyclerView;
    private TransactionAdapter adapter;
    private DatabaseHelper dbHelper;
    private TextView balanceText;
    private FloatingActionButton fabAdd;
    private List<Uri> selectedImages;
    private ActivityResultLauncher<String> requestPermissionLauncher;
    private ActivityResultLauncher<Intent> imagePickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dbHelper = new DatabaseHelper(this);
        selectedImages = new ArrayList<>();

        setupPermissionLauncher();
        setupImagePickerLauncher();
        initViews();
        loadTransactions();
        updateBalance();
    }

    private void setupPermissionLauncher() {
        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        openImagePicker();
                    } else {
                        Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private void setupImagePickerLauncher() {
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri imageUri = result.getData().getData();
                        if (imageUri != null) {
                            selectedImages.add(imageUri);
                        }
                    }
                }
        );
    }

    private void initViews() {
        balanceText = findViewById(R.id.balanceText);
        recyclerView = findViewById(R.id.recyclerView);
        fabAdd = findViewById(R.id.fabAdd);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TransactionAdapter(new ArrayList<>(), this);
        recyclerView.setAdapter(adapter);

        fabAdd.setOnClickListener(v -> showAddTransactionDialog());
    }

    private void showAddTransactionDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_transaction, null);
        builder.setView(dialogView);

        EditText amountInput = dialogView.findViewById(R.id.amountInput);
        EditText descriptionInput = dialogView.findViewById(R.id.descriptionInput);
        RadioButton expenseRadio = dialogView.findViewById(R.id.expenseRadio);
        RadioButton incomeRadio = dialogView.findViewById(R.id.incomeRadio);
        Spinner categorySpinner = dialogView.findViewById(R.id.categorySpinner);
        Button addImageButton = dialogView.findViewById(R.id.addImageButton);
        LinearLayout imagesContainer = dialogView.findViewById(R.id.imagesContainer);

        String[] categories = {"Food", "Transport", "Shopping", "Bills", "Salary", "Other"};
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, categories);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        categorySpinner.setAdapter(spinnerAdapter);

        selectedImages.clear();

        addImageButton.setOnClickListener(v -> {
            checkPermissionAndPickImage();
        });

        builder.setPositiveButton("Add", (dialog, which) -> {
            String amountStr = amountInput.getText().toString();
            String description = descriptionInput.getText().toString();
            String category = categorySpinner.getSelectedItem().toString();

            if (amountStr.isEmpty() || description.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            double amount = Double.parseDouble(amountStr);
            if (expenseRadio.isChecked()) {
                amount = -Math.abs(amount);
            } else {
                amount = Math.abs(amount);
            }

            Transaction transaction = new Transaction(amount, description, category);

            for (Uri imageUri : selectedImages) {
                String savedPath = saveImageToInternalStorage(imageUri);
                if (savedPath != null) {
                    transaction.addImagePath(savedPath);
                }
            }

            dbHelper.addTransaction(transaction);
            loadTransactions();
            updateBalance();

            Toast.makeText(this, "Transaction added", Toast.LENGTH_SHORT).show();
        });

        builder.setNegativeButton("Cancel", null);
        builder.create().show();
    }

    private void checkPermissionAndPickImage() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES);
            } else {
                openImagePicker();
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE);
            } else {
                openImagePicker();
            }
        }
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        imagePickerLauncher.launch(intent);
    }

    private String saveImageToInternalStorage(Uri imageUri) {
        try {
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
            File directory = new File(getFilesDir(), "transaction_images");
            if (!directory.exists()) {
                directory.mkdirs();
            }

            String filename = "IMG_" + System.currentTimeMillis() + ".jpg";
            File file = new File(directory, filename);

            FileOutputStream fos = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, fos);
            fos.close();

            return file.getAbsolutePath();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void loadTransactions() {
        List<Transaction> transactions = dbHelper.getAllTransactions();
        adapter.updateTransactions(transactions);
    }

    private void updateBalance() {
        double balance = dbHelper.getTotalBalance();
        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("he", "IL"));
        currencyFormat.setCurrency(java.util.Currency.getInstance("ILS"));
        balanceText.setText(currencyFormat.format(balance));

        if (balance < 0) {
            balanceText.setTextColor(Color.parseColor("#F44336"));
        } else {
            balanceText.setTextColor(Color.parseColor("#4CAF50"));
        }
    }

    @Override
    public void onDeleteClick(Transaction transaction) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Transaction")
                .setMessage("Are you sure you want to delete this transaction?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    dbHelper.deleteTransaction(transaction.getId());
                    loadTransactions();
                    updateBalance();
                    Toast.makeText(this, "Transaction deleted", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onItemClick(Transaction transaction) {
        // Show transaction details
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Transaction Details");

        String message = "Description: " + transaction.getDescription() + "\n" +
                "Category: " + transaction.getCategory() + "\n" +
                "Amount: " + transaction.getAmount() + " ₪\n" +
                "Date: " + transaction.getDate().toString();

        builder.setMessage(message);
        builder.setPositiveButton("OK", null);
        builder.show();
    }
}