package com.example.iot_gps.activity;
import android.app.Application;

import com.google.firebase.database.FirebaseDatabase;

public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        // Bật chế độ lưu offline cho Firebase Realtime Database
        FirebaseDatabase.getInstance().setPersistenceEnabled(true);
    }
}
