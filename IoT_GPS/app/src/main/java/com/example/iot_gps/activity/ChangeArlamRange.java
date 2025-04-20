//package com.example.iot_gps.activity;
//
//import android.content.Intent;
//import android.content.SharedPreferences;
//import android.os.Bundle;
//import android.widget.Button;
//import android.widget.EditText;
//import android.widget.Toast;
//
//import androidx.appcompat.app.AppCompatActivity;
//
//import com.example.iot_gps.R;
//
//public class ChangeArlamRange extends AppCompatActivity {
//    public  void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_change_arlam_range);
//        Button btnSaveDistance = findViewById(R.id.btnSaveDistance);
//        EditText etDistance = findViewById(R.id.etDistance);
//        btnSaveDistance.setOnClickListener(v -> {
//            String distanceStr = etDistance.getText().toString().trim();
//
//            if (distanceStr.isEmpty()) {
//                Toast.makeText(this, "Please enter a distance!", Toast.LENGTH_SHORT).show();
//                return;
//            }
//
//            try {
//                int distance = Integer.parseInt(distanceStr);
//
//                if (distance <= 0) {
//                    Toast.makeText(this, "Distance must be > 0", Toast.LENGTH_SHORT).show();
//                    return;
//                }
//
//                SharedPreferences prefs = getSharedPreferences("Settings", MODE_PRIVATE);
//                prefs.edit().putInt("alert_distance", distance).apply();
//
//                Toast.makeText(this, "Alert distance updated: " + distance + "m", Toast.LENGTH_SHORT).show();
//                Intent intent = new Intent(this, MainActivity.class);
////                Intent intent_distance_alert = new Intent("DISTANCE_ALERT");
//                intent.putExtra("alert_distance", distance);
////                sendBroadcast(intent_distance_alert);
//                startActivity(intent);
//                finish();
//
//
//            } catch (NumberFormatException e) {
//                Toast.makeText(this, "Invalid distance format", Toast.LENGTH_SHORT).show();
//            }
//        });
//    }
//}
