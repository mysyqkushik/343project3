package com.groupxx.greengrocer.util;

import com.groupxx.greengrocer.model.CartLine;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Generates a very simple invoice PDF as bytes using Apache PDFBox.
 * Stored in DB in order_info.invoice_pdf.
 */
public final class InvoicePdfGenerator {

    private InvoicePdfGenerator() {}

    public static byte[] generate(long orderId,
                                  String customerUsername,
                                  LocalDateTime orderTime,
                                  LocalDateTime requestedDeliveryTime,
                                  List<CartLine> lines,
                                  BigDecimal subtotal,
                                  BigDecimal discount,
                                  BigDecimal vat,
                                  BigDecimal totalInclVat) throws Exception {

        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                // PDFBox 3.x: Standard 14 fonts are created via Standard14Fonts.FontName
                final PDType1Font FONT_TITLE = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
                final PDType1Font FONT_BODY  = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
                final PDType1Font FONT_MONO  = new PDType1Font(Standard14Fonts.FontName.COURIER);

                float x = 50;
                float y = 780;
                float leading = 14;
                float yPos = y;

                cs.beginText();
                cs.setFont(FONT_TITLE, 16);
                cs.newLineAtOffset(x, y);
                cs.showText("GreenGrocer Invoice");
                cs.newLineAtOffset(0, -leading);
                yPos -= leading;

                cs.setFont(FONT_BODY, 11);
                cs.showText("Order ID: " + orderId);
                cs.newLineAtOffset(0, -leading);
                yPos -= leading;
                cs.showText("Customer: " + safe(customerUsername));
                cs.newLineAtOffset(0, -leading);
                yPos -= leading;
                cs.showText("Order time: " + Formatters.formatDateTime(orderTime));
                cs.newLineAtOffset(0, -leading);
                yPos -= leading;
                cs.showText("Requested delivery: " + Formatters.formatDateTime(requestedDeliveryTime));
                cs.newLineAtOffset(0, -leading * 2);
                yPos -= leading * 2;
                cs.showText("Items:");
                cs.newLineAtOffset(0, -leading);
                yPos -= leading;

                cs.setFont(FONT_MONO, 10);
                for (CartLine l : lines) {
                    String line = String.format("%-20s  %6s kg  x %8s  = %8s",
                            trim(l.name(), 20),
                            Formatters.formatQuantity(l.kg()),
                            Formatters.formatMoney(l.unitPricePerKg()),
                            Formatters.formatMoney(l.lineTotal()));
                    cs.showText(line);
                    cs.newLineAtOffset(0, -leading);
                    yPos -= leading;
                    if (yPos < 120) break; // keep it one page for the course
                }

                cs.setFont(FONT_BODY, 11);
                cs.newLineAtOffset(0, -leading);
                yPos -= leading;
                cs.showText("Subtotal: " + Formatters.formatMoney(subtotal));
                cs.newLineAtOffset(0, -leading);
                yPos -= leading;
                cs.showText("Discount: " + Formatters.formatMoney(discount));
                cs.newLineAtOffset(0, -leading);
                yPos -= leading;
                cs.showText("VAT: " + Formatters.formatMoney(vat));
                cs.newLineAtOffset(0, -leading);
                yPos -= leading;
                cs.setFont(FONT_TITLE, 12);
                cs.showText("Total (incl. VAT): " + Formatters.formatMoney(totalInclVat));
                cs.endText();
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        }
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static String trim(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        return s.substring(0, Math.max(0, max - 1)) + "…";
    }
}
