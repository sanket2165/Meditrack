package com.example.meditrack;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.List;

public class PatientAdapter extends RecyclerView.Adapter<PatientAdapter.PatientViewHolder> {

    private List<User> patients;
    private final AppDatabase db;
    private final OnPatientClickListener listener;

    public interface OnPatientClickListener {
        void onPatientClick(User patient);
        void onWriteNoteClick(User patient);
        default void onProfileClick(User patient) {}
    }

    public PatientAdapter(List<User> patients, AppDatabase db, OnPatientClickListener listener) {
        this.patients = patients;
        this.db = db;
        this.listener = listener;
    }

    public void updateList(List<User> newList) {
        this.patients = newList;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public PatientViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_patient, parent, false);
        return new PatientViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PatientViewHolder holder, int position) {
        User patient = patients.get(position);
        holder.tvPatientName.setText(patient.username);

        // Fetch progress from Firestore instead of local DB for Doctor view
        FirebaseFirestore.getInstance()
                .collection("medicines")
                .whereEqualTo("username", patient.username)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (queryDocumentSnapshots.isEmpty()) {
                        holder.tvPatientProgress.setText("No medicines scheduled");
                    } else {
                        int total = queryDocumentSnapshots.size();
                        int taken = 0;
                        for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                            Boolean isTaken = doc.getBoolean("isTaken");
                            if (isTaken != null && isTaken) taken++;
                        }
                        int percent = (taken > 0) ? (taken * 100) / total : 0;
                        holder.tvPatientProgress.setText("Today's Progress: " + percent + "%");
                        
                        if (percent < 50) {
                            holder.tvPatientProgress.setTextColor(Color.RED);
                        } else {
                            holder.tvPatientProgress.setTextColor(Color.parseColor("#4CAF50"));
                        }
                    }
                });

        holder.itemView.setOnClickListener(v -> listener.onPatientClick(patient));
        holder.ivWriteNote.setOnClickListener(v -> listener.onWriteNoteClick(patient));
        if (holder.ivViewProfile != null) {
            holder.ivViewProfile.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onProfileClick(patient);
                }
            });
        }
        if (holder.ivCallPatient != null) {
            holder.ivCallPatient.setOnClickListener(v -> {
                String phone = patient.username;
                if (phone != null && (phone.startsWith("+") || phone.matches("\\d+"))) {
                    Intent intent = new Intent(Intent.ACTION_DIAL);
                    intent.setData(Uri.parse("tel:" + phone));
                    v.getContext().startActivity(intent);
                } else {
                    Toast.makeText(v.getContext(), "No direct phone number available for " + patient.username, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    @Override
    public int getItemCount() {
        return patients.size();
    }

    static class PatientViewHolder extends RecyclerView.ViewHolder {
        TextView tvPatientName, tvPatientProgress;
        ImageView ivWriteNote, ivViewProfile, ivCallPatient;

        public PatientViewHolder(@NonNull View itemView) {
            super(itemView);
            tvPatientName = itemView.findViewById(R.id.tvPatientName);
            tvPatientProgress = itemView.findViewById(R.id.tvPatientProgress);
            ivWriteNote = itemView.findViewById(R.id.ivWriteNote);
            ivViewProfile = itemView.findViewById(R.id.ivViewProfile);
            ivCallPatient = itemView.findViewById(R.id.ivCallPatient);
        }
    }
}