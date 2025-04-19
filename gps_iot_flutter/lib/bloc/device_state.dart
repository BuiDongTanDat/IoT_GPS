import 'package:equatable/equatable.dart';
import 'package:geolocator/geolocator.dart';
import '../model/DeviceIoT.dart';

class DeviceState extends Equatable {
  final List<DeviceIoT> devices;
  final Position? userLocation;
  final String? userAddress; // Add this field for storing the address

  const DeviceState({this.devices = const [], this.userLocation, this.userAddress});

  DeviceState copyWith({List<DeviceIoT>? devices, Position? userLocation, String? userAddress}) {
    return DeviceState(
      devices: devices ?? this.devices,
      userLocation: userLocation ?? this.userLocation,
      userAddress: userAddress ?? this.userAddress, // Update the address field  

    );
  }

  @override
  List<Object?> get props => [devices, userLocation, userAddress];
}
