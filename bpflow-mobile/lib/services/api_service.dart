import 'dart:convert';
import 'dart:io';
import 'package:http/http.dart' as http;
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:path_provider/path_provider.dart';

class ApiService {
  static const String baseUrl = "http://98.85.254.204/api";
  final storage = const FlutterSecureStorage();

  Future<Map<String, dynamic>?> login(String email, String password) async {
    final response = await http.post(
      Uri.parse("$baseUrl/auth/login"),
      headers: {"Content-Type": "application/json"},
      body: jsonEncode({"email": email, "password": password}),
    );

    if (response.statusCode == 200) {
      final data = jsonDecode(response.body);
      await storage.write(key: 'jwt', value: data['accessToken']);
      return data;
    }
    return null;
  }

  Future<List<dynamic>> getMyTasks() async {
    final token = await storage.read(key: 'jwt');
    final response = await http.get(
      Uri.parse("$baseUrl/tasks/my"),
      headers: {"Authorization": "Bearer $token"},
    );

    if (response.statusCode == 200) {
      return jsonDecode(response.body);
    }
    return [];
  }
  
  Future<void> logout() async {
    await storage.delete(key: 'jwt');
  }

  Future<void> updateFcmToken(String? fcmToken) async {
    final token = await storage.read(key: 'jwt');
    if (token == null || fcmToken == null) return;

    await http.patch(
      Uri.parse("$baseUrl/users/me/fcm-token"),
      headers: {
        "Authorization": "Bearer $token",
        "Content-Type": "application/json"
      },
      body: jsonEncode({"token": fcmToken}),
    );
  }

  Future<List<dynamic>> getMyInstances() async {
    final token = await storage.read(key: 'jwt');
    final response = await http.get(
      Uri.parse("$baseUrl/instances/my"),
      headers: {"Authorization": "Bearer $token"},
    );

    if (response.statusCode == 200) {
      return jsonDecode(response.body);
    }
    return [];
  }

  Future<String?> downloadInstancePdf(String instanceId, String filename) async {
    final token = await storage.read(key: 'jwt');
    final response = await http.get(
      Uri.parse("$baseUrl/instances/$instanceId/pdf"),
      headers: {"Authorization": "Bearer $token"},
    );

    if (response.statusCode == 200) {
      final dir = await getApplicationDocumentsDirectory();
      final file = File("${dir.path}/$filename");
      await file.writeAsBytes(response.bodyBytes);
      return file.path;
    }
    return null;
  }
}
