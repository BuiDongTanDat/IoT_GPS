package com.example.iot_gps.activity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.iot_gps.R;
import com.example.iot_gps.adapter.DeviceAdapter;
import com.example.iot_gps.model.DeviceIoT;
import com.example.iot_gps.model.GeoPoint;
import com.example.iot_gps.service.LocationService;
import com.example.iot_gps.utils.FirebaseDatabaseHelper;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.tasks.Task;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import android.content.pm.PackageManager;

public class MainActivity extends AppCompatActivity {

    private DeviceAdapter deviceAdapter;
    private List<DeviceIoT> deviceIoTList;
    private GeoPoint userLocation; // Store user location
    private String userId;  // Lấy từ Intent
    private double alert_distance;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 100;
    private boolean isReceiverRegistered = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ImageButton btnPairService = findViewById(R.id.btnPairService);
        alert_distance= getIntent().getIntExtra("alert_distance", 0);
        // Nhận userId được truyền từ Login (hoặc SignIn) qua Intent
        userId = getIntent().getStringExtra("userId");
        if (userId == null || userId.isEmpty()) {
            Toast.makeText(this, "User không xác định", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Kiểm tra và yêu cầu quyền truy cập vị trí
        checkLocationPermission();

        // Khởi động RecyclerView để hiển thị danh sách thiết bị của user
        RecyclerView recyclerViewDevices = findViewById(R.id.recyclerDevice);
        recyclerViewDevices.setLayoutManager(new LinearLayoutManager(this));
        deviceIoTList = new ArrayList<>();
        deviceAdapter = new DeviceAdapter(deviceIoTList, userId, alert_distance,this);
        recyclerViewDevices.setAdapter(deviceAdapter);

        // Đăng ký Broadcast nhận dữ liệu vị trí của user (được cập nhật từ LocationService)
        registerReceiver(locationReceiver, new IntentFilter("LOCATION_UPDATE"), Context.RECEIVER_NOT_EXPORTED);
        isReceiverRegistered = true;

        // Đọc thiết bị của user từ Firebase dưới node: users/{userId}/devices
        setupDeviceListener();

        // Nút để chuyển sang AddDevice
        ImageButton btnAddService = findViewById(R.id.btnAddService);
        btnAddService.setOnClickListener(v -> {
            Intent intent = new Intent(this, AddDevice.class);
            intent.putExtra("userId", userId);
            startActivity(intent);
        });

        // Nút dừng dịch vụ vị trí
        ImageButton btnStopService = findViewById(R.id.btnStopService);
        btnStopService.setOnClickListener(v -> {
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Xác nhận")
                    .setMessage("Bạn có muốn tắt dịch vụ vị trí và đăng xuất không?")
                    .setPositiveButton("Yes", (dialog, which) -> {
                        Intent stopIntent = new Intent(MainActivity.this, LocationService.class);
                        stopService(stopIntent);
                        unregisterReceiver(locationReceiver);
                        isReceiverRegistered = false;
                        //Xóa userId trong preference
                        getSharedPreferences("UserPrefs", MODE_PRIVATE)
                                .edit()
                                .remove("userId")
                                .apply();
                        Toast.makeText(MainActivity.this, "Đã tắt dịch vụ vị trí", Toast.LENGTH_SHORT).show();
                        finish();
                    })
                    .setNegativeButton("No", null)
                    .show();
        });
        btnPairService.setOnClickListener(v -> {
            Intent intent = new Intent(this, PairDevice.class);
            intent.putExtra("userId", userId);
            startActivity(intent);

        });

    }

    private void setupDeviceListener() {
        DatabaseReference userRef = FirebaseDatabaseHelper.getReference("users").child(userId);

        // Đầu tiên, lấy timestamp
        userRef.child("location/timestamp").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot timestampSnapshot) {
                Long timestamp = timestampSnapshot.getValue(Long.class);
                if (timestamp == null) timestamp = System.currentTimeMillis();

                // Tiếp tục lấy danh sách thiết bị sau khi có timestamp
                userRef.child("devices").addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (!snapshot.exists()) {
                            deviceIoTList.clear();
                            calculateDistancesAndUpdateAdapter();
                            return;
                        }

                        deviceIoTList.clear();
                        List<DeviceIoT> tempList = new ArrayList<>();
                        int totalDevices = (int) snapshot.getChildrenCount();
                        AtomicInteger loadedCount = new AtomicInteger(0);

                        // Lặp qua tất cả các thiết bị của user
                        for (DataSnapshot deviceSnapshot : snapshot.getChildren()) {
                            String deviceId = deviceSnapshot.getKey();
                            Boolean isTracking = deviceSnapshot.child("isTracking").getValue(Boolean.class);

                            if (deviceId == null || isTracking == null) {
                                loadedCount.incrementAndGet();
                                continue;
                            }

                            // Lấy thông tin từ /devices/{deviceId}
                            DatabaseReference deviceRef = FirebaseDatabaseHelper
                                    .getReference("devices")
                                    .child(deviceId);

                            deviceRef.addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot deviceDetailSnapshot) {
                                    // Lấy thông tin chi tiết của thiết bị
                                    String deviceName = deviceDetailSnapshot.child("username").getValue(String.class);
                                    String deviceDesc = deviceDetailSnapshot.child("desc").getValue(String.class);
                                    Double lat = deviceDetailSnapshot.child("location/latitude").getValue(Double.class);
                                    Double lng = deviceDetailSnapshot.child("location/longitude").getValue(Double.class);

                                    // Nếu thông tin hợp lệ, tạo đối tượng DeviceIoT
                                    if (deviceName != null && deviceDesc != null && lat != null && lng != null) {
                                        GeoPoint location = new GeoPoint(lat, lng);
                                        DeviceIoT device = new DeviceIoT(deviceId, deviceName, deviceDesc, location, isTracking);
                                        tempList.add(device);
                                    }

                                    // Kiểm tra nếu đã tải hết các thiết bị
                                    if (loadedCount.incrementAndGet() == totalDevices) {
                                        deviceIoTList.clear();
                                        deviceIoTList.addAll(tempList);
                                        calculateDistancesAndUpdateAdapter();
                                    }
                                }

                                @Override
                                public void onCancelled(@NonNull DatabaseError error) {
                                    Log.e("DeviceDetail", "Error: " + error.getMessage());
                                    if (loadedCount.incrementAndGet() == totalDevices) {
                                        deviceIoTList.clear();
                                        deviceIoTList.addAll(tempList);
                                        calculateDistancesAndUpdateAdapter();
                                    }
                                }
                            });
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e("DeviceListener", "Error: " + error.getMessage());
                    }
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("Timestamp", "Error loading timestamp: " + error.getMessage());
            }
        });
    }





    private void calculateDistancesAndUpdateAdapter() {
        if (userLocation == null) {
            deviceAdapter.setDeviceIoTList(deviceIoTList);
            deviceAdapter.notifyDataSetChanged();
            return;
        }

        // Tính khoảng cách từ vị trí user hiện tại đến vị trí của từng thiết bị
        for (DeviceIoT device : deviceIoTList) {
            double distance = calculateDistance(userLocation, device.getLocation());
            device.setDistance(distance);  // Giả sử bạn đã thêm phương thức setDistance trong DeviceIoT
        }
        deviceAdapter.setDeviceIoTList(deviceIoTList);
        deviceAdapter.notifyDataSetChanged();
    }

    private double calculateDistance(GeoPoint userLocation, GeoPoint deviceLocation) {
        double lat1 = userLocation.getLatitude();
        double lon1 = userLocation.getLongitude();
        double lat2 = deviceLocation.getLatitude();
        double lon2 = deviceLocation.getLongitude();

        double earthRadius = 6371000; // đơn vị: mét
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadius * c;
    }

    private final BroadcastReceiver locationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            double latitude = intent.getDoubleExtra("latitude", 0.0);
            double longitude = intent.getDoubleExtra("longitude", 0.0);
            userLocation = new GeoPoint(latitude, longitude);
            calculateDistancesAndUpdateAdapter();
        }
    };

    private void checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            startLocationService();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d("MainActivity", "Permission granted");
                turnOnGPSIfNeeded(); // Kiểm tra và bật GPS nếu cần
                startLocationService(); // Khởi động dịch vụ sau khi được cấp quyền
            } else {
                Log.e("MainActivity", "Permission denied");
            }
        }
    }

    private void startLocationService() {
        String userId = getIntent().getStringExtra("userId");

        Intent serviceIntent = new Intent(this, LocationService.class);
        serviceIntent.putExtra("USER_ID", userId);
        startService(serviceIntent);
    }

    private void turnOnGPSIfNeeded() {
        // Sử dụng LocationRequest cũ để yêu cầu bật GPS
        LocationRequest locationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(10000)
                .setFastestInterval(5000);

        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                .addLocationRequest(locationRequest);

        SettingsClient client = LocationServices.getSettingsClient(this);
        Task<LocationSettingsResponse> task = client.checkLocationSettings(builder.build());

        task.addOnSuccessListener(this, locationSettingsResponse -> {
            // GPS đã bật, không cần làm gì
        });

        task.addOnFailureListener(this, e -> {
            if (e instanceof ResolvableApiException) {
                try {
                    ResolvableApiException resolvable = (ResolvableApiException) e;
                    resolvable.startResolutionForResult(MainActivity.this, 101); // requestCode=101
                } catch (IntentSender.SendIntentException sendEx) {
                    // Bỏ qua lỗi
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isReceiverRegistered) {
            unregisterReceiver(locationReceiver);
            isReceiverRegistered = false;
        }
    }
}