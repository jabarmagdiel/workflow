import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:firebase_core/firebase_core.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_local_notifications/flutter_local_notifications.dart';
import 'package:provider/provider.dart';
import '../main.dart';
import '../providers/notification_provider.dart';
import 'api_service.dart';

class PushNotificationService {
  static final FirebaseMessaging _firebaseMessaging = FirebaseMessaging.instance;
  static final FlutterLocalNotificationsPlugin _localNotifications = FlutterLocalNotificationsPlugin();
  final ApiService _apiService = ApiService();

  static Future<void> initialize() async {
    // 1. Initialize local notifications (always do this first so it works without Firebase)
    try {
      const AndroidInitializationSettings initializationSettingsAndroid =
          AndroidInitializationSettings('@mipmap/ic_launcher');
      
      const InitializationSettings initializationSettings = InitializationSettings(
        android: initializationSettingsAndroid,
      );

      await _localNotifications.initialize(
        settings: initializationSettings,
        onDidReceiveNotificationResponse: (NotificationResponse response) {
          debugPrint("Notification tapped: ${response.payload}");
        },
      );

      const AndroidNotificationChannel channel = AndroidNotificationChannel(
        'high_importance_channel_v2',
        'High Importance Notifications',
        description: 'This channel is used for important notifications.',
        importance: Importance.max,
        playSound: true,
        enableVibration: true,
      );

      await _localNotifications
          .resolvePlatformSpecificImplementation<AndroidFlutterLocalNotificationsPlugin>()
          ?.createNotificationChannel(channel);
          
      // Request permission for Android 13+
      await _localNotifications
          .resolvePlatformSpecificImplementation<AndroidFlutterLocalNotificationsPlugin>()
          ?.requestNotificationsPermission();
          
      debugPrint('✅ Local Notifications inicializadas');
    } catch (e) {
      debugPrint('⚠️ Error inicializando Local Notifications: $e');
    }

    // 2. Initialize Firebase (might fail if config is missing)
    try {
      if (Firebase.apps.isEmpty) {
        await Firebase.initializeApp();
      }

      NotificationSettings settings = await _firebaseMessaging.requestPermission(
        alert: true,
        badge: true,
        sound: true,
      );

      if (settings.authorizationStatus == AuthorizationStatus.authorized) {
        debugPrint('✅ Permisos de Firebase concedidos');
      }
    } catch (e) {
      debugPrint('⚠️ Firebase no configurado: $e');
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
    try {
      // Add to internal notification list
      if (navigatorKey.currentContext != null) {
        Provider.of<NotificationProvider>(navigatorKey.currentContext!, listen: false)
            .addNotification(title, body);
      }

      const AndroidNotificationDetails androidPlatformChannelSpecifics =
          AndroidNotificationDetails(
        'high_importance_channel_v2',
        'High Importance Notifications',
        channelDescription: 'This channel is used for important notifications.',
        importance: Importance.max,
        priority: Priority.high,
        playSound: true,
        enableVibration: true,
        showWhen: true,
      );
      
      const NotificationDetails platformChannelSpecifics =
          NotificationDetails(android: androidPlatformChannelSpecifics);
      
      await _localNotifications.show(
        id: DateTime.now().millisecond % 100000,
        title: title,
        body: body,
        notificationDetails: platformChannelSpecifics,
      );
    } catch (e) {
      debugPrint('⚠️ Error mostrando notificación local: $e');
    }
  }
}
