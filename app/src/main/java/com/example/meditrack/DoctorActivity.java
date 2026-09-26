package com.example.meditrack;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.Timestamp;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DoctorActivity extends AppCompatActivity {

    private AppDatabase db;
    private FirebaseFirestore firestore;
    private RecyclerView rvPatients;
    private PatientAdapter adapter;
    private android.widget.TextView txtTotalPatients, txtSosAlerts;
    private com.google.firebase.analytics.FirebaseAnalytics mFirebaseAnalytics;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_doctor);

        Toolbar toolbar = findViewById(R.id.toolbarDoctor);
        setSupportActionBar(toolbar);

        txtTotalPatients = findViewById(R.id.txtTotalPatients);
        txtSosAlerts = findViewById(R.id.txtSosAlerts);
        
        // Initialize Analytics
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);
        
        // Make SOS card clickable to show history
        findViewById(R.id.cardSos).setOnClickListener(v -> showSosHistory());

        db = AppDatabase.getInstance(getApplicationContext());

        firestore = FirebaseFirestore.getInstance();

        rvPatients = findViewById(R.id.rvPatients);
        rvPatients.setLayoutManager(new LinearLayoutManager(this));

        loadPatients();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadPatients();
    }

    private void loadPatients() {
        firestore.collection("users")
                .whereEqualTo("role", "Patient")
                .addSnapshotListener((queryDocumentSnapshots, e) -> {
                    if (e != null) {
                        Toast.makeText(this, "Error listening for updates", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (queryDocumentSnapshots != null) {
                        List<User> patients = new ArrayList<>();
                        int sosCount = 0;
                        for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                            String uname = doc.getString("username");
                            if (uname == null || uname.trim().isEmpty()) continue;
                            
                            User p = new User();
                            p.username = uname;
                            p.role = "Patient";
                            
                            Boolean hasSos = doc.getBoolean("hasSos");
                            if (hasSos != null && hasSos) {
                                sosCount++;
                            }
                            patients.add(p);
                        }
                        
                        txtTotalPatients.setText(String.valueOf(patients.size()));
                        txtSosAlerts.setText(String.valueOf(sosCount));

                        adapter = new PatientAdapter(patients, db, new PatientAdapter.OnPatientClickListener() {
                            @Override
                            public void onPatientClick(User patient) {
                                // Open a detailed view for the doctor
                                Intent intent = new Intent(DoctorActivity.this, MainActivity.class);
                                intent.putExtra("username", patient.username);
                                intent.putExtra("isDoctorView", true); // Tells MainActivity to show this patient's data
                                
                                // LOG ANALYTICS: Track which patient the doctor is checking
                                Bundle bundle = new Bundle();
                                bundle.putString("patient_username", patient.username);
                                mFirebaseAnalytics.logEvent("doctor_view_patient_details", bundle);

                                startActivity(intent);
                            }

                            @Override
                            public void onWriteNoteClick(User patient) {
                                showWriteNoteDialog(patient);
                            }

                            @Override
                            public void onProfileClick(User patient) {
                                Intent intent = new Intent(DoctorActivity.this, ProfileActivity.class);
                                intent.putExtra("username", patient.username);
                                intent.putExtra("isDoctorView", true);
                                startActivity(intent);
                            }
                        });
                        rvPatients.setAdapter(adapter);

                        // Setup Search after loading patients
                        androidx.appcompat.widget.SearchView searchView = findViewById(R.id.searchPatient);
                        searchView.setOnQueryTextListener(new androidx.appcompat.widget.SearchView.OnQueryTextListener() {
                            @Override
                            public boolean onQueryTextSubmit(String query) {
                                filterPatients(query, patients);
                                
                                // LOG ANALYTICS: Track that a search happened
                                android.os.Bundle bundle = new android.os.Bundle();
                                bundle.putString("search_query", query);
                                mFirebaseAnalytics.logEvent("doctor_search_patient", bundle);
                                
                                return true;
                            }

                            @Override
                            public boolean onQueryTextChange(String newText) {
                                filterPatients(newText, patients);
                                return true;
                            }
                        });
                    }
                });
    }

    private void filterPatients(String query, List<User> fullList) {
        List<User> filtered = new ArrayList<>();
        for (User u : fullList) {
            if (u.username.toLowerCase().contains(query.toLowerCase())) {
                filtered.add(u);
            }
        }
        if (adapter != null) {
            adapter.updateList(filtered);
        }
    }

    private void showWriteNoteDialog(User patient) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Write Prescription/Note for " + patient.username);

        final android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("Enter medication advice or notes...");
        builder.setView(input);

        builder.setPositiveButton("Send", (dialog, which) -> {
            String note = input.getText().toString().trim();
            if (!note.isEmpty()) {
                saveNoteToFirestore(patient.username, note);
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    private void saveNoteToFirestore(String patientUsername, String note) {
        if (patientUsername == null || patientUsername.trim().isEmpty()) return;

        Map<String, Object> noteData = new HashMap<>();
        noteData.put("note", note);
        noteData.put("timestamp", Timestamp.now());
        noteData.put("doctorName", getSharedPreferences("MeditrackPrefs", MODE_PRIVATE).getString("username", "Doctor"));

        firestore.collection("users").document(patientUsername)
                .update("doctorNote", noteData)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Note sent to patient!", Toast.LENGTH_SHORT).show();
                    
                    // LOG ANALYTICS: Track prescription sent
                    Bundle bundle = new Bundle();
                    bundle.putString("patient_id", patientUsername);
                    mFirebaseAnalytics.logEvent("doctor_sent_note", bundle);
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to send note", Toast.LENGTH_SHORT).show());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_profile) {
            Intent intent = new Intent(this, ProfileActivity.class);
            intent.putExtra("username", getSharedPreferences("MeditrackPrefs", MODE_PRIVATE).getString("username", "Doctor"));
            intent.putExtra("isDoctorView", false);
            startActivity(intent);
            return true;
        } else if (id == R.id.action_logout) {
            getSharedPreferences("MeditrackPrefs", MODE_PRIVATE).edit().clear().apply();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showSosHistory() {
        firestore.collection("users")
                .whereEqualTo("hasSos", true)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    StringBuilder patients = new StringBuilder();
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        patients.append("- ").append(doc.getString("username")).append("\n");
                    }
                    
                    String message = patients.length() > 0 ? 
                        "The following patients need immediate attention:\n\n" + patients.toString() :
                        "No active SOS alerts at the moment.";

                    new android.app.AlertDialog.Builder(this)
                            .setTitle("Active SOS Alerts")
                            .setMessage(message)
                            .setPositiveButton("OK", null)
                            .show();
                });
    }
}