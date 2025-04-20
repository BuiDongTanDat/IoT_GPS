import 'dart:async';
import 'dart:math';
import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:google_maps_flutter/google_maps_flutter.dart';
import 'package:gps_iot_flutter/Helper.dart';

import '../bloc/device_bloc.dart';
import '../bloc/device_state.dart';
import '../model/DeviceIoT.dart';

class TrackLocationPage extends StatefulWidget {
  final String deviceId;

  const TrackLocationPage({Key? key, required this.deviceId}) : super(key: key);

  @override
  State<TrackLocationPage> createState() => _TrackLocationPageState();
}

class _TrackLocationPageState extends State<TrackLocationPage> {
  GoogleMapController? _mapController;
  final Map<String, Marker> _markers = {};
  StreamSubscription? _locationSubscription;
  LatLng _initialTarget =
      const LatLng(10.762622, 106.660172); // Default location (Saigon)
  final Set<Polyline> _polylines = {};

  @override
  void initState() {
    super.initState();
    _setInitialTarget();
    _startLocationStream();

    _handleDeviceState(context.read<DeviceBloc>().state);
  }
  

  @override
  void dispose() {
    _locationSubscription?.cancel();
    super.dispose();
  }

  void _setInitialTarget() {
    final deviceBloc = context.read<DeviceBloc>();
    final userLocation = deviceBloc.state.userLocation;

    if (userLocation != null) {
      _initialTarget = LatLng(userLocation.latitude, userLocation.longitude);
      _addUserMarker(userLocation.latitude, userLocation.longitude);
    }
  }

  void _startLocationStream() {
    final deviceBloc = context.read<DeviceBloc>();

    _locationSubscription = deviceBloc.stream.listen((state) {
      _handleDeviceState(state);
    });
  }

  void _handleDeviceState(DeviceState state) {
    final device = state.devices.firstWhere(
      (d) => d.id == widget.deviceId,
      orElse: () => DeviceIoT.empty(),
    );
    final userLoc = state.userLocation;

    if (device.latitude != 0 && device.longitude != 0) {
      final devicePosition = LatLng(device.latitude, device.longitude);
      _markers['device'] = Marker(
        markerId: const MarkerId('device'),
        position: devicePosition,
        infoWindow: InfoWindow(title: 'Thiết bị: ${device.username}'),
      );
    }

    if (userLoc != null) {
      _addUserMarker(userLoc.latitude, userLoc.longitude);

      //Thêm đường thẳng từ user đến device
      _addPolylineToDevice(userLoc, device);
    }

    setState(() {});
  }

  void _addPolylineToDevice(user, DeviceIoT device) {
    if (device.latitude == 0 || device.longitude == 0) return;

    final userPosition = LatLng(user.latitude, user.longitude);
    final devicePosition = LatLng(device.latitude, device.longitude);

    final polyline = Polyline(
      polylineId: const PolylineId('line_user_device'),
      points: [userPosition, devicePosition],
      color: Colors.green,
      width: 4,
    );

    _polylines.clear();
    _polylines.add(polyline);
  }

  void _addUserMarker(double latitude, double longitude) {
    final userPosition = LatLng(latitude, longitude);

    _markers['user'] = Marker(
      markerId: const MarkerId('user'),
      position: userPosition,
      infoWindow: const InfoWindow(title: 'Vị trí của bạn'),
      icon: BitmapDescriptor.defaultMarkerWithHue(BitmapDescriptor.hueBlue),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.white,
      body: SafeArea(
        child: Column(
          children: [
            // App Bar
            Padding(
              padding: const EdgeInsets.only(
                top: 10,
                left: 10,
                right: 10,
                bottom: 0,
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      IconButton(
                        icon: const Icon(Icons.arrow_back_ios_new),
                        onPressed: () => Navigator.pop(context),
                      ),
                      const SizedBox(width: 8),
                      const Expanded(
                        child: Text(
                          'Tracking',
                          style: TextStyle(
                            fontSize: 20,
                            fontWeight: FontWeight.bold,
                            color: Colors.black,
                          ),
                        ),
                      ),
                      BlocBuilder<DeviceBloc, DeviceState>(
                    builder: (context, state) {
                      final device = state.devices.firstWhere(
                        (d) => d.id == widget.deviceId,
                        orElse: () => DeviceIoT.empty(),
                      );
                      final userLoc = state.userLocation;

                      if (device.latitude != 0 &&
                          device.longitude != 0 &&
                          userLoc != null) {
                        final distance = device.calculateDistance(
                          userLoc.latitude,
                          userLoc.longitude,
                          
                        );
                        return Padding(
                          padding: const EdgeInsets.only(left: 0),
                          child: Text(
                            displayDistance(distance/1000),
                            style: TextStyle(
                              color: getTextColorBasedOnDistance(distance),
                              fontSize: 14,
                            ),
                          ),
                        );
                      } else {
                        return const SizedBox.shrink();
                      }
                    },
                  ),
                    ],
                  ),
                  
                ],
              ),
            ),

            BlocBuilder<DeviceBloc, DeviceState>(
              builder: (context, state) {
                final device = state.devices.firstWhere(
                  (d) => d.id == widget.deviceId,
                  orElse: () => DeviceIoT.empty(),
                );
                return _addressContainer(
                  "Vị trí thiết bị: ${device.username}",
                  "${device.latitude}, ${device.longitude}",
                  device.address ?? "Đang lấy địa chỉ...",
                  onTap: () {
                    if (device.latitude != 0 && device.longitude != 0) {
                      _mapController?.animateCamera(CameraUpdate.newLatLng(
                        LatLng(device.latitude, device.longitude),
                      ));
                    }
                  },
                );
              },
            ),

            // Google Map
            Expanded(
              child: GoogleMap(
                initialCameraPosition: CameraPosition(
                  target: _initialTarget,
                  zoom: 15.0,
                ),
                onMapCreated: (controller) => _mapController = controller,
                markers: Set<Marker>.of(_markers.values),
                polylines: _polylines,
                myLocationEnabled: true,
                myLocationButtonEnabled: true,
                zoomControlsEnabled: false,
              ),
            ),
            BlocBuilder<DeviceBloc, DeviceState>(
              builder: (context, state) {
                return _addressContainer(
                  "Vị trí của bạn",
                  "${state.userLocation?.latitude}, ${state.userLocation?.longitude}",
                  state.userAddress ?? "Đang lấy địa chỉ...",
                  onTap: () {
                    final userLoc = state.userLocation;
                    if (userLoc != null) {
                      _mapController?.animateCamera(CameraUpdate.newLatLng(
                        LatLng(userLoc.latitude, userLoc.longitude),
                      ));
                    }
                  },
                );
              },
            ),
          ],
        ),
      ),
    );
  }

  Widget _addressContainer(String title, String location, String address,
      {VoidCallback? onTap}) {
    return Padding(
      padding: EdgeInsets.zero,
      child: Material(
        color: Colors.grey[100],
        borderRadius: BorderRadius.circular(8),
        child: InkWell(
          onTap: onTap,
          borderRadius: BorderRadius.circular(8),
          child: Container(
            padding: const EdgeInsets.all(12),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  style: const TextStyle(
                    fontSize: 14,
                    fontWeight: FontWeight.bold,
                    color: Colors.black87,
                  ),
                ),
                Text(
                  ' - $location',
                  style: const TextStyle(
                    color: Colors.grey,
                  ),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
                const SizedBox(height: 2),
                Text(
                  'Địa chỉ: $address',
                  style: const TextStyle(
                    fontSize: 12,
                    color: Colors.black87,
                  ),
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
