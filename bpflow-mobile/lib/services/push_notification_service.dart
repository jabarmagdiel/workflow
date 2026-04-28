import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:firebase_core/firebase_core.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_local_notifications/flutter_local_notifications.dart';
import 'api_service.dart';

class PushNotificationService {
  static final _firebaseMessaging = FirebaseMessaging.instance;
  static final _localNotifications = FlutterLocalNotificationsPlugin();
  final ApiService _apiService = ApiService();

  static Future<void> initialize() async {
    try {
      if (Firebase.apps.isEmpty) {
        await Firebase.initializeApp();
      }

      // Initialize local notifications
      const AndroidInitializationSettings initializationSettingsAndroid =
          AndroidInitializationSettings('@mipmap/ic_launcher');
      const InitializationSettings initializationSettings = InitializationSettings(
        android: initializationSettingsAndroid,
      );
      await _localNotifications.initialize(initializationSettings);

      // Notification permissions for Firebase
      NotificationSettings settings = await _firebaseMessaging.requestPermission(
        alert: true,
        badge: true,
        sound: true,
      );

      if (settings.authorizationStatus == AuthorizationStatus.authorized) {
        debugPrint('✅ Permisos de notificaciones concedidos');
      }
    } catch (e) {
      debugPrint('⚠️ Error inicializando Push Notifications: $e');
      rethrow;
    }
  }

  Future<void> setupInteractions() async {
    try {
      String? token = await _firebaseMessaging.getToken();
      debugPrint("FCM Token: $token");
      await _apiService.updateFcmToken(token);

      FirebaseMessaging.instance.onTokenRefresh.listen((newToken) {
        _apiService.updateFcmToken(newToken);
      });

      FirebaseMessaging.onMessage.listen((RemoteMessage message) {
        debugPrint('Mensaje en primer plano: ${message.data}');
        if (message.notification != null) {
          showLocalNotification(
            title: message.notification?.title ?? "BPFlow",
            body: message.notification?.body ?? "Nuevo mensaje",
          );
        }
      });
    } catch (e) {
      debugPrint('⚠️ Error configurando interacciones de notificaciones: $e');
    }
  }

  static Future<void> showLocalNotification({required String title, required String body}) async {
    const AndroidNotificationDetails androidPlatformChannelSpecifics =
        AndroidNotificationDetails(
      'bpflow_channel_id',
      'BPFlow Notifications',
      channelDescription: 'Canal principal de notificaciones de BPFlow',
      importance: Importance.max,
      priority: Priority.high,
      showWhen: true,
      enableVibration: true,
      playSound: true,
      // Sound: default sound for now
    );
    const NotificationDetails platformChannelSpecifics =
        NotificationDetails(android: androidPlatformChannelSpecifics);
    
    await _localNotifications.show(
      DateTime.now().millisecond,
      title,
      body,
      platformChannelSpecifics,
    );
  }
}
