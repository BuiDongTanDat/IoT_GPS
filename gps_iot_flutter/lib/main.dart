import 'package:flutter/material.dart';
import 'package:firebase_core/firebase_core.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'bloc/device_bloc.dart';
import 'bloc/device_event.dart';
import 'firebase_options.dart'; // Import the generated firebase_options.dart
import 'package:google_maps_flutter_android/google_maps_flutter_android.dart';
import 'package:google_maps_flutter_platform_interface/google_maps_flutter_platform_interface.dart';
import 'package:flutter/foundation.dart';

import 'package:gps_iot_flutter/page/LoginPage.dart';
import 'package:gps_iot_flutter/page/HomePage.dart';


final GlobalKey<ScaffoldMessengerState> scaffoldMessengerKey =
    GlobalKey<ScaffoldMessengerState>();


void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // Ép chế độ Hybrid Composition cho Android
  final GoogleMapsFlutterPlatform mapsImplementation = GoogleMapsFlutterPlatform.instance;
  if (mapsImplementation is GoogleMapsFlutterAndroid) {
    mapsImplementation.useAndroidViewSurface = true;
  }

  try {
    // Initialize Firebase
    await Firebase.initializeApp( options: DefaultFirebaseOptions.currentPlatform,);
    debugPrint('Firebase initialized successfully');

    // Retrieve userId from SharedPreferences
    SharedPreferences prefs = await SharedPreferences.getInstance();
    String? userId = prefs.getString('userId');

    runApp(MyApp(userId: userId));
  } catch (e) {
    debugPrint('Error initializing app: $e');
    runApp(const MyApp(userId: null)); // Fallback to LoginPage
  }
}


class MyApp extends StatelessWidget {
  final String? userId;

  const MyApp({Key? key, this.userId}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return MultiBlocProvider(
      providers: [
        BlocProvider<DeviceBloc>(  // Khởi tạo DeviceBloc và gọi sự kiện LoadDevices
          create: (context) => DeviceBloc()..add(LoadDevices()),
        ),
      ],
      child: MaterialApp(
        scaffoldMessengerKey: scaffoldMessengerKey,
        debugShowCheckedModeBanner: false,
        home: userId == null ? LoginPage() : HomePage(),
      ),
    );
  }
}
