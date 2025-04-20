import 'package:flutter/material.dart';
import 'package:gps_iot_flutter/Helper.dart';
import '../AppColor.dart';
import '../model/DeviceIoT.dart';
import '../page/TrackingLocationPage.dart';

Widget DeviceCard(context, DeviceIoT device,
    {required double distance, required Function(bool) onTrackingChanged}) {
  return Card(
    shape: RoundedRectangleBorder(
      borderRadius: BorderRadius.circular(10),
    ),
    elevation: 2,
    margin: const EdgeInsets.symmetric(vertical: 5, horizontal: 20),
    color: AppColor.white,
    child: InkWell(
      onTap: () {
        debugPrint("Tapped on ${device.username}");
         Navigator.push(
    context,
    MaterialPageRoute(
      builder: (context) => TrackLocationPage(deviceId: device.id),
    ),
  );
      },
      splashColor: AppColor.blue.withOpacity(0.2),
      highlightColor: AppColor.blue.withOpacity(0.1),
      borderRadius: BorderRadius.circular(10),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const CircleAvatar(
                  radius: 20,
                  backgroundImage: AssetImage('assets/phone.png'),
                  backgroundColor: Colors.transparent,
                ),
                const SizedBox(width: 16),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        device.username,
                        style: TextStyle(
                          fontSize: 16,
                          fontWeight: FontWeight.bold,
                          color: AppColor.black,
                        ),
                      ),
                      const SizedBox(height: 4),
                      Text(
                        device.details,
                        style: TextStyle(
                          fontSize: 14,
                          color: AppColor.drakerGrey,
                        ),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ],
                  ),
                ),
                Text(
                  distance > 0
                      ? displayDistance(distance)
                      : '0 m',
                  style: TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.bold,
                    color: getTextColorBasedOnDistance(distance),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 16),
            Row(
              crossAxisAlignment: CrossAxisAlignment.center,
              children: [
                Expanded(
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(
                        Icons.location_on_outlined,
                        size: 16,
                        color: AppColor.drakerGrey,
                      ),
                      const SizedBox(width: 4),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Text('${device.latitude.toStringAsFixed(5)}, ${device.longitude.toStringAsFixed(5)}',
                              style: TextStyle(
                                fontSize: 12,
                                color: AppColor.drakerGrey,
                                fontStyle: FontStyle.italic,
                              ),
                              overflow: TextOverflow.ellipsis,
                              maxLines: 1,
                            ),
                            Text(
                              device.address ??
                                  '${device.latitude.toStringAsFixed(5)}, ${device.longitude.toStringAsFixed(5)}',
                              style: TextStyle(
                                fontSize: 12,
                                color: AppColor.drakerGrey,
                                fontStyle:FontStyle.italic,
                              ),
                              overflow: TextOverflow.ellipsis,
                              maxLines: 2,
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
                IconButton(
                  icon: Icon(
                    device.isTracking
                        ? Icons.notifications_active
                        : Icons.notifications_none,
                    color:
                        device.isTracking ? AppColor.blue : AppColor.drakerGrey,
                  ),
                  tooltip: device.isTracking
                      ? 'Tắt nhận thông báo'
                      : 'Bật nhận thông báo',
                  onPressed: () {
                    onTrackingChanged(!device.isTracking);
                  },
                  padding: EdgeInsets.zero,
                  constraints: const BoxConstraints(),
                ),
              ],
            )
          ],
        ),
      ),
    ),
  );
}