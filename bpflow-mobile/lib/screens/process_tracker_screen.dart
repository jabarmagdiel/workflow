import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../providers/auth_provider.dart';
import '../services/api_service.dart';
import 'package:intl/intl.dart';
import '../services/push_notification_service.dart';
import 'process_detail_screen.dart';

class ProcessTrackerScreen extends StatefulWidget {
  const ProcessTrackerScreen({super.key});

  @override
  State<ProcessTrackerScreen> createState() => _ProcessTrackerScreenState();
}

class _ProcessTrackerScreenState extends State<ProcessTrackerScreen> {
  final ApiService _apiService = ApiService();
  List<dynamic> _instances = [];
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _fetchInstances();
  }

  Future<void> _fetchInstances() async {
    setState(() => _loading = true);
    try {
      final data = await _apiService.getMyInstances();
      setState(() {
        _instances = data;
        _loading = false;
      });
    } catch (e) {
      setState(() => _loading = false);
    }
  }

  Color _getStatusColor(String status) {
    switch (status) {
      case 'COMPLETED': return Colors.greenAccent;
      case 'RUNNING': return Colors.blueAccent;
      case 'CANCELLED': return Colors.redAccent;
      case 'ERROR': return Colors.orangeAccent;
      default: return Colors.grey;
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF0F172A),
      appBar: AppBar(
        title: const Text("Mis Trámites", style: TextStyle(color: Colors.white, fontWeight: FontWeight.bold)),
        backgroundColor: Colors.transparent,
        elevation: 0,
        actions: [
          IconButton(
            icon: const Icon(Icons.notifications_active_outlined, color: Colors.amberAccent),
            tooltip: "Probar Notificación",
            onPressed: () {
              PushNotificationService.showLocalNotification(
                title: "BPFlow: Prueba de Sonido",
                body: "¡Las notificaciones están funcionando correctamente! 🚀"
              );
            },
          ),
          IconButton(
            icon: const Icon(Icons.refresh, color: Colors.white),
            onPressed: _fetchInstances,
          ),
          IconButton(
...
            icon: const Icon(Icons.logout, color: Colors.white70),
            onPressed: () {
              Provider.of<AuthProvider>(context, listen: false).logout();
            },
          )
        ],
      ),
      body: RefreshIndicator(
        onRefresh: _fetchInstances,
        color: Colors.indigoAccent,
        child: _loading
            ? const Center(child: CircularProgressIndicator(color: Colors.indigoAccent))
            : _instances.isEmpty
                ? const Center(
                    child: SingleChildScrollView(
                      physics: AlwaysScrollableScrollPhysics(),
                      child: Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          Icon(Icons.assignment_late_outlined, size: 60, color: Colors.white24),
                          SizedBox(height: 16),
                          Text("No tienes trámites activos", style: TextStyle(color: Colors.white70)),
                          Text("Desliza hacia abajo para actualizar", style: TextStyle(color: Colors.white24, fontSize: 12)),
                        ],
                      ),
                    ),
                  )
                : ListView.builder(
                    physics: const AlwaysScrollableScrollPhysics(),
                  padding: const EdgeInsets.all(16),
                  itemCount: _instances.length,
                  itemBuilder: (context, index) {
                    final instance = _instances[index];
                    final history = instance['history'] as List<dynamic>? ?? [];
                    final lastStep = history.isNotEmpty ? history.last : null;
                    
                    // Priorizar el nombre del nodo actual del backend
                    final department = instance['currentNodeName'] ?? (lastStep != null ? lastStep['nodeName'] : "Iniciando...");
                    
                    return InkWell(
                      onTap: () {
                        Navigator.push(
                          context,
                          MaterialPageRoute(builder: (context) => ProcessDetailScreen(instance: instance)),
                        );
                      },
                      child: Card(
                        color: Colors.white.withOpacity(0.05),
                        margin: const EdgeInsets.only(bottom: 16),
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(20),
                          side: const BorderSide(color: Colors.white10),
                        ),
                        child: Padding(
                          padding: const EdgeInsets.all(20),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Row(
                                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                children: [
                                  Text(
                                    "#${instance['referenceNumber'] ?? 'S/N'}",
                                    style: const TextStyle(color: Colors.indigoAccent, fontWeight: FontWeight.bold, fontSize: 16),
                                  ),
                                  Container(
                                    padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
                                    decoration: BoxDecoration(
                                      color: _getStatusColor(instance['status']).withOpacity(0.2),
                                      borderRadius: BorderRadius.circular(12),
                                    ),
                                    child: Text(
                                      instance['status'],
                                      style: TextStyle(color: _getStatusColor(instance['status']), fontSize: 12, fontWeight: FontWeight.bold),
                                    ),
                                  ),
                                ],
                              ),
                              const SizedBox(height: 12),
                              Text(
                                instance['workflowName'] ?? "Proceso Desconocido",
                                style: const TextStyle(color: Colors.white, fontSize: 18, fontWeight: FontWeight.bold),
                              ),
                              const SizedBox(height: 8),
                                Row(
                                  children: [
                                    const Icon(Icons.person_outline, size: 16, color: Colors.white54),
                                    const SizedBox(width: 4),
                                    Text(
                                      "Cliente: ${instance['clientName'] ?? 'No asignado'}",
                                      style: const TextStyle(color: Colors.white70, fontSize: 14),
                                    ),
                                  ],
                                ),
                                const SizedBox(height: 4),
                                Row(
                                  children: [
                                    const Icon(Icons.location_on_outlined, size: 16, color: Colors.white54),
                                    const SizedBox(width: 4),
                                    Text(
                                      "Ubicación actual: $department",
                                      style: const TextStyle(color: Colors.white70, fontSize: 14),
                                    ),
                                  ],
                                ),
                              const Divider(color: Colors.white10, height: 24),
                              Row(
                                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                children: [
                                  Text(
                                    "Iniciado: ${DateFormat('dd/MM/yyyy').format(DateTime.parse(instance['createdAt']))}",
                                    style: const TextStyle(color: Colors.white38, fontSize: 12),
                                  ),
                                  const Text("Ver Detalles", style: TextStyle(color: Colors.indigoAccent, fontWeight: FontWeight.bold))
                                ],
                              ),
                            ],
                          ),
                        ),
                      ),
                    );
                  },
                ),
      ),
    );
  }
}
