importScripts('https://www.gstatic.com/firebasejs/9.17.1/firebase-app-compat.js');
importScripts('https://www.gstatic.com/firebasejs/9.17.1/firebase-messaging-compat.js');

firebase.initializeApp({
  apiKey: "AIzaSyDY6fAQYd6AhhWCliN3lzDPjyyxDxfqGSE",
  authDomain: "iotgps-c3464.firebaseapp.com",
  projectId: "iotgps-c3464",
  storageBucket: "iotgps-c3464.firebasestorage.app",
  messagingSenderId: "99667928892",
  appId: "1:99667928892:web:716e0ed111e83af4ace039"
});


const messaging = firebase.messaging();

messaging.onBackgroundMessage(function(payload) {
  console.log('[firebase-messaging-sw.js] Received background message ', payload);
  const notificationTitle = payload.notification.title;
  const notificationOptions = {
    body: payload.notification.body,
  };

  self.registration.showNotification(notificationTitle, notificationOptions);
});
