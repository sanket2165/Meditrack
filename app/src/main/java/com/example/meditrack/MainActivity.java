package com.example.meditrack;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private AppDatabase db;
    private FirebaseFirestore firestore;
    private RecyclerView recyclerView;
    private MedicineAdapter adapter;
    private TextView txtSummary, txtPercent, txtGreeting, txtDate;
    private TextView countTaken, countPending, countMissed;
    private View emptyState;
    private CircularProgressIndicator progressDaily;
    private FloatingActionButton btnAdd;
    private Button btnViewHistory;

    private int userId;
    private String username;
    private boolean isDoctorView = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activitymain);

        // Initialize Firebase Analytics
        FirebaseAnalytics.getInstance(this);

        userId = getIntent().getIntExtra("userId", -1);
        username = getIntent().getStringExtra("username");
        isDoctorView = getIntent().getBooleanExtra("isDoctorView", false);

        // Fallback for username if missing from Intent (e.g. on Auto-login)
        if (username == null) {
            username = getSharedPreferences("MeditrackPrefs", MODE_PRIVATE).getString("username", null);
        }

        if ((userId == -1 && !isDoctorView) || username == null || username.trim().isEmpty()) {
            // If still null or empty, we can't proceed
            getSharedPreferences("MeditrackPrefs", MODE_PRIVATE).edit().clear().apply();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (isDoctorView && getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Monitoring: " + username);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            toolbar.setNavigationOnClickListener(v -> finish());
        }

        db = AppDatabase.getInstance(getApplicationContext());
        
        firestore = FirebaseFirestore.getInstance();

        txtSummary = findViewById(R.id.txtSummary);
        txtPercent = findViewById(R.id.txtPercent);
        txtGreeting = findViewById(R.id.txtGreeting);
        txtDate = findViewById(R.id.txtDate);
        countTaken = findViewById(R.id.countTaken);
        countPending = findViewById(R.id.countPending);
        countMissed = findViewById(R.id.countMissed);
        emptyState = findViewById(R.id.emptyState);
        
        progressDaily = findViewById(R.id.progressDaily);
        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        btnAdd = findViewById(R.id.btnAdd);
        btnViewHistory = findViewById(R.id.btnViewHistory);

        if (isDoctorView) {
            btnAdd.setVisibility(View.GONE);
            btnViewHistory.setVisibility(View.VISIBLE);
            btnViewHistory.setOnClickListener(v -> {
                Intent intent = new Intent(this, HistoryActivity.class);
                intent.putExtra("username", username);
                startActivity(intent);
            });
        }

        btnAdd.setOnClickListener(v -> {
            Intent intent = new Intent(this, AddMedicineActivity.class);
            intent.putExtra("userId", userId);
            startActivity(intent);
        });

        loadMedicines();
        syncFromCloud();
        updateHeader();
        requestNotificationPermission();
    }

    private void syncFromCloud() {
        if (isDoctorView) {
            // Doctors don't need to sync local DB, they view live Cloud data
            return;
        }

        if (userId != -1) {
            firestore.collection("medicines")
                    .whereEqualTo("userId", userId)
                    .get()
                    .addOnSuccessListener(queryDocumentSnapshots -> {
                        new Thread(() -> {
                            boolean dataChanged = false;
                            for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                                String fId = doc.getId();
                                Medicine localMed = db.medicineDao().getByFirestoreId(fId);
                                if (localMed == null) {
                                    Medicine m = new Medicine();
                                    m.name = doc.getString("name");
                                    m.time = doc.getString("time");
                                    m.dosage = doc.getString("dosage");
                                    m.type = doc.getString("type");
                                    m.userId = userId;
                                    m.firestoreId = fId;
                                    m.isTaken = doc.getBoolean("isTaken") != null && doc.getBoolean("isTaken");
                                    m.stock = doc.getLong("stock") != null ? doc.getLong("stock").intValue() : 0;
                                    m.days = doc.getString("days") != null ? doc.getString("days") : "Every day";
                                    db.medicineDao().insert(m);
                                    dataChanged = true;
                                }
                            }
                            if (dataChanged) {
                                runOnUiThread(this::loadMedicines);
                            }
                        }).start();
                    });
        }

        // Fetch Doctor's Note - only if username is not empty
        if (username != null && !username.trim().isEmpty()) {
            firestore.collection("users").document(username)
                    .addSnapshotListener((documentSnapshot, e) -> {
                        if (e != null) {
                            Log.e("MainActivity", "Error getting doctor note", e);
                            return;
                        }
                        if (documentSnapshot != null && documentSnapshot.exists()) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> noteData = (Map<String, Object>) documentSnapshot.get("doctorNote");
                            if (noteData != null) {
                                String note = (String) noteData.get("note");
                                String doctorName = (String) noteData.get("doctorName");

                                View card = findViewById(R.id.cardDoctorNote);
                                TextView txtNote = findViewById(R.id.txtDoctorNote);
                                TextView txtDocName = findViewById(R.id.txtDoctorNameLabel);

                                if (card != null) card.setVisibility(View.VISIBLE);
                                if (txtNote != null && note != null) txtNote.setText(note);
                                if (txtDocName != null) txtDocName.setText("Note from Dr. " + (doctorName != null ? doctorName : "Doctor"));
                            } else {
                                View card = findViewById(R.id.cardDoctorNote);
                                if (card != null) card.setVisibility(View.GONE);
                            }
                        }
                    });
        }
    }

    private void updateHeader() {
        // Set Date
        SimpleDateFormat sdf = new SimpleDateFormat("EEEE, dd MMMM", Locale.getDefault());
        txtDate.setText(sdf.format(Calendar.getInstance().getTime()));

        // Set Greeting based on time
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String greeting;
        if (hour < 12) greeting = "Good Morning";
        else if (hour < 17) greeting = "Good Afternoon";
        else greeting = "Good Evening";

        // If you had user name, you could use it here
        txtGreeting.setText(greeting + "!");
    }


    @Override
    protected void onResume() {
        super.onResume();
        loadMedicines();
        syncFromCloud();
        updateHeader();
        requestNotificationPermission();
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
            intent.putExtra("username", username);
            intent.putExtra("isDoctorView", isDoctorView);
            startActivity(intent);
            return true;
        } else if (id == R.id.action_logout) {
            getSharedPreferences("MeditrackPrefs", MODE_PRIVATE).edit().clear().apply();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return true;
        } else if (id == R.id.action_sos) {
            // FEATURE: Emergency SOS Sync to Doctor
            if (username != null && !username.trim().isEmpty()) {
                firestore.collection("users").document(username)
                        .update("hasSos", true)
                        .addOnSuccessListener(aVoid -> {
                            Toast.makeText(this, "SOS Alert Sent to Doctor!", Toast.LENGTH_LONG).show();
                            
                            // Also trigger the phone call
                            String emergencyNumber = "911"; 
                            Intent intent = new Intent(Intent.ACTION_DIAL);
                            intent.setData(Uri.parse("tel:" + emergencyNumber));
                            startActivity(intent);
                        });
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void loadMedicines() {
        if (isDoctorView) {
            // REAL-TIME MONITORING: Use SnapshotListener so Doctor sees updates live
            firestore.collection("medicines")
                    .whereEqualTo("username", username)
                    .addSnapshotListener((queryDocumentSnapshots, e) -> {
                        if (e != null || queryDocumentSnapshots == null) return;
                        
                        List<Medicine> medicineList = new ArrayList<>();
                        for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                            Medicine m = new Medicine();
                            m.name = doc.getString("name");
                            m.time = doc.getString("time");
                            m.dosage = doc.getString("dosage");
                            m.type = doc.getString("type");
                            m.isTaken = doc.getBoolean("isTaken") != null && doc.getBoolean("isTaken");
                            m.firestoreId = doc.getId();
                            m.stock = doc.getLong("stock") != null ? doc.getLong("stock").intValue() : 0;
                            medicineList.add(m);
                        }
                        displayMedicines(medicineList);
                    });
        } else {
            // Load from Local Room DB for Patient
            new Thread(() -> {
                checkDailyReset();
                List<Medicine> medicineList = db.medicineDao().getByUserId(userId);
                
                List<Medicine> filteredList = new ArrayList<>();
                Calendar calendar = Calendar.getInstance();
                String[] dayNames = {"S", "M", "T", "W", "T", "F", "S"};
                String currentDayName = dayNames[calendar.get(Calendar.DAY_OF_WEEK) - 1];

                for (Medicine m : medicineList) {
                    if (m.days == null || m.days.equals("Every day") || m.days.contains(currentDayName)) {
                        filteredList.add(m);
                    }
                }

                runOnUiThread(() -> displayMedicines(filteredList));
            }).start();
        }
    }

    private void displayMedicines(List<Medicine> medicineList) {
        if (medicineList.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
            updateProgress(medicineList);
            return;
        }

        emptyState.setVisibility(View.GONE);
        recyclerView.setVisibility(View.VISIBLE);
        
        // Enable interaction even for doctors as per requirements
        adapter = new MedicineAdapter(medicineList, 
                isDoctorView ? this::showDeleteDialog : this::showDeleteDialog, 
                this::updateMedicineStatus);
        
        recyclerView.setAdapter(adapter);
        updateProgress(medicineList);
    }

    private void updateProgress(List<Medicine> medicineList) {
        if (medicineList == null || medicineList.isEmpty()) {
            progressDaily.setProgress(0);
            txtPercent.setText("0%");
            txtSummary.setText("No medicines scheduled for today.");
            countTaken.setText("0");
            countPending.setText("0");
            countMissed.setText("0");
            return;
        }

        int taken = 0;
        int missed = 0;
        int pending = 0;

        Calendar now = Calendar.getInstance();
        SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());

        for (Medicine m : medicineList) {
            if (m.isTaken) {
                taken++;
            } else {
                try {
                    // Convert medicine time string (e.g. "08:00 AM") to a Calendar object
                    Calendar medTime = Calendar.getInstance();
                    medTime.setTime(timeFormat.parse(m.time));
                    
                    // Set medTime to TODAY
                    medTime.set(Calendar.YEAR, now.get(Calendar.YEAR));
                    medTime.set(Calendar.MONTH, now.get(Calendar.MONTH));
                    medTime.set(Calendar.DAY_OF_MONTH, now.get(Calendar.DAY_OF_MONTH));

                    if (now.after(medTime)) {
                        missed++;
                    } else {
                        pending++;
                    }
                } catch (Exception e) {
                    pending++; // Fallback if time format is wrong
                }
            }
        }

        int total = medicineList.size();
        int percent = (taken * 100) / total;
        
        progressDaily.setProgress(percent);
        txtPercent.setText(percent + "%");
        
        countTaken.setText(String.valueOf(taken));
        countPending.setText(String.valueOf(pending));
        countMissed.setText(String.valueOf(missed));

        if (percent == 100) {
            txtSummary.setText("Excellent! All medicines taken.");
        } else if (missed > 0) {
            txtSummary.setText("You missed " + missed + " dose(s). Take them now if safe!");
        } else {
            txtSummary.setText("You're on track! Keep going.");
        }
    }

    private void updateMedicineStatus(Medicine medicine, boolean isChecked) {
        medicine.isTaken = isChecked;
        
        // Deduct stock if taken
        if (isChecked && medicine.stock > 0) {
            medicine.stock--;
            if (medicine.stock < 5) {
                Toast.makeText(this, "Low stock alert for patient's " + medicine.name + ": " + medicine.stock + " left", Toast.LENGTH_LONG).show();
            }
        }

        new Thread(() -> {
            if (!isDoctorView) {
                db.medicineDao().update(medicine);
            }

            // Update Cloud - This reflects for both Doctor and Patient
            if (medicine.firestoreId != null) {
                Map<String, Object> updates = new HashMap<>();
                updates.put("isTaken", isChecked);
                updates.put("stock", medicine.stock);
                updates.put("username", username); // Ensure username is always attached
                firestore.collection("medicines").document(medicine.firestoreId)
                        .update(updates)
                        .addOnSuccessListener(aVoid -> {
                            if (isDoctorView) {
                                runOnUiThread(() -> Toast.makeText(this, "Patient record updated", Toast.LENGTH_SHORT).show());
                            }
                        });
            }

            if (!isDoctorView) {
                runOnUiThread(this::loadMedicines); 
            }
        }).start();
    }

    private void showDeleteDialog(Medicine medicine) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Delete Medicine")
                .setMessage("Are you sure you want to delete " + medicine.name + "?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    new Thread(() -> {
                        if (!isDoctorView) {
                            db.medicineDao().delete(medicine);
                        }
                        
                        // Delete from Cloud
                        if (medicine.firestoreId != null) {
                            firestore.collection("medicines").document(medicine.firestoreId)
                                    .delete()
                                    .addOnSuccessListener(aVoid -> {
                                        if (isDoctorView) {
                                            runOnUiThread(() -> Toast.makeText(this, "Medicine removed from patient record", Toast.LENGTH_SHORT).show());
                                        }
                                    });
                        }

                        runOnUiThread(() -> {
                            loadMedicines();
                            Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show();
                        });
                    }).start();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void checkDailyReset() {
        android.content.SharedPreferences prefs = getSharedPreferences("MeditrackPrefs", MODE_PRIVATE);
        int lastDay = prefs.getInt("last_day", -1);
        int currentDay = Calendar.getInstance().get(Calendar.DAY_OF_YEAR);

        if (lastDay != -1 && lastDay != currentDay) {
            // New day detected! Before resetting, save yesterday's results to history
            saveDailyResultToHistory(lastDay);

            // New day! Reset all checkboxes for this user
            List<Medicine> medicineList = db.medicineDao().getByUserId(userId);
            for (Medicine m : medicineList) {
                m.isTaken = false;
                db.medicineDao().update(m);
            }
        }
        prefs.edit().putInt("last_day", currentDay).apply();
    }

    private void saveDailyResultToHistory(int dayOfYear) {
        new Thread(() -> {
            List<Medicine> medicineList = db.medicineDao().getByUserId(userId);
            if (medicineList == null || medicineList.isEmpty()) return;

            int taken = 0;
            for (Medicine m : medicineList) {
                if (m.isTaken) taken++;
            }
            int percent = (taken * 100) / medicineList.size();

            // Prepare data for Firestore
            Map<String, Object> historyData = new HashMap<>();
            historyData.put("userId", userId);
            historyData.put("username", username); // Use the class-level username variable
            historyData.put("date", new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Calendar.getInstance().getTime()));
            historyData.put("percentage", (long) percent);
            historyData.put("takenCount", (long) taken);
            historyData.put("totalCount", (long) medicineList.size());
            historyData.put("timestamp", System.currentTimeMillis());

            firestore.collection("history")
                    .add(historyData)
                    .addOnSuccessListener(documentReference -> Log.d("History", "Saved successfully"))
                    .addOnFailureListener(e -> Log.e("History", "Failed to save: " + e.getMessage()));
        }).start();
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 101);
            }

        }
        
        // Check for Exact Alarm permission on Android 14+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            android.app.AlarmManager alarmManager = (android.app.AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        }
    }
}