package com.example.iot_gps.activity;

import android.app.Application;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.iot_gps.R;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;

public class PairDevice extends AppCompatActivity {

    EditText etUserName, etPassword;
    Button btnSyncIn;
    String currentUserId;  // <-- Lấy từ Intent

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pair_service);

        etUserName = findViewById(R.id.etUserName);
        etPassword = findViewById(R.id.etUserPassword);
        btnSyncIn = findViewById(R.id.btnSyncIn);

        currentUserId = getIntent().getStringExtra("userId"); // Nhớ truyền từ MainActivity

        btnSyncIn.setOnClickListener(v -> {
            String targetUser = etUserName.getText().toString().trim();
            String password = etPassword.getText().toString().trim();

            if (targetUser.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập đủ thông tin", Toast.LENGTH_SHORT).show();
                return;
            }

            DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(targetUser);

            userRef.get().addOnSuccessListener(snapshot -> {
                if (!snapshot.exists()) {
                    Toast.makeText(this, "Người dùng không tồn tại", Toast.LENGTH_SHORT).show();
                    return;
                }

                String storedPassword = snapshot.child("password").getValue(String.class);
                if (!password.equals(storedPassword)) {
                    Toast.makeText(this, "Sai mật khẩu", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Lấy vị trí của người dùng B
                DataSnapshot locationSnap = snapshot.child("location");
                if (!locationSnap.exists()) {
                    Toast.makeText(this, "Người dùng này chưa có vị trí", Toast.LENGTH_SHORT).show();
                    return;
                }

                Double lat = locationSnap.child("latitude").getValue(Double.class);
                Double lng = locationSnap.child("longitude").getValue(Double.class);

                if (lat == null || lng == null) {
                    Toast.makeText(this, "Dữ liệu vị trí không hợp lệ", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Thêm vị trí người B như một thiết bị vào user hiện tại
                Map<String, Object> deviceData = new HashMap<>();
                deviceData.put("name", targetUser + " (user)");
                deviceData.put("desc", "Theo dõi người dùng");
                deviceData.put("status", true);
                deviceData.put("timestamp", System.currentTimeMillis());

                Map<String, Object> location = new HashMap<>();
                location.put("latitude", lat);
                location.put("longitude", lng);
                deviceData.put("location", location);

                FirebaseDatabase.getInstance()
                        .getReference("users")
                        .child(currentUserId)
                        .child("devices")
                        .child(targetUser)
                        .setValue(deviceData)
                        .addOnSuccessListener(aVoid -> {
                            Toast.makeText(this, "Đã ghép nối thành công!", Toast.LENGTH_SHORT).show();
                            finish();
                        })
                        .addOnFailureListener(e -> {
                            Toast.makeText(this, "Lỗi khi ghép nối", Toast.LENGTH_SHORT).show();
                        });

            }).addOnFailureListener(e -> {
                Toast.makeText(this, "Lỗi khi kiểm tra người dùng", Toast.LENGTH_SHORT).show();
            });
        });
    }
}
