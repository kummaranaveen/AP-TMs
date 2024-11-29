package com.example.myapp;

import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
public class EditTaskActivity extends AppCompatActivity {
    private EditText titleEditText;
    private EditText descriptionEditText;
    private Spinner statusSpinner;
    private Spinner prioritySpinner;
    private TextView dueDatePicker;
    private Button updateButton;
    private Date dueDate;
    private int taskId;
    private SimpleDateFormat apiDateFormat;
    private SimpleDateFormat displayDateFormat;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_task);

        apiDateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        displayDateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());

        initializeViews();
        setupSpinners();
        loadTaskData();
        setupDatePicker();
        setupUpdateButton();
    }

    private void initializeViews() {
        titleEditText = findViewById(R.id.edit_task_title);
        descriptionEditText = findViewById(R.id.edit_task_description);
        statusSpinner = findViewById(R.id.edit_task_status);
        prioritySpinner = findViewById(R.id.edit_task_priority);
        dueDatePicker = findViewById(R.id.edit_task_due_date);
        updateButton = findViewById(R.id.update_task_button);
    }

    private void setupSpinners() {
        ArrayAdapter<CharSequence> statusAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new String[]{"yet-to-start", "in-progress", "completed"});
        statusAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        statusSpinner.setAdapter(statusAdapter);

        ArrayAdapter<CharSequence> priorityAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new String[]{"low", "medium", "high"});
        priorityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        prioritySpinner.setAdapter(priorityAdapter);
    }

    private void loadTaskData() {
        Intent intent = getIntent();
        taskId = intent.getIntExtra("task_id", -1);
        titleEditText.setText(intent.getStringExtra("task_title"));
        descriptionEditText.setText(intent.getStringExtra("task_description"));

        // Set spinner selections
        String status = intent.getStringExtra("task_status");
        String priority = intent.getStringExtra("task_priority");
        setSpinnerSelection(statusSpinner, status);
        setSpinnerSelection(prioritySpinner, priority);

        // Set due date
        String dueDateStr = intent.getStringExtra("task_due_date");
        if (dueDateStr != null) {
            try {
                dueDate = apiDateFormat.parse(dueDateStr);
                dueDatePicker.setText(displayDateFormat.format(dueDate));
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }
    }

    private void setSpinnerSelection(Spinner spinner, String value) {
        ArrayAdapter adapter = (ArrayAdapter) spinner.getAdapter();
        for (int i = 0; i < adapter.getCount(); i++) {
            if (adapter.getItem(i).toString().equals(value)) {
                spinner.setSelection(i);
                break;
            }
        }
    }

    private void setupDatePicker() {
        dueDatePicker.setOnClickListener(v -> {
            Calendar calendar = Calendar.getInstance();
            if (dueDate != null) {
                calendar.setTime(dueDate);
            }

            DatePickerDialog datePickerDialog = new DatePickerDialog(
                    this,
                    (view, year, month, dayOfMonth) -> {
                        Calendar selectedDate = Calendar.getInstance();
                        selectedDate.set(year, month, dayOfMonth);
                        dueDate = selectedDate.getTime();
                        dueDatePicker.setText(displayDateFormat.format(dueDate));
                    },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH)
            );
            datePickerDialog.show();
        });
    }

    private void setupUpdateButton() {
        updateButton.setOnClickListener(v -> updateTask());
    }

    private void updateTask() {
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL("http://172.16.20.85:8000/api/tasks/" + taskId + "/");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("PUT");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("Authorization", "Bearer " + getAccessToken());
                connection.setDoOutput(true);

                JSONObject taskData = new JSONObject();
                taskData.put("title", titleEditText.getText().toString());
                taskData.put("description", descriptionEditText.getText().toString());
                taskData.put("status", statusSpinner.getSelectedItem().toString());
                taskData.put("priority", prioritySpinner.getSelectedItem().toString());
                if (dueDate != null) {
                    taskData.put("deadline", apiDateFormat.format(dueDate));
                }

                try (OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream())) {
                    writer.write(taskData.toString());
                }

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Task updated successfully", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                } else {
                    // Handle error
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(connection.getErrorStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    final String errorMessage = response.toString();
                    runOnUiThread(() ->
                            Toast.makeText(this, "Failed to update task: " + errorMessage,
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

    private String getAccessToken() {
        SharedPreferences sharedPreferences = getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE);
        return sharedPreferences.getString("access_token", null);
    }
}
