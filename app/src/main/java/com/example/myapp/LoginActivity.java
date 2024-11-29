package com.example.myapp;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;
import android.app.ProgressDialog;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONObject;
import org.json.JSONException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class LoginActivity extends AppCompatActivity {

    private EditText usernameInput, passwordInput;
    private Button loginButton, registerLink;
    private RadioGroup roleGroup;
    private ProgressDialog progressDialog;
    private final String LOGIN_URL = "http://172.16.20.85:8000/api/login/"; // Replace with your backend URL

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        EdgeToEdge.enable(this);// Make sure you have this layout for login

        // Initialize views
        usernameInput = findViewById(R.id.username_input);
        passwordInput = findViewById(R.id.password_input);
        loginButton = findViewById(R.id.loginButton);
        registerLink = findViewById(R.id.registerLink);
        roleGroup = findViewById(R.id.role_group);

        // Initialize progress dialog
        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Logging in...");
        progressDialog.setCancelable(false);
        progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);

        // Hide register link for admin login
        roleGroup.setOnCheckedChangeListener((group, checkedId) -> {
            registerLink.setVisibility(checkedId == R.id.radio_admin ? View.GONE : View.VISIBLE);
        });

        // Set click listeners
        loginButton.setOnClickListener(v -> attemptLogin());
        registerLink.setOnClickListener(v -> navigateToRegister());
    }

    // Login method
    public void attemptLogin() {
        String username = usernameInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();

        // Hardcoded admin credentials
        if (username.equals("admin") && password.equals("1234")) {
            SharedPreferences sharedPreferences = getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean("is_admin", true);
            editor.apply();

            // Navigate to Admin Dashboard
            Intent intent = new Intent(this, AdminDashboardActivity.class);
            startActivity(intent);
            finish();
        } else {
            boolean isAdmin = roleGroup.getCheckedRadioButtonId() == R.id.radio_admin;

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            // Show progress dialog
            runOnUiThread(() -> {
                progressDialog.show();
                loginButton.setEnabled(false); // Disable login button while processing
            });

            new Thread(() -> {
                HttpURLConnection connection = null;
                try {
                    URL url = new URL(LOGIN_URL);
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("POST");
                    connection.setRequestProperty("Content-Type", "application/json");
                    connection.setDoOutput(true);

                    JSONObject loginData = new JSONObject();
                    loginData.put("username", username);
                    loginData.put("password", password);
                    loginData.put("is_admin", isAdmin);

                    try (OutputStream os = connection.getOutputStream()) {
                        os.write(loginData.toString().getBytes("UTF-8"));
                    }

                    int responseCode = connection.getResponseCode();
                    BufferedReader reader;
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    } else {
                        reader = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
                    }

                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }

                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        JSONObject jsonResponse = new JSONObject(response.toString());
                        String accessToken = jsonResponse.getString("access");
                        String refreshToken = jsonResponse.getString("refresh");
                        boolean isAdminResponse = jsonResponse.optBoolean("is_admin", false);

                        if (isAdmin && !isAdminResponse) {
                            runOnUiThread(() -> {
                                progressDialog.dismiss();
                                loginButton.setEnabled(true);
                                Toast.makeText(this, "Invalid admin code", Toast.LENGTH_SHORT).show();
                            });
                        } else {
                            // Save user data
                            SharedPreferences sharedPreferences = getSharedPreferences("MyAppPrefs", MODE_PRIVATE);
                            SharedPreferences.Editor editor = sharedPreferences.edit();
                            editor.putString("access_token", accessToken);
                            editor.putString("refresh_token", refreshToken);
                            editor.putString("username", username);
                            editor.putBoolean("is_admin", isAdminResponse);
                            editor.apply();

                            // Navigate to dashboard
                            runOnUiThread(() -> {
                                try {
                                    progressDialog.dismiss();
                                    Intent intent = new Intent(LoginActivity.this, DashBoardActivity.class);
                                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | 
                                                  Intent.FLAG_ACTIVITY_NEW_TASK | 
                                                  Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                    startActivity(intent);
                                    finish();
                                } catch (Exception e) {
                                    Log.e("Navigation", "Error navigating to dashboard: " + e.getMessage());
                                    loginButton.setEnabled(true);
                                    Toast.makeText(this, "Error opening dashboard: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                }
                            });
                        }
                    } else {
                        runOnUiThread(() -> {
                            progressDialog.dismiss();
                            loginButton.setEnabled(true);
                            try {
                                JSONObject errorResponse = new JSONObject(response.toString());
                                String errorMessage = errorResponse.optString("detail", "Login failed");
                                Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show();
                            } catch (JSONException e) {
                                Log.e("Login", "Error parsing error response: " + e.getMessage());
                                Toast.makeText(this, "Login failed", Toast.LENGTH_LONG).show();
                            }
                        });
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                    final String errorMessage = e.getMessage();
                    runOnUiThread(() -> {
                        progressDialog.dismiss();
                        loginButton.setEnabled(true);
                        Toast.makeText(this, "Error: " + errorMessage, Toast.LENGTH_LONG).show();
                    });
                } finally {
                    if (connection != null) {
                        connection.disconnect();
                    }
                }
            }).start();
        }
    }

    // Method to navigate to the registration page
    private void navigateToRegister() {
        Intent intent = new Intent(LoginActivity.this, RegistrationActivity.class);
        // Start registration activity for result
        startActivityForResult(intent, 100); // Using 100 as request code for registration
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 100 && resultCode == RESULT_OK) {
            // Registration was successful, navigate to dashboard
            Intent dashboardIntent = new Intent(LoginActivity.this, DashBoardActivity.class);
            dashboardIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | 
                                   Intent.FLAG_ACTIVITY_NEW_TASK | 
                                   Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(dashboardIntent);
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }
}
