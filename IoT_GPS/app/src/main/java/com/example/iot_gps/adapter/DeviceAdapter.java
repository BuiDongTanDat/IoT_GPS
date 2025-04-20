package com.example.iot_gps.adapter;

import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.iot_gps.R;
import com.example.iot_gps.activity.TrackLocation;
import com.example.iot_gps.model.DeviceIoT;
import com.example.iot_gps.model.GeoPoint;
import com.example.iot_gps.utils.FirebaseDatabaseHelper;
import com.google.android.gms.maps.model.LatLng;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.List;

public class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder> {

    private  List<DeviceIoT> deviceIoTList;
    private  String userId;
    private final DatabaseReference databaseRef = FirebaseDatabaseHelper.getReference("users");
    private DatabaseReference deviceRef, userRef;
    private  double alert_distance;
    private  Context context;

    public DeviceAdapter(List<DeviceIoT> deviceIoTList , String userId, Double alert_distance,Context context) {
        this.deviceIoTList = deviceIoTList;
        this.userId = userId;
        this.alert_distance=alert_distance;
        this.context=context;
    }

    public void setDeviceIoTList(List<DeviceIoT> deviceIoTList) {
        this.deviceIoTList = deviceIoTList;
    }

    @NonNull
    @Override
    public DeviceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.card_device, parent, false);
        Handler alertHandler = new Handler(Looper.getMainLooper());
        Runnable alertRunnable;
        return new DeviceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DeviceViewHolder holder, int position) {
        DeviceIoT device = deviceIoTList.get(position);
        holder.tvNameDevice.setText(device.getName());
        holder.tvDescDevice.setText(device.getDesc());

        // Tạo biến LatLng riêng cho item này
        final LatLng[] userLatLng = {null};
        final LatLng[] deviceLatLng = {null};

        // 1. Lấy vị trí người dùng đang đăng nhập
        DatabaseReference userLocationRef = FirebaseDatabase.getInstance()
                .getReference("users")
                .child(userId)
                .child("location");

        userLocationRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Double lat = snapshot.child("latitude").getValue(Double.class);
                Double lng = snapshot.child("longitude").getValue(Double.class);
                if (lat != null && lng != null) {
                    userLatLng[0] = new LatLng(lat, lng);
                    if (deviceLatLng[0] != null) {
                        updateDistance(holder, userLatLng[0], deviceLatLng[0],device);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("Firebase", "Failed to load user location", error.toException());
            }
        });

        // 2. Lấy vị trí thiết bị từ devices/{deviceId}/location
        DatabaseReference deviceLocationRef = FirebaseDatabase.getInstance()
                .getReference("devices")
                .child(device.getId())
                .child("location");

        deviceLocationRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Double lat = snapshot.child("latitude").getValue(Double.class);
                Double lng = snapshot.child("longitude").getValue(Double.class);
                if (lat != null && lng != null) {
                    deviceLatLng[0] = new LatLng(lat, lng);
                    if (userLatLng[0] != null) {
                        updateDistance(holder, userLatLng[0], deviceLatLng[0],device);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("Firebase", "Failed to load device location", error.toException());
            }
        });

        // Cập nhật nút trạng thái
        holder.btnTracking.setText(device.isTracking() ? "ON" : "OFF");
        holder.btnTracking.setBackgroundColor(device.isTracking()
                ? holder.itemView.getContext().getResources().getColor(R.color.green)
                : holder.itemView.getContext().getResources().getColor(R.color.red)
        );

        holder.btnTracking.setOnClickListener(v -> {
            boolean newTrackingState = !device.isTracking();
            device.setTracking(newTrackingState);

            holder.btnTracking.setText(newTrackingState ? "ON" : "OFF");
            holder.btnTracking.setBackgroundColor(newTrackingState
                    ? holder.itemView.getContext().getResources().getColor(R.color.green)
                    : holder.itemView.getContext().getResources().getColor(R.color.red)
            );

            // Cập nhật status trong Firebase
            DatabaseReference statusRef = FirebaseDatabase.getInstance()
                    .getReference("users")
                    .child(userId)
                    .child("devices")
                    .child(device.getId())
                    .child("isTracking");

            statusRef.setValue(newTrackingState);
        });

        // Chuyển sang TrackLocation activity
        holder.itemView.setOnClickListener(v -> {
            Context context = v.getContext();
            Intent intent = new Intent(context, TrackLocation.class);
            intent.putExtra("userId", userId);
            intent.putExtra("device_id", device.getId());
            context.startActivity(intent);
        });
    }



    @Override
    public int getItemCount() {
        return deviceIoTList.size();
    }

    public static class DeviceViewHolder extends RecyclerView.ViewHolder {
        TextView tvNameDevice;
        TextView tvDescDevice;
        TextView distanceDevice;
        Button btnTracking;

        public DeviceViewHolder(@NonNull View itemView) {
            super(itemView);
            tvNameDevice = itemView.findViewById(R.id.tvNameDevice);
            tvDescDevice = itemView.findViewById(R.id.tvDescDevice);
            distanceDevice = itemView.findViewById(R.id.distanceDevice);
            btnTracking = itemView.findViewById(R.id.btnTracking);
        }
    }
    private double calculateDistance(LatLng userLatLng, LatLng deviceLatLng) {
        double lat1 = userLatLng.latitude;
        double lon1 = userLatLng.longitude;
        double lat2 = deviceLatLng.latitude;
        double lon2 = deviceLatLng.longitude;

        double earthRadius = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return earthRadius * c;
    }
    private void updateDistance(DeviceViewHolder holder, LatLng userLatLng, LatLng deviceLatLng,DeviceIoT device) {
        double distance = calculateDistance(userLatLng, deviceLatLng);
        holder.distanceDevice.setText(String.format("%.2f m", distance));

        if (distance < 5) {
            holder.distanceDevice.setTextColor(holder.itemView.getContext().getResources().getColor(R.color.green));
        } else if (distance < 10) {
            holder.distanceDevice.setTextColor(holder.itemView.getContext().getResources().getColor(R.color.yellow));
        } else {
            holder.distanceDevice.setTextColor(holder.itemView.getContext().getResources().getColor(R.color.red));
        }
//        if(distance>alert_distance){
//            sendNotification("Device " + device.getName(), "Exceeded distance: " + (int) distance + "m");
////            vibratePhone();
//        }
    }
//    private void sendNotification(String title, String message) {
//        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
//        String channelId = "distance_alert_channel";
//
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            NotificationChannel channel = new NotificationChannel(channelId, "Distance Alerts", NotificationManager.IMPORTANCE_HIGH);
//            channel.enableVibration(true);
//            notificationManager.createNotificationChannel(channel);
//        }
//
//        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
//                .setSmallIcon(R.drawable.baseline_warning_24) // thay icon tùy bạn
//                .setContentTitle(title)
//                .setContentText(message)
//                .setPriority(NotificationCompat.PRIORITY_HIGH)
//                .setAutoCancel(true);
//
//        notificationManager.notify((int) System.currentTimeMillis(), builder.build());
//    }

    private void vibratePhone() {
        Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);

        if (vibrator != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(1000);
            }
        }
    }


}