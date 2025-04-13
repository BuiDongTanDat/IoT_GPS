package com.example.iot_gps.adapter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.iot_gps.R;
import com.example.iot_gps.activity.TrackLocation;
import com.example.iot_gps.model.DeviceIoT;
import com.example.iot_gps.model.GeoPoint;
import com.example.iot_gps.utils.FirebaseDatabaseHelper;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.List;

public class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder> {

    private  List<DeviceIoT> deviceIoTList;
    private  String userId;
    private final DatabaseReference databaseRef = FirebaseDatabaseHelper.getReference("users");

    public DeviceAdapter(List<DeviceIoT> deviceIoTList , String userId) {
        this.deviceIoTList = deviceIoTList;
        this.userId = userId;
    }

    public void setDeviceIoTList(List<DeviceIoT> deviceIoTList) {
        this.deviceIoTList = deviceIoTList;
    }

    @NonNull
    @Override
    public DeviceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.card_device, parent, false);
        return new DeviceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DeviceViewHolder holder, int position) {
        DeviceIoT device = deviceIoTList.get(position);
        holder.tvNameDevice.setText(device.getName());
        holder.tvDescDevice.setText(device.getDesc());
        holder.distanceDevice.setText(String.format("%.2f m", device.getDistance()));

        // Cập nhật màu sắc khoảng cách
        if (device.getDistance() < 5) {
            holder.distanceDevice.setTextColor(holder.itemView.getContext().getResources().getColor(R.color.green));
        } else if (device.getDistance() < 10) {
            holder.distanceDevice.setTextColor(holder.itemView.getContext().getResources().getColor(R.color.yellow));
        } else {
            holder.distanceDevice.setTextColor(holder.itemView.getContext().getResources().getColor(R.color.red));
        }

        // Cập nhật trạng thái nút
        holder.btnTracking.setText(device.isTracking() ? "ON" : "OFF");
        holder.btnTracking.setBackgroundColor(device.isTracking()
                ? holder.itemView.getContext().getResources().getColor(R.color.green)
                : holder.itemView.getContext().getResources().getColor(R.color.red)
        );

        // Xử lý sự kiện bấm nút
        holder.btnTracking.setOnClickListener(v -> {
            boolean newTrackingState = !device.isTracking();
            device.setTracking(newTrackingState);

            // Giao diện
            holder.btnTracking.setText(newTrackingState ? "ON" : "OFF");
            holder.btnTracking.setBackgroundColor(newTrackingState
                    ? holder.itemView.getContext().getResources().getColor(R.color.green)
                    : holder.itemView.getContext().getResources().getColor(R.color.red)
            );

            // Gửi trạng thái mới lên Firebase
            String deviceId = device.getId(); // Đảm bảo device có ID hợp lệ
            DatabaseReference statusRef = FirebaseDatabase.getInstance()
                    .getReference("users")
                    .child(userId)
                    .child("devices")
                    .child(deviceId)
                    .child("status");

            statusRef.setValue(newTrackingState);

        });

        //Bắt đầu activity theo dõi
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
}