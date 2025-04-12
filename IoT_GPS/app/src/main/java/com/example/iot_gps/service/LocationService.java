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
import android.os.Looper; // Import Looper
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable; // Import Nullable
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
import com.google.android.gms.location.Priority; // Import Priority for newer LocationRequest
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError; // Import DatabaseError
import com.google.firebase.database.ValueEventListener; // Import ValueEventListener

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

    // --- Location ---
    private FusedLocationProviderClient fusedLocationProviderClient;
    private LocationCallback locationCallback;
    private LocationRequest locationRequest;
    private Location currentUserLocation; // Store the latest location

    // --- User/Device ID ---
    private String userId = "user_location"; // TODO: Replace with dynamic user ID if possible

    // --- Distance Check ---
    private static final double DISTANCE_THRESHOLD = 10; // Threshold in meters
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private static final long DISTANCE_CHECK_INTERVAL_SECONDS = 30; // Check every 30 seconds

    // --- Firebase Keys ---
    private static final String FB_LOCATIONS_NODE = "locations";
    private static final String FB_LOCATION_FIELD = "location";
    private static final String FB_LATITUDE_FIELD = "latitude";
    private static final String FB_LONGITUDE_FIELD = "longitude";
    private static final String FB_TIMESTAMP_FIELD = "timestamp";


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
                    // updateForegroundNotification("Tracking: " + lastLocation.getLatitude() + ", " + lastLocation.getLongitude());
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

    // --- Distance Check Logic (Scheduled) ---
    private void performDistanceCheck() {
        if (currentUserLocation == null) {
            Log.d(TAG, "Skipping distance check, current user location unknown.");
            return; // Don't check if we don't know our own location
        }

        final double userLat = currentUserLocation.getLatitude();
        final double userLng = currentUserLocation.getLongitude();
        Log.d(TAG, "Performing scheduled distance check from: " + userLat + ", " + userLng);

        FirebaseDatabaseHelper.getReference(FB_LOCATIONS_NODE).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (!dataSnapshot.exists()) {
                    Log.d(TAG, "No locations found in Firebase for distance check.");
                    return;
                }
                Log.d(TAG, "Checking distances against " + dataSnapshot.getChildrenCount() + " devices.");
                for (DataSnapshot deviceSnapshot : dataSnapshot.getChildren()) {
                    String deviceKey = deviceSnapshot.getKey();

                    if (userId.equals(deviceKey)) {
                        continue;
                    }

                    // Kiểm tra trạng thái thiết bị
                    Boolean status = deviceSnapshot.child("status").getValue(Boolean.class);
                    if (status == null || status == false) {
                        Log.d(TAG, "Thiết bị " + deviceKey + " đang off, bỏ qua kiểm tra.");
                        continue;
                    }

                    DataSnapshot locationSnapshot = deviceSnapshot.child(FB_LOCATION_FIELD);
                    if (locationSnapshot.exists()) {
                        Double deviceLat = locationSnapshot.child(FB_LATITUDE_FIELD).getValue(Double.class);
                        Double deviceLng = locationSnapshot.child(FB_LONGITUDE_FIELD).getValue(Double.class);

                        if (deviceLat != null && deviceLng != null) {
                            double distance = calculateDistance(userLat, userLng, deviceLat, deviceLng);
                            Log.d(TAG, "Distance to " + deviceKey + ": " + String.format("%.2f", distance) + "m");

                            if (distance > DISTANCE_THRESHOLD) {
                                sendDistanceAlertNotification("Khoảng cách vượt ngưỡng",
                                        "Thiết bị " + deviceKey + " cách bạn " + String.format("%.2f", distance) + " mét.");
                            }
                        } else {
                            Log.w(TAG, "Dữ liệu vị trí không đầy đủ cho thiết bị: " + deviceKey);
                        }
                    } else {
                        Log.w(TAG, "Không có node 'location' cho thiết bị: " + deviceKey);
                    }
                }

            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.w(TAG, "Firebase read cancelled for distance check: " + databaseError.getMessage(), databaseError.toException());
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


    private void sendDistanceAlertNotification(String title, String message) {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 1, intent, // Use different request code (e.g., 1)
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, ALERT_CHANNEL_ID) // Use ALERT channel
                .setSmallIcon(R.drawable.baseline_notifications_none_24) // Replace with your alert icon
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH) // Match channel importance
                .setContentIntent(pendingIntent)
                .setAutoCancel(true) // Dismiss notification when tapped
                .setDefaults(NotificationCompat.DEFAULT_ALL); // Use default sound, vibrate, etc.

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);

        // --- IMPORTANT: Check for POST_NOTIFICATIONS permission (Android 13+) ---
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "POST_NOTIFICATIONS permission not granted. Cannot send alert.");
                // Optionally, inform the user via a different mechanism or log it.
                return;
            }
        }
        // Use the dedicated ID for alerts
        notificationManager.notify(DISTANCE_ALERT_NOTIFICATION_ID, builder.build());
        Log.d(TAG, "Sent distance alert notification: " + message);
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