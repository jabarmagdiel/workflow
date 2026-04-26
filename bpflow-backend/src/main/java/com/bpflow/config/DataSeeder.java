package com.bpflow.config;

import com.bpflow.model.Workflow;
import com.bpflow.model.WorkflowEdge;
import com.bpflow.model.WorkflowNode;
import com.bpflow.repository.WorkflowRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Seeds the database with the "f1-proces" workflow on startup if it doesn't exist.
 * Matches the BPMN diagram exactly:
 *   Inicio → Revisión de Recepción → Documentación completa?
 *     ↳ Aprobado  → Revisión Técnica → Revisión técnica OK?
 *         ↳ Aprobado  → Aprobación Final → Decisión Final → Aprobado(END) / Rechazado(END)
 *         ↳ Observado → Corrección Técnica → [back to] Revisión Técnica
 *     ↳ Rechazado → Corrección de Documentos → ¿Subsanado?
 *         ↳ Corregido  → [back to] Revisión Técnica
 *         ↳ Cancelado  → Cancelado(END)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements ApplicationRunner {

    private final WorkflowRepository workflowRepository;

    @Override
    public void run(ApplicationArguments args) {
        if (workflowRepository.existsByName("f1-proces")) {
            log.info("f1-proces workflow already seeded, skipping.");
            return;
        }

        log.info("Seeding f1-proces workflow...");

        // ── Node IDs ─────────────────────────────────────────────
        String nInicio          = uid();
        String nRevRecepcion    = uid();
        String nDecDocCompleta  = uid();
        String nCorrDocumentos  = uid();
        String nDecSubsanado    = uid();
        String nCancelado       = uid();
        String nRevTecnica      = uid();
        String nDecRevTecnica   = uid();
        String nCorrTecnica     = uid();
        String nAprobFinal      = uid();
        String nDecFinal        = uid();
        String nAprobado        = uid();
        String nRechazado       = uid();

        // ── Nodes ─────────────────────────────────────────────────
        List<WorkflowNode> nodes = new ArrayList<>(List.of(

            node(nInicio,         "Inicio",                    WorkflowNode.NodeType.START,    null, true,  false, 0,   100, 300),
            node(nRevRecepcion,   "Revisión de Recepción",     WorkflowNode.NodeType.TASK,     "OFFICER", false, false, 24, 250, 300),
            node(nDecDocCompleta, "Documentación completa?",   WorkflowNode.NodeType.DECISION, null, false, false, 0,  450, 300),
            node(nCorrDocumentos, "Corrección de Documentos",  WorkflowNode.NodeType.TASK,     "OFFICER", false, false, 48, 450, 480),
            node(nDecSubsanado,   "¿Subsanado?",               WorkflowNode.NodeType.DECISION, null, false, false, 0,  650, 480),
            node(nCancelado,      "Cancelado",                 WorkflowNode.NodeType.END,      null, false, true,  0,  800, 480),
            node(nRevTecnica,     "Revisión Técnica",          WorkflowNode.NodeType.TASK,     "ANALYST", false, false, 48, 650, 300),
            node(nDecRevTecnica,  "Revisión técnica OK?",      WorkflowNode.NodeType.DECISION, null, false, false, 0,  850, 300),
            node(nCorrTecnica,    "Corrección Técnica",        WorkflowNode.NodeType.TASK,     "OFFICER", false, false, 24, 850, 480),
            node(nAprobFinal,     "Aprobación Final",          WorkflowNode.NodeType.TASK,     "MANAGER", false, false, 24, 1050, 300),
            node(nDecFinal,       "Decisión final",            WorkflowNode.NodeType.DECISION, null, false, false, 0, 1250, 300),
            node(nAprobado,       "Aprobado",                  WorkflowNode.NodeType.END,      null, false, true,  0, 1400, 200),
            node(nRechazado,      "Rechazado",                 WorkflowNode.NodeType.END,      null, false, true,  0, 1400, 400)
        ));

        // ── Edges ─────────────────────────────────────────────────
        List<WorkflowEdge> edges = new ArrayList<>(List.of(

            // Start → Revisión de Recepción
            edge(nInicio,         nRevRecepcion,   "SEQUENCE",    null,          ""),

            // Revisión de Recepción → Documentación completa?
            edge(nRevRecepcion,   nDecDocCompleta, "SEQUENCE",    null,          ""),

            // Documentación completa? → Revisión Técnica (Aprobado)
            edge(nDecDocCompleta, nRevTecnica,     "CONDITIONAL", "APPROVE",     "Aprobado"),

            // Documentación completa? → Corrección de Documentos (Rechazado)
            edge(nDecDocCompleta, nCorrDocumentos, "CONDITIONAL", "REJECT",      "Rechazado"),

            // Corrección de Documentos → ¿Subsanado?
            edge(nCorrDocumentos, nDecSubsanado,   "SEQUENCE",    null,          "Corregido"),

            // ¿Subsanado? → Revisión Técnica (Corregido)
            edge(nDecSubsanado,   nRevTecnica,     "CONDITIONAL", "APPROVE",     "Corregido"),

            // ¿Subsanado? → Cancelado (Cancelado)
            edge(nDecSubsanado,   nCancelado,      "CONDITIONAL", "REJECT",      "Cancelado"),

            // Revisión Técnica → Revisión técnica OK?
            edge(nRevTecnica,     nDecRevTecnica,  "SEQUENCE",    null,          ""),

            // Revisión técnica OK? → Aprobación Final (Aprobado)
            edge(nDecRevTecnica,  nAprobFinal,     "CONDITIONAL", "APPROVE",     "Aprobado"),

            // Revisión técnica OK? → Corrección Técnica (Observado)
            edge(nDecRevTecnica,  nCorrTecnica,    "CONDITIONAL", "REJECT",      "Observado"),

            // Corrección Técnica → Revisión Técnica (loop back)
            edge(nCorrTecnica,    nRevTecnica,     "SEQUENCE",    null,          ""),

            // Aprobación Final → Decisión final
            edge(nAprobFinal,     nDecFinal,       "SEQUENCE",    null,          ""),

            // Decisión final → Aprobado
            edge(nDecFinal,       nAprobado,       "CONDITIONAL", "APPROVE",     "Aprobado"),

            // Decisión final → Rechazado
            edge(nDecFinal,       nRechazado,      "CONDITIONAL", "REJECT",      "Rechazado")
        ));

        Workflow wf = Workflow.builder()
                .name("f1-proces")
                .description("Proceso de revisión y aprobación de solicitudes")
                .category("Operaciones")
                .status(Workflow.WorkflowStatus.PUBLISHED)
                .version(11)
                .nodes(nodes)
                .edges(edges)
                .defaultSlaHours(168)  // 7 days
                .build();

        workflowRepository.save(wf);
        log.info("✅ f1-proces workflow seeded successfully with {} nodes and {} edges",
                nodes.size(), edges.size());
    }

    // ── Helpers ───────────────────────────────────────────────────

    private static String uid() {
        return UUID.randomUUID().toString();
    }

    private static WorkflowNode node(String id, String label, WorkflowNode.NodeType type,
            String role, boolean start, boolean end, int sla, double x, double y) {
        return WorkflowNode.builder()
                .id(id)
                .label(label)
                .type(type)
                .assignedRole(role)
                .startNode(start)
                .endNode(end)
                .slaHours(sla > 0 ? sla : null)
                .x(x)
                .y(y)
                .build();
    }

    private static WorkflowEdge edge(String src, String tgt, String type,
            String condition, String label) {
        return WorkflowEdge.builder()
                .id(UUID.randomUUID().toString())
                .sourceNodeId(src)
                .targetNodeId(tgt)
                .type(WorkflowEdge.EdgeType.valueOf(type))
                .condition(condition)
                .label(label)
                .build();
    }
}
