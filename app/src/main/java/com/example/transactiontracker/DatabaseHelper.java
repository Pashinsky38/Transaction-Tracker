package com.example.transactiontracker;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "transactions.db";
    private static final int DATABASE_VERSION = 2;

    private static final String TABLE_TRANSACTIONS = "transactions";
    private static final String TABLE_IMAGES = "transaction_images";

    private static final String COL_ID = "id";
    private static final String COL_AMOUNT = "amount";
    private static final String COL_DESCRIPTION = "description";
    private static final String COL_CATEGORY = "category";
    private static final String COL_DATE = "date";

    private static final String COL_IMAGE_ID = "image_id";
    private static final String COL_TRANSACTION_ID = "transaction_id";
    private static final String COL_IMAGE_PATH = "image_path";

    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
    private SimpleDateFormat monthFormat = new SimpleDateFormat("MMM yyyy", Locale.getDefault());

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTransactionsTable = "CREATE TABLE " + TABLE_TRANSACTIONS + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_AMOUNT + " REAL, "
                + COL_DESCRIPTION + " TEXT, "
                + COL_CATEGORY + " TEXT, "
                + COL_DATE + " TEXT)";
        db.execSQL(createTransactionsTable);

        String createImagesTable = "CREATE TABLE " + TABLE_IMAGES + " ("
                + COL_IMAGE_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_TRANSACTION_ID + " INTEGER, "
                + COL_IMAGE_PATH + " TEXT, "
                + "FOREIGN KEY(" + COL_TRANSACTION_ID + ") REFERENCES "
                + TABLE_TRANSACTIONS + "(" + COL_ID + ") ON DELETE CASCADE)";
        db.execSQL(createImagesTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_IMAGES);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_TRANSACTIONS);
        onCreate(db);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    public long addTransaction(Transaction transaction) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();

        values.put(COL_AMOUNT, transaction.getAmount());
        values.put(COL_DESCRIPTION, transaction.getDescription());
        values.put(COL_CATEGORY, transaction.getCategory());
        values.put(COL_DATE, dateFormat.format(transaction.getDate()));

        long transactionId = db.insert(TABLE_TRANSACTIONS, null, values);

        if (transaction.getImagePaths() != null && !transaction.getImagePaths().isEmpty()) {
            for (String imagePath : transaction.getImagePaths()) {
                ContentValues imageValues = new ContentValues();
                imageValues.put(COL_TRANSACTION_ID, transactionId);
                imageValues.put(COL_IMAGE_PATH, imagePath);
                db.insert(TABLE_IMAGES, null, imageValues);
            }
        }

        return transactionId;
    }

    public List<Transaction> getAllTransactions() {
        List<Transaction> transactions = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.query(TABLE_TRANSACTIONS, null, null, null, null, null, COL_DATE + " DESC");

        if (cursor.moveToFirst()) {
            do {
                Transaction transaction = createTransactionFromCursor(cursor);
                transactions.add(transaction);
            } while (cursor.moveToNext());
        }

        cursor.close();
        return transactions;
    }

    public List<Transaction> getTransactionsByMonth(String month) {
        List<Transaction> transactions = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.query(TABLE_TRANSACTIONS, null, null, null, null, null, COL_DATE + " DESC");

        if (cursor.moveToFirst()) {
            do {
                String dateString = cursor.getString(cursor.getColumnIndexOrThrow(COL_DATE));
                try {
                    Date date = dateFormat.parse(dateString);
                    String transactionMonth = monthFormat.format(date);

                    if (transactionMonth.equals(month)) {
                        Transaction transaction = createTransactionFromCursor(cursor);
                        transactions.add(transaction);
                    }
                } catch (ParseException e) {
                    e.printStackTrace();
                }
            } while (cursor.moveToNext());
        }

        cursor.close();
        return transactions;
    }

    private Transaction createTransactionFromCursor(Cursor cursor) {
        Transaction transaction = new Transaction();
        transaction.setId(cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID)));
        transaction.setAmount(cursor.getDouble(cursor.getColumnIndexOrThrow(COL_AMOUNT)));
        transaction.setDescription(cursor.getString(cursor.getColumnIndexOrThrow(COL_DESCRIPTION)));
        transaction.setCategory(cursor.getString(cursor.getColumnIndexOrThrow(COL_CATEGORY)));

        String dateString = cursor.getString(cursor.getColumnIndexOrThrow(COL_DATE));
        try {
            transaction.setDate(dateFormat.parse(dateString));
        } catch (ParseException e) {
            transaction.setDate(new Date());
        }

        List<String> imagePaths = getImagesForTransaction(transaction.getId());
        transaction.setImagePaths(imagePaths);

        return transaction;
    }

    private List<String> getImagesForTransaction(long transactionId) {
        List<String> imagePaths = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.query(TABLE_IMAGES,
                new String[]{COL_IMAGE_PATH},
                COL_TRANSACTION_ID + " = ?",
                new String[]{String.valueOf(transactionId)},
                null, null, null);

        if (cursor.moveToFirst()) {
            do {
                imagePaths.add(cursor.getString(cursor.getColumnIndexOrThrow(COL_IMAGE_PATH)));
            } while (cursor.moveToNext());
        }

        cursor.close();
        return imagePaths;
    }

    public void deleteTransaction(long id) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_TRANSACTIONS, COL_ID + " = ?", new String[]{String.valueOf(id)});
    }

    public double getTotalBalance() {
        double balance = 0;
        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.rawQuery("SELECT SUM(" + COL_AMOUNT + ") FROM " + TABLE_TRANSACTIONS, null);

        if (cursor.moveToFirst()) {
            balance = cursor.getDouble(0);
        }

        cursor.close();
        return balance;
    }

    public double getTotalIncome() {
        double income = 0;
        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.rawQuery("SELECT SUM(" + COL_AMOUNT + ") FROM " + TABLE_TRANSACTIONS + " WHERE " + COL_AMOUNT + " > 0", null);

        if (cursor.moveToFirst()) {
            income = cursor.getDouble(0);
        }

        cursor.close();
        return income;
    }

    public double getTotalExpenses() {
        double expenses = 0;
        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.rawQuery("SELECT SUM(" + COL_AMOUNT + ") FROM " + TABLE_TRANSACTIONS + " WHERE " + COL_AMOUNT + " < 0", null);

        if (cursor.moveToFirst()) {
            expenses = cursor.getDouble(0);
        }

        cursor.close();
        return expenses;
    }

    public double getBalanceForMonth(String month) {
        List<Transaction> transactions = getTransactionsByMonth(month);
        double balance = 0;

        for (Transaction t : transactions) {
            balance += t.getAmount();
        }

        return balance;
    }

    public double getIncomeForMonth(String month) {
        List<Transaction> transactions = getTransactionsByMonth(month);
        double income = 0;

        for (Transaction t : transactions) {
            if (t.getAmount() > 0) {
                income += t.getAmount();
            }
        }

        return income;
    }

    public double getExpensesForMonth(String month) {
        List<Transaction> transactions = getTransactionsByMonth(month);
        double expenses = 0;

        for (Transaction t : transactions) {
            if (t.getAmount() < 0) {
                expenses += t.getAmount();
            }
        }

        return expenses;
    }

    public int updateTransaction(Transaction transaction) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();

        values.put(COL_AMOUNT, transaction.getAmount());
        values.put(COL_DESCRIPTION, transaction.getDescription());
        values.put(COL_CATEGORY, transaction.getCategory());
        values.put(COL_DATE, dateFormat.format(transaction.getDate()));

        return db.update(TABLE_TRANSACTIONS, values, COL_ID + " = ?",
                new String[]{String.valueOf(transaction.getId())});
    }
}