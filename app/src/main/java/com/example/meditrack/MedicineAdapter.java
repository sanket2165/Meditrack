package com.example.meditrack;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class MedicineAdapter extends RecyclerView.Adapter<MedicineAdapter.MedicineViewHolder> {

    private List<Medicine> medicineList;
    private OnItemLongClickListener longClickListener;
    private OnCheckedChangeListener checkedChangeListener;

    public interface OnItemLongClickListener {
        void onItemLongClick(Medicine medicine);
    }

    public interface OnCheckedChangeListener {
        void onCheckedChanged(Medicine medicine, boolean isChecked);
    }

    public MedicineAdapter(List<Medicine> medicineList, OnItemLongClickListener longClickListener, OnCheckedChangeListener checkedChangeListener) {
        this.medicineList = medicineList;
        this.longClickListener = longClickListener;
        this.checkedChangeListener = checkedChangeListener;
    }

    @NonNull
    @Override
    public MedicineViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_medicine, parent, false);
        return new MedicineViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MedicineViewHolder holder, int position) {
        Medicine medicine = medicineList.get(position);
        holder.txtMedName.setText(medicine.name);
        String details = medicine.dosage + " " + medicine.type + " · " + medicine.time;
        holder.txtMedDetails.setText(details);
        holder.txtMedDays.setText(medicine.days);
        holder.txtStock.setText("Stock: " + medicine.stock + " left");

        if (medicine.stock < 5) {
            holder.txtStock.setTextColor(holder.itemView.getContext().getResources().getColor(android.R.color.holo_red_dark));
        } else {
            holder.txtStock.setTextColor(holder.itemView.getContext().getResources().getColor(R.color.blue_primary));
        }
        
        holder.cbTaken.setOnCheckedChangeListener(null);
        holder.cbTaken.setChecked(medicine.isTaken);
        
        holder.cbTaken.setOnCheckedChangeListener((buttonView, isChecked) -> {
            checkedChangeListener.onCheckedChanged(medicine, isChecked);
        });

        holder.itemView.setOnLongClickListener(v -> {
            longClickListener.onItemLongClick(medicine);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return medicineList.size();
    }

    static class MedicineViewHolder extends RecyclerView.ViewHolder {
        TextView txtMedName, txtMedDetails, txtMedDays, txtStock;
        CheckBox cbTaken;

        public MedicineViewHolder(@NonNull View itemView) {
            super(itemView);
            txtMedName = itemView.findViewById(R.id.txtMedName);
            txtMedDetails = itemView.findViewById(R.id.txtMedDetails);
            txtMedDays = itemView.findViewById(R.id.txtMedDays);
            txtStock = itemView.findViewById(R.id.txtStock);
            cbTaken = itemView.findViewById(R.id.cbTaken);
        }
    }
}