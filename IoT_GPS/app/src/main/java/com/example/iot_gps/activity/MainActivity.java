package com.example.iot_gps.activity;


import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toolbar;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.iot_gps.R;
import com.example.iot_gps.adapter.DeviceAdapter;
import com.example.iot_gps.model.DeviceIoT;
import com.example.iot_gps.service.LocationService;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private RecyclerView recyclerViewDevices;
    private DeviceAdapter deviceAdapter;
    private List<DeviceIoT> deviceIoTList;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private Toolbar toolbar;

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Khởi động LocationService
        Intent serviceIntent = new Intent(this, LocationService.class);
        startService(serviceIntent);

        recyclerViewDevices = findViewById(R.id.recyclerDevice);
        recyclerViewDevices.setLayoutManager(new LinearLayoutManager(this));

        deviceIoTList = new ArrayList<>();
        deviceAdapter = new DeviceAdapter(deviceIoTList);
        recyclerViewDevices.setAdapter(deviceAdapter);


        fetchDevicesFromFirestore();

        // Đăng ký BroadcastReceiver để lắng nghe cập nhật vị trí từ service
        registerReceiver(locationReceiver, new IntentFilter("LOCATION_UPDATE"), Context.RECEIVER_NOT_EXPORTED);


    }

    private void fetchDevicesFromFirestore() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("locations")
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d("MainActivity", "Successfully fetched documents");
                        deviceIoTList.clear(); // Xóa danh sách cũ trước khi thêm mới
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            String documentId = document.getId();

                            // Bỏ qua document có ID "user_location"
                            if ("user_location".equals(documentId)) {
                                continue;
                            }

                            String name = document.getString("name");
                            String desc = document.getString("desc");
                            GeoPoint location = document.getGeoPoint("location");
                            Long timestamp = document.getLong("timestamp");

                            if (name != null && desc != null && location != null && timestamp != null) {
                                DeviceIoT device = new DeviceIoT(documentId, name, desc, location, timestamp);
                                Log.d("MainActivity", "Device: " + device.toString());
                                deviceIoTList.add(device);
                            } else {
                                Log.d("MainActivity", "Incomplete device data in document: " + documentId);
                            }
                        }
                        deviceAdapter.notifyDataSetChanged();
                    } else {
                        Log.e("MainActivity", "Error getting documents: ", task.getException());
                    }
                });
    }


    // Nhận vị trí mới từ LocationService thông qua broadcast
    private final BroadcastReceiver locationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            double latitude = intent.getDoubleExtra("latitude", 0.0);
            double longitude = intent.getDoubleExtra("longitude", 0.0);
            long timestamp = intent.getLongExtra("timestamp", 0);

            GeoPoint newLocation = new GeoPoint(latitude, longitude);

            // Cập nhật thiết bị trong danh sách (giả sử danh sách có 1 thiết bị)
            deviceAdapter.updateUserLocation(newLocation);
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(locationReceiver);
    }

}

