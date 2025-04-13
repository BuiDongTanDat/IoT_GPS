package com.example.iot_gps.utils;

import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.DatabaseReference;

public class FirebaseDatabaseHelper {

    // Khởi tạo FirebaseDatabase một lần duy nhất
    private static FirebaseDatabase databaseInstance;

    private FirebaseDatabaseHelper() {
        // private constructor để ngăn việc khởi tạo lại
    }

    // Trả về instance duy nhất của FirebaseDatabase
    public static FirebaseDatabase getInstance() {
        if (databaseInstance == null) {
            synchronized (FirebaseDatabase.class) {
                if (databaseInstance == null) {
                    databaseInstance = FirebaseDatabase.getInstance();
//                    databaseInstance.setPersistenceEnabled(true); // Bật cache offline nếu muốn
                }
            }
        }
        return databaseInstance;
    }

    // Lấy DatabaseReference dễ dàng
    public static DatabaseReference getRootReference() {
        return getInstance().getReference();
    }

    // Lấy DatabaseReference theo node cụ thể
    public static DatabaseReference getReference(String path) {
        return getInstance().getReference(path);
    }
}
