import 'package:flutter/material.dart';

class BPNotification {
  final String title;
  final String body;
  final DateTime timestamp;
  bool isRead;

  BPNotification({
    required this.title,
    required this.body,
    required this.timestamp,
    this.isRead = false,
  });
}

class NotificationProvider with ChangeNotifier {
  final List<BPNotification> _notifications = [];

  List<BPNotification> get notifications => List.unmodifiable(_notifications);
  
  int get unreadCount => _notifications.where((n) => !n.isRead).length;

  void addNotification(String title, String body) {
    _notifications.insert(0, BPNotification(
      title: title,
      body: body,
      timestamp: DateTime.now(),
    ));
    notifyListeners();
  }

  void markAsRead(int index) {
    _notifications[index].isRead = true;
    notifyListeners();
  }

  void clearAll() {
    _notifications.clear();
    notifyListeners();
  }
}
