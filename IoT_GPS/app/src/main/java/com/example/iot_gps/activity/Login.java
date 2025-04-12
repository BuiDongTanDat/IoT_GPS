package com.example.iot_gps.activity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.example.iot_gps.R;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;

public class Login extends AppCompatActivity {
    private static final int LOCATION_PERMISSION_REQUEST = 124;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        EditText etUserName = findViewById(R.id.etUserName);
        EditText etUserPassword = findViewById(R.id.etUserPassword);
        Button btnLogin = findViewById(R.id.btnLogin);
        TextView tvSignIn = findViewById(R.id.tvSignIn);

        btnLogin.setOnClickListener(v -> {
            String userId = etUserName.getText().toString().trim();
            String passwordInput = etUserPassword.getText().toString().trim();

            if (userId.isEmpty() || passwordInput.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập đầy đủ thông tin", Toast.LENGTH_SHORT).show();
                return;
            }

            DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(userId);

            userRef.get().addOnSuccessListener(snapshot -> {
                if (!snapshot.exists()) {
                    Toast.makeText(this, "Tài khoản không tồn tại", Toast.LENGTH_SHORT).show();
                    return;
                }

                String storedPassword = snapshot.child("password").getValue(String.class);
                if (storedPassword != null && storedPassword.equals(passwordInput)) {
                    // Đăng nhập thành công → cập nhật vị trí
                    saveUserLocation(userId);

                    Toast.makeText(this, "Đăng nhập thành công", Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(Login.this, MainActivity.class);
                    intent.putExtra("userId", userId);
                    startActivity(intent);
                    finish();
                } else {
                    Toast.makeText(this, "Sai mật khẩu", Toast.LENGTH_SHORT).show();
                }
            });
        });

        tvSignIn.setOnClickListener(v -> {
            Intent intent = new Intent(Login.this, SignIn.class);
            startActivity(intent);
            finish();
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
