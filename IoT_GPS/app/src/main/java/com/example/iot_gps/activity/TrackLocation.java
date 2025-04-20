package com.example.iot_gps.activity;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;

//import com.android.volley.Request;
//import com.android.volley.RequestQueue;
//import com.android.volley.toolbox.JsonObjectRequest;
//import com.android.volley.toolbox.Volley;
import com.example.iot_gps.R;
import com.example.iot_gps.utils.FirebaseDatabaseHelper;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TrackLocation extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap googleMap;
    private boolean isFirstTime = true;

    private TextView toado, vitri, toadoTB, vitriTB, distanceTextView;
    private Marker userMarker, deviceMarker;
    private ImageView backButton;
    private CardView tapToFocus;
    private String deviceId;
    private final float POND_WARNING_DISTANCE = 1000f; // đơn vị: mét
    private DatabaseReference deviceRef, userRef;
    private LatLng userLatLng, deviceLatLng;
    private double distance;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_track_location);

        toado = findViewById(R.id.toado);
        vitri = findViewById(R.id.vitri);
        toadoTB = findViewById(R.id.toadoTB);
        vitriTB = findViewById(R.id.vitriTB);
        tapToFocus = findViewById(R.id.tapToFocus);
        backButton = findViewById(R.id.backButton);
        distanceTextView = findViewById(R.id.distanceTextView);

        backButton.setOnClickListener(v -> finish());

        tapToFocus.setOnClickListener(v -> {
            if (deviceLatLng != null && googleMap != null) {
                googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(deviceLatLng, 18));
            } else {
                Toast.makeText(this, "Chưa có vị trí thiết bị", Toast.LENGTH_SHORT).show();
            }
        });
        deviceId = getIntent().getStringExtra("device_id");

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        // Start realtime listener
        setupRealtimeListeners();
        ImageView handPointer = findViewById(R.id.handPointerImage);
        handPointer.setVisibility(View.GONE); // Ẩn lúc ban đầu
        // Chờ 5 giây rồi mới hiện và bắt đầu animation
        handPointer.postDelayed(() -> {
            handPointer.setVisibility(View.VISIBLE); // Hiện bàn tay
            // Tạo animation nâng lên - hạ xuống
            ObjectAnimator upDown = ObjectAnimator.ofFloat(handPointer, "translationY", 0f, -30f, 0f);
            upDown.setDuration(1000);
            upDown.setRepeatCount(ObjectAnimator.INFINITE);
            upDown.setRepeatMode(ValueAnimator.RESTART);
            upDown.start();
            // Nếu muốn ẩn sau 4 giây kể từ khi xuất hiện:
            handPointer.postDelayed(() -> {
                upDown.cancel(); // Dừng animation
                handPointer.setVisibility(View.GONE);
            }, 4000);

        }, 5000);
    }

    private void setupRealtimeListeners() {
        String userId = getIntent().getStringExtra("userId");      // 👈 username như "a"
        String deviceId = getIntent().getStringExtra("device_id"); // 👈 mã thiết bị như "123"

        // 🔁 Vị trí thiết bị: users/{userId}/devices/{deviceId}/location
        deviceRef = FirebaseDatabaseHelper.getReference("devices")
                .child(deviceId)
                .child("location");

        deviceRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Double lat = snapshot.child("latitude").getValue(Double.class);
                Double lng = snapshot.child("longitude").getValue(Double.class);
                if (lat != null && lng != null) {
                    deviceLatLng = new LatLng(lat, lng);
                    updateDeviceLocationOnMap(lat, lng);
                    updateDistanceAndRoute();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(TrackLocation.this, "Lỗi tải vị trí thiết bị", Toast.LENGTH_SHORT).show();
                Log.e("Firebase", "Error loading device location: ", error.toException());
            }
        });

        // 🔁 Vị trí người dùng: users/{userId}/location
        userRef = FirebaseDatabaseHelper.getReference("users")
                .child(userId)
                .child("location");

        userRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Double lat = snapshot.child("latitude").getValue(Double.class);
                Double lng = snapshot.child("longitude").getValue(Double.class);
                if (lat != null && lng != null) {
                    userLatLng = new LatLng(lat, lng);
                    updateUserLocationOnMap(lat, lng);
                    updateDistanceAndRoute();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(TrackLocation.this, "Lỗi tải vị trí của bạn", Toast.LENGTH_SHORT).show();
                Log.e("Firebase", "Error loading user location: ", error.toException());
            }
        });
    }


    private void updateDistanceAndRoute() {
        if (userLatLng != null && deviceLatLng != null) {
            drawDirectLine(userLatLng, deviceLatLng); // 👈 thay vì gọi drawRoute()

            distance = calculateDistance(userLatLng, deviceLatLng);
            if (distance < 5) {
                distanceTextView.setTextColor(getResources().getColor(R.color.green));
            } else if (distance < 10) {
                distanceTextView.setTextColor(getResources().getColor(R.color.yellow));
            } else {
                distanceTextView.setTextColor(getResources().getColor(R.color.red));
            }

            String distanceText = distance >= 1000 ?
                    String.format(Locale.getDefault(), "%.2f km", distance / 1000) :
                    String.format(Locale.getDefault(), "%.0f m", distance);
            distanceTextView.setText(distanceText);
        } else {
            distanceTextView.setText("Đang tải vị trí...");
        }
    }



    @Override
    public void onMapReady(GoogleMap map) {
        this.googleMap = map;

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    100);
            return;
        }

        googleMap.setMyLocationEnabled(true);
    }

    private void updateUserLocationOnMap(double latitude, double longitude) {
        if (googleMap == null) return;

        //userLatLng = new LatLng(latitude, longitude);  // Moved to the ValueEventListener

        if (userMarker != null) userMarker.remove();
        userMarker = googleMap.addMarker(new MarkerOptions().position(userLatLng).title("Vị trí của bạn"));

        if (isFirstTime) {
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 100));
            isFirstTime = false;
        } else {
            googleMap.animateCamera(CameraUpdateFactory.newLatLng(userLatLng));
        }

        toado.setText("Tọa độ: " + latitude + ", " + longitude);
        vitri.setText("Vị trí: " + getAddressFromLocation(latitude, longitude));
    }

    private void updateDeviceLocationOnMap(double latitude, double longitude) {
        if (googleMap == null) return;

        //deviceLatLng = new LatLng(latitude, longitude); // Moved to the ValueEventListener

        if (deviceMarker != null) deviceMarker.remove();
        deviceMarker = googleMap.addMarker(new MarkerOptions()
                .position(deviceLatLng)
                .title("Thiết bị")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));
        fetchPondsFromOSM(deviceLatLng);
        toadoTB.setText("Tọa độ TB: " + latitude + ", " + longitude);
        vitriTB.setText("Vị trí TB: " + getAddressFromLocation(latitude, longitude));
    }

    private String getAddressFromLocation(double lat, double lng) {
        try {
            Geocoder geocoder = new Geocoder(this, Locale.getDefault());
            List<Address> list = geocoder.getFromLocation(lat, lng, 1);
            if (list != null && !list.isEmpty()) {
                return list.get(0).getAddressLine(0);
            }
        } catch (IOException e) {
            e.printStackTrace();
            Log.e("Geocoder", "Error getting address: ", e); // Add error logging
        }
        return "Không xác định";
    }


    private void drawDirectLine(LatLng start, LatLng end) {
        if (googleMap == null) return;

        // Clear tuyến đường cũ (nếu có)
        googleMap.clear();

        // Vẽ marker lại
        googleMap.addMarker(new MarkerOptions().position(start).title("Vị trí của bạn"));
        googleMap.addMarker(new MarkerOptions()
                .position(end)
                .title("Thiết bị")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));

        // Vẽ đường thẳng (chim bay)
        PolylineOptions lineOptions = new PolylineOptions()
                .add(start)
                .add(end)
                .width(10)
                .color(Color.RED); // Đường chim bay màu đỏ

        googleMap.addPolyline(lineOptions);
    }




    private List<LatLng> decodePolyline(String encoded) {
        List<LatLng> poly = new ArrayList<>();
        int index = 0, len = encoded.length();
        int lat = 0, lng = 0;

        while (index < len) {
            int b, shift = 0, result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += dlat;

            shift = 0;
            result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lng += dlng;

            LatLng p = new LatLng(lat / 1E5, lng / 1E5);
            poly.add(p);
        }
        return poly;
    }

    //Hàm tính khoảng cách giữa 2 điểm
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
    private void fetchPondsFromOSM(LatLng deviceLatLng) {
        double delta = 0.005; // tương đương bán kính ~500m
        double minLat = deviceLatLng.latitude - delta;
        double maxLat = deviceLatLng.latitude + delta;
        double minLng = deviceLatLng.longitude - delta;
        double maxLng = deviceLatLng.longitude + delta;

        String url = "https://overpass-api.de/api/interpreter?data=[out:json];"
                + "(way[\"natural\"=\"water\"](" + minLat + "," + minLng + "," + maxLat + "," + maxLng + ");"
                + "relation[\"natural\"=\"water\"](" + minLat + "," + minLng + "," + maxLat + "," + maxLng + "););out center;";

        RequestQueue queue = Volley.newRequestQueue(this);
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url, null,
                response -> {
                    try {
                        JSONArray elements = response.getJSONArray("elements");
                        for (int i = 0; i < elements.length(); i++) {
                            JSONObject element = elements.getJSONObject(i);
                            JSONObject center = element.getJSONObject("center");
                            double lat = center.getDouble("lat");
                            double lon = center.getDouble("lon");
                            String type = element.getString("type");

                            LatLng pondLatLng = new LatLng(lat, lon);
                            double distance = calculateDistance(deviceLatLng.latitude, deviceLatLng.longitude, lat, lon);
                            if (distance < POND_WARNING_DISTANCE) {
                                Toast.makeText(this, "⚠️ Gần ao/hồ OSM (" + type + "): " + (int) distance + "m", Toast.LENGTH_LONG).show();

                                // Vẽ marker ao/hồ lên bản đồ
                                googleMap.addMarker(new MarkerOptions()
                                        .position(pondLatLng)
                                        .title("Ao/Hồ (OSM)")
                                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
                            }
                        }
                    } catch (JSONException e) {
                        Log.e("OSM_JSON", "Lỗi đọc JSON từ OSM", e);
                    }
                },
                error -> Log.e("OSM_API", "Lỗi gọi Overpass API", error)
        );

        queue.add(request);
    }
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        float[] result = new float[1];
        android.location.Location.distanceBetween(lat1, lon1, lat2, lon2, result);
        return result[0];
    }
}