import 'dart:convert';
import 'dart:io';
import 'package:http/http.dart' as http;
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:path_provider/path_provider.dart';

class ApiService {
  static String baseUrl = "http://98.85.254.204/api";
  static const Duration _timeout = Duration(seconds: 15);
  final storage = const FlutterSecureStorage();

  // ─── LOGIN ──────────────────────────────────────────────────────
  Future<Map<String, dynamic>?> login(String email, String password) async {
    try {
      final response = await http
          .post(
            Uri.parse("$baseUrl/auth/login"),
            headers: {"Content-Type": "application/json"},
            body: jsonEncode({"email": email, "password": password}),
          )
          .timeout(_timeout);

      if (response.statusCode == 200) {
        final data = jsonDecode(response.body);
        await storage.write(key: 'jwt', value: data['accessToken']);
        await storage.write(key: 'refreshToken', value: data['refreshToken']);
        return data;
      } else if (response.statusCode == 401 || response.statusCode == 400) {
        throw Exception("Credenciales incorrectas. Verifique su email y contraseña.");
      } else if (response.statusCode == 423) {
        throw Exception("Cuenta bloqueada temporalmente. Intente más tarde.");
      } else {
        throw Exception("Error del servidor (${response.statusCode}). Intente nuevamente.");
      }
    } on SocketException {
      throw Exception("Sin conexión. Verifique su red e intente de nuevo.");
    } on Exception {
      rethrow;
    }
  }

  // ─── MY TASKS ────────────────────────────────────────────────────
  Future<List<dynamic>> getMyTasks() async {
    final token = await storage.read(key: 'jwt');
    try {
      final response = await http
          .get(
            Uri.parse("$baseUrl/tasks/my"),
            headers: {"Authorization": "Bearer $token"},
          )
          .timeout(_timeout);

      if (response.statusCode == 200) {
        return jsonDecode(response.body);
      }
    } catch (_) {}
    return [];
  }

  // ─── LOGOUT ──────────────────────────────────────────────────────
  Future<void> logout() async {
    await storage.delete(key: 'jwt');
    await storage.delete(key: 'refreshToken');
  }

  // ─── FCM TOKEN ───────────────────────────────────────────────────
  Future<void> updateFcmToken(String? fcmToken) async {
    final token = await storage.read(key: 'jwt');
    if (token == null || fcmToken == null) return;

    try {
      await http
          .patch(
            Uri.parse("$baseUrl/users/me/fcm-token"),
            headers: {
              "Authorization": "Bearer $token",
              "Content-Type": "application/json"
            },
            body: jsonEncode({"token": fcmToken}),
          )
          .timeout(_timeout);
    } catch (_) {
      // FCM token update is non-critical, ignore errors
    }
  }

  // ─── MY INSTANCES ────────────────────────────────────────────────
  Future<List<dynamic>> getMyInstances() async {
    final token = await storage.read(key: 'jwt');
    try {
      final response = await http
          .get(
            Uri.parse("$baseUrl/instances/my"),
            headers: {"Authorization": "Bearer $token"},
          )
          .timeout(_timeout);

      if (response.statusCode == 200) {
        return jsonDecode(response.body);
      }
    } catch (_) {}
    return [];
  }

  // ─── DOWNLOAD PDF ────────────────────────────────────────────────
  Future<String?> downloadInstancePdf(String instanceId, String filename) async {
    final token = await storage.read(key: 'jwt');
    try {
      final response = await http
          .get(
            Uri.parse("$baseUrl/instances/$instanceId/pdf"),
            headers: {"Authorization": "Bearer $token"},
          )
          .timeout(const Duration(seconds: 30));

      if (response.statusCode == 200) {
        final dir = await getApplicationDocumentsDirectory();
        final file = File("${dir.path}/$filename");
        await file.writeAsBytes(response.bodyBytes);
        return file.path;
      }
    } catch (_) {}
    return null;
  }

  // ─── UPLOAD ATTACHMENT ───────────────────────────────────────────
  Future<bool> uploadTaskAttachment(String taskId, String filePath) async {
    final token = await storage.read(key: 'jwt');
    try {
      var request = http.MultipartRequest(
        'POST',
        Uri.parse("$baseUrl/tasks/$taskId/attachments"),
      );
      request.headers["Authorization"] = "Bearer $token";
      request.files.add(await http.MultipartFile.fromPath('file', filePath));

      var streamedResponse = await request.send().timeout(const Duration(seconds: 45));
      var response = await http.Response.fromStream(streamedResponse);

      return response.statusCode == 200;
    } catch (e) {
      debugPrint("Error uploading file: $e");
      return false;
    }
  }
}
