//Hàm set màu text dựa vào khoảng cách
import 'package:flutter/material.dart';
import 'AppColor.dart';

Color getTextColorBasedOnDistance(double distance) { 
  print("Distance: $distance"); // Add this line for debugging
  if (distance < 5) {
    return AppColor.green; // Close distance
  } else if (distance < 10) {
    return AppColor.yellow; // Medium distance
  } else {
    return AppColor.red; // Far distance
  }
}


String displayDistance(double distance) {
 return "${distance.toStringAsFixed(4)} m"; // Format the distance to 2 decimal places
}