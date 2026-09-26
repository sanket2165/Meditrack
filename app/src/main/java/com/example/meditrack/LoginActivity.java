package com.example.meditrack;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;
import android.util.Patterns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.room.Room;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;

public class LoginActivity extends AppCompatActivity {

    AppDatabase db;
    EditText edtUsername, edtPassword;
    Button btnLogin;
    TextView tvRegister, tvForgotPassword, tvForgotEmail;
    CardView btnGoogle, btnPhone, btnFacebook;

    private FirebaseAuth mAuth;
    private GoogleSignInClient mGoogleSignInClient;
    private static final int RC_SIGN_IN = 9001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences("MeditrackPrefs", MODE_PRIVATE);
        int savedUserId = prefs.getInt("userId", -1);
        String savedRole = prefs.getString("role", "");
        String savedUsername = prefs.getString("username", "");

        if (savedUserId != -1 && savedUsername != null && !savedUsername.trim().isEmpty()) {
            checkBiometricAndLogin(savedUserId, savedRole, savedUsername);
            // Don't call setContentView yet if we're doing biometric
        } else {
            initLoginUi();
        }
    }

    private void checkBiometricAndLogin(int userId, String role, String username) {
        BiometricManager biometricManager = BiometricManager.from(this);
        switch (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.DEVICE_CREDENTIAL)) {
            case BiometricManager.BIOMETRIC_SUCCESS:
                showBiometricPrompt(userId, role, username);
                break;
            default:
                // Biometrics not available, proceed to normal login or auto-login if you prefer
                navigateToDashboard(userId, role, username);
                break;
        }
    }

    private void showBiometricPrompt(int userId, String role, String username) {
        Executor executor = ContextCompat.getMainExecutor(this);
        BiometricPrompt biometricPrompt = new BiometricPrompt(LoginActivity.this,
                executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                // On error, show login UI
                initLoginUi();
                Toast.makeText(getApplicationContext(), "Authentication error: " + errString, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                navigateToDashboard(userId, role, username);
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                Toast.makeText(getApplicationContext(), "Authentication failed", Toast.LENGTH_SHORT).show();
            }
        });

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Biometric Login for Meditrack")
                .setSubtitle("Log in using your biometric credential")
                .setNegativeButtonText("Use Password")
                .build();

        biometricPrompt.authenticate(promptInfo);
    }

    private void navigateToDashboard(int userId, String role, String username) {
        Intent intent;
        if ("Doctor".equals(role)) {
            intent = new Intent(this, DoctorActivity.class);
        } else {
            intent = new Intent(this, MainActivity.class);
        }
        intent.putExtra("userId", userId);
        intent.putExtra("username", username);
        startActivity(intent);
        finish();
    }

    private void initLoginUi() {
        setContentView(R.layout.activity_login);

        db = AppDatabase.getInstance(getApplicationContext());

        edtUsername = findViewById(R.id.edtUsername);
        edtPassword = findViewById(R.id.edtPassword);
        btnLogin = findViewById(R.id.btnLogin);
        tvRegister = findViewById(R.id.tvRegister);
        tvForgotPassword = findViewById(R.id.tvForgotPassword);
        tvForgotEmail = findViewById(R.id.tvForgotEmail);
        btnGoogle = findViewById(R.id.btnGoogle);
        btnPhone = findViewById(R.id.btnPhone);
        btnFacebook = findViewById(R.id.btnFacebook);

        // Language Switcher Logic
        findViewById(R.id.btnChangeLang).setOnClickListener(v -> showLanguageDialog());

        mAuth = FirebaseAuth.getInstance();

        // Configure Google Sign In - Use a direct string if R.string.default_web_client_id is missing
        String webClientId = "692310113837-dq3f55mh387iesshiftqfgsph5aindio.apps.googleusercontent.com";
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(webClientId)
                .requestEmail()
                .build();

        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        btnLogin.setOnClickListener(v -> {
            String user = edtUsername.getText().toString().trim();
            String pass = edtPassword.getText().toString().trim();

            if (user.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            new Thread(() -> {
                User loggedInUser = db.userDao().login(user, pass);
                runOnUiThread(() -> {
                    if (loggedInUser != null) {
                        SharedPreferences prefs = getSharedPreferences("MeditrackPrefs", MODE_PRIVATE);
                        SharedPreferences.Editor editor = prefs.edit();
                        editor.putInt("userId", loggedInUser.id);
                        editor.putString("role", loggedInUser.role);
                        editor.putString("username", loggedInUser.username);
                        editor.apply();

                        navigateToDashboard(loggedInUser.id, loggedInUser.role, loggedInUser.username);
                    } else {
                        Toast.makeText(this, "Invalid credentials", Toast.LENGTH_SHORT).show();
                    }
                });
            }).start();
        });

        tvRegister.setOnClickListener(v -> {
            startActivity(new Intent(this, RegisterActivity.class));
        });

        tvForgotPassword.setOnClickListener(v -> {
            String email = edtUsername.getText().toString().trim();
            if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "Please enter your registered email in the Username field", Toast.LENGTH_LONG).show();
                return;
            }
            
            mAuth.sendPasswordResetEmail(email)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Toast.makeText(this, "Reset link sent to " + email, Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(this, "Error: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
        });

        tvForgotEmail.setOnClickListener(v -> {
            Toast.makeText(this, "Try checking your Google/Facebook account or contact support@meditrack.com", Toast.LENGTH_LONG).show();
        });

        btnGoogle.setOnClickListener(v -> signInWithGoogle());
        btnFacebook.setOnClickListener(v -> showSocialToast("Facebook"));
        btnPhone.setOnClickListener(v -> {
            saveTempRole();
            startActivity(new Intent(this, PhoneLoginActivity.class));
        });

        // Add listeners to radio group to save role immediately
        RadioGroup rgRole = findViewById(R.id.rgRole);
        if (rgRole != null) {
            rgRole.setOnCheckedChangeListener((group, checkedId) -> saveTempRole());
        }
    }

    private void saveTempRole() {
        RadioGroup rgRole = findViewById(R.id.rgRole);
        String selectedRole = (rgRole != null && rgRole.getCheckedRadioButtonId() == R.id.rbDoctor) ? "Doctor" : "Patient";
        getSharedPreferences("MeditrackPrefs", MODE_PRIVATE).edit().putString("temp_role", selectedRole).apply();
    }

    private void signInWithGoogle() {
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                firebaseAuthWithGoogle(account.getIdToken());
            } catch (ApiException e) {
                Toast.makeText(this, "Google sign in failed", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        handleFirebaseUser(user);
                    } else {
                        Toast.makeText(this, "Authentication Failed.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void handleFirebaseUser(FirebaseUser firebaseUser) {
        if (firebaseUser == null) return;

        String email = firebaseUser.getEmail();
        if (email == null) email = firebaseUser.getPhoneNumber();
        if (email == null) email = firebaseUser.getUid();

        final String finalEmail = email;
        
        RadioGroup rgRole = findViewById(R.id.rgRole);
        final String selectedRole = (rgRole != null && rgRole.getCheckedRadioButtonId() == R.id.rbDoctor) ? "Doctor" : "Patient";

        new Thread(() -> {
            User localUser = db.userDao().findByUsername(finalEmail);
            
            if (localUser == null) {
                localUser = new User();
                localUser.username = finalEmail;
                localUser.password = "social_auth_" + firebaseUser.getUid();
                localUser.role = selectedRole; 
                db.userDao().register(localUser);
                localUser = db.userDao().findByUsername(finalEmail);
            } else {
                // UPDATE: If user exists, update their role to the one currently selected
                localUser.role = selectedRole;
                db.userDao().update(localUser);
            }

            // Sync to Firestore if email is valid
            if (finalEmail != null && !finalEmail.trim().isEmpty()) {
                Map<String, Object> userMap = new HashMap<>();
                userMap.put("username", finalEmail);
                userMap.put("role", selectedRole);
                FirebaseFirestore.getInstance()
                        .collection("users").document(finalEmail)
                        .set(userMap, SetOptions.merge());
            }

            final User finalLocalUser = localUser;
            runOnUiThread(() -> {
                SharedPreferences prefs = getSharedPreferences("MeditrackPrefs", MODE_PRIVATE);
                SharedPreferences.Editor editor = prefs.edit();
                editor.putInt("userId", finalLocalUser.id);
                editor.putString("role", finalLocalUser.role);
                editor.putString("username", finalLocalUser.username);
                editor.apply();

                navigateToDashboard(finalLocalUser.id, finalLocalUser.role, finalLocalUser.username);
            });
        }).start();
    }

    private void showSocialToast(String platform) {
        Toast.makeText(this, platform + " login requires API integration (Firebase/SDK).", Toast.LENGTH_LONG).show();
    }

    private void showLanguageDialog() {
        String[] languages = {"English", "हिन्दी (Hindi)", "বাংলা (Bengali)"};
        new AlertDialog.Builder(this)
                .setTitle(R.string.select_language)
                .setItems(languages, (dialog, which) -> {
                    String langCode = "en";
                    if (which == 1) langCode = "hi";
                    if (which == 2) langCode = "bn";
                    setLocale(langCode);
                })
                .show();
    }

    private void setLocale(String lang) {
        Locale locale = new Locale(lang);
        Locale.setDefault(locale);
        Configuration config = new Configuration();
        config.setLocale(locale);
        getResources().updateConfiguration(config, getResources().getDisplayMetrics());

        // Save selected language to prefs
        getSharedPreferences("MeditrackPrefs", MODE_PRIVATE).edit().putString("app_lang", lang).apply();

        // Restart activity to apply changes
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }
}