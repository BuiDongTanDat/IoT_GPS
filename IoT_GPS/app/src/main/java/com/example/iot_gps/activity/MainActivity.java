package com.example.iot_gps.activity;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.iot_gps.R;
import com.example.iot_gps.adapter.DeviceAdapter;
import com.example.iot_gps.model.DeviceIoT;
import com.example.iot_gps.model.GeoPoint;
import com.example.iot_gps.service.LocationService;
import com.example.iot_gps.utils.FirebaseDatabaseHelper;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private DeviceAdapter deviceAdapter;
    private List<DeviceIoT> deviceIoTList;
    private GeoPoint userLocation; // Store user location

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Start LocationService
        Intent serviceIntent = new Intent(this, LocationService.class);
        startService(serviceIntent);

        // Initialize RecyclerView
        RecyclerView recyclerViewDevices = findViewById(R.id.recyclerDevice);
        recyclerViewDevices.setLayoutManager(new LinearLayoutManager(this));

        // Initialize Adapter and device list
        deviceIoTList = new ArrayList<>();
        deviceAdapter = new DeviceAdapter(deviceIoTList);
        recyclerViewDevices.setAdapter(deviceAdapter);

        // Register BroadcastReceiver
        registerReceiver(locationReceiver, new IntentFilter("LOCATION_UPDATE"), Context.RECEIVER_NOT_EXPORTED);

        // Fetch devices and set up listener
        setupDeviceListener();
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(locationReceiver);
    }
}