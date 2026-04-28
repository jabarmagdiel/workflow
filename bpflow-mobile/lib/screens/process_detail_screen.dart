import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:open_filex/open_filex.dart';
import 'package:file_picker/file_picker.dart';
import '../services/api_service.dart';

class ProcessDetailScreen extends StatefulWidget {
  final dynamic instance;
  const ProcessDetailScreen({super.key, required this.instance});

  @override
  State<ProcessDetailScreen> createState() => _ProcessDetailScreenState();
}

class _ProcessDetailScreenState extends State<ProcessDetailScreen> {
  final ApiService _apiService = ApiService();
  bool _isDownloading = false;

  void _downloadPdf() async {
    setState(() => _isDownloading = true);
    final filename = "audit-${widget.instance['referenceNumber']}.pdf";
    final path = await _apiService.downloadInstancePdf(widget.instance['id'], filename);
    setState(() => _isDownloading = false);

    if (path != null && mounted) {
      await OpenFilex.open(path);
    } else if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text("Error al descargar el reporte PDF")),
      );
    }
  }

  bool _isUploading = false;
  void _uploadDocument() async {
    FilePickerResult? result = await FilePicker.platform.pickFiles(
      type: FileType.custom,
      allowedExtensions: ['pdf', 'jpg', 'png', 'jpeg'],
    );

    if (result != null && mounted) {
      setState(() => _isUploading = true);
      try {
        final tasks = await _apiService.getMyTasks();
        final task = tasks.firstWhere(
          (t) => t['instanceId'] == widget.instance['id'],
          orElse: () => null,
        );

        if (task != null) {
          final success = await _apiService.uploadTaskAttachment(task['id'], result.files.single.path!);
          if (mounted) {
            ScaffoldMessenger.of(context).showSnackBar(
              SnackBar(content: Text(success ? "Documento subido con éxito" : "Error al subir documento")),
            );
          }
        } else {
           if (mounted) {
             ScaffoldMessenger.of(context).showSnackBar(
               const SnackBar(content: Text("No hay tareas activas disponibles para subir")),
             );
           }
        }
      } catch (e) {
        if (mounted) {
           ScaffoldMessenger.of(context).showSnackBar(
             SnackBar(content: Text("Error: $e")),
           );
        }
      } finally {
        if (mounted) setState(() => _isUploading = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final history = widget.instance['history'] as List<dynamic>? ?? [];

    return Scaffold(
      backgroundColor: const Color(0xFF0F172A),
      appBar: AppBar(
        title: Text("Detalle #${widget.instance['referenceNumber']}"),
        backgroundColor: Colors.transparent,
        elevation: 0,
        foregroundColor: Colors.white,
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Header Card
            Container(
              padding: const EdgeInsets.all(20),
              decoration: BoxDecoration(
                gradient: const LinearGradient(
                  colors: [Colors.indigoAccent, Colors.blueAccent],
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                ),
                borderRadius: BorderRadius.circular(24),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    widget.instance['workflowName'] ?? "Proceso",
                    style: const TextStyle(color: Colors.white, fontSize: 22, fontWeight: FontWeight.bold),
                  ),
                  const SizedBox(height: 8),
                  Text(
                    "Cliente: ${widget.instance['clientName'] ?? 'No especificado'}",
                    style: TextStyle(color: Colors.white.withOpacity(0.9), fontSize: 16),
                  ),
                  Text(
                    "Fase Actual: ${widget.instance['currentNodeName'] ?? '—'}",
                    style: TextStyle(color: Colors.white.withOpacity(0.9), fontSize: 16),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    "Estado: ${widget.instance['status']}",
                    style: TextStyle(color: Colors.white.withOpacity(0.9), fontSize: 14, fontWeight: FontWeight.bold),
                  ),
                  const SizedBox(height: 20),
                  Row(
                    children: [
                      Expanded(
                        child: ElevatedButton.icon(
                          onPressed: _isDownloading ? null : _downloadPdf,
                          icon: _isDownloading 
                            ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.indigo))
                            : const Icon(Icons.picture_as_pdf),
                          label: Text(_isDownloading ? "..." : "Rep. PDF"),
                          style: ElevatedButton.styleFrom(
                            backgroundColor: Colors.white,
                            foregroundColor: Colors.indigo,
                            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                          ),
                        ),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: ElevatedButton.icon(
                          onPressed: _isUploading ? null : _uploadDocument,
                          icon: _isUploading
                            ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                            : const Icon(Icons.upload_file),
                          label: Text(_isUploading ? "..." : "Subir"),
                          style: ElevatedButton.styleFrom(
                            backgroundColor: Colors.white.withOpacity(0.2),
                            foregroundColor: Colors.white,
                            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                          ),
                        ),
                      ),
                    ],
                  )
                ],
              ),
            ),
            
            const SizedBox(height: 32),
            const Text(
              "Línea de Tiempo",
              style: TextStyle(color: Colors.white, fontSize: 20, fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 16),
            
            // Timeline
            ...history.reversed.map((step) {
              final isLast = history.reversed.toList().indexOf(step) == history.length - 1;
              return IntrinsicHeight(
                child: Row(
                  children: [
                    Column(
                      children: [
                        Container(
                          width: 12,
                          height: 12,
                          decoration: const BoxDecoration(
                            color: Colors.indigoAccent,
                            shape: BoxShape.circle,
                          ),
                        ),
                        if (!isLast)
                          Expanded(
                            child: Container(
                              width: 2,
                              color: Colors.white10,
                            ),
                          ),
                      ],
                    ),
                    const SizedBox(width: 16),
                    Expanded(
                      child: Padding(
                        padding: const EdgeInsets.only(bottom: 24),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              step['nodeName'] ?? "Paso",
                              style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 16),
                            ),
                            const SizedBox(height: 4),
                            Text(
                              "${step['action']} por ${step['performedBy'] ?? 'Sistema'}",
                              style: const TextStyle(color: Colors.white38, fontSize: 13),
                            ),
                            if (step['comment'] != null && step['comment'].isNotEmpty)
                              Padding(
                                padding: const EdgeInsets.only(top: 8),
                                child: Text(
                                  "💬 ${step['comment']}",
                                  style: const TextStyle(color: Colors.white70, fontStyle: FontStyle.italic),
                                ),
                              ),
                            const SizedBox(height: 4),
                            Text(
                              DateFormat('dd/MM, HH:mm').format(DateTime.parse(step['timestamp'])),
                              style: const TextStyle(color: Colors.indigoAccent, fontSize: 11),
                            ),
                          ],
                        ),
                      ),
                    ),
                  ],
                ),
              );
            }).toList(),
          ],
        ),
      ),
    );
  }
}
