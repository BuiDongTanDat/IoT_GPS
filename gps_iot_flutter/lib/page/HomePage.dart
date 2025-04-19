import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:geolocator/geolocator.dart';
import 'package:gps_iot_flutter/AppColor.dart';
import '../bloc/device_bloc.dart';
import '../bloc/device_event.dart';
import '../bloc/device_state.dart';
import '../service/LocationService.dart';
import '../widgets/DeviceCard.dart';

class HomePage extends StatefulWidget {
  const HomePage({super.key});

  @override
  _HomePageState createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  late DeviceBloc _deviceBloc;
  String _currentLocation = "Đang lấy vị trí...";

  @override
  void initState() {
    super.initState();
    _deviceBloc = context.read<DeviceBloc>();
    _deviceBloc.add(LoadDevices());

    // Start location service
    LocationService().startLocationService(_deviceBloc);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.grey[200],
      body: SafeArea(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Image displayed below the AppBar
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 10),
              color: Colors.white,
              child: Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  const Text(
                    'Trang chủ',
                    style: TextStyle(
                      fontSize: 18,
                      fontWeight: FontWeight.bold,
                      color: AppColor.black,
                    ),
                  ),
                  Spacer(),
                  IconButton(
                    icon: const Icon(Icons.phone_android, color: AppColor.blue),
                    onPressed: () {
                      // Handle notification button press
                      debugPrint(
                        "Add device button pressed",
                      );
                      _showAddDeviceDialog(context);
                    },
                  ),
                  IconButton(
                    icon: const Icon(Icons.remove_circle, color: Colors.red),
                    onPressed: () {
                      // Handle notification button press
                      debugPrint("Stop service button pressed");
                    },
                  ),
                ],
              ),
            ),
            BlocBuilder<DeviceBloc, DeviceState>(
              builder: (context, state) {
                final position = state.userLocation;
                if (position != null) {
                  // Check if address is available
                  final address = state.userAddress ?? "Đang lấy địa chỉ...";

                  return Container(
                    width: double.infinity,
                    margin: const EdgeInsets.all(10),
                    padding: const EdgeInsets.symmetric(
                        horizontal: 10, vertical: 10),
                    decoration: BoxDecoration(
                      color: AppColor.purple,
                      borderRadius: BorderRadius.all(
                        Radius.circular(10),
                      ),
                    ),
                    child: Column(
                      mainAxisAlignment:
                          MainAxisAlignment.center, // Align content vertically
                      crossAxisAlignment: CrossAxisAlignment
                          .start, // Align content horizontally
                      children: [
                        Text(
                          'VỊ TRÍ CỦA BẠN',
                          textAlign: TextAlign.center,
                          style: const TextStyle(color: Colors.white),
                        ),
                        Text(
                          'Tọa độ: ${position.latitude}, ${position.longitude}',
                          textAlign: TextAlign.center,
                          style: const TextStyle(
                              color: Colors.white, fontSize: 12),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          'Địa chỉ: $address',
                          textAlign: TextAlign.justify,
                          style: const TextStyle(
                              color: Colors.white, fontSize: 12),
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ],
                    ),
                  );
                } else {
                  return Container(
                    width: double.infinity,
                    margin: const EdgeInsets.all(10),
                    padding: const EdgeInsets.symmetric(
                        horizontal: 10, vertical: 10),
                    decoration: BoxDecoration(
                      color: AppColor.purple,
                      borderRadius: BorderRadius.all(
                        Radius.circular(10),
                      ),
                    ),
                    child: Column(
                      mainAxisAlignment:
                          MainAxisAlignment.center, // Align content vertically
                      crossAxisAlignment: CrossAxisAlignment
                          .start, // Align content horizontally
                      children: [
                        Text(
                          'VỊ TRÍ CỦA BẠN',
                          textAlign: TextAlign.center,
                          style: const TextStyle(color: Colors.white),
                        ),
                        Text(
                          'Tọa độ: Đang lấy tọa độ...',
                          textAlign: TextAlign.center,
                          style: const TextStyle(
                              color: Colors.white, fontSize: 12),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          'Địa chỉ: Đang lấy địa chỉ...',
                          textAlign: TextAlign.center,
                          style: const TextStyle(
                              color: Colors.white, fontSize: 12),
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ],
                    ),
                  );
                }
              },
            ),
            const SizedBox(height: 20),

            Padding(
              padding: const EdgeInsets.only(left: 20),
              child: const Text(
                'DANH SÁCH THIẾT BỊ',
                textAlign: TextAlign.left,
                style: TextStyle(
                  fontSize: 16,
                  fontWeight: FontWeight.bold,
                  color: AppColor.blue,
                ),
              ),
            ),

            // List of devices
            Expanded(
              child: BlocBuilder<DeviceBloc, DeviceState>(
                builder: (context, state) {
                  final devices = state.devices;

                  // Check if no devices are available
                  if (devices.isEmpty) {
                    return const Center(child: Text("Không có thiết bị nào."));
                  }

                  return ListView.builder(
                    padding:
                        const EdgeInsets.symmetric(horizontal: 0, vertical: 5),
                    itemCount: devices.length,
                    itemBuilder: (context, index) {
                      final device = devices[index];

                      // Distance already calculated in DeviceIoT
                      double distance = device.distance ?? 0;

                      return DeviceCard(
                        context,
                        device,
                        distance:
                            distance / 1000, // Convert meters to kilometers
                        onTrackingChanged: (isTracking) {
                          _deviceBloc.add(UpdateTrackingStatus(
                            deviceId: device.id,
                            isTracking: isTracking,
                          ));
                        },
                      );
                    },
                  );
                },
              ),
            ),
          ],
        ),
      ),
    );
  }

  void _showAddDeviceDialog(BuildContext context) {
    final TextEditingController usernameController = TextEditingController();
    final TextEditingController passwordController = TextEditingController();

    showDialog(
      context: context,
      builder: (context) {
        return AlertDialog(
          title: const Text("Thêm thiết bị"),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextField(
                controller: usernameController,
                decoration: const InputDecoration(labelText: "ID thiết bị"),
              ),
              TextField(
                controller: passwordController,
                decoration: const InputDecoration(labelText: "Mật khẩu"),
                obscureText: true,
              ),
            ],
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(context).pop(),
              child: const Text("Hủy"),
            ),
            TextButton(
              onPressed: () {
                final username = usernameController.text.trim();
                final password = passwordController.text.trim();

                if (username.isNotEmpty && password.isNotEmpty) {
                  _deviceBloc.add(AddDevice(
                    username: username,
                    password: password,
                  ));
                  Navigator.of(context).pop();
                }
              },
              child: const Text("Thêm"),
            ),
          ],
        );
      },
    );
  }
}
