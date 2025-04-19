import 'dart:async';
import 'package:flutter/foundation.dart' show kIsWeb; // Import kIsWeb
import 'package:geolocator/geolocator.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:firebase_database/firebase_database.dart';

import '../bloc/device_bloc.dart';
import '../bloc/device_event.dart';

class LocationService {
  StreamSubscription<Position>? _positionStreamSubscription;
  static final LocationService _instance = LocationService._internal();

  // Removed the callback as updates are primarily handled via the BLoC now
  // Function(Position)? _onLocationChanged;

  factory LocationService() => _instance;

  LocationService._internal();

  // Removed setLocationChangedCallback as it wasn't used after BLoC introduction

  Future<void> startLocationService(DeviceBloc deviceBloc) async {
    // Ensure previous stream is cancelled before starting a new one
    stopLocationService();

    SharedPreferences prefs = await SharedPreferences.getInstance();
    String? userId = prefs.getString('userId');

    if (userId == null) {
      print('LocationService: User ID is null. Cannot start.');
      return;
    }

    print('LocationService: Attempting to start for user $userId...');

    // Handle Permissions specifically for Web vs Mobile
    bool permissionsGranted = await _handlePermissions();
    if (!permissionsGranted) {
      print('LocationService: Permissions not granted. Aborting.');
      // Optionally, inform the user via the BLoC or another mechanism
      return;
    }

    print('LocationService: Permissions granted. Setting up location stream...');

    const LocationSettings locationSettings = LocationSettings(
      accuracy: LocationAccuracy.high, // High accuracy
      // distanceFilter: 0 means report all movements.
      // On web, browser might still throttle updates.
      // Consider a small value (e.g., 10 meters) if updates are too frequent
      // or battery/performance is a concern, but 0 is fine for testing.
      distanceFilter: 0,
    );

    try {
      _positionStreamSubscription = Geolocator.getPositionStream(
        locationSettings: locationSettings,
      ).listen(
        (Position position) { // Changed from Position? to Position - stream usually doesn't emit null
          print('LocationService: Received position: ${position.latitude}, ${position.longitude}');
          // Update Firebase
          _updateUserLocationToFirebase(
              userId, position.latitude, position.longitude);

          // Send update to BLoC
          deviceBloc.add(UpdateUserLocation(position));

          // If you still needed the direct callback (though BLoC is preferred):
          // _onLocationChanged?.call(position);
        },
        onError: (error) {
          // Handle potential errors from the stream
          print('LocationService: Error in location stream: $error');
          // Potentially stop the service or notify the user/bloc
          // Example: deviceBloc.add(LocationErrorOccurred(error.toString()));
          stopLocationService(); // Stop on error to prevent repeated issues
        },
        onDone: () {
          // Stream closed (e.g., permissions revoked maybe?)
          print('LocationService: Location stream closed (onDone).');
          _positionStreamSubscription = null; // Clear subscription reference
        },
        cancelOnError: true, // Automatically cancel subscription on error
      );
       print('LocationService: Stream listener attached.');

    } catch (e) {
      print('LocationService: Error setting up location stream: $e');
      // Handle exceptions during stream setup (e.g., initial permission issue missed)
    }
  }

  Future<void> _updateUserLocationToFirebase(
      String userId, double latitude, double longitude) async {
    try {
      DatabaseReference userRef =
          FirebaseDatabase.instance.ref('users/$userId/location');

      await userRef.update({
        'latitude': latitude,
        'longitude': longitude,
        'timestamp': ServerValue.timestamp, // Use server timestamp for consistency
      });
      // print('LocationService: Firebase updated successfully.'); // Optional success log
    } catch (e) {
      print('LocationService: Error updating user location to Firebase: $e');
    }
  }

  void stopLocationService() {
    if (_positionStreamSubscription != null) {
      print('LocationService: Stopping location service...');
      _positionStreamSubscription?.cancel();
      _positionStreamSubscription = null;
      print('LocationService: Service stopped.');
    }
  }

  Future<bool> _handlePermissions() async {
    LocationPermission permission;

    if (kIsWeb) {
      // --- Web Permission Handling ---
      print("LocationService: Checking permissions on Web...");
      permission = await Geolocator.checkPermission();
      print("LocationService: Web permission status: $permission");

      if (permission == LocationPermission.denied) {
        permission = await Geolocator.requestPermission();
        print("LocationService: Web permission status after request: $permission");
        if (permission == LocationPermission.denied) {
          print('LocationService (Web): Location permissions are denied.');
          // Inform user: "Please allow location access in your browser settings for this site."
          return false;
        }
      }

      if (permission == LocationPermission.deniedForever) {
        print('LocationService (Web): Location permissions are permanently denied.');
        // Inform user: "Location permissions are blocked. Please enable them in your browser settings for this site."
        return false;
      }

      // On Web, 'whileInUse' and 'always' often map to a single 'granted' state by the browser
      print('LocationService (Web): Permissions granted.');
      return true;

    } else {
      // --- Mobile Permission Handling (Original Logic) ---
      print("LocationService: Checking service enabled on Mobile...");
      bool serviceEnabled = await Geolocator.isLocationServiceEnabled();
      if (!serviceEnabled) {
        print('LocationService (Mobile): Location services are disabled.');
        // Suggest opening settings, but don't block if user doesn't
        // You might want to return false here or show a specific UI message
        // await Geolocator.openLocationSettings(); // Keep this if you want to prompt
        // return false; // Decide if disabled service should prevent startup
        print('LocationService (Mobile): Consider enabling location services.');
        // For now, let's allow proceeding, requestPermission will handle it partially
      }

      print("LocationService: Checking permissions on Mobile...");
      permission = await Geolocator.checkPermission();
      print("LocationService: Mobile permission status: $permission");

      if (permission == LocationPermission.denied) {
        permission = await Geolocator.requestPermission();
         print("LocationService: Mobile permission status after request: $permission");
        if (permission == LocationPermission.denied) {
          print('LocationService (Mobile): Location permissions are denied.');
          return false;
        }
      }

      if (permission == LocationPermission.deniedForever) {
        print('LocationService (Mobile): Location permissions are permanently denied.');
        // Optionally prompt to open app settings
        // Consider showing a dialog first before calling openAppSettings()
        // await Geolocator.openAppSettings();
        return false;
      }

      print('LocationService (Mobile): Permissions granted.');
      return true;
    }
  }
}