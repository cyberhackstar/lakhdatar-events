package com.neelastack.lakhdatar.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketPdfServiceTest {
    @Mock
    private TicketQueryService tickets;

    @Test
    void generatesValidPdfWithServerGeneratedQrImageInHeadlessRuntime() throws Exception {
        UUID ticketId = UUID.randomUUID();
        String qrDataUri = pngDataUri();
        BrandService.BrandView brand = new BrandService.BrandView(
                null, "Test Organizer", "BOTH", null, null,
                "#111111", "#FFFFFF", true, "Neelastack", null,
                "https://neelastack.com", false, null, null, null, null);
        TicketQueryService.TicketView view = new TicketQueryService.TicketView(
                ticketId, "TKT-000001", "ISSUED", "Guest ₹ 😀", "Enterprise Test Event",
                Instant.parse("2026-12-01T18:30:00Z"), Instant.parse("2026-12-01T21:30:00Z"),
                "Test Venue", "Jaipur, Rajasthan", "General", 99900L, "INR", null,
                "ONLINE_PAYMENT", null, 1, 2, qrDataUri, brand);
        when(tickets.get(ticketId, "test-token")).thenReturn(view);

        byte[] pdf = new TicketPdfService(tickets).generate(ticketId, "test-token");

        assertNotNull(pdf);
        assertTrue(pdf.length > 1000, "ticket PDF should contain real PDF content");
        assertEquals('%', (char) pdf[0]);
        assertEquals('P', (char) pdf[1]);
        assertEquals('D', (char) pdf[2]);
        assertEquals('F', (char) pdf[3]);
        assertEquals('-', (char) pdf[4]);

        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertEquals(1, document.getNumberOfPages());
            int imageCount = 0;
            for (var name : document.getPage(0).getResources().getXObjectNames()) {
                if (document.getPage(0).getResources().getXObject(name)
                        instanceof org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject) {
                    imageCount++;
                }
            }
            assertEquals(1, imageCount, "ticket PDF must embed the gate QR image");
        }
    }

    @Test
    void refusesToGenerateCustomerPdfWhenTheServerQrPayloadIsInvalid() {
        UUID ticketId = UUID.randomUUID();
        BrandService.BrandView brand = new BrandService.BrandView(
                null, "Test Organizer", "BOTH", null, null,
                "#111111", "#FFFFFF", true, "Neelastack", null,
                "https://neelastack.com", false, null, null, null, null);
        TicketQueryService.TicketView view = new TicketQueryService.TicketView(
                ticketId, "TKT-000002", "ISSUED", "Guest", "Enterprise Test Event",
                Instant.parse("2026-12-01T18:30:00Z"), Instant.parse("2026-12-01T21:30:00Z"),
                null, null, "General", 1000L, "INR", null, "ONLINE_PAYMENT", null,
                1, 1, "data:image/png;base64,not-a-png", brand);
        when(tickets.get(ticketId, "test-token")).thenReturn(view);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new TicketPdfService(tickets).generate(ticketId, "test-token"));
        assertEquals("Ticket PDF generation failed", ex.getMessage());
        assertNotNull(ex.getCause());
    }


    private static String pngDataUri() throws Exception {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int value = ((x / 8) + (y / 8)) % 2 == 0 ? 0x000000 : 0xFFFFFF;
                image.setRGB(x, y, value);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, "PNG", out));
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
    }
}
