// DeviceAdapter.java
package com.example.iot_gps.adapter;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.iot_gps.R;
import com.example.iot_gps.activity.TrackLocation;
import com.example.iot_gps.model.DeviceIoT;
import com.google.firebase.firestore.GeoPoint;

import java.util.List;

public class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder> {

    private List<DeviceIoT> deviceIoTList;
    private GeoPoint userLocation;

    public DeviceAdapter(List<DeviceIoT> deviceIoTList) {
        this.deviceIoTList = deviceIoTList;
    }

    public void updateUserLocation(GeoPoint userLocation) {
        this.userLocation = userLocation;
        notifyDataSetChanged();
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

        if (userLocation != null) {
            double distance = calculateDistance(userLocation, device.getLocation());
            holder.distanceDevice.setText(String.format("%.2f m", distance));
            // Set color based on distance
            if (distance < 1) {
                holder.distanceDevice.setTextColor(holder.itemView.getContext().getResources().getColor(R.color.green));
            } else if (distance < 5) {
                holder.distanceDevice.setTextColor(holder.itemView.getContext().getResources().getColor(R.color.yellow));
            } else {
                holder.distanceDevice.setTextColor(holder.itemView.getContext().getResources().getColor(R.color.red));
            }
        }

        holder.itemView.setOnClickListener(v -> {
            Context context = v.getContext();
            Intent intent = new Intent(context, TrackLocation.class);
            intent.putExtra("device_id", device.getId());
            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return deviceIoTList.size();
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

    public static class DeviceViewHolder extends RecyclerView.ViewHolder {
        TextView tvNameDevice;
        TextView tvDescDevice;
        TextView distanceDevice;

        public DeviceViewHolder(@NonNull View itemView) {
            super(itemView);
            tvNameDevice = itemView.findViewById(R.id.tvNameDevice);
            tvDescDevice = itemView.findViewById(R.id.tvDescDevice);
            distanceDevice = itemView.findViewById(R.id.distanceDevice);
        }
    }
}