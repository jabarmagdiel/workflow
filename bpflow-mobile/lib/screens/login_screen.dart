import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../providers/auth_provider.dart';
import '../services/api_service.dart';

class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key});

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final _emailController = TextEditingController();
  final _passwordController = TextEditingController();
  bool _obscurePassword = true;

  // Debug settings
  int _logoTapCount = 0;
  final _apiController = TextEditingController(text: ApiService.baseUrl);

  void _handleLogin() async {
    final email = _emailController.text.trim();
    final password = _passwordController.text.trim();

    if (email.isEmpty || password.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text("Por favor ingrese su correo y contraseña."),
          backgroundColor: Colors.orange,
        ),
      );
      return;
    }

    final auth = Provider.of<AuthProvider>(context, listen: false);
    final success = await auth.login(email, password);

    if (!success && mounted) {
      final msg = auth.errorMessage ?? "Error al iniciar sesión. Verifique sus credenciales.";
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(msg),
          backgroundColor: Colors.redAccent,
          duration: const Duration(seconds: 4),
        ),
      );
    }
  }

  void _showDebugMenu() {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        backgroundColor: const Color(0xFF1E293B),
        title: const Text("Configuración de Servidor", style: TextStyle(color: Colors.white)),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Text("Cambiar la URL base de la API:", style: TextStyle(color: Colors.white70)),
            const SizedBox(height: 16),
            TextField(
              controller: _apiController,
              style: const TextStyle(color: Colors.white),
              decoration: const InputDecoration(
                labelText: "Base URL",
                labelStyle: TextStyle(color: Colors.indigoAccent),
                enabledBorder: UnderlineInputBorder(borderSide: BorderSide(color: Colors.white24)),
              ),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text("Cancelar", style: TextStyle(color: Colors.white54)),
          ),
          ElevatedButton(
            onPressed: () {
              ApiService.baseUrl = _apiController.text.trim();
              Navigator.pop(context);
              ScaffoldMessenger.of(context).showSnackBar(
                SnackBar(content: Text("URL actualizada a: ${ApiService.baseUrl}")),
              );
            },
            child: const Text("Guardar"),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Container(
        decoration: const BoxDecoration(
          gradient: LinearGradient(
            colors: [Color(0xFF0F172A), Color(0xFF1E293B)],
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
          ),
        ),
        child: SafeArea(
          child: Center(
            child: SingleChildScrollView(
              padding: const EdgeInsets.all(24.0),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  // Logo with secret tap for debug
                  GestureDetector(
                    onTap: () {
                      _logoTapCount++;
                      if (_logoTapCount >= 5) {
                        _logoTapCount = 0;
                        _showDebugMenu();
                      }
                    },
                    child: Container(
                      width: 90,
                      height: 90,
                      decoration: BoxDecoration(
                        color: Colors.indigoAccent.withOpacity(0.15),
                        shape: BoxShape.circle,
                        border: Border.all(color: Colors.indigoAccent.withOpacity(0.4), width: 2),
                      ),
                      child: const Icon(Icons.account_tree_outlined, size: 48, color: Colors.indigoAccent),
                    ),
                  ),
                  const SizedBox(height: 24),
                  const Text(
                    "BPFlow",
                    style: TextStyle(
                      fontSize: 32,
                      fontWeight: FontWeight.bold,
                      color: Colors.white,
                      letterSpacing: 2,
                    ),
                  ),
                  const Text(
                    "Gestión de Procesos Inteligentes",
                    style: TextStyle(color: Colors.white70),
                  ),
                  const SizedBox(height: 48),

                  Container(
                    padding: const EdgeInsets.all(20),
                    decoration: BoxDecoration(
                      color: Colors.white.withOpacity(0.05),
                      borderRadius: BorderRadius.circular(24),
                      border: Border.all(color: Colors.white10),
                    ),
                    child: Column(
                      children: [
                        TextField(
                          controller: _emailController,
                          keyboardType: TextInputType.emailAddress,
                          autocorrect: false,
                          style: const TextStyle(color: Colors.white),
                          decoration: InputDecoration(
                            hintText: "Correo electrónico",
                            hintStyle: const TextStyle(color: Colors.white38),
                            prefixIcon: const Icon(Icons.email_outlined, color: Colors.indigoAccent),
                            filled: true,
                            fillColor: Colors.white.withOpacity(0.05),
                            border: OutlineInputBorder(
                              borderRadius: BorderRadius.circular(16),
                              borderSide: BorderSide.none,
                            ),
                          ),
                        ),
                        const SizedBox(height: 16),
                        TextField(
                          controller: _passwordController,
                          obscureText: _obscurePassword,
                          style: const TextStyle(color: Colors.white),
                          decoration: InputDecoration(
                            hintText: "Contraseña",
                            hintStyle: const TextStyle(color: Colors.white38),
                            prefixIcon: const Icon(Icons.lock_outline, color: Colors.indigoAccent),
                            suffixIcon: IconButton(
                              icon: Icon(
                                _obscurePassword ? Icons.visibility_off : Icons.visibility,
                                color: Colors.white38,
                              ),
                              onPressed: () => setState(() => _obscurePassword = !_obscurePassword),
                            ),
                            filled: true,
                            fillColor: Colors.white.withOpacity(0.05),
                            border: OutlineInputBorder(
                              borderRadius: BorderRadius.circular(16),
                              borderSide: BorderSide.none,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),

                  const SizedBox(height: 32),

                  Consumer<AuthProvider>(
                    builder: (context, auth, _) {
                      return auth.isLoading
                          ? Column(
                              children: [
                                const CircularProgressIndicator(color: Colors.indigoAccent),
                                const SizedBox(height: 12),
                                const Text("Conectando...", style: TextStyle(color: Colors.white54)),
                              ],
                            )
                          : SizedBox(
                              width: double.infinity,
                              height: 56,
                              child: ElevatedButton(
                                onPressed: _handleLogin,
                                style: ElevatedButton.styleFrom(
                                  backgroundColor: Colors.indigoAccent,
                                  foregroundColor: Colors.white,
                                  shape: RoundedRectangleBorder(
                                    borderRadius: BorderRadius.circular(16),
                                  ),
                                  elevation: 0,
                                ),
                                child: const Text(
                                  "Iniciar Sesión",
                                  style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
                                ),
                              ),
                            );
                    },
                  ),

                  const SizedBox(height: 20),

                  Text(
                    "Toca 5 veces el logo para configurar servidor",
                    style: TextStyle(color: Colors.white.withOpacity(0.2), fontSize: 10),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
