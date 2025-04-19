import 'package:equatable/equatable.dart';
import 'package:geolocator/geolocator.dart';

abstract class DeviceEvent extends Equatable {
  const DeviceEvent();

  @override
  List<Object?> get props => [];
}

// Sự kiện tải danh sách thiết bị
class LoadDevices extends DeviceEvent {}

// Sự kiện cập nhật vị trí thiết bị (đang có sẵn)
class UpdateDeviceLocation extends DeviceEvent {
  final String deviceId;
  final double latitude;
  final double longitude;

  const UpdateDeviceLocation(this.deviceId, this.latitude, this.longitude);

  @override
  List<Object> get props => [deviceId, latitude, longitude];
}

//Sự kiện cập nhật trạng thái theo dõi
class UpdateTrackingStatus extends DeviceEvent {
  final String deviceId;
  final bool isTracking;

  const UpdateTrackingStatus({
    required this.deviceId,
    required this.isTracking,
  });

  @override
  List<Object> get props => [deviceId, isTracking];
}


class UpdateUserLocation extends DeviceEvent {
  final Position position;

  const UpdateUserLocation(this.position);
}

class AddDevice extends DeviceEvent {
  final String username; // Id là ussername luôn
  final String password;

  const AddDevice({
    required this.username,
    required this.password,
  });
}
