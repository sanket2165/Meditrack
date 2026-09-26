package com.example.meditrack;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.room.Room;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    AppDatabase db;
    FirebaseFirestore firestore;
    EditText edtRegUsername, edtRegPassword;
    Spinner spnRole;
    Button btnRegister;
    TextView tvLogin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        db = AppDatabase.getInstance(getApplicationContext());

        firestore = FirebaseFirestore.getInstance();

        edtRegUsername = findViewById(R.id.edtRegUsername);
        edtRegPassword = findViewById(R.id.edtRegPassword);
        spnRole = findViewById(R.id.spnRole);
        btnRegister = findViewById(R.id.btnRegister);
        tvLogin = findViewById(R.id.tvLogin);

        String[] roles = {"Patient", "Doctor"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, roles);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spnRole.setAdapter(adapter);

        btnRegister.setOnClickListener(v -> {
            String user = edtRegUsername.getText().toString().trim();
            String pass = edtRegPassword.getText().toString().trim();
            String role = spnRole.getSelectedItem().toString();

            if (user.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            } else {
                // 1. Save to Local Room DB (as before)
                User newUser = new User();
                newUser.username = user;
                newUser.password = pass;
                newUser.role = role;
                db.userDao().register(newUser);

                // 2. Save to Firebase Cloud Firestore
                Map<String, Object> userData = new HashMap<>();
                userData.put("username", user);
                userData.put("role", role); // "Patient" or "Doctor"
                userData.put("password", pass);
                userData.put("createdAt", System.currentTimeMillis());

                firestore.collection("users")
                        .document(user)
                        .set(userData)
                        .addOnSuccessListener(aVoid -> {
                            Toast.makeText(this, "Cloud Sync Successful!", Toast.LENGTH_SHORT).show();
                        })
                        .addOnFailureListener(e -> {
                            Toast.makeText(this, "Cloud Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        });

                Toast.makeText(this, "Registration Successful!", Toast.LENGTH_SHORT).show();
                finish();
            }
        });

        tvLogin.setOnClickListener(v -> finish());
    }
}