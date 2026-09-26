package com.example.meditrack;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
import java.util.Map;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {

    private List<Map<String, Object>> historyList;

    public HistoryAdapter(List<Map<String, Object>> historyList) {
        this.historyList = historyList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_history, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Map<String, Object> data = historyList.get(position);
        
        String date = data.get("date") != null ? data.get("date").toString() : "Unknown Date";
        
        long percentage = 0;
        long taken = 0;
        long total = 0;

        try {
            Object p = data.get("percentage");
            if (p instanceof Long) percentage = (long) p;
            else if (p instanceof Double) percentage = ((Double) p).longValue();
            else if (p instanceof Integer) percentage = (int) p;

            Object t = data.get("takenCount");
            if (t instanceof Long) taken = (long) t;
            else if (t instanceof Double) taken = ((Double) t).longValue();
            else if (t instanceof Integer) taken = (int) t;

            Object tot = data.get("totalCount");
            if (tot instanceof Long) total = (long) tot;
            else if (tot instanceof Double) total = ((Double) tot).longValue();
            else if (tot instanceof Integer) total = (int) tot;
        } catch (Exception e) {
            e.printStackTrace();
        }

        holder.txtDate.setText(date);
        holder.txtDetails.setText("Took " + taken + " of " + total + " medicines");
        holder.txtPercent.setText(percentage + "%");
        
        // Change color based on performance
        if (percentage >= 80) {
            holder.txtPercent.setTextColor(holder.itemView.getContext().getResources().getColor(android.R.color.holo_green_dark));
        } else if (percentage >= 50) {
            holder.txtPercent.setTextColor(holder.itemView.getContext().getResources().getColor(android.R.color.holo_orange_dark));
        } else {
            holder.txtPercent.setTextColor(holder.itemView.getContext().getResources().getColor(android.R.color.holo_red_dark));
        }
    }

    @Override
    public int getItemCount() {
        return historyList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView txtDate, txtDetails, txtPercent;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            txtDate = itemView.findViewById(R.id.txtHistoryDate);
            txtDetails = itemView.findViewById(R.id.txtHistoryDetails);
            txtPercent = itemView.findViewById(R.id.txtHistoryPercent);
        }
    }
}
