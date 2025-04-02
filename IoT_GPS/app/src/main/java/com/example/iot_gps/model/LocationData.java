package com.example.iot_gps.model;
import com.google.firebase.firestore.GeoPoint;

public class LocationData {
    private GeoPoint location;
    private long timestamp;

    public LocationData() {
        // Default constructor required for Firestore
    }

    public LocationData(GeoPoint location, long timestamp) {
        this.location = location;
        this.timestamp = timestamp;
    }

    public GeoPoint getLocation() {
        return location;
    }

    public void setLocation(GeoPoint location) {
        this.location = location;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
