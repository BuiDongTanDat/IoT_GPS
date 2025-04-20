package com.example.iot_gps.activity;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.example.iot_gps.R;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;

public class SignIn extends AppCompatActivity {
    private static final int LOCATION_PERMISSION_REQUEST = 123;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_in);


        EditText etUserName = findViewById(R.id.etUserName);
        EditText etUserPassword = findViewById(R.id.etUserPassword);
        Button btnSignIn = findViewById(R.id.btnSignIn);

        btnSignIn.setOnClickListener(v -> {
            String username = etUserName.getText().toString().trim();
            String password = etUserPassword.getText().toString().trim();

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập đầy đủ thông tin", Toast.LENGTH_SHORT).show();
                return;
            }

            String userId = username;

            Map<String, Object> userData = new HashMap<>();
            userData.put("username", username);
            userData.put("password", password);

            FirebaseDatabase.getInstance().getReference("users")
                    .child(userId)
                    .setValue(userData)
                    .addOnSuccessListener(unused -> {

                        // Sau khi tạo user -> lấy vị trí
                        saveUserLocation(userId);

                        Toast.makeText(this, "Đăng ký thành công", Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(SignIn.this, Login.class);
                        startActivity(intent);
                        finish();
                    });
        });
    }

    private void saveUserLocation(String userId) {
        FusedLocationProviderClient locationClient = LocationServices.getFusedLocationProviderClient(this);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST);
            return;
        }

        locationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        Map<String, Object> loc = new HashMap<>();
                        loc.put("latitude", location.getLatitude());
                        loc.put("longitude", location.getLongitude());

                        FirebaseDatabase.getInstance().getReference("users")
                                .child(userId)
                                .child("location")
                                .setValue(loc);
                    }
                });
    }
}
