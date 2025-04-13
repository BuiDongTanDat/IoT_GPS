package com.example.iot_gps.activity;

import android.Manifest;
import android.content.pm.PackageManager;
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

public class AddDevice extends AppCompatActivity {

    private String userId; // nhận từ Intent

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_device);

        // Nhận userId từ Intent
        userId = getIntent().getStringExtra("userId");
        if (userId == null || userId.isEmpty()) {
            Toast.makeText(this, "Không tìm thấy userId", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        EditText etDeviceName = findViewById(R.id.etDeviceName);
        EditText etDeviceId = findViewById(R.id.etDeviceId);
        Button btnAddDevice = findViewById(R.id.btnAddDevice);

        btnAddDevice.setOnClickListener(v -> {
            String deviceName = etDeviceName.getText().toString().trim();
            String deviceId = etDeviceId.getText().toString().trim();

            if (deviceName.isEmpty() || deviceId.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập đầy đủ thông tin", Toast.LENGTH_SHORT).show();
                return;
            }

            // Lấy GPS của máy người dùng
            FusedLocationProviderClient fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Cần cấp quyền truy cập vị trí", Toast.LENGTH_SHORT).show();
                return;
            }

            fusedLocationProviderClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener(location -> {
                        if (location != null) {
                            double lat = location.getLatitude();
                            double lng = location.getLongitude();
                            long timestamp = System.currentTimeMillis();

                            // Gửi thiết bị + vị trí lên Firebase theo user
                            Map<String, Object> locationMap = new HashMap<>();
                            locationMap.put("latitude", lat);
                            locationMap.put("longitude", lng);

                            Map<String, Object> deviceData = new HashMap<>();
                            deviceData.put("name", deviceName);
                            deviceData.put("desc", "Thiết bị thêm thủ công");
                            deviceData.put("status", true);
                            deviceData.put("timestamp", timestamp);
                            deviceData.put("location", locationMap);

                            FirebaseDatabase.getInstance().getReference("users")
                                    .child(userId)
                                    .child("devices")
                                    .child(deviceId)
                                    .setValue(deviceData)
                                    .addOnSuccessListener(unused -> {
                                        Toast.makeText(this, "Thêm thiết bị thành công", Toast.LENGTH_SHORT).show();
                                        finish();
                                    })
                                    .addOnFailureListener(e -> {
                                        Toast.makeText(this, "Lỗi khi lưu thiết bị", Toast.LENGTH_SHORT).show();
                                    });

                        } else {
                            Toast.makeText(this, "Không lấy được vị trí hiện tại", Toast.LENGTH_SHORT).show();
                        }
                    });
        });
    }
}
