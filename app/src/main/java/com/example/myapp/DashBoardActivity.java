package com.example.myapp;

import android.app.DatePickerDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.Log;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.graphics.Rect;
import android.view.View;

import android.content.Intent;
import android.widget.EditText;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import android.app.AlertDialog;
import android.widget.ImageButton;

import android.app.NotificationManager;
import androidx.core.app.NotificationManagerCompat;
import android.net.Uri;
import android.provider.Settings;
import android.os.Build;

public class DashBoardActivity extends AppCompatActivity implements TaskAdapter.OnTaskActionListener{
    private TextView totalTasksTextView;
    private TextView completedTasksTextView;
    private TextView priorityTaskCountTextView;
    private Spinner statusSpinner;
    private Spinner prioritySpinner;

    private TextView startDatePicker;

    private TextView endDatePicker;
    private Date startDate;
    private Date endDate;
    private List<Task> allTasks;

    private static final String DATE_FORMAT_DISPLAY = "MMM dd, yyyy";
    private static final String DATE_FORMAT_API = "yyyy-MM-dd";
    private SimpleDateFormat displayDateFormat;
    private SimpleDateFormat apiDateFormat;

    private RecyclerView tasksRecyclerView;
    private TaskAdapter taskAdapter;
    private FloatingActionButton fabAddTask;

    private ImageButton logoutButton;

    private FloatingActionButton  fabFilterTasks;

    private ImageButton profileButton;

    private int lowPriorityCount = 0;
    private int mediumPriorityCount = 0;
    private int highPriorityCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_dashboard);




        displayDateFormat = new SimpleDateFormat(DATE_FORMAT_DISPLAY, Locale.getDefault());
        apiDateFormat = new SimpleDateFormat(DATE_FORMAT_API, Locale.getDefault());


        initializeViews();
        setupCardClickListeners();
        initializeFilterViews();
        setupSpinners();
        setupDatePickers();
        setupFilterButtons();
        setupFab();
        fetchTasksFromServer();

        //fabFilterTasks = findViewById(R.id.fab_filter_tasks);
//        fabFilterTasks.setOnClickListener(v -> showFilterBottomSheet());

    }

    private void setupFab() {
        fabAddTask = findViewById(R.id.fab_add_task);
        fabAddTask.setOnClickListener(v -> {
            Intent intent = new Intent(this, CreateTaskActivity.class);
            startActivity(intent);
        });
    }

//    private void showFilterBottomSheet() {
//        FilterBottomSheet filterBottomSheet = new FilterBottomSheet();
//        filterBottomSheet.show(getSupportFragmentManager(), "filter_bottom_sheet");
//    }



    private void showLogoutConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Yes", (dialog, which) -> performLogout())
                .setNegativeButton("No", null)
                .show();
    }

    private void performLogout() {
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL("http://172.16.20.85:8000/api/logout/");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Authorization", "Bearer " + getAccessToken());

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK ||
                        responseCode == HttpURLConnection.HTTP_NO_CONTENT) {

                    // Clear stored tokens
                    SharedPreferences sharedPreferences =
                            getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.remove("access_token");
                    editor.remove("refresh_token");
                    editor.apply();

                    runOnUiThread(() -> {
                        Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show();
                        // Navigate to MainActivity
                        Intent intent = new Intent(this, MainActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    });
                } else {
                    runOnUiThread(() ->
                            Toast.makeText(this, "Logout failed. Please try again.",
                                    Toast.LENGTH_SHORT).show()
                    );
                }
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() ->
                        Toast.makeText(this, "Error during logout: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show()
                );
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }).start();
    }
//    private void setupFab() {
//        fabAddTask = findViewById(R.id.fab_add_task);
//        fabAddTask.setOnClickListener(v -> {
//            Intent intent = new Intent(this, CreateTaskActivity.class);
//            startActivity(intent);
//        });
//    }

    private void initializeViews() {
        totalTasksTextView = findViewById(R.id.total_task_count);
        completedTasksTextView = findViewById(R.id.completed_task_count);
        priorityTaskCountTextView = findViewById(R.id.priority_status_count);
        tasksRecyclerView = findViewById(R.id.tasks_recycler_view);
        setupRecyclerView();

        profileButton = findViewById(R.id.profile_logo);
        profileButton.setOnClickListener(v -> showUserProfile());

        TextView dashboardTitle = findViewById(R.id.dashboard_title);
        SharedPreferences sharedPreferences = getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE);
        String username = sharedPreferences.getString("username", "User");
        dashboardTitle.setText("Welcome, " + username);
    }

    private void setupRecyclerView() {
        taskAdapter = new TaskAdapter(this);
        tasksRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        tasksRecyclerView.setAdapter(taskAdapter);

        // Add item decoration for spacing
        int spacing = getResources().getDimensionPixelSize(R.dimen.task_item_spacing);
        tasksRecyclerView.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull Rect outRect, @NonNull View view,
                                       @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                outRect.bottom = spacing;
            }
        });
    }


    private void initializeFilterViews() {
        statusSpinner = findViewById(R.id.status_spinner);
        prioritySpinner = findViewById(R.id.priority_spinner);
        startDatePicker = findViewById(R.id.start_date_picker);
        endDatePicker = findViewById(R.id.end_date_picker);
    }

    private void setupSpinners() {
        ArrayAdapter<CharSequence> statusAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new String[]{"All", "yet-to-start", "in-progress", "completed", "hold"});
        statusAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        statusSpinner.setAdapter(statusAdapter);

        ArrayAdapter<CharSequence> priorityAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new String[]{"All", "low", "medium", "high"});
        priorityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        prioritySpinner.setAdapter(priorityAdapter);
    }

    private void setupDatePickers() {
        startDatePicker.setText("Select Start Date");
        endDatePicker.setText("Select End Date");
        startDatePicker.setOnClickListener(v -> showDatePicker(true));
        endDatePicker.setOnClickListener(v -> showDatePicker(false));
    }

    private void setupFilterButtons() {
        statusSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                applyTaskFilters();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        prioritySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                applyTaskFilters();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void showDatePicker(boolean isStartDate) {
        Calendar calendar = Calendar.getInstance();
        if (isStartDate && startDate != null) {
            calendar.setTime(startDate);
        } else if (!isStartDate && endDate != null) {
            calendar.setTime(endDate);
        }

        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    Calendar selectedDate = Calendar.getInstance();
                    selectedDate.set(year, month, dayOfMonth);
                    setToStartOfDay(selectedDate);

                    Date selectedDateTime = selectedDate.getTime();
                    TextView dateText = isStartDate ? startDatePicker : endDatePicker;

                    if (isStartDate) {
                        startDate = selectedDateTime;
                        String formattedDate = displayDateFormat.format(startDate);
                        dateText.setText(formattedDate);
                        Log.d("DatePicker", "Set start date: " + formattedDate);
                    } else {
                        endDate = selectedDateTime;
                        String formattedDate = displayDateFormat.format(endDate);
                        dateText.setText(formattedDate);
                        Log.d("DatePicker", "Set end date: " + formattedDate);
                    }

                    applyTaskFilters();
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );

        datePickerDialog.show();
    }

    private void setToStartOfDay(Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
    }

    private String getAccessToken() {
        SharedPreferences sharedPreferences = getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE);
        return sharedPreferences.getString("access_token", null);
    }


    private void fetchTasksFromServer() {
        new Thread(() -> {
            HttpURLConnection connection = null;
            BufferedReader reader = null;
            try {
                URL url = new URL("http://172.16.20.85:8000/api/tasks/list/");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setDoInput(true);

                String accessToken = getAccessToken();
                if (accessToken == null) {
                    runOnUiThread(() -> Toast.makeText(this, "Access token not found", Toast.LENGTH_SHORT).show());
                    return;
                }

                connection.setRequestProperty("Authorization", "Bearer " + accessToken);
                int responseCode = connection.getResponseCode();

                if (responseCode != 200) {
                    BufferedReader errorReader = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
                    StringBuilder errorResponse = new StringBuilder();
                    String line;
                    while ((line = errorReader.readLine()) != null) {
                        errorResponse.append(line);
                    }
                    String finalErrorMessage = errorResponse.toString();
                    runOnUiThread(() -> Toast.makeText(this, "Error: " + finalErrorMessage, Toast.LENGTH_SHORT).show());
                    return;
                }

                reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }

                // Parse JSON response
                JSONObject jsonResponse = new JSONObject(response.toString());
                JSONArray tasksArray = jsonResponse.getJSONArray("results");
                List<Task> tasks = new ArrayList<>();

                // Log the raw JSON response
                Log.d("TaskFetch", "Raw JSON response: " + response.toString());
                Log.d("TaskFetch", "Number of tasks in response: " + tasksArray.length());

                for (int i = 0; i < tasksArray.length(); i++) {
                    JSONObject taskObject = tasksArray.getJSONObject(i);
                    // Log the individual task JSON
                    Log.d("TaskCreation", "Processing task JSON: " + taskObject.toString());

                    int id = taskObject.getInt("id");
                    String status = taskObject.getString("status");
                    String priority = taskObject.getString("priority");
                    String title = taskObject.getString("title");
                    String description = taskObject.getString("description");

                    Date dueDate = null;
                    if (!taskObject.isNull("deadline")) {
                        String deadlineStr = taskObject.getString("deadline");
                        try {
                            dueDate = apiDateFormat.parse(deadlineStr);
                        } catch (ParseException e) {
                            Log.e("DateParse", "Error parsing deadline: " + deadlineStr, e);
                        }
                    }

                    Task task = new Task(id, status, priority, dueDate, title, description);
                    tasks.add(task);
                    Log.d("TaskCreation", String.format("Created task - Title: %s, Status: %s, Priority: %s, Deadline: %s",
                            title, status, priority,
                            dueDate != null ? apiDateFormat.format(dueDate) : "null"));
                }

                // Update UI on main thread
                List<Task> finalTasks = tasks;
                runOnUiThread(() -> {
                    allTasks = new ArrayList<>(finalTasks);
                    Log.d("TaskCreation", "Total tasks loaded: " + allTasks.size());
                    updateUIWithTasks(finalTasks);
                });

            } catch (Exception e) {
                Log.e("FetchTasksError", "Error fetching tasks", e);
                String errorMessage = e.getMessage();
                runOnUiThread(() -> Toast.makeText(this, "Error: " + errorMessage, Toast.LENGTH_SHORT).show());
            } finally {
                try {
                    if (reader != null) reader.close();
                    if (connection != null) connection.disconnect();
                } catch (Exception e) {
                    Log.e("Cleanup", "Error during cleanup", e);
                }
            }
        }).start();
    }

    private void applyTaskFilters() {
        if (allTasks == null || allTasks.isEmpty()) {
            Toast.makeText(this, "No tasks to filter", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            String selectedStatus = statusSpinner.getSelectedItem().toString();
            String selectedPriority = prioritySpinner.getSelectedItem().toString();

            // Log all tasks before filtering
            Log.d("TaskFilter", "Before filtering - Total tasks: " + allTasks.size());
            for (Task task : allTasks) {
                Log.d("TaskFilter", String.format("Task - Title: %s, Status: %s, Priority: %s, Due Date: %s",
                        task.getTitle(),
                        task.getStatus(),
                        task.getPriority(),
                        task.getDueDate() != null ? apiDateFormat.format(task.getDueDate()) : "null"));
            }

            TaskFilter filter = new TaskFilter.Builder()
                    .setStatus(selectedStatus)
                    .setPriority(selectedPriority)
                    .setDateRange(startDate, endDate)
                    .build();

            List<Task> filteredTasks = filter.apply(allTasks);

            // Log filter parameters
            Log.d("TaskFilter", String.format("Filter parameters - Status: %s, Priority: %s",
                    selectedStatus, selectedPriority));
            if (startDate != null || endDate != null) {
                Log.d("TaskFilter", String.format("Date range: %s to %s",
                        startDate != null ? apiDateFormat.format(startDate) : "none",
                        endDate != null ? apiDateFormat.format(endDate) : "none"));
            }

            // Log filtered results
            Log.d("TaskFilter", "After filtering - Tasks: " + filteredTasks.size());
            for (Task task : filteredTasks) {
                Log.d("TaskFilter", String.format("Filtered Task - Title: %s, Status: %s, Priority: %s, Due Date: %s",
                        task.getTitle(),
                        task.getStatus(),
                        task.getPriority(),
                        task.getDueDate() != null ? apiDateFormat.format(task.getDueDate()) : "null"));
            }

            updateUIWithTasks(filteredTasks);

        } catch (IllegalArgumentException e) {
            Log.e("TaskFilter", "Filter error: " + e.getMessage());
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDeleteTask(Task task) {
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL("http://172.16.20.85:8000/api/tasks/delete/" + task.getId() + "/");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("DELETE");
                connection.setRequestProperty("Authorization", "Bearer " + getAccessToken());

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_NO_CONTENT ||
                        responseCode == HttpURLConnection.HTTP_OK) {
                    runOnUiThread(() -> {
                        allTasks.remove(task);
                        updateUIWithTasks(allTasks);
                        Toast.makeText(this, "Task deleted successfully", Toast.LENGTH_SHORT).show();
                    });
                } else {
                    // Read error response
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(connection.getErrorStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    final String errorMessage = response.toString();
                    runOnUiThread(() ->
                            Toast.makeText(this, "Failed to delete task: " + errorMessage,
                                    Toast.LENGTH_SHORT).show()
                    );
                }
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() ->
                        Toast.makeText(this, "Error: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show()
                );
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }).start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        fetchTasksFromServer(); // Refresh tasks when returning from EditTaskActivity
    }

    @Override
    public void onEditTask(Task task) {
        Intent intent = new Intent(this, EditTaskActivity.class);
        intent.putExtra("task_id", task.getId());
        intent.putExtra("task_title", task.getTitle());
        intent.putExtra("task_description", task.getDescription());
        intent.putExtra("task_status", task.getStatus());
        intent.putExtra("task_priority", task.getPriority());
        if (task.getDueDate() != null) {
            intent.putExtra("task_due_date", apiDateFormat.format(task.getDueDate()));
        }
        startActivity(intent);
    }

    private void clearFilters() {
        statusSpinner.setSelection(0);
        prioritySpinner.setSelection(0);
        startDate = null;
        endDate = null;
        startDatePicker.setText("Select Start Date");
        endDatePicker.setText("Select End Date");

        if (allTasks != null) {
            updateUIWithTasks(allTasks);
            Toast.makeText(this, "Filters cleared", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateUIWithTasks(List<Task> tasks) {
        int totalTasks = tasks.size();
        int completedTasks = 0;
        lowPriorityCount = 0;
        mediumPriorityCount = 0;
        highPriorityCount = 0;

        for (Task task : tasks) {
            if (task.getStatus().toLowerCase().equals("completed")) {
                completedTasks++;
            }

            switch (task.getPriority().toLowerCase()) {
                case "low":
                    lowPriorityCount++;
                    break;
                case "medium":
                    mediumPriorityCount++;
                    break;
                case "high":
                    highPriorityCount++;
                    break;
            }
        }

        totalTasksTextView.setText(String.valueOf(totalTasks));
        completedTasksTextView.setText(String.valueOf(completedTasks));
        priorityTaskCountTextView.setText(String.valueOf(highPriorityCount));

        taskAdapter.setTasks(tasks);
    }

    private void showUserProfile() {
        SharedPreferences sharedPreferences = getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE);
        boolean isAdmin = sharedPreferences.getBoolean("is_admin", false);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(
            isAdmin ? R.layout.dialog_admin_profile : R.layout.dialog_user_profile, 
            null
        );
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        if (isAdmin) {
            setupAdminProfile(dialogView, dialog);
        } else {
            setupUserProfile(dialogView, dialog);
        }

        dialog.show();
    }

    private void setupAdminProfile(View view, AlertDialog dialog) {
        TextView totalUsersCount = view.findViewById(R.id.total_users_count);
        TextView totalTasksCount = view.findViewById(R.id.total_tasks_count);
        Button viewUsersButton = view.findViewById(R.id.view_users_button);
        Button viewAllTasksButton = view.findViewById(R.id.view_all_tasks_button);
        Button logoutButton = view.findViewById(R.id.logout_button);
        Button closeButton = view.findViewById(R.id.close_dialog);

        // Fetch and display admin stats
        fetchAdminStats(totalUsersCount, totalTasksCount);

        viewUsersButton.setOnClickListener(v -> {
            dialog.dismiss();
            showUsersManagementDialog();
        });

        viewAllTasksButton.setOnClickListener(v -> {
            dialog.dismiss();
            showAllTasksDialog();
        });

        logoutButton.setOnClickListener(v -> {
            dialog.dismiss();
            showLogoutConfirmation();
        });

        closeButton.setOnClickListener(v -> dialog.dismiss());
    }

    private void fetchAdminStats(TextView usersCount, TextView tasksCount) {
        // Implement API call to fetch admin statistics
        // Update the TextViews with the fetched data
    }

    private void showUsersManagementDialog() {
        // Implement dialog to show and manage users
    }

    private void showAllTasksDialog() {
        // Implement dialog to show all users' tasks
    }

    private void setupUserProfile(View view, AlertDialog dialog) {
        // Find views
        TextView usernameText = view.findViewById(R.id.profile_username);
        TextView emailText = view.findViewById(R.id.profile_email);
        Button notificationsButton = view.findViewById(R.id.notifications_button);
        Button storageButton = view.findViewById(R.id.storage_button);
        Button helpButton = view.findViewById(R.id.help_button);
        Button logoutButton = view.findViewById(R.id.logout_button);
        Button closeButton = view.findViewById(R.id.close_profile_dialog);

        // Get user data from SharedPreferences
        SharedPreferences sharedPreferences = getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE);
        String username = sharedPreferences.getString("username", "Not available");
        String email = sharedPreferences.getString("email", "Not available");

        // Set user data
        usernameText.setText(username);
        emailText.setText(email);

        // Make username and email clickable
        usernameText.setOnClickListener(v -> {
            showEditDialog("username", username, (newUsername) -> {
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString("username", newUsername);
                editor.apply();
                usernameText.setText(newUsername);
                TextView dashboardTitle = findViewById(R.id.dashboard_title);
                dashboardTitle.setText("Welcome, " + newUsername);
                Toast.makeText(this, "Username updated successfully", Toast.LENGTH_SHORT).show();
            });
        });

        emailText.setOnClickListener(v -> {
            showEditDialog("email", email, (newEmail) -> {
                if (android.util.Patterns.EMAIL_ADDRESS.matcher(newEmail).matches()) {
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putString("email", newEmail);
                    editor.apply();
                    emailText.setText(newEmail);
                    Toast.makeText(this, "Email updated successfully", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT).show();
                }
            });
        });

        // Button click listeners
        notificationsButton.setOnClickListener(v -> {
            if (!areNotificationsEnabled()) {
                Toast.makeText(this, "Notifications are disabled. Please enable them in settings.", 
                    Toast.LENGTH_LONG).show();
                openNotificationSettings();
            } else {
                Toast.makeText(this, "Notifications are enabled", Toast.LENGTH_SHORT).show();
            }
        });

        storageButton.setOnClickListener(v -> {
            long usedStorage = calculateStorageUsed();
            String storageMessage = String.format("Storage used: %.2f MB", usedStorage / (1024.0 * 1024.0));
            Toast.makeText(this, storageMessage, Toast.LENGTH_LONG).show();
        });

        helpButton.setOnClickListener(v -> {
            showHelpDialog();
        });



        logoutButton.setOnClickListener(v -> {
            dialog.dismiss();
            showLogoutConfirmation();
        });

        closeButton.setOnClickListener(v -> dialog.dismiss());
    }

    private void showEditDialog(String field, String currentValue, OnValueUpdateListener listener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_edit_profile, null);
        builder.setView(dialogView);

        TextView titleText = dialogView.findViewById(R.id.edit_title);
        EditText editText = dialogView.findViewById(R.id.edit_value);
        Button saveButton = dialogView.findViewById(R.id.save_button);
        Button cancelButton = dialogView.findViewById(R.id.cancel_button);

        titleText.setText("Edit " + field.substring(0, 1).toUpperCase() + field.substring(1));
        editText.setText(currentValue);
        editText.setHint("Enter new " + field);

        AlertDialog dialog = builder.create();
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        saveButton.setOnClickListener(v -> {
            String newValue = editText.getText().toString().trim();
            if (!newValue.isEmpty()) {
                listener.onUpdate(newValue);
                dialog.dismiss();
            } else {
                editText.setError(field + " cannot be empty");
            }
        });

        cancelButton.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private interface OnValueUpdateListener {
        void onUpdate(String newValue);
    }

    private boolean areNotificationsEnabled() {
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        return notificationManager.areNotificationsEnabled();
    }

    private void openNotificationSettings() {
        Intent intent = new Intent();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent.setAction(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        } else {
            intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + getPackageName()));
        }
        startActivity(intent);
    }

    private long calculateStorageUsed() {
        long size = 0;
        File dir = getFilesDir();
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                size += file.length();
            }
        }
        return size;
    }

    private void showHelpDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_help, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        // Find views in custom layout
        TextView titleText = dialogView.findViewById(R.id.help_title);
        TextView messageText = dialogView.findViewById(R.id.help_message);
        Button okButton = dialogView.findViewById(R.id.help_ok_button);

        // Set help content
        messageText.setText("Welcome to Task Management App!\n\n" +
                          "• Create tasks using the + button\n" +
                          "• View task statistics in cards\n" +
                          "• Filter tasks by status and priority\n" +
                          "• Edit your profile settings\n\n" +
                          "For more help, contact support at:\n" +
                          "support@taskmanagement.com");

        okButton.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void setupCardClickListeners() {
        View totalTasksCard = findViewById(R.id.total_tasks_card);
        View completedTasksCard = findViewById(R.id.completed_tasks_card);
        View progressStatusCard = findViewById(R.id.progress_status_card);
        View priorityStatusCard = findViewById(R.id.priority_status_card);

        totalTasksCard.setOnClickListener(v -> showTaskDetails("total"));
        completedTasksCard.setOnClickListener(v -> showTaskDetails("completed"));
        progressStatusCard.setOnClickListener(v -> showTaskDetails("progress"));
        priorityStatusCard.setOnClickListener(v -> showTaskDetails("priority"));
    }

    private void showTaskDetails(String type) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_task_details, null);
        builder.setView(dialogView);

        TextView titleText = dialogView.findViewById(R.id.detail_title);
        TextView countText = dialogView.findViewById(R.id.detail_count);
        TextView descriptionText = dialogView.findViewById(R.id.detail_description);

        switch (type) {
            case "total":
                titleText.setText("Total Tasks");
                countText.setText(totalTasksTextView.getText());
                descriptionText.setText("All tasks in your list");
                break;
            case "completed":
                titleText.setText("Completed Tasks");
                countText.setText(completedTasksTextView.getText());
                descriptionText.setText("Tasks that have been completed");
                break;
            case "progress":
                StringBuilder progressText = new StringBuilder();
                int totalTasks = Integer.parseInt(totalTasksTextView.getText().toString());
                int completedTasks = Integer.parseInt(completedTasksTextView.getText().toString());
                progressText.append("In Progress: ").append(totalTasks - completedTasks).append("\n");
                progressText.append("Completed: ").append(completedTasks);
                titleText.setText("Progress Status");
                countText.setVisibility(View.GONE);
                descriptionText.setText(progressText.toString());
                break;
            case "priority":
                StringBuilder priorityText = new StringBuilder();
                priorityText.append("High Priority: ").append(highPriorityCount).append("\n");
                priorityText.append("Medium Priority: ").append(mediumPriorityCount).append("\n");
                priorityText.append("Low Priority: ").append(lowPriorityCount);
                titleText.setText("Priority Status");
                countText.setVisibility(View.GONE);
                descriptionText.setText(priorityText.toString());
                break;
        }

        AlertDialog dialog = builder.create();
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        Button closeButton = dialogView.findViewById(R.id.close_dialog);
        closeButton.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }
}