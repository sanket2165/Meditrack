package com.example.meditrack;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class ProfileActivity extends AppCompatActivity {

    private FirebaseFirestore firestore;
    private TextView txtProfileUsername, txtProfileRole, txtProfilePhoneOrEmail;
    private TextView txtProfileAdherence, txtProfileTotalMedications;
    private TextView txtProfileDoctorNameLabel, txtProfileDoctorNote;
    private View cardProfileSos, cardProfileDoctorNote;
    private Button btnProfileWriteNote, btnProfileCallPatient;

    private String targetUsername;
    private String userRole;
    private boolean isDoctorView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        firestore = FirebaseFirestore.getInstance();

        Intent intent = getIntent();
        targetUsername = intent.getStringExtra("username");
        isDoctorView = intent.getBooleanExtra("isDoctorView", false);

        if (targetUsername == null || targetUsername.isEmpty()) {
            targetUsername = getSharedPreferences("MeditrackPrefs", MODE_PRIVATE).getString("username", "User");
        }

        userRole = getSharedPreferences("MeditrackPrefs", MODE_PRIVATE).getString("role", "Patient");

        Toolbar toolbar = findViewById(R.id.toolbarProfile);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            if (isDoctorView) {
                getSupportActionBar().setTitle("Patient Profile");
            } else {
                getSupportActionBar().setTitle("My Profile");
            }
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        txtProfileUsername = findViewById(R.id.txtProfileUsername);
        txtProfileRole = findViewById(R.id.txtProfileRole);
        txtProfilePhoneOrEmail = findViewById(R.id.txtProfilePhoneOrEmail);
        txtProfileAdherence = findViewById(R.id.txtProfileAdherence);
        txtProfileTotalMedications = findViewById(R.id.txtProfileTotalMedications);
        txtProfileDoctorNameLabel = findViewById(R.id.txtProfileDoctorNameLabel);
        txtProfileDoctorNote = findViewById(R.id.txtProfileDoctorNote);

        cardProfileSos = findViewById(R.id.cardProfileSos);
        cardProfileDoctorNote = findViewById(R.id.cardProfileDoctorNote);

        btnProfileWriteNote = findViewById(R.id.btnProfileWriteNote);
        btnProfileCallPatient = findViewById(R.id.btnProfileCallPatient);
        Button btnProfileLogout = findViewById(R.id.btnProfileLogout);

        txtProfileUsername.setText(targetUsername);

        if (isDoctorView) {
            // Doctor viewing Patient
            btnProfileLogout.setVisibility(View.GONE);
            btnProfileWriteNote.setVisibility(View.VISIBLE);
            btnProfileCallPatient.setVisibility(View.VISIBLE);

            btnProfileWriteNote.setOnClickListener(v -> showWriteNoteDialog());
            btnProfileCallPatient.setOnClickListener(v -> callPatient());
        } else {
            // User viewing own profile
            btnProfileLogout.setVisibility(View.VISIBLE);
            btnProfileWriteNote.setVisibility(View.GONE);
            btnProfileCallPatient.setVisibility(View.GONE);

            btnProfileLogout.setOnClickListener(v -> logoutUser());
        }

        loadProfileData();
    }

    private void loadProfileData() {
        if (targetUsername == null || targetUsername.trim().isEmpty()) {
            txtProfileTotalMedications.setText("0");
            txtProfileAdherence.setText("0%");
            return;
        }

        // Load User details from Firestore
        firestore.collection("users").document(targetUsername)
                .addSnapshotListener((documentSnapshot, e) -> {
                    if (e != null || documentSnapshot == null || !documentSnapshot.exists()) {
                        txtProfileRole.setText(String.format("Role: %s", isDoctorView ? "Patient" : userRole));
                        return;
                    }

                    String roleStr = documentSnapshot.getString("role");
                    if (roleStr == null) {
                        roleStr = isDoctorView ? "Patient" : userRole;
                    }
                    txtProfileRole.setText(String.format("Role: %s", roleStr));

                    String phone = documentSnapshot.getString("phone");
                    if (phone != null) {
                        txtProfilePhoneOrEmail.setText(phone);
                    } else {
                        txtProfilePhoneOrEmail.setText(String.format("Account ID: %s", targetUsername));
                    }

                    Boolean hasSos = documentSnapshot.getBoolean("hasSos");
                    if (hasSos != null && hasSos) {
                        cardProfileSos.setVisibility(View.VISIBLE);
                    } else {
                        cardProfileSos.setVisibility(View.GONE);
                    }

                    @SuppressWarnings("unchecked")
                    Map<String, Object> noteData = (Map<String, Object>) documentSnapshot.get("doctorNote");
                    if (noteData != null) {
                        cardProfileDoctorNote.setVisibility(View.VISIBLE);
                        String note = (String) noteData.get("note");
                        String docName = (String) noteData.get("doctorName");
                        txtProfileDoctorNameLabel.setText(String.format("Doctor's Note (from Dr. %s):", docName != null ? docName : "Doctor"));
                        txtProfileDoctorNote.setText(note != null ? note : "No note content");
                    } else {
                        cardProfileDoctorNote.setVisibility(View.GONE);
                    }
                });

        // Load Patient Medication Stats from Firestore
        firestore.collection("medicines")
                .whereEqualTo("username", targetUsername)
                .addSnapshotListener((queryDocumentSnapshots, e) -> {
                    if (e != null || queryDocumentSnapshots == null) {
                        txtProfileTotalMedications.setText("0");
                        txtProfileAdherence.setText("0%");
                        return;
                    }

                    int total = queryDocumentSnapshots.size();
                    int taken = 0;
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        Boolean isTaken = doc.getBoolean("isTaken");
                        if (isTaken != null && isTaken) {
                            taken++;
                        }
                    }

                    int percent = (total > 0) ? (taken * 100) / total : 0;
                    txtProfileTotalMedications.setText(String.valueOf(total));
                    txtProfileAdherence.setText(String.format(Locale.getDefault(), "%d%%", percent));
                });
    }

    private void showWriteNoteDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(String.format("Write Prescription/Note for %s", targetUsername));

        final EditText input = new EditText(this);
        input.setHint("Enter medication advice or notes...");
        builder.setView(input);

        builder.setPositiveButton("Send", (dialog, which) -> {
            String note = input.getText().toString().trim();
            if (!note.isEmpty()) {
                saveNoteToFirestore(note);
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    private void saveNoteToFirestore(String note) {
        if (targetUsername == null || targetUsername.trim().isEmpty()) return;

        Map<String, Object> noteData = new HashMap<>();
        noteData.put("note", note);
        noteData.put("timestamp", Timestamp.now());
        noteData.put("doctorName", getSharedPreferences("MeditrackPrefs", MODE_PRIVATE).getString("username", "Doctor"));

        firestore.collection("users").document(targetUsername)
                .update("doctorNote", noteData)
                .addOnSuccessListener(aVoid -> Toast.makeText(this, "Note sent to " + targetUsername, Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to send note", Toast.LENGTH_SHORT).show());
    }

    private void callPatient() {
        String phoneNum = targetUsername;
        if (!phoneNum.startsWith("+") && !phoneNum.matches("\\d+")) {
            Toast.makeText(this, "No direct phone number found for " + targetUsername, Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(Intent.ACTION_DIAL);
        intent.setData(Uri.parse("tel:" + phoneNum));
        startActivity(intent);
    }

    private void logoutUser() {
        getSharedPreferences("MeditrackPrefs", MODE_PRIVATE).edit().clear().apply();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}