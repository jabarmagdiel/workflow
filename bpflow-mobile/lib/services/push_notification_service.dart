import 'package:firebase_messaging/firebase_messaging.dart';
import 'api_service.dart';

class PushNotificationService {
  static final FirebaseMessaging _firebaseMessaging = FirebaseMessaging.instance;
  final ApiService _apiService = ApiService();

  static Future<void> initialize() async {
    // Solicitar permisos en iOS
    NotificationSettings settings = await _firebaseMessaging.requestPermission(
      alert: true,
      badge: true,
      sound: true,
    );

    if (settings.authorizationStatus == AuthorizationStatus.authorized) {
      print('✅ User granted permission');
    } else {
      print('❌ User declined or has not accepted permission');
    }
  }

  Future<void> setupInteractions() async {
    // Get initial token
    String? token = await _firebaseMessaging.getToken();
    print("FCM Token: $token");
    await _apiService.updateFcmToken(token);

    // Listen for environment refresh token
    FirebaseMessaging.instance.onTokenRefresh.listen((newToken) {
      _apiService.updateFcmToken(newToken);
    });

    // Handle messages when app is in foreground
    FirebaseMessaging.onMessage.listen((RemoteMessage message) {
      print('Message data: ${message.data}');
      if (message.notification != null) {
        print('Message also contained a notification: ${message.notification}');
      }
    });

    // Handle messages when app is in background but opened via notification
    FirebaseMessaging.onMessageOpenedApp.listen((RemoteMessage message) {
      print('App opened from notification: ${message.data}');
    });
  }
}
