import 'dart:async';
import 'dart:convert';
import 'package:bloc/bloc.dart';
import 'package:firebase_database/firebase_database.dart';
import 'package:geocoding/geocoding.dart';
import 'package:gps_iot_flutter/main.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../model/DeviceIoT.dart';
import 'device_event.dart';
import 'device_state.dart';
import 'package:http/http.dart' as http;
import 'package:flutter_local_notifications/flutter_local_notifications.dart';
import 'package:flutter/foundation.dart'; // For kIsWeb
import 'package:flutter/material.dart'; // For SnackBar

const double distanceThreshold = 5.0; // 5m

class DeviceBloc extends Bloc<DeviceEvent, DeviceState> {
  final _dbRef = FirebaseDatabase.instance.ref();
  final Map<String, StreamSubscription> _locationSubscriptions = {};
  final FlutterLocalNotificationsPlugin flutterLocalNotificationsPlugin =
      FlutterLocalNotificationsPlugin();

  DeviceBloc() : super(const DeviceState()) {
    initializeNotifications(); // gọi ở đây
    on<LoadDevices>(_onLoadDevices);
    on<UpdateDeviceLocation>(_onUpdateDeviceLocation);
    on<UpdateTrackingStatus>(_onUpdateTrackingStatus);
    on<UpdateUserLocation>(_onUpdateUserLocation);
    on<AddDevice>(_onAddDevice);
  }

  Future<void> _onLoadDevices(
      LoadDevices event, Emitter<DeviceState> emit) async {
    SharedPreferences prefs = await SharedPreferences.getInstance();
    final userId = prefs.getString('userId');
    if (userId == null) return;

    final snapshot = await _dbRef.child('users/$userId/devices').get();
    final data = snapshot.value as Map?;

    if (data == null) return;
    //Print receive data

    List<DeviceIoT> newDevices = [];

    for (final entry in data.entries) {
      final deviceId = entry.key;
      final deviceData = entry.value as Map?;

      if (deviceData == null) continue;

      final isTracking = deviceData['isTracking'] ?? false;

      final deviceSnapshot = await _dbRef.child('devices/$deviceId').get();
      final deviceInfo = deviceSnapshot.value as Map?;
      print("Received data: $deviceInfo");
      if (deviceInfo == null) continue;

      final location = deviceInfo['location'] as Map?;
      double lat = (location?['latitude'] as num?)?.toDouble() ?? 0;
      double lon = (location?['longitude'] as num?)?.toDouble() ?? 0;

      print("Latitude: $lat, Longitude: $lon");
      //get description from deviceInfo

      String? address;
      if (lat != 0 || lon != 0) {
        address =
            await _getAddressFromCoordinates(lat, lon) ?? "Chưa rõ địa chỉ";
      }
      print("Address: $address");

      newDevices.add(DeviceIoT(
        id: deviceId,
        username: deviceInfo['username'] ?? "Không tên",
        details: deviceInfo['desc'] ?? "",
        latitude: lat,
        longitude: lon,
        isTracking: isTracking,
        address: address,
      ));

      _listenToDeviceLocation(deviceId); // Theo dõi thiết bị
    }

    emit(state.copyWith(devices: newDevices));
  }

  Future<String?> _getAddressFromCoordinates(double lat, double lon) async {
    final url =
        'https://nominatim.openstreetmap.org/reverse?lat=$lat&lon=$lon&format=json';

    try {
      final response = await http.get(Uri.parse(url));

      if (response.statusCode == 200) {
        final data = json.decode(response.body);
        if (data['error'] == null) {
          return data['display_name']; // Trả về địa chỉ
        } else {
          print('Lỗi từ Nominatim API: ${data['error']}');
        }
      } else {
        print('Lỗi HTTP: ${response.statusCode}');
      }
    } catch (e) {
      print('Lỗi reverse geocoding web: $e');
    }
    return null;
  }

  Future<void> _listenToDeviceLocation(String deviceId) async {
    final locationRef = _dbRef.child('devices/$deviceId/location');

    SharedPreferences prefs = await SharedPreferences.getInstance();
    final userId = prefs.getString('userId');
    final trackingRef =
        _dbRef.child('users/$userId/devices/$deviceId/isTracking');

    // Cancel existing subscriptions if they exist
    _locationSubscriptions[deviceId]?.cancel();

    // Listen for location updates
    _locationSubscriptions[deviceId] = locationRef.onValue.listen((event) {
      final data = event.snapshot.value;
      if (data is Map) {
        double lat = (data['latitude'] as num?)?.toDouble() ?? 0;
        double lon = (data['longitude'] as num?)?.toDouble() ?? 0;
        add(UpdateDeviceLocation(deviceId, lat, lon));
      }
    });

    // Listen for isTracking updates
    trackingRef.onValue.listen((event) {
      final isTracking = event.snapshot.value as bool? ?? false;
      add(UpdateTrackingStatus(deviceId: deviceId, isTracking: isTracking));
    });
  }

  // Modify the existing method to ensure proper usage of the ScaffoldMessenger
  Future<void> _onUpdateDeviceLocation(
      UpdateDeviceLocation event, Emitter<DeviceState> emit) async {
    final devices = List<DeviceIoT>.from(state.devices);
    final index = devices.indexWhere((d) => d.id == event.deviceId);
    if (index != -1) {
      final address =
          await _getAddressFromCoordinates(event.latitude, event.longitude);

      final distance = devices[index].calculateDistance(
        state.userLocation?.latitude ?? 0.0,
        state.userLocation?.longitude ?? 0.0,
      );

      // Show Snackbar if the device is being tracked and exceeds the distance threshold
      String text =
          "Thiết bị ${devices[index].username} đang cách bạn ~ ${distance.toStringAsFixed(2)} m!";
      if (distance > distanceThreshold && devices[index].isTracking) {
        if (kIsWeb) {
          // Ensure the context is valid when showing Snackbar in the Web environment
          if (scaffoldMessengerKey.currentState != null) {
            scaffoldMessengerKey.currentState?.showSnackBar(
              SnackBar(
                content: Text(text),
                duration: const Duration(seconds: 3),
              ),
            );
          }
        } else {
          await _showNotification(
            'Cảnh báo thiết bị',
            text,
          );
        }
      }

      devices[index] = devices[index].copyWith(
        latitude: event.latitude,
        longitude: event.longitude,
        address: address,
        distance: distance, // Update distance
      );
      emit(state.copyWith(devices: devices));
    }
  }

  Future<void> _onUpdateTrackingStatus(
      UpdateTrackingStatus event, Emitter<DeviceState> emit) async {
    final devices = List<DeviceIoT>.from(state.devices);
    final index = devices.indexWhere((d) => d.id == event.deviceId);
    if (index != -1) {
      devices[index] = devices[index].copyWith(isTracking: event.isTracking);
      emit(state.copyWith(devices: devices));
    }

    SharedPreferences prefs = await SharedPreferences.getInstance();
    final userId = prefs.getString('userId');
    await _dbRef
        .child("users/$userId/devices/${event.deviceId}/isTracking")
        .set(event.isTracking);
  }

  Future<void> _onUpdateUserLocation(
      UpdateUserLocation event, Emitter<DeviceState> emit) async {
    final userLocation = event.position;

    // Lấy địa chỉ từ tọa độ
    String? userAddress = await _getAddressFromCoordinates(
      userLocation.latitude,
      userLocation.longitude,
    );

    List<DeviceIoT> updatedDevices = [];

    for (var device in state.devices) {
      final distance = device.calculateDistance(
        userLocation.latitude,
        userLocation.longitude,
      );

      // Nếu đang theo dõi và vượt ngưỡng thì gửi thông báo
      String text =
          "Thiết bị ${device.username} đang cách bạn ~ ${distance.toStringAsFixed(2)} m!";
      if (device.isTracking && distance > distanceThreshold) {
        if (kIsWeb) {
          scaffoldMessengerKey.currentState?.showSnackBar(
            SnackBar(
              content: Text(text),
              duration: const Duration(seconds: 3),
            ),
          );
        } else {
          await _showNotification(
            'Cảnh báo thiết bị',
            text,
          );
        }
      }

      updatedDevices.add(device.copyWith(distance: distance));
    }

    emit(state.copyWith(
      userLocation: userLocation,
      userAddress: userAddress,
      devices: updatedDevices,
    ));
  }

  Future<void> _onAddDevice(AddDevice event, Emitter<DeviceState> emit) async {
    try {
      SharedPreferences prefs = await SharedPreferences.getInstance();
      final userId = prefs.getString('userId');
      if (userId == null) return;

      // Fetch the device details from Firebase
      final deviceSnapshot =
          await _dbRef.child("devices/${event.username}").get();
      if (!deviceSnapshot.exists) {
        print("Device not found");
        return;
      }

      final deviceData = deviceSnapshot.value as Map;
      final deviceUsername = deviceData['username'];
      final devicePassword = deviceData['password'];

      // Verify the username and password
      if (deviceUsername != event.username ||
          devicePassword != event.password) {
        print("Invalid username or password");
        return;
      }

      // Add the device to the user's devices in Firebase
      await _dbRef.child("users/$userId/devices/${event.username}").set({
        "isTracking": false, // Default tracking status
      });

      // Reload devices to reflect the new device
      add(LoadDevices());
    } catch (e) {
      print("Error adding device: $e");
    }
  }

  Future<void> initializeNotifications() async {
    const AndroidInitializationSettings androidSettings =
        AndroidInitializationSettings('@mipmap/ic_launcher');

    const InitializationSettings initSettings =
        InitializationSettings(android: androidSettings);

    await flutterLocalNotificationsPlugin.initialize(initSettings);
  }

  Future<void> _showNotification(String title, String body,
      {BuildContext? context}) async {
    if (kIsWeb) {
      // Show a SnackBar if running on the web
      if (context != null) {
        final snackBar = SnackBar(
          content: Text('$title: $body'),
          duration: const Duration(seconds: 5),
        );
        ScaffoldMessenger.of(context).showSnackBar(snackBar);
      } else {
        print("Context is null. Cannot show SnackBar.");
      }
    } else if (flutterLocalNotificationsPlugin != null) {
      const AndroidNotificationDetails androidPlatformChannelSpecifics =
          AndroidNotificationDetails(
        'distance_alert_channel', // ID
        'Distance Alert',
        channelDescription: 'Thông báo khi thiết bị vượt ngưỡng khoảng cách',
        importance: Importance.max,
        priority: Priority.high,
      );

      const NotificationDetails platformChannelSpecifics =
          NotificationDetails(android: androidPlatformChannelSpecifics);

      // Show notification
      await flutterLocalNotificationsPlugin.show(
        DateTime.now()
            .millisecondsSinceEpoch, // Unique ID for each notification
        title,
        body,
        platformChannelSpecifics,
      );
    } else {
      print("Notifications are not supported on this platform.");
    }
  }

  @override
  Future<void> close() {
    _locationSubscriptions.values.forEach((s) => s.cancel());
    return super.close();
  }
}
