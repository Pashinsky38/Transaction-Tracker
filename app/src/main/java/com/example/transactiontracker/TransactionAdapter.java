package com.example.transactiontracker;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder> {
    private List<Transaction> transactions;
    private OnTransactionClickListener listener;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());
    private NumberFormat currencyFormat;

    public interface OnTransactionClickListener {
        void onDeleteClick(Transaction transaction);
        void onItemClick(Transaction transaction);
    }

    public TransactionAdapter(List<Transaction> transactions, OnTransactionClickListener listener) {
        this.transactions = transactions;
        this.listener = listener;
        this.currencyFormat = NumberFormat.getCurrencyInstance(new Locale("he", "IL"));
        this.currencyFormat.setCurrency(java.util.Currency.getInstance("ILS"));
    }

    @NonNull
    @Override
    public TransactionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_transaction, parent, false);
        return new TransactionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TransactionViewHolder holder, int position) {
        Transaction transaction = transactions.get(position);

        holder.descriptionText.setText(transaction.getDescription());
        holder.categoryText.setText(transaction.getCategory());
        holder.dateText.setText(dateFormat.format(transaction.getDate()));

        String amountText = currencyFormat.format(Math.abs(transaction.getAmount()));
        holder.amountText.setText(amountText);

        // Set color based on transaction type
        if (transaction.isExpense()) {
            holder.amountText.setTextColor(Color.parseColor("#F44336")); // Red for expenses
        } else {
            holder.amountText.setTextColor(Color.parseColor("#4CAF50")); // Green for income
        }

        // Show image indicator if transaction has images
        if (transaction.getImagePaths() != null && !transaction.getImagePaths().isEmpty()) {
            holder.imageIndicator.setVisibility(View.VISIBLE);
            holder.imageIndicator.setText("📷 " + transaction.getImagePaths().size());
        } else {
            holder.imageIndicator.setVisibility(View.GONE);
        }

        holder.deleteButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDeleteClick(transaction);
            }
        });

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(transaction);
            }
        });
    }

    @Override
    public int getItemCount() {
        return transactions.size();
    }

    public void updateTransactions(List<Transaction> newTransactions) {
        this.transactions = newTransactions;
        notifyDataSetChanged();
    }

    static class TransactionViewHolder extends RecyclerView.ViewHolder {
        TextView descriptionText;
        TextView categoryText;
        TextView amountText;
        TextView dateText;
        TextView imageIndicator;
        ImageButton deleteButton;

        public TransactionViewHolder(@NonNull View itemView) {
            super(itemView);
            descriptionText = itemView.findViewById(R.id.descriptionText);
            categoryText = itemView.findViewById(R.id.categoryText);
            amountText = itemView.findViewById(R.id.amountText);
            dateText = itemView.findViewById(R.id.dateText);
            imageIndicator = itemView.findViewById(R.id.imageIndicator);
            deleteButton = itemView.findViewById(R.id.deleteButton);
        }
    }
}