package com.example.iot_gps.activity;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;

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

    private TextView toado, vitri, toadoTB, vitriTB;
    private Marker userMarker, deviceMarker;
    private CardView tapToFocus;
    private String deviceId;


    private DatabaseReference deviceRef, userRef;

    private LatLng userLatLng, deviceLatLng;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_track_location);

        toado = findViewById(R.id.toado);
        vitri = findViewById(R.id.vitri);
        toadoTB = findViewById(R.id.toadoTB);
        vitriTB = findViewById(R.id.vitriTB);
        tapToFocus = findViewById(R.id.tapToFocus);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> finish());

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
    }

    private void setupRealtimeListeners() {
        // Use FirebaseDatabaseHelper to get references
        deviceRef = FirebaseDatabaseHelper.getReference("locations").child(deviceId).child("location");
        deviceRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Double lat = snapshot.child("latitude").getValue(Double.class);
                Double lng = snapshot.child("longitude").getValue(Double.class);
                if (lat != null && lng != null) {
                    updateDeviceLocationOnMap(lat, lng);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(TrackLocation.this, "Lỗi tải vị trí thiết bị", Toast.LENGTH_SHORT).show();
            }
        });

        String currentUserId = "user_location"; // Replace with actual user ID logic
        userRef = FirebaseDatabaseHelper.getReference("locations").child(currentUserId).child("location");
        userRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Double lat = snapshot.child("latitude").getValue(Double.class);
                Double lng = snapshot.child("longitude").getValue(Double.class);
                if (lat != null && lng != null) {
                    updateUserLocationOnMap(lat, lng);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(TrackLocation.this, "Lỗi tải vị trí của bạn", Toast.LENGTH_SHORT).show();
            }
        });
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

        userLatLng = new LatLng(latitude, longitude);


        if (userMarker != null) userMarker.remove();
        userMarker = googleMap.addMarker(new MarkerOptions().position(userLatLng).title("Vị trí của bạn"));

        if (isFirstTime) {
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 100));
            isFirstTime = false;
        } else {
            googleMap.animateCamera(CameraUpdateFactory.newLatLng(userLatLng));
        }

        if (userLatLng != null && deviceLatLng != null) {
            drawRoute(userLatLng.latitude, userLatLng.longitude, deviceLatLng.latitude, deviceLatLng.longitude);
        }

        toado.setText("Tọa độ: " + latitude + ", " + longitude);
        vitri.setText("Vị trí: " + getAddressFromLocation(latitude, longitude));
    }

    private void updateDeviceLocationOnMap(double latitude, double longitude) {
        if (googleMap == null) return;

        deviceLatLng = new LatLng(latitude, longitude);

        if (deviceMarker != null) deviceMarker.remove();
        deviceMarker = googleMap.addMarker(new MarkerOptions()
                .position(deviceLatLng)
                .title("Thiết bị")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));

        if (userLatLng != null && deviceLatLng != null) {
            drawRoute(userLatLng.latitude, userLatLng.longitude, deviceLatLng.latitude, deviceLatLng.longitude);
        }

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
        }
        return "Không xác định";
    }



    private void drawRoute(double startLat, double startLng, double endLat, double endLng) {
        String apiKey = getString(R.string.google_maps_key); // Đặt API key trong strings.xml
        String url = "https://maps.googleapis.com/maps/api/directions/json?origin=" +
                startLat + "," + startLng + "&destination=" + endLat + "," + endLng + "&key=" + apiKey;

        new Thread(() -> {
            try {
                URL directionsUrl = new URL(url);
                HttpURLConnection connection = (HttpURLConnection) directionsUrl.openConnection();
                connection.connect();

                InputStream inputStream = connection.getInputStream();
                StringBuilder result = new StringBuilder();
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                String line;
                while ((line = reader.readLine()) != null) {
                    result.append(line);
                }

                JSONObject jsonObject = new JSONObject(result.toString());
                JSONArray routes = jsonObject.getJSONArray("routes");
                if (routes.length() > 0) {
                    JSONObject route = routes.getJSONObject(0);
                    JSONObject overviewPolyline = route.getJSONObject("overview_polyline");
                    String points = overviewPolyline.getString("points");

                    List<LatLng> decodedPath = decodePolyline(points);

                    runOnUiThread(() -> {
                        PolylineOptions polylineOptions = new PolylineOptions()
                                .addAll(decodedPath)
                                .width(12)
                                .color(Color.BLUE)
                                .geodesic(true);
                        googleMap.addPolyline(polylineOptions);
                    });
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
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

}