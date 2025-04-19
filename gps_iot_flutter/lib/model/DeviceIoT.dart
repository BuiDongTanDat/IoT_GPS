import 'package:geolocator/geolocator.dart';

class DeviceIoT {
  String id;
  String username;
  String details;
  double latitude;
  double longitude;
  bool isTracking;
  String? address;
  double? distance; // Khoảng cách từ vị trí người dùng

  DeviceIoT({
    required this.id,
    required this.username,
    required this.details,
    required this.latitude,
    required this.longitude,
    required this.isTracking,
    this.address,
    this.distance,
  });

  factory DeviceIoT.fromMap(Map<dynamic, dynamic> map) {
    return DeviceIoT(
      id: map['username'] ?? '',
      username: map['username'] ?? '',
      details: map['desc'] ?? '',
      latitude: (map['location']?['latitude'] ?? 0.0).toDouble(),
      longitude: (map['location']?['longitude'] ?? 0.0).toDouble(),
      isTracking: map['isTracking'] ?? false,
      address: map['address'],
    );
  }

  factory DeviceIoT.empty() {
    return DeviceIoT(
      id: '',
      username: '',
      details: '',
      latitude: 0.0,
      longitude: 0.0,
      address: null,
      isTracking: false,
    );
  }

  // Phương thức tính khoảng cách từ người dùng tới thiết bị
  double calculateDistance(double userLatitude, double userLongitude) {
    double distanceInMeters = Geolocator.distanceBetween(
      userLatitude,
      userLongitude,
      latitude,
      longitude,
    );
    return distanceInMeters; // Khoảng cách tính được (mét)
  }

  DeviceIoT copyWith({
    String? id,
    String? name,
    String? details,
    double? latitude,
    double? longitude,
    bool? isTracking,
    String? address,
    double? distance,
  }) {
    return DeviceIoT(
      id: id ?? this.id,
      username: name ?? this.username,
      details: details ?? this.details,
      latitude: latitude ?? this.latitude,
      longitude: longitude ?? this.longitude,
      isTracking: isTracking ?? this.isTracking,
      address: address ?? this.address,
      distance: distance ?? this.distance,
    );
  }
}
