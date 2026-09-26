package com.example.meditrack;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.room.Room;

import com.google.firebase.FirebaseException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;

import java.util.concurrent.TimeUnit;

public class PhoneLoginActivity extends AppCompatActivity {

    private EditText edtPhone, edtOTP;
    private Button btnAction;
    private ProgressBar progressBar;
    private FirebaseAuth mAuth;
    private String verificationId;
    private boolean isCodeSent = false;
    private AppDatabase db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_phone_login);

        edtPhone = findViewById(R.id.edtPhoneNumber);
        edtOTP = findViewById(R.id.edtOTP);
        btnAction = findViewById(R.id.btnAction);
        progressBar = findViewById(R.id.progressPhone);

        mAuth = FirebaseAuth.getInstance();
        db = AppDatabase.getInstance(getApplicationContext());

        btnAction.setOnClickListener(v -> {
            if (!isCodeSent) {
                sendVerificationCode();
            } else {
                verifyCode();
            }
        });
    }

    private void sendVerificationCode() {
        String phoneNumber = edtPhone.getText().toString().trim();
        
        if (phoneNumber.isEmpty()) {
            Toast.makeText(this, "Enter phone number", Toast.LENGTH_SHORT).show();
            return;
        }

        // IMPROVED: Handle country code intelligently
        if (!phoneNumber.startsWith("+")) {
            // Only add +91 if the user hasn't typed any country code
            if (phoneNumber.length() == 10) {
                phoneNumber = "+91" + phoneNumber;
            } else {
                Toast.makeText(this, "Please include country code (e.g. +91...)", Toast.LENGTH_LONG).show();
                return;
            }
        }

        progressBar.setVisibility(View.VISIBLE);
        btnAction.setEnabled(false);

        PhoneAuthOptions options = PhoneAuthOptions.newBuilder(mAuth)
                .setPhoneNumber(phoneNumber)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(this)
                .setCallbacks(mCallbacks)
                .build();
        PhoneAuthProvider.verifyPhoneNumber(options);
    }

    private final PhoneAuthProvider.OnVerificationStateChangedCallbacks mCallbacks = new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
        @Override
        public void onVerificationCompleted(@NonNull PhoneAuthCredential credential) {
            signInWithPhoneAuthCredential(credential);
        }

        @Override
        public void onVerificationFailed(@NonNull FirebaseException e) {
            progressBar.setVisibility(View.GONE);
            btnAction.setEnabled(true);
            
            String errorMessage = e.getMessage();
            if (e instanceof com.google.firebase.auth.FirebaseAuthInvalidCredentialsException) {
                errorMessage = "Invalid Request. Check your phone number format.";
            } else if (e instanceof com.google.firebase.FirebaseTooManyRequestsException) {
                errorMessage = "Quota exceeded. Try again later.";
            } else if (errorMessage != null && errorMessage.contains("BILLING_NOT_ENABLED")) {
                errorMessage = "SMS login requires a Blaze Plan. Please use Test Phone Numbers from Firebase Console for development.";
            }
            
            Log.e("PhoneAuth", "Error: " + e.getMessage(), e);
            Toast.makeText(PhoneLoginActivity.this, "Error: " + errorMessage, Toast.LENGTH_LONG).show();
        }

        @Override
        public void onCodeSent(@NonNull String s, @NonNull PhoneAuthProvider.ForceResendingToken token) {
            progressBar.setVisibility(View.GONE);
            btnAction.setEnabled(true);
            verificationId = s;
            isCodeSent = true;
            findViewById(R.id.tlOTP).setVisibility(View.VISIBLE);
            btnAction.setText("Verify OTP");
            Toast.makeText(PhoneLoginActivity.this, "OTP Sent", Toast.LENGTH_SHORT).show();
        }
    };

    private void verifyCode() {
        String code = edtOTP.getText().toString().trim();
        if (code.isEmpty()) {
            Toast.makeText(this, "Enter OTP", Toast.LENGTH_SHORT).show();
            return;
        }
        progressBar.setVisibility(View.VISIBLE);
        PhoneAuthCredential credential = PhoneAuthProvider.getCredential(verificationId, code);
        signInWithPhoneAuthCredential(credential);
    }

    private void signInWithPhoneAuthCredential(PhoneAuthCredential credential) {
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    progressBar.setVisibility(View.GONE);
                    if (task.isSuccessful()) {
                        handleLoginSuccess(task.getResult().getUser().getPhoneNumber());
                    } else {
                        Toast.makeText(this, "Invalid OTP", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void handleLoginSuccess(String phone) {
        if (phone == null || phone.isEmpty()) {
            phone = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getPhoneNumber() : "unknown";
        }

        final String finalPhone = phone;
        SharedPreferences loginPrefs = getSharedPreferences("MeditrackPrefs", MODE_PRIVATE);
        // Important: Get the role selected by the user on the Login screen
        String selectedRole = loginPrefs.getString("temp_role", "Patient");

        Log.d("PhoneAuth", "Successful Login. Phone: " + finalPhone + " | Selected Role: " + selectedRole);

        new Thread(() -> {
            // Flexible lookup: check with and without '+' prefix
            User user = db.userDao().findByUsername(finalPhone);
            if (user == null && finalPhone.startsWith("+")) {
                user = db.userDao().findByUsername(finalPhone.substring(1));
            }
            if (user == null && !finalPhone.startsWith("+")) {
                user = db.userDao().findByUsername("+" + finalPhone);
            }

            if (user == null) {
                user = new User();
                user.username = finalPhone;
                user.password = "phone_auth_" + System.currentTimeMillis();
                user.role = selectedRole;
                db.userDao().register(user);
                user = db.userDao().findByUsername(finalPhone);
            } else {
                // FORCE update the role and username consistency
                user.role = selectedRole;
                user.username = finalPhone; // Ensure we use the full Firebase format
                db.userDao().update(user);
            }

            // Sync this role to Firestore so the Doctor list works correctly
            java.util.Map<String, Object> userMap = new java.util.HashMap<>();
            userMap.put("username", finalPhone);
            userMap.put("role", selectedRole);
            userMap.put("lastLogin", com.google.firebase.Timestamp.now());
            
            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("users").document(finalPhone)
                    .set(userMap, com.google.firebase.firestore.SetOptions.merge());

            final User finalUser = user;
            runOnUiThread(() -> {
                SharedPreferences.Editor editor = loginPrefs.edit();
                editor.putInt("userId", finalUser.id);
                editor.putString("role", finalUser.role);
                editor.putString("username", finalUser.username);
                editor.apply();

                Log.d("PhoneAuth", "Navigating to Dashboard. Role in Session: " + finalUser.role);

                Intent intent;
                if ("Doctor".equals(finalUser.role)) {
                    intent = new Intent(this, DoctorActivity.class);
                } else {
                    intent = new Intent(this, MainActivity.class);
                }
                intent.putExtra("userId", finalUser.id);
                intent.putExtra("username", finalUser.username);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            });
        }).start();
    }
}
