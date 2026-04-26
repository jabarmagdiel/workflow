package com.bpflow.service;

import com.bpflow.model.WorkflowInstance;
import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;

@Service
public class ReportService {

    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public byte[] generateInstanceAuditPdf(WorkflowInstance instance) {
        Document document = new Document(PageSize.A4);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter.getInstance(document, out);
            document.open();

            // --- Header ---
            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, Color.BLACK);
            Paragraph header = new Paragraph("BPFlow AI - Reporte de Auditoría", headerFont);
            header.setAlignment(Element.ALIGN_CENTER);
            header.setSpacingAfter(20);
            document.add(header);

            // --- Summary Info ---
            PdfPTable summaryTable = new PdfPTable(2);
            summaryTable.setWidthPercentage(100);
            summaryTable.setSpacingBefore(10);
            
            addTableCell(summaryTable, "Referencia:", true);
            addTableCell(summaryTable, instance.getReferenceNumber(), false);
            addTableCell(summaryTable, "Flujo de Trabajo:", true);
            addTableCell(summaryTable, instance.getWorkflowName() + " (v" + instance.getWorkflowVersion() + ")", false);
            addTableCell(summaryTable, "Iniciado Por:", true);
            addTableCell(summaryTable, instance.getInitiatedBy(), false);
            addTableCell(summaryTable, "Estado Actual:", true);
            addTableCell(summaryTable, instance.getStatus().toString(), false);
            addTableCell(summaryTable, "Prioridad:", true);
            addTableCell(summaryTable, instance.getPriority().toString(), false);
            
            document.add(summaryTable);

            // --- History Section ---
            Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, new Color(37, 99, 235));
            Paragraph sectionTitle = new Paragraph("\nHistorial de Ejecución", sectionFont);
            sectionTitle.setSpacingBefore(20);
            sectionTitle.setSpacingAfter(10);
            document.add(sectionTitle);

            PdfPTable historyTable = new PdfPTable(new float[]{1, 2, 2, 2});
            historyTable.setWidthPercentage(100);
            
            String[] headers = {"Fecha", "Nodo", "Acción", "Responsable"};
            for (String h : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(h, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE)));
                cell.setBackgroundColor(new Color(15, 23, 42));
                cell.setPadding(5);
                historyTable.addCell(cell);
            }

            for (WorkflowInstance.ExecutionStep step : instance.getHistory()) {
                historyTable.addCell(createSmallCell(step.getTimestamp().format(formatter)));
                historyTable.addCell(createSmallCell(step.getNodeName()));
                historyTable.addCell(createSmallCell(step.getAction() + (step.getComment() != null ? "\nObs: " + step.getComment() : "")));
                historyTable.addCell(createSmallCell(step.getPerformedBy()));
            }

            document.add(historyTable);

            // --- Variables / Data Section ---
            if (instance.getVariables() != null && !instance.getVariables().isEmpty()) {
                Paragraph varTitle = new Paragraph("\nDatos del Proceso", sectionFont);
                varTitle.setSpacingBefore(20);
                varTitle.setSpacingAfter(10);
                document.add(varTitle);

                PdfPTable varTable = new PdfPTable(2);
                varTable.setWidthPercentage(100);
                
                instance.getVariables().forEach((k, v) -> {
                    addTableCell(varTable, k, true);
                    addTableCell(varTable, String.valueOf(v), false);
                });
                
                document.add(varTable);
            }

            // --- Footer ---
            Paragraph footer = new Paragraph("\nDocumento generado automáticamente por el motor BPFlow AI.", 
                FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8, Color.GRAY));
            footer.setAlignment(Element.ALIGN_RIGHT);
            document.add(footer);

            document.close();
        } catch (DocumentException e) {
            e.printStackTrace();
        }

        return out.toByteArray();
    }

    private void addTableCell(PdfPTable table, String text, boolean isLabel) {
        Font font = isLabel ? FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10) : FontFactory.getFont(FontFactory.HELVETICA, 10);
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(5);
        if (isLabel) cell.setBackgroundColor(new Color(241, 245, 249));
        table.addCell(cell);
    }

    private PdfPCell createSmallCell(String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, FontFactory.getFont(FontFactory.HELVETICA, 8)));
        cell.setPadding(5);
        return cell;
    }
}
