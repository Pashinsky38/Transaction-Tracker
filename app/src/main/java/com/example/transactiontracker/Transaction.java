package com.example.transactiontracker;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Transaction implements Serializable {
    private long id;
    private double amount;
    private String description;
    private Date date;
    private List<String> imagePaths;
    private String category;

    public Transaction() {
        this.date = new Date();
        this.imagePaths = new ArrayList<>();
    }

    public Transaction(double amount, String description, String category) {
        this();
        this.amount = amount;
        this.description = description;
        this.category = category;
    }

    // Getters and Setters
    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public List<String> getImagePaths() {
        return imagePaths;
    }

    public void setImagePaths(List<String> imagePaths) {
        this.imagePaths = imagePaths;
    }

    public void addImagePath(String path) {
        this.imagePaths.add(path);
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public boolean isExpense() {
        return amount < 0;
    }
}