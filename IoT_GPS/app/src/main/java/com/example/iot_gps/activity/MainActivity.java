package com.example.iot_gps.activity;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
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
import android.content.pm.PackageManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private DeviceAdapter deviceAdapter;
    private List<DeviceIoT> deviceIoTList;
    private GeoPoint userLocation; // Store user location

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Kiểm tra và yêu cầu quyền
        checkLocationPermission();

//        // Khởi động dịch vụ vị trí
//        Intent serviceIntent = new Intent(this, LocationService.class);
//        startService(serviceIntent);

        // Cài đặt RecyclerView
        RecyclerView recyclerViewDevices = findViewById(R.id.recyclerDevice);
        recyclerViewDevices.setLayoutManager(new LinearLayoutManager(this));
        deviceIoTList = new ArrayList<>();
        deviceAdapter = new DeviceAdapter(deviceIoTList);
        recyclerViewDevices.setAdapter(deviceAdapter);

        registerReceiver(locationReceiver, new IntentFilter("LOCATION_UPDATE"), Context.RECEIVER_NOT_EXPORTED);

        setupDeviceListener();

        ImageButton btnStopService = findViewById(R.id.btnStopService);
        btnStopService.setOnClickListener(v -> {
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Xác nhận")
                    .setMessage("Bạn có muốn tắt dịch vụ vị trí không?")
                    .setPositiveButton("Yes", (dialog, which) -> {
                        Intent stopIntent = new Intent(MainActivity.this, LocationService.class);
                        stopService(stopIntent);
                        unregisterReceiver(locationReceiver);
                        Toast.makeText(MainActivity.this, "Đã tắt dịch vụ vị trí", Toast.LENGTH_SHORT).show();
                        finish();
                    })
                    .setNegativeButton("No", null)
                    .show();
        });

    }

    private void setupDeviceListener() {
        DatabaseReference devicesRef = FirebaseDatabaseHelper.getReference("locations");

        devicesRef.addValueEventListener(new ValueEventListener() { // Use addValueEventListener
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                deviceIoTList.clear();
                for (DataSnapshot deviceSnapshot : snapshot.getChildren()) {
                    String deviceId = deviceSnapshot.getKey();
                    if ("user_location".equals(deviceId)) continue;

                    String name = deviceSnapshot.child("name").getValue(String.class);
                    String desc = deviceSnapshot.child("desc").getValue(String.class);
                    Double lat = deviceSnapshot.child("location/latitude").getValue(Double.class);
                    Double lng = deviceSnapshot.child("location/longitude").getValue(Double.class);
                    Long timestamp = deviceSnapshot.child("timestamp").getValue(Long.class);
                    Boolean isTracking = deviceSnapshot.child("status").getValue(Boolean.class);

                    if (name != null && desc != null && lat != null && lng != null && timestamp != null) {
                        GeoPoint location = new GeoPoint(lat, lng);
                        DeviceIoT device = new DeviceIoT(deviceId, name, desc, location, timestamp, isTracking);
                        deviceIoTList.add(device);
                    }
                }

                // Calculate distances and update adapter
                calculateDistancesAndUpdateAdapter();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("MainActivity", "Error fetching devices: " + error.getMessage());
            }
        });
    }

    private void calculateDistancesAndUpdateAdapter() {
        if (userLocation == null) {
            deviceAdapter.setDeviceIoTList(deviceIoTList);
            deviceAdapter.notifyDataSetChanged();
            return;
        }

        for (int i = 0; i < deviceIoTList.size(); i++) {
            DeviceIoT device = deviceIoTList.get(i);
            double distance = calculateDistance(userLocation, device.getLocation());
            device.setDistance(distance); // Assuming you add a setDistance method to DeviceIoT
        }
        deviceAdapter.setDeviceIoTList(deviceIoTList); //update list
        deviceAdapter.notifyDataSetChanged();
    }

    private double calculateDistance(GeoPoint userLocation, GeoPoint deviceLocation) {
        double lat1 = userLocation.getLatitude();
        double lon1 = userLocation.getLongitude();
        double lat2 = deviceLocation.getLatitude();
        double lon2 = deviceLocation.getLongitude();

        double earthRadius = 6371000; // meters
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
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
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
        Intent serviceIntent = new Intent(this, LocationService.class);
        startService(serviceIntent);
    }

    private void turnOnGPSIfNeeded() {
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
                    resolvable.startResolutionForResult(MainActivity.this, 101); // 101 là requestCode
                } catch (IntentSender.SendIntentException sendEx) {
                    // Ignore the error.
                }
            }
        });
    }



    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(locationReceiver);
    }
}