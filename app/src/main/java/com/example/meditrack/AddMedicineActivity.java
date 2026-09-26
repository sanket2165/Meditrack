package com.example.meditrack;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.room.Room;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AddMedicineActivity extends AppCompatActivity {

    AppDatabase db;
    FirebaseFirestore firestore;
    EditText edtName, edtTime, edtDosage, edtStock;
    ImageView imgMedicine;
    String currentPhotoPath;
    private static final int REQUEST_IMAGE_CAPTURE = 1;
    Spinner spnType;
    CheckBox cbEveryDay;
    TextView[] dayViews;
    boolean[] selectedDays = new boolean[7];
    FloatingActionButton btnSave;
    private int userId;
    private int selectedHour = -1, selectedMinute = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activityaddmedicine);

        userId = getIntent().getIntExtra("userId", -1);
        if (userId == -1) {
            finish();
            return;
        }

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        db = AppDatabase.getInstance(getApplicationContext());

        firestore = FirebaseFirestore.getInstance();

        edtName = findViewById(R.id.edtName);
        edtTime = findViewById(R.id.edtTime);
        edtDosage = findViewById(R.id.edtDosage);
        edtStock = findViewById(R.id.edtStock);
        spnType = findViewById(R.id.spnType);
        imgMedicine = findViewById(R.id.imgMedicine);
        cbEveryDay = findViewById(R.id.cbEveryDay);

        imgMedicine.setOnClickListener(v -> dispatchTakePictureIntent());
        btnSave = findViewById(R.id.btnSave);

        // Time Picker Setup
        edtTime.setFocusable(false);
        edtTime.setOnClickListener(v -> {
            Calendar mcurrentTime = Calendar.getInstance();
            int hour = mcurrentTime.get(Calendar.HOUR_OF_DAY);
            int minute = mcurrentTime.get(Calendar.MINUTE);
            TimePickerDialog mTimePicker;
            mTimePicker = new TimePickerDialog(AddMedicineActivity.this, (timePicker, hourOfDay, minuteOfHour) -> {
                selectedHour = hourOfDay;
                selectedMinute = minuteOfHour;
                String amPm = (hourOfDay < 12) ? "AM" : "PM";
                int displayHour = (hourOfDay > 12) ? hourOfDay - 12 : (hourOfDay == 0 ? 12 : hourOfDay);
                edtTime.setText(String.format(Locale.getDefault(), "%02d:%02d %s", displayHour, minuteOfHour, amPm));
            }, hour, minute, false); // false = 12 hour mode
            mTimePicker.setTitle("Select Time");
            mTimePicker.show();
        });

        // Days of week views
        dayViews = new TextView[]{
                findViewById(R.id.dayS1), findViewById(R.id.dayM), findViewById(R.id.dayT1),
                findViewById(R.id.dayW), findViewById(R.id.dayT2), findViewById(R.id.dayF),
                findViewById(R.id.dayS2)
        };

        for (int i = 0; i < 7; i++) {
            final int index = i;
            dayViews[i].setOnClickListener(v -> {
                selectedDays[index] = !selectedDays[index];
                updateDayView(index);
                cbEveryDay.setChecked(false);
            });
        }

        cbEveryDay.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                for (int i = 0; i < 7; i++) {
                    selectedDays[i] = false;
                    updateDayView(i);
                }
            }
        });

        // Setup Spinner
        String[] types = {"adhesive(s)", "capsule(s)", "cachet(s)", "cream(s)", "dragee(s)", "emulsion(s)"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, types);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spnType.setAdapter(adapter);

        btnSave.setOnClickListener(v -> saveMedicine());
    }

    private void updateDayView(int index) {
        if (selectedDays[index]) {
            dayViews[index].setBackgroundResource(R.drawable.circle_background_selected);
            dayViews[index].setTextColor(getResources().getColor(android.R.color.white));
        } else {
            dayViews[index].setBackgroundResource(R.drawable.circle_background_unselected);
            dayViews[index].setTextColor(getResources().getColor(R.color.blue_primary));
        }
    }

    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            java.io.File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (java.io.IOException ex) {
                Toast.makeText(this, "Error creating file", Toast.LENGTH_SHORT).show();
            }
            if (photoFile != null) {
                android.net.Uri photoURI = androidx.core.content.FileProvider.getUriForFile(this,
                        "com.example.meditrack.fileprovider",
                        photoFile);
                takePictureIntent.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, photoURI);
                startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
            }
        }
    }

    private java.io.File createImageFile() throws java.io.IOException {
        String timeStamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new java.util.Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        java.io.File storageDir = getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES);
        java.io.File image = java.io.File.createTempFile(imageFileName, ".jpg", storageDir);
        currentPhotoPath = image.getAbsolutePath();
        return image;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            imgMedicine.setImageURI(android.net.Uri.parse(currentPhotoPath));
        }
    }

    private void saveMedicine() {
        String name = edtName.getText().toString().trim();
        String time = edtTime.getText().toString().trim();
        String dosage = edtDosage.getText().toString().trim();
        String stockStr = edtStock.getText().toString().trim();
        String type = spnType.getSelectedItem().toString();

        if (name.isEmpty() || time.isEmpty() || selectedHour == -1) {
            Toast.makeText(this, "Please enter name and time", Toast.LENGTH_SHORT).show();
            return;
        }

        int stock = 0;
        try {
            stock = Integer.parseInt(stockStr);
        } catch (Exception ignored) {}

        Medicine medicine = new Medicine();
        medicine.name = name;
        medicine.time = time;
        medicine.dosage = dosage;
        medicine.type = type;
        medicine.userId = userId;
        medicine.stock = stock;
        medicine.imagePath = currentPhotoPath;

        if (cbEveryDay.isChecked()) {
            medicine.days = "Every day";
        } else {
            List<String> daysList = new ArrayList<>();
            String[] names = {"S", "M", "T", "W", "T", "F", "S"};
            for (int i = 0; i < 7; i++) {
                if (selectedDays[i]) daysList.add(names[i]);
            }
            if (daysList.isEmpty()) {
                medicine.days = "Every day"; // Default if nothing selected
            } else {
                medicine.days = String.join(", ", daysList);
            }
        }

        // Save to Firebase first to get the ID
        Map<String, Object> medData = new HashMap<>();
        medData.put("name", name);
        medData.put("time", time);
        medData.put("dosage", dosage);
        medData.put("type", type);
        medData.put("userId", userId);
        medData.put("days", medicine.days);
        medData.put("username", getSharedPreferences("MeditrackPrefs", MODE_PRIVATE).getString("username", "Unknown"));
        medData.put("isTaken", false);
        medData.put("stock", stock);
        medData.put("imagePath", currentPhotoPath);
        
        firestore.collection("medicines")
                .add(medData)
                .addOnSuccessListener(documentReference -> {
                    new Thread(() -> {
                        medicine.firestoreId = documentReference.getId();
                        long id = db.medicineDao().insert(medicine);
                        medicine.id = (int) id;

                        runOnUiThread(() -> {
                            scheduleReminder(medicine);
                            Toast.makeText(this, "Medicine Saved!", Toast.LENGTH_SHORT).show();
                            finish();
                        });
                    }).start();
                })
                .addOnFailureListener(e -> {
                    new Thread(() -> {
                        // Fallback to local only if Firestore fails
                        long id = db.medicineDao().insert(medicine);
                        medicine.id = (int) id;
                        runOnUiThread(() -> {
                            scheduleReminder(medicine);
                            Toast.makeText(this, "Saved locally (Cloud sync failed)", Toast.LENGTH_SHORT).show();
                            finish();
                        });
                    }).start();
                });
    }

    private void scheduleReminder(Medicine medicine) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, selectedHour);
        calendar.set(Calendar.MINUTE, selectedMinute);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }

        Intent intent = new Intent(this, ReminderReceiver.class);
        intent.putExtra("medId", medicine.id);
        intent.putExtra("medName", medicine.name);
        intent.putExtra("medDetails", medicine.dosage + " " + medicine.type);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this, medicine.id, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), pendingIntent);
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), pendingIntent);
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), pendingIntent);
            }
        }
    }
}