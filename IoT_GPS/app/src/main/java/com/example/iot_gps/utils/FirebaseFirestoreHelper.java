package com.example.iot_gps.utils;

import com.google.firebase.firestore.FirebaseFirestore;

public class FirebaseFirestoreHelper {

    // Khởi tạo Firestore một lần duy nhất
    private static FirebaseFirestore dbInstance;

    private FirebaseFirestoreHelper() {
        // private constructor để ngăn việc khởi tạo lại Firestore
    }

    // Hàm trả về instance duy nhất của Firestore
    public static FirebaseFirestore getInstance() {
        if (dbInstance == null) {
            synchronized (FirebaseFirestore.class) {
                if (dbInstance == null) {
                    dbInstance = FirebaseFirestore.getInstance();
                }
            }
        }
        return dbInstance;
    }


}
