import 'dart:convert';
import 'package:http/http.dart' as http;
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

class ApiService {
  static const String baseUrl = "http://localhost:8080/api"; // Cambiar a IP servidor en dispositivo real
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
}
