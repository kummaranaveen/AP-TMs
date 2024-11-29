package com.example.myapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

import org.json.JSONObject;

public class RegistrationActivity extends AppCompatActivity {

    private EditText firstNameInput, lastNameInput, usernameInput, emailInput, passwordInput, confirmPasswordInput;
    private Button registerButton, loginLink;
    private RadioGroup roleGroup;
    private String adminCode = "1234"; // Changed admin code to 1234

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_registration);
        EdgeToEdge.enable(this);

        // Initialize views
        firstNameInput = findViewById(R.id.firstname_input);
        lastNameInput = findViewById(R.id.lastname_input);
        usernameInput = findViewById(R.id.username_input);
        emailInput = findViewById(R.id.email_input);
        passwordInput = findViewById(R.id.password_input);
        confirmPasswordInput = findViewById(R.id.confirm_password_input);
        registerButton = findViewById(R.id.registerButton);
        loginLink = findViewById(R.id.loginLink);
//        roleGroup = findViewById(R.id.role_group);

        // Show/hide admin code field based on role selection
//        roleGroup.setOnCheckedChangeListener((group, checkedId) -> {
//            if (checkedId == R.id.radio_admin) {
//                showAdminCodeDialog();
//            }
//        });

        registerButton.setOnClickListener(v -> attemptRegistration());
        loginLink.setOnClickListener(v -> finish()); // Return to login screen
    }

    private void showAdminCodeDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_admin_code, null);
        EditText codeInput = dialogView.findViewById(R.id.admin_code_input);
        
        builder.setView(dialogView)
               .setTitle("Admin Verification")
               .setPositiveButton("Verify", (dialog, which) -> {
                    String enteredCode = codeInput.getText().toString().trim();
                    if (!enteredCode.equals(adminCode)) {
                        roleGroup.check(R.id.radio_user);
                        Toast.makeText(this, "Invalid admin code", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Admin code verified", Toast.LENGTH_SHORT).show();
                    }
               })
               .setNegativeButton("Cancel", (dialog, which) -> {
                    roleGroup.check(R.id.radio_user);
               });
        
        builder.create().show();
    }

    private void attemptRegistration() {
        String firstName = firstNameInput.getText().toString().trim();
        String lastName = lastNameInput.getText().toString().trim();
        String username = usernameInput.getText().toString().trim();
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString();
        String confirmPassword = confirmPasswordInput.getText().toString();
        boolean isAdmin = roleGroup.getCheckedRadioButtonId() == R.id.radio_admin;

        // Validate inputs
        if (firstName.isEmpty() || lastName.isEmpty() || username.isEmpty() || 
            email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!password.equals(confirmPassword)) {
            Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT).show();
            return;
        }

        // Perform registration
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL("http://172.16.20.85:8000/api/register/");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);

                JSONObject registrationData = new JSONObject();
                registrationData.put("first_name", firstName);
                registrationData.put("last_name", lastName);
                registrationData.put("username", username);
                registrationData.put("email", email);
                registrationData.put("password", password);
                registrationData.put("is_admin", isAdmin);

                try (OutputStream os = connection.getOutputStream()) {
                    os.write(registrationData.toString().getBytes());
                }

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_CREATED) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Registration successful", Toast.LENGTH_SHORT).show();
                        // Save user data
                        SharedPreferences sharedPreferences = getSharedPreferences("MyAppPrefs", MODE_PRIVATE);
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putString("username", username);
                        editor.putString("email", email);
                        editor.putBoolean("is_admin", isAdmin);
                        editor.apply();

                        // Set result OK and finish
                        setResult(RESULT_OK);
                        finish();
                    });
                } else {
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(connection.getErrorStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    final String errorMessage = response.toString();
                    runOnUiThread(() -> 
                        Toast.makeText(this, 
                            "Registration failed: " + errorMessage, Toast.LENGTH_SHORT).show()
                    );
                }
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> 
                    Toast.makeText(this, 
                        "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }).start();
    }
}
