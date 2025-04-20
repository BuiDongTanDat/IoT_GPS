package com.example.iot_gps.service;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
import com.google.android.gms.location.Priority;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class LocationService extends Service {

    private static final String TAG = "LocationService";

    // --- Notification Channels ---
    private static final String FOREGROUND_CHANNEL_ID = "location_service_channel";
    private static final String ALERT_CHANNEL_ID = "distance_alert_channel";

    // --- Notification IDs ---
    private static final int FOREGROUND_NOTIFICATION_ID = 1;
    private static final int DISTANCE_ALERT_NOTIFICATION_ID = 2; // Different ID
    // --- Distance Check ---
    private static final double DISTANCE_THRESHOLD = 10; // Threshold in meters
    private static final long DISTANCE_CHECK_INTERVAL_SECONDS = 30; // Check every 30 seconds
    // --- Firebase Keys ---
    private static final String FB_LOCATIONS_NODE = "locations";
    private static final String FB_LOCATION_FIELD = "location";
    private static final String FB_LATITUDE_FIELD = "latitude";
    private static final String FB_LONGITUDE_FIELD = "longitude";
    private static final String FB_TIMESTAMP_FIELD = "timestamp";
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    // --- Location ---
    private FusedLocationProviderClient fusedLocationProviderClient;
    private LocationCallback locationCallback;
    private LocationRequest locationRequest;
    private Location currentUserLocation; // Store the latest location
    // --- User/Device ID ---
    private String userId = "user_location"; // TODO: Replace with dynamic user ID if possible

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");
        createNotificationChannels();

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);

        // Initialize LocationRequest (using new Priority constants if available)
        locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 4000) // Interval 4 seconds
                .setMinUpdateIntervalMillis(3000) // Fastest interval 3 seconds
                .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                Log.d(TAG, "onLocationResult received");
                Location lastLocation = locationResult.getLastLocation();
                if (lastLocation != null) {
                    currentUserLocation = lastLocation; // Update current location
                    Log.d(TAG, "New location: " + lastLocation.getLatitude() + ", " + lastLocation.getLongitude());
                    saveLocationToRealtimeDB(lastLocation);
                    sendLocationBroadcast(lastLocation.getLatitude(), lastLocation.getLongitude(), System.currentTimeMillis());

                    // Optional: Update foreground notification content dynamically
                    updateForegroundNotification("Tracking: " + lastLocation.getLatitude() + ", " + lastLocation.getLongitude());
                } else {
                    Log.w(TAG, "onLocationResult: Location is null");
                }
            }
        };

        // Schedule periodic distance checks
        scheduler.scheduleAtFixedRate(this::performDistanceCheck,
                10, // Initial delay 10 seconds
                DISTANCE_CHECK_INTERVAL_SECONDS, // Repeat interval
                TimeUnit.SECONDS);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");
        // TODO: Get actual userId from intent if available
        if (intent != null && intent.hasExtra("USER_ID")) {
            userId = intent.getStringExtra("USER_ID");
        }

        startForeground(FOREGROUND_NOTIFICATION_ID, createForegroundNotification("Đang theo dõi vị trí..."));
        startLocationUpdates();
        return START_STICKY;
    }

    private void startLocationUpdates() {
        Log.d(TAG, "Requesting location updates");
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Location permissions not granted. Stopping service.");
            stopSelf(); // Stop service if permissions are missing
            return;
        }
        try {
            fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
        } catch (SecurityException unlikely) {
            Log.e(TAG, "Lost location permission." + unlikely);
            stopSelf();
        }
    }

    private void saveLocationToRealtimeDB(Location location) {
        double latitude = location.getLatitude();
        double longitude = location.getLongitude();

        Map<String, Object> locationData = new HashMap<>();
        locationData.put("latitude", latitude);
        locationData.put("longitude", longitude);

        // ❗ Ghi vào đường dẫn: users/{userId}/location
        FirebaseDatabaseHelper.getReference("users")
                .child(userId)
                .child("location")
                .setValue(locationData)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Location updated in users/" + userId))
                .addOnFailureListener(e -> Log.w(TAG, "Failed to update location for " + userId, e));
    }


    private void sendLocationBroadcast(double latitude, double longitude, long timestamp) {
        Intent intent = new Intent("LOCATION_UPDATE");
        intent.putExtra("latitude", latitude);
        intent.putExtra("longitude", longitude);
        intent.putExtra("timestamp", timestamp);
        sendBroadcast(intent);
        Log.d(TAG, "Broadcast sent: LOCATION_UPDATE");
    }

    private void performDistanceCheck() {
        DatabaseReference userLocationRef = FirebaseDatabase.getInstance()
                .getReference("users").child(userId).child("location");

        userLocationRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot userLocationSnapshot) {
                Double userLat = userLocationSnapshot.child("latitude").getValue(Double.class);
                Double userLng = userLocationSnapshot.child("longitude").getValue(Double.class);

                if (userLat == null || userLng == null) {
                    Log.d(TAG, "Không tìm thấy vị trí của người dùng.");
                    return;
                }

                Log.d(TAG, "Vị trí người dùng: " + userLat + ", " + userLng);

                // Truy vấn danh sách thiết bị từ users/{userId}/devices
                DatabaseReference devicesRef = FirebaseDatabase.getInstance()
                        .getReference("users").child(userId).child("devices");

                devicesRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot devicesSnapshot) {
                        if (!devicesSnapshot.exists()) {
                            Log.d(TAG, "Không có thiết bị nào dưới users/" + userId + "/devices.");
                            return;
                        }

                        Log.d(TAG, "Đang kiểm tra khoảng cách với " + devicesSnapshot.getChildrenCount() + " thiết bị.");

                        for (DataSnapshot deviceSnapshot : devicesSnapshot.getChildren()) {
                            String deviceId = deviceSnapshot.getKey();
                            if (deviceId == null) continue;

                            // Đọc vị trí của thiết bị từ Firebase và gửi thông báo nếu khoảng cách vượt ngưỡng
                            DatabaseReference deviceRef = FirebaseDatabase.getInstance()
                                    .getReference("devices").child(deviceId).child("location");

                            deviceRef.addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot deviceLocationSnapshot) {
                                    // Đọc trạng thái isTracking từ Firebase
                                    DatabaseReference statusRef = FirebaseDatabase.getInstance()
                                            .getReference("users").child(userId).child("devices").child(deviceId).child("isTracking");

                                    statusRef.addListenerForSingleValueEvent(new ValueEventListener() {
                                        @Override
                                        public void onDataChange(@NonNull DataSnapshot statusSnapshot) {
                                            Boolean isTracking = statusSnapshot.getValue(Boolean.class);
                                            if (isTracking == null || !isTracking) {
                                                Log.d(TAG, "Thiết bị " + deviceId + " đang off, bỏ qua.");
                                                return;
                                            }

                                            // Đọc vị trí thiết bị
                                            Double deviceLat = deviceLocationSnapshot.child("latitude").getValue(Double.class);
                                            Double deviceLng = deviceLocationSnapshot.child("longitude").getValue(Double.class);

                                            if (deviceLat != null && deviceLng != null) {
                                                double distance = calculateDistance(userLat, userLng, deviceLat, deviceLng);
                                                Log.d(TAG, "Khoảng cách đến " + deviceId + ": " + String.format("%.2f", distance) + "m");

                                                if (distance > DISTANCE_THRESHOLD) {
                                                    // Gửi thông báo cho thiết bị này
                                                    sendDistanceAlertNotification(
                                                            deviceId,
                                                            "Khoảng cách vượt ngưỡng",
                                                            "Thiết bị " + deviceId + " cách bạn " + String.format("%.2f", distance) + " mét."
                                                    );
                                                }
                                            } else {
                                                Log.w(TAG, "Thiết bị " + deviceId + " không có dữ liệu vị trí.");
                                            }
                                        }

                                        @Override
                                        public void onCancelled(@NonNull DatabaseError error) {
                                            Log.e(TAG, "Lỗi khi đọc trạng thái của thiết bị " + deviceId + ": " + error.getMessage());
                                        }
                                    });
                                }

                                @Override
                                public void onCancelled(@NonNull DatabaseError error) {
                                    Log.e(TAG, "Lỗi khi đọc vị trí thiết bị " + deviceId + ": " + error.getMessage());
                                }
                            });
                        }


                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Lỗi đọc danh sách thiết bị: " + error.getMessage());
                    }
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Lỗi khi đọc vị trí người dùng: " + error.getMessage());
            }
        });
    }


    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        float[] results = new float[1];
        Location.distanceBetween(lat1, lon1, lat2, lon2, results);
        return results[0];
    }

    // --- Notification Management ---

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager == null) return;

            // Channel for Foreground Service (Low Importance)
            NotificationChannel foregroundChannel = new NotificationChannel(
                    FOREGROUND_CHANNEL_ID,
                    "Location Service Status",
                    NotificationManager.IMPORTANCE_LOW // Low importance for persistent notification
            );
            foregroundChannel.setDescription("Shows that the location service is running.");
            manager.createNotificationChannel(foregroundChannel);

            // Channel for Distance Alerts (High Importance)
            NotificationChannel alertChannel = new NotificationChannel(
                    ALERT_CHANNEL_ID,
                    "Distance Alerts",
                    NotificationManager.IMPORTANCE_HIGH // High importance for alerts
            );
            alertChannel.setDescription("Notifications when a device exceeds the distance threshold.");
            manager.createNotificationChannel(alertChannel);
        }
    }

    private Notification createForegroundNotification(String contentText) {
        Intent notificationIntent = new Intent(this, MainActivity.class); // Intent to open when notification is tapped
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, FOREGROUND_CHANNEL_ID)
                .setContentTitle("GPS Tracking")
                .setContentText(contentText)
                .setSmallIcon(R.drawable.baseline_location_on_24) // Replace with your icon
                .setContentIntent(pendingIntent)
                .setOngoing(true) // Makes it non-dismissible
                .setPriority(NotificationCompat.PRIORITY_LOW) // Match channel importance
                .build();
    }

    // Optional: Call this if you want to update the foreground notification text
    private void updateForegroundNotification(String contentText) {
        Notification notification = createForegroundNotification(contentText);
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        // No permission check needed here as foreground service already implies permission
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        notificationManager.notify(FOREGROUND_NOTIFICATION_ID, notification);
    }


    private void sendDistanceAlertNotification(String deviceId, String title, String message) {
        // Tạo kênh thông báo riêng cho thiết bị
        createDeviceNotificationChannel(deviceId);

        // Chúng ta dùng deviceId để tạo một unique notificationId
        int notificationId = deviceId.hashCode(); // Tạo ID thông báo từ deviceId

        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, notificationId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "device_channel_" + deviceId)
                .setSmallIcon(R.drawable.baseline_notifications_none_24) // Icon cho thông báo
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setDefaults(NotificationCompat.DEFAULT_ALL); // Mặc định âm thanh, rung, v.v.

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);

        // Kiểm tra quyền POST_NOTIFICATIONS đối với Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }

        notificationManager.notify(notificationId, builder.build());
    }


    private void createDeviceNotificationChannel(String deviceId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager == null) return;

            // Tạo kênh riêng cho từng thiết bị
            String channelId = "device_channel_" + deviceId; // Dùng deviceId làm tên kênh
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "Device " + deviceId + " Location Alerts", // Tiêu đề kênh
                    NotificationManager.IMPORTANCE_HIGH // Cao nhất cho thông báo khẩn cấp
            );
            channel.setDescription("Notifications for device " + deviceId + " exceeding distance threshold.");
            manager.createNotificationChannel(channel);
        }
    }


    @Nullable // Change return type to Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null; // Not binding
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        if (fusedLocationProviderClient != null && locationCallback != null) {
            Log.d(TAG, "Removing location updates");
            fusedLocationProviderClient.removeLocationUpdates(locationCallback); // Ensure this is called
        }
        // Shutdown the scheduler
        if (scheduler != null && !scheduler.isShutdown()) {
            Log.d(TAG, "Shutting down scheduler");
            scheduler.shutdown();
        }
    }
}