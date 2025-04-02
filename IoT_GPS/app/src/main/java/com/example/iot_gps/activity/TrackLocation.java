package com.example.iot_gps.activity;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;

import com.example.iot_gps.R;
import com.example.iot_gps.service.LocationService;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.firestore.ListenerRegistration;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class TrackLocation extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap googleMap;
    private boolean isFirstTime = true;
    private TextView toado, vitri, toadoTB, vitriTB;
    private LocationReceiver locationReceiver;
    private String deviceId;
    private ListenerRegistration deviceLocationListener;
    private Marker userMarker, deviceMarker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_track_location);

        toado = findViewById(R.id.toado);
        vitri = findViewById(R.id.vitri);
        toadoTB = findViewById(R.id.toadoTB);
        vitriTB = findViewById(R.id.vitriTB);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        // Start LocationService
        startService(new Intent(this, LocationService.class));

        // Register BroadcastReceiver to receive location updates
        locationReceiver = new LocationReceiver();
        registerReceiver(locationReceiver, new IntentFilter("LOCATION_UPDATE"), Context.RECEIVER_NOT_EXPORTED);

        // Set up the toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        // Retrieve the device ID from the Intent
        deviceId = getIntent().getStringExtra("device_id");

        // Fetch and update the device's initial location
        fetchDeviceLocation();
    }

    private void fetchDeviceLocation() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        DocumentReference deviceRef = db.collection("locations").document(deviceId);
        deviceLocationListener = deviceRef.addSnapshotListener((snapshot, e) -> {
            if (e != null) {
                Toast.makeText(this, "Error fetching device location", Toast.LENGTH_SHORT).show();
                return;
            }
            if (snapshot != null && snapshot.exists()) {
                GeoPoint location = snapshot.getGeoPoint("location");
                if (location != null) {
                    updateDeviceLocationOnMap(location.getLatitude(), location.getLongitude());
                }
            }
        });
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        this.googleMap = googleMap;
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    100);
            return;
        }
        googleMap.setMyLocationEnabled(true);
    }

    private class LocationReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            double latitude = intent.getDoubleExtra("latitude", 0.0);
            double longitude = intent.getDoubleExtra("longitude", 0.0);
            updateUserLocationOnMap(latitude, longitude);
        }
    }

    private void updateUserLocationOnMap(double latitude, double longitude) {
        if (googleMap == null) return;

        LatLng currentLocation = new LatLng(latitude, longitude);
        if (userMarker != null) {
            userMarker.remove();
        }
        userMarker = googleMap.addMarker(new MarkerOptions().position(currentLocation).title("Your current location"));

        if (isFirstTime) {
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 100));
            isFirstTime = false;
        } else {
            googleMap.animateCamera(CameraUpdateFactory.newLatLng(currentLocation));
        }

        toado.setText("Tọa độ: " + latitude + ", " + longitude);

        try {
            Geocoder geocoder = new Geocoder(this, Locale.getDefault());
            List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);
            if (addresses != null && !addresses.isEmpty()) {
                vitri.setText("Vị trí: " + addresses.get(0).getAddressLine(0));
            } else {
                vitri.setText("Vị trí: Không xác định");
            }
        } catch (IOException e) {
            e.printStackTrace();
            vitri.setText("Vị trí: Không xác định");
            Toast.makeText(this, "Unable to get address", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateDeviceLocationOnMap(double latitude, double longitude) {
        if (googleMap == null) return;

        LatLng deviceLocation = new LatLng(latitude, longitude);

        if (deviceMarker != null) {
            deviceMarker.remove();
        }
        deviceMarker = googleMap.addMarker(new MarkerOptions()
                .position(deviceLocation)
                .title("Device location")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))); // Set marker color to green

        toadoTB.setText("Tọa độ TB: " + latitude + ", " + longitude);

        try {
            Geocoder geocoder = new Geocoder(this, Locale.getDefault());
            List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);
            if (addresses != null && !addresses.isEmpty()) {
                vitriTB.setText("Vị trí TB: " + addresses.get(0).getAddressLine(0));
            } else {
                vitriTB.setText("Vị trí TB: Không xác định");
            }
        } catch (IOException e) {
            e.printStackTrace();
            vitriTB.setText("Vị trí TB: Không xác định");
            Toast.makeText(this, "Unable to get address", Toast.LENGTH_SHORT).show();
        }
    }

    
}