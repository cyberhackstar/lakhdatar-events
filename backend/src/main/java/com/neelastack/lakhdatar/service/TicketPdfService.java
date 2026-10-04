package com.neelastack.lakhdatar.service;

import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

/** Generates the canonical ticket PDF server-side. No customer bearer token is ever embedded in the PDF. */
@Service
@RequiredArgsConstructor
public class TicketPdfService {
    private final TicketQueryService tickets;

    public byte[] generate(java.util.UUID ticketId, String token) {
        TicketQueryService.TicketView t = tickets.get(ticketId, token);
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float w = page.getMediaBox().getWidth();
                float margin = 42f;
                float y = page.getMediaBox().getHeight() - margin;

                cs.setNonStrokingColor(24, 19, 27);
                cs.addRect(0, page.getMediaBox().getHeight() - 92, w, 92);
                cs.fill();

                text(cs, new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 20, margin, y - 8, safe(t.brand().organizerName(), "Neelastack Events"));
                text(cs, new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10, margin, y - 28, "Digital admission pass");

                float contentY = y - 125;
                text(cs, new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 24, margin, contentY, safe(t.eventName(), "Event"));
                text(cs, new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11, margin, contentY - 24,
                        "Ticket " + safe(t.ticketNumber(), "") + " · " + t.ticketPosition() + " of " + t.orderTicketCount());
                text(cs, new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 10, margin, contentY - 42,
                        t.orderTicketCount() + " " + (t.orderTicketCount() == 1 ? "seat" : "seats") + " booked in this order");
                text(cs, new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 12, margin, contentY - 66,
                        safe(t.ticketType(), "Ticket"));
                text(cs, new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12, margin, contentY - 92,
                        "Attendee: " + safe(t.attendeeName(), "Guest"));
                text(cs, new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10, margin, contentY - 117,
                        "Starts: " + t.startsAt());
                if (t.venueName() != null) {
                    text(cs, new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10, margin, contentY - 142,
                            "Venue: " + safe(t.venueName(), "Venue"));
                }
                if (t.venueAddress() != null) {
                    text(cs, new PDType1Font(Standard14Fonts.FontName.HELVETICA), 9, margin, contentY - 162,
                            "Address: " + safe(t.venueAddress(), ""));
                }

                byte[] qrBytes = decodeQr(t.qrDataUri());
                if (qrBytes != null) {
                    var qrImage = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(qrBytes));
                    if (qrImage != null) {
                        var image = LosslessFactory.createFromImage(doc, qrImage);
                        float size = 190f;
                        cs.drawImage(image, w - margin - size, contentY - 170, size, size);
                        text(cs, new PDType1Font(Standard14Fonts.FontName.HELVETICA), 9, w - margin - size, contentY - 183, "Present this QR at the gate");
                    }
                }

                cs.setStrokingColor(220, 213, 205);
                cs.moveTo(margin, 185);
                cs.lineTo(w - margin, 185);
                cs.stroke();
                text(cs, new PDType1Font(Standard14Fonts.FontName.HELVETICA), 9, margin, 164,
                        "Status: " + safe(t.status(), "ISSUED"));
                text(cs, new PDType1Font(Standard14Fonts.FontName.HELVETICA), 9, margin, 148,
                        "Source: " + safe(t.source(), "ONLINE_PAYMENT"));
                text(cs, new PDType1Font(Standard14Fonts.FontName.HELVETICA), 9, margin, 132,
                        "Technology partner: " + safe(t.brand().technologyPartnerName(), "Neelastack"));
                text(cs, new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE), 8, margin, 88,
                        "Keep this pass available on your phone. Admission is subject to event rules and ticket validity.");
            }
            doc.save(out);
            return out.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Ticket PDF generation failed", ex);
        }
    }

    private static void text(PDPageContentStream cs, PDType1Font font, float size, float x, float y, String value) throws IOException {
        cs.beginText(); cs.setFont(font, size); cs.newLineAtOffset(x, y); cs.showText(ascii(value)); cs.endText();
    }

    private static byte[] decodeQr(String dataUri) {
        if (dataUri == null) return null;
        int comma = dataUri.indexOf(',');
        if (comma < 0) return null;
        try { return Base64.getDecoder().decode(dataUri.substring(comma + 1)); }
        catch (IllegalArgumentException ignored) { return null; }
    }

    private static String safe(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }

    /** Standard 14 fonts are intentionally used so the PDF has no external-font deployment dependency. */
    private static String ascii(String value) {
        if (value == null) return "";
        return value.replace("₹", "INR ").replaceAll("[^\\x20-\\x7E]", "?");
    }
}
