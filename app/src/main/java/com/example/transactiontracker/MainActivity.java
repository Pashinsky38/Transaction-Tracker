package com.example.transactiontracker;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.NumberPicker;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.transactiontracker.databinding.ActivityMainBinding;
import com.example.transactiontracker.databinding.DialogAddTransactionBinding;

import java.io.File;
import java.io.FileOutputStream;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements TransactionAdapter.OnTransactionClickListener {
    private ActivityMainBinding binding;
    private TransactionAdapter adapter;
    private DatabaseHelper dbHelper;
    private List<Uri> selectedImages;
    private ActivityResultLauncher<String> requestPermissionLauncher;
    private ActivityResultLauncher<Intent> imagePickerLauncher;

    private Calendar currentMonth;
    private SimpleDateFormat monthFormat;
    private NumberFormat currencyFormat;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Force LTR layout direction
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        }

        // Enable edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Apply window insets to handle system bars
        binding.getRoot().setOnApplyWindowInsetsListener((v, insets) -> {
            int statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            int navBarHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;

            v.setPadding(0, statusBarHeight, 0, navBarHeight);
            return WindowInsetsCompat.CONSUMED.toWindowInsets();
        });

        dbHelper = new DatabaseHelper(this);
        selectedImages = new ArrayList<>();

        // Initialize current month to current date
        currentMonth = Calendar.getInstance();
        monthFormat = new SimpleDateFormat("MMM yyyy", Locale.getDefault());
        currencyFormat = NumberFormat.getCurrencyInstance(new Locale("he", "IL"));
        currencyFormat.setCurrency(java.util.Currency.getInstance("ILS"));

        setupPermissionLauncher();
        setupImagePickerLauncher();
        initViews();
        updateMonthDisplay();
        loadMonthTransactions();
    }

    private void setupPermissionLauncher() {
        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        openImagePicker();
                    } else {
                        Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_SHORT).show();
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
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TransactionAdapter(new ArrayList<>(), this);
        binding.recyclerView.setAdapter(adapter);

        binding.fabAdd.setOnClickListener(v -> showAddTransactionDialog());

        // Month navigation buttons
        binding.previousMonthButton.setOnClickListener(v -> {
            currentMonth.add(Calendar.MONTH, -1);
            updateMonthDisplay();
            loadMonthTransactions();
        });

        binding.nextMonthButton.setOnClickListener(v -> {
            currentMonth.add(Calendar.MONTH, 1);
            updateMonthDisplay();
            loadMonthTransactions();
        });
    }

    private void updateMonthDisplay() {
        String monthStr = monthFormat.format(currentMonth.getTime());
        binding.currentMonthText.setText(monthStr);

        // Update statistics
        double income = dbHelper.getIncomeForMonth(monthStr);
        double expenses = dbHelper.getExpensesForMonth(monthStr);
        double profitLoss = income + expenses; // expenses are negative

        binding.incomeText.setText(currencyFormat.format(income));
        binding.expensesText.setText(currencyFormat.format(Math.abs(expenses)));
        binding.profitLossText.setText(currencyFormat.format(profitLoss));

        // Color profit/loss based on value
        if (profitLoss < 0) {
            binding.profitLossText.setTextColor(Color.parseColor("#F44336")); // Red
        } else if (profitLoss > 0) {
            binding.profitLossText.setTextColor(Color.parseColor("#4CAF50")); // Green
        } else {
            binding.profitLossText.setTextColor(Color.parseColor("#757575")); // Gray
        }
    }

    private void loadMonthTransactions() {
        String monthStr = monthFormat.format(currentMonth.getTime());
        List<Transaction> transactions = dbHelper.getTransactionsByMonth(monthStr);
        adapter.updateTransactions(transactions);
    }

    private void showAddTransactionDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        DialogAddTransactionBinding dialogBinding = DialogAddTransactionBinding.inflate(
                LayoutInflater.from(this)
        );
        builder.setView(dialogBinding.getRoot());

        // Setup category spinner
        ArrayAdapter<CharSequence> spinnerAdapter = ArrayAdapter.createFromResource(
                this,
                R.array.categories,
                android.R.layout.simple_spinner_item
        );
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        dialogBinding.categorySpinner.setAdapter(spinnerAdapter);

        selectedImages.clear();

        // Get current date and time
        Calendar calendar = Calendar.getInstance();

        // Setup Hour Picker (0-23)
        NumberPicker hourPicker = dialogBinding.hourPicker;
        hourPicker.setMinValue(0);
        hourPicker.setMaxValue(23);
        hourPicker.setValue(calendar.get(Calendar.HOUR_OF_DAY));
        hourPicker.setFormatter(value -> String.format(Locale.getDefault(), "%02d", value));

        // Setup Minute Picker (0-59)
        NumberPicker minutePicker = dialogBinding.minutePicker;
        minutePicker.setMinValue(0);
        minutePicker.setMaxValue(59);
        minutePicker.setValue(calendar.get(Calendar.MINUTE));
        minutePicker.setFormatter(value -> String.format(Locale.getDefault(), "%02d", value));

        // Setup Day Picker (1-31)
        NumberPicker dayPicker = dialogBinding.dayPicker;
        dayPicker.setMinValue(1);
        dayPicker.setMaxValue(31);
        dayPicker.setValue(calendar.get(Calendar.DAY_OF_MONTH));
        dayPicker.setFormatter(value -> String.format(Locale.getDefault(), "%02d", value));

        // Setup Month Picker (1-12)
        NumberPicker monthPicker = dialogBinding.monthPicker;
        monthPicker.setMinValue(1);
        monthPicker.setMaxValue(12);
        monthPicker.setValue(calendar.get(Calendar.MONTH) + 1); // Calendar.MONTH is 0-based
        monthPicker.setFormatter(value -> String.format(Locale.getDefault(), "%02d", value));

        // Setup Year Picker (current year - 10 to current year + 10)
        NumberPicker yearPicker = dialogBinding.yearPicker;
        int currentYear = calendar.get(Calendar.YEAR);
        yearPicker.setMinValue(currentYear - 10);
        yearPicker.setMaxValue(currentYear + 10);
        yearPicker.setValue(currentYear);

        dialogBinding.addImageButton.setOnClickListener(v -> checkPermissionAndPickImage());

        builder.setPositiveButton(R.string.add_button, (dialog, which) -> {
            String amountStr = dialogBinding.amountInput.getText().toString();
            String description = dialogBinding.descriptionInput.getText().toString();
            String category = dialogBinding.categorySpinner.getSelectedItem().toString();

            if (amountStr.isEmpty() || description.isEmpty()) {
                Toast.makeText(this, R.string.fill_all_fields, Toast.LENGTH_SHORT).show();
                return;
            }

            double amount = Double.parseDouble(amountStr);
            if (dialogBinding.expenseRadio.isChecked()) {
                amount = -Math.abs(amount);
            } else {
                amount = Math.abs(amount);
            }

            Transaction transaction = new Transaction(amount, description, category);

            // Set the selected date and time
            Calendar selectedCalendar = Calendar.getInstance();
            selectedCalendar.set(Calendar.YEAR, yearPicker.getValue());
            selectedCalendar.set(Calendar.MONTH, monthPicker.getValue() - 1); // Calendar.MONTH is 0-based
            selectedCalendar.set(Calendar.DAY_OF_MONTH, dayPicker.getValue());
            selectedCalendar.set(Calendar.HOUR_OF_DAY, hourPicker.getValue());
            selectedCalendar.set(Calendar.MINUTE, minutePicker.getValue());
            selectedCalendar.set(Calendar.SECOND, 0);
            selectedCalendar.set(Calendar.MILLISECOND, 0);

            transaction.setDate(selectedCalendar.getTime());

            for (Uri imageUri : selectedImages) {
                String savedPath = saveImageToInternalStorage(imageUri);
                if (savedPath != null) {
                    transaction.addImagePath(savedPath);
                }
            }

            dbHelper.addTransaction(transaction);

            // Check if the added transaction is in the current displayed month
            String transactionMonth = monthFormat.format(transaction.getDate());
            String displayedMonth = monthFormat.format(currentMonth.getTime());

            if (transactionMonth.equals(displayedMonth)) {
                loadMonthTransactions();
            }

            updateMonthDisplay();
            Toast.makeText(this, R.string.transaction_added, Toast.LENGTH_SHORT).show();
        });

        builder.setNegativeButton(R.string.cancel_button, null);
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

    @Override
    public void onDeleteClick(Transaction transaction) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_transaction_title)
                .setMessage(R.string.delete_transaction_message)
                .setPositiveButton(R.string.delete_confirm, (dialog, which) -> {
                    dbHelper.deleteTransaction(transaction.getId());
                    loadMonthTransactions();
                    updateMonthDisplay();
                    Toast.makeText(this, R.string.transaction_deleted, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cancel_button, null)
                .show();
    }

    @Override
    public void onItemClick(Transaction transaction) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.transaction_details_title);

        String message = getString(R.string.detail_description, transaction.getDescription()) + "\n" +
                getString(R.string.detail_category, transaction.getCategory()) + "\n" +
                getString(R.string.detail_amount, String.valueOf(transaction.getAmount())) + "\n" +
                getString(R.string.detail_date, transaction.getDate().toString());

        builder.setMessage(message);
        builder.setPositiveButton(R.string.ok_button, null);
        builder.show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}