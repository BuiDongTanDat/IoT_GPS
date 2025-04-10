package com.example.iot_gps.service;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.example.iot_gps.R;
import com.example.iot_gps.activity.MainActivity;
import com.example.iot_gps.utils.FirebaseDatabaseHelper;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import java.util.HashMap;
import java.util.Map;

public class LocationService extends Service {

    private static final String CHANNEL_ID = "location_channel";
    private static final int NOTIFICATION_ID = 1;

    private FusedLocationProviderClient fusedLocationProviderClient;
    private LocationCallback locationCallback;
    private String userId;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
        userId = "user_location"; // fallback nếu chưa đăng nhập

        // Callback để nhận vị trí liên tục
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                if (locationResult != null) {
                    for (Location location : locationResult.getLocations()) {
                        saveLocationToRealtimeDB(location);
                    }
                }
            }
        };

        startLocationUpdates();
    }

    private void startLocationUpdates() {
        LocationRequest locationRequest = LocationRequest.create()
                .setInterval(5000) // Cập nhật mỗi 5 giây
                .setFastestInterval(3000)
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, null);
    }

    private void saveLocationToRealtimeDB(Location location) {
        double latitude = location.getLatitude();
        double longitude = location.getLongitude();

        // Tạo map cho location
        Map<String, Object> locationMap = new HashMap<>();
        locationMap.put("latitude", latitude);
        locationMap.put("longitude", longitude);

        // Ghi location map vào Firebase, giữ lại các trường khác
        FirebaseDatabaseHelper.getReference("locations").child(userId).child("location").setValue(locationMap)
                .addOnSuccessListener(aVoid -> Log.d("LocationService", "Location updated in Realtime DB"))
                .addOnFailureListener(e -> Log.w("LocationService", "Failed to update location", e));

        // Gửi broadcast (vẫn giữ lại timestamp cũ)
        FirebaseDatabaseHelper.getReference("locations").child(userId).child("timestamp").get()
                .addOnSuccessListener(dataSnapshot -> {
                    if (dataSnapshot.exists()) {
                        Long timestamp = dataSnapshot.getValue(Long.class);
                        sendLocationBroadcast(latitude, longitude, timestamp);
                    } else {
                        // Không tìm thấy timestamp cũ, sử dụng timestamp hiện tại và cập nhật Firebase
                        long timestamp = System.currentTimeMillis();
                        FirebaseDatabaseHelper.getReference("locations").child(userId).child("timestamp").setValue(timestamp);
                        sendLocationBroadcast(latitude, longitude, timestamp);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.w("LocationService", "Failed to get timestamp, using current time", e);
                    // Lỗi khi lấy timestamp cũ, sử dụng timestamp hiện tại và cập nhật Firebase
                    long timestamp = System.currentTimeMillis();
                    FirebaseDatabaseHelper.getReference("locations").child(userId).child("timestamp").setValue(timestamp);
                    sendLocationBroadcast(latitude, longitude, timestamp);
                });
    }


    private void sendLocationBroadcast(double latitude, double longitude, long timestamp) {
        Intent intent = new Intent("LOCATION_UPDATE");
        intent.putExtra("latitude", latitude);
        intent.putExtra("longitude", longitude);
        intent.putExtra("timestamp", timestamp);
        sendBroadcast(intent);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Location Channel", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Channel for location updates");

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        fusedLocationProviderClient.removeLocationUpdates(locationCallback);
    }
}