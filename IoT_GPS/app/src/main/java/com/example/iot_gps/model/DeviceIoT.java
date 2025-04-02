package com.example.iot_gps.model;

import com.google.firebase.firestore.GeoPoint;

public class DeviceIoT {
    private String id;
    private String name;
    private String desc;
    private GeoPoint location;
    private long timestamp;


    public DeviceIoT(String id,String name, String desc, GeoPoint location, long timestamp) {
        this.id = id;
        this.name = name;
        this.desc = desc;
        this.location = location;
        this.timestamp = timestamp;
    }


    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
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

    public String toString() {
        return "DeviceIoT{" +
                "name='" + name + '\'' +
                ", desc='" + desc + '\'' +
                ", location=" + location +
                ", timestamp=" + timestamp +
                '}';
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}