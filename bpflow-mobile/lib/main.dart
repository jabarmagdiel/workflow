import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'services/push_notification_service.dart';
import 'providers/auth_provider.dart';
import 'screens/login_screen.dart';
import 'screens/process_tracker_screen.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // FIX: Firebase initialization is optional — never crash the app if it fails
  try {
    // Only initialize Firebase if google-services.json is present
    await PushNotificationService.initialize();
  } catch (e) {
    debugPrint("⚠️ Firebase not configured (push notifications disabled): $e");
    // Continue without Firebase — core login+workflow still works
  }

  runApp(
    MultiProvider(
      providers: [
        ChangeNotifierProvider(create: (_) => AuthProvider()),
      ],
      child: const MyApp(),
    ),
  );
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'BPFlow Mobile',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.indigoAccent),
        useMaterial3: true,
      ),
      home: const RootScreen(),
    );
  }
}

class RootScreen extends StatelessWidget {
  const RootScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Consumer<AuthProvider>(
      builder: (context, auth, _) {
        if (auth.isAuthenticated) {
          return const ProcessTrackerScreen();
        } else {
          return const LoginScreen();
        }
      },
    );
  }
}
