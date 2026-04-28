import 'package:flutter/material.dart';
import '../services/api_service.dart';
import '../services/push_notification_service.dart';

class AuthProvider extends ChangeNotifier {
  final ApiService _apiService = ApiService();
  final PushNotificationService _pushService = PushNotificationService();

  bool _isAuthenticated = false;
  bool _isLoading = false;
  String? _errorMessage;
  Map<String, dynamic>? _user;

  bool get isAuthenticated => _isAuthenticated;
  bool get isLoading => _isLoading;
  String? get errorMessage => _errorMessage;
  Map<String, dynamic>? get user => _user;

  Future<bool> login(String email, String password) async {
    _isLoading = true;
    _errorMessage = null;
    notifyListeners();

    try {
      final response = await _apiService.login(email, password);

      if (response != null) {
        _user = response['user'];
        _isAuthenticated = true;
        _isLoading = false;
        notifyListeners();

        // FIX: Setup push notifications in background - never block login
        _pushService.setupInteractions().catchError((e) {
          debugPrint("⚠️ Push notification setup failed (non-critical): $e");
        });

        return true;
      }
    } catch (e) {
      // Extract clean error message
      _errorMessage = e.toString().replaceFirst("Exception: ", "");
    }

    _isLoading = false;
    notifyListeners();
    return false;
  }

  Future<void> logout() async {
    await _apiService.logout();
    _isAuthenticated = false;
    _user = null;
    _errorMessage = null;
    notifyListeners();
  }
}
