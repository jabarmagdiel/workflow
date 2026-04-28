import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:firebase_core/firebase_core.dart';
import 'package:flutter/foundation.dart';
import 'api_service.dart';

class PushNotificationService {
  static final _firebaseMessaging = FirebaseMessaging.instance;
  final ApiService _apiService = ApiService();

  static Future<void> initialize() async {
    try {
      // Intentamos inicializar Firebase solo si es necesario (generalmente main.dart ya lo hace)
      // Pero para ser robustos, verificamos si ya está inicializado
      if (Firebase.apps.isEmpty) {
        await Firebase.initializeApp();
      }

      // Solicitar permisos
      NotificationSettings settings = await _firebaseMessaging.requestPermission(
        alert: true,
        badge: true,
        sound: true,
      );

      if (settings.authorizationStatus == AuthorizationStatus.authorized) {
        debugPrint('✅ Permisos de notificaciones concedidos');
      } else {
        debugPrint('❌ Permisos de notificaciones denegados');
      }
    } catch (e) {
      debugPrint('⚠️ Error inicializando Push Notifications: $e');
      rethrow; // Dejamos que main.dart maneje el error
    }
  }

  Future<void> setupInteractions() async {
    try {
      // Get initial token
      String? token = await _firebaseMessaging.getToken();
      debugPrint("FCM Token: $token");
      await _apiService.updateFcmToken(token);

      // Listen for environment refresh token
      FirebaseMessaging.instance.onTokenRefresh.listen((newToken) {
        _apiService.updateFcmToken(newToken);
      });

      // Handle messages when app is in foreground
      FirebaseMessaging.onMessage.listen((RemoteMessage message) {
        debugPrint('Mensaje en primer plano: ${message.data}');
      });

      // Handle messages when app is in background but opened via notification
      FirebaseMessaging.onMessageOpenedApp.listen((RemoteMessage message) {
        debugPrint('App abierta desde notificación: ${message.data}');
      });
    } catch (e) {
      debugPrint('⚠️ Error configurando interacciones de notificaciones: $e');
    }
  }
}
