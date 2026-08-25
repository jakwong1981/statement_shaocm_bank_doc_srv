package com.reportcentre.watermark;

import com.reportcentre.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.util.Matrix;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class WatermarkEngine {

    private final StorageService storageService;

    public WatermarkResult process(String rawStoragePath, String benchmarkTag, String clientId) throws Exception {
        InputStream rawStream = storageService.downloadRaw(rawStoragePath);
        byte[] rawBytes = rawStream.readAllBytes();
        rawStream.close();

        try (PDDocument document = Loader.loadPDF(rawBytes)) {
            int totalPages = document.getNumberOfPages();
            String timestamp = Instant.now().toString();
            String checksumPlaceholder = "PENDING";

            for (int i = 0; i < totalPages; i++) {
                PDPage page = document.getPage(i);
                PDRectangle cropBox = page.getCropBox();
                float width = cropBox.getWidth();
                float height = cropBox.getHeight();
                boolean isPortrait = height > width;

                try (PDPageContentStream cs = new PDPageContentStream(document, page,
                        PDPageContentStream.AppendMode.APPEND, true, true)) {

                    // Zone 1: Header - Benchmark tag
                    drawHeader(cs, cropBox, benchmarkTag);

                    // Zone 2: Center diagonal - Client ID + Timestamp
                    drawCenterWatermark(cs, cropBox, clientId, timestamp, isPortrait);

                    // Zone 3: Footer - Checksum + Page index
                    drawFooter(cs, cropBox, checksumPlaceholder, i + 1, totalPages);

                    // Zone 4: QR Code - Verification hash
                    drawQrCode(cs, document, page, cropBox, rawStoragePath);
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            byte[] watermarkedBytes = out.toByteArray();

            String checksum = computeSha256(watermarkedBytes);
            String watermarkedPath = rawStoragePath;
            storageService.uploadWatermarked(watermarkedPath,
                    new ByteArrayInputStream(watermarkedBytes),
                    watermarkedBytes.length, "application/pdf");

            log.info("Watermark complete for {}, {} pages", rawStoragePath, totalPages);
            return new WatermarkResult(watermarkedPath, checksum, totalPages);
        }
    }

    private void drawHeader(PDPageContentStream cs, PDRectangle cropBox, String benchmarkTag) throws IOException {
        PDType1Font font = new PDType1Font(Standard14Fonts.FontName.COURIER);
        float fontSize = 8f;
        String text = "Benchmark Ref: " + (benchmarkTag != null ? benchmarkTag : "N/A") + " | Class: STRICT";

        PDExtendedGraphicsState gs = new PDExtendedGraphicsState();
        gs.setNonStrokingAlphaConstant(0.70f);
        cs.setGraphicsStateParameters(gs);

        cs.beginText();
        cs.setFont(font, fontSize);
        cs.newLineAtOffset(cropBox.getLowerLeftX() + 36, cropBox.getUpperRightY() - 24);
        cs.showText(text);
        cs.endText();

        cs.setGraphicsStateParameters(new PDExtendedGraphicsState());
    }

    private void drawCenterWatermark(PDPageContentStream cs, PDRectangle cropBox,
                                      String clientId, String timestamp, boolean isPortrait) throws IOException {
        PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        float fontSize = 42f;
        float angle = isPortrait ? -45f : -30f;
        String text = "CLIENT: " + clientId + " / " + timestamp;

        PDExtendedGraphicsState gs = new PDExtendedGraphicsState();
        gs.setNonStrokingAlphaConstant(0.18f);
        cs.setGraphicsStateParameters(gs);

        float centerX = cropBox.getLowerLeftX() + cropBox.getWidth() / 2;
        float centerY = cropBox.getLowerLeftY() + cropBox.getHeight() / 2;
        double radians = Math.toRadians(angle);

        cs.beginText();
        cs.setFont(font, fontSize);
        cs.setTextMatrix(Matrix.getRotateInstance(radians, centerX, centerY));
        cs.showText(text);
        cs.endText();

        cs.setGraphicsStateParameters(new PDExtendedGraphicsState());
    }

    private void drawFooter(PDPageContentStream cs, PDRectangle cropBox,
                             String checksum, int pageNum, int totalPages) throws IOException {
        PDType1Font font = new PDType1Font(Standard14Fonts.FontName.COURIER);
        float fontSize = 8f;
        String text = "SHA-256: " + checksum + " | Page " + pageNum + "/" + totalPages;

        PDExtendedGraphicsState gs = new PDExtendedGraphicsState();
        gs.setNonStrokingAlphaConstant(0.65f);
        cs.setGraphicsStateParameters(gs);

        cs.beginText();
        cs.setFont(font, fontSize);
        cs.newLineAtOffset(cropBox.getLowerLeftX() + 36, cropBox.getLowerLeftY() + 20);
        cs.showText(text);
        cs.endText();

        cs.setGraphicsStateParameters(new PDExtendedGraphicsState());
    }

    private void drawQrCode(PDPageContentStream cs, PDDocument document, PDPage page,
                             PDRectangle cropBox, String reportId) throws IOException {
        try {
            BufferedImage qrImage = QrCodeGenerator.generate(reportId, 128, 128);
            ByteArrayOutputStream imgOut = new ByteArrayOutputStream();
            ImageIO.write(qrImage, "PNG", imgOut);
            PDImageXObject pdImage = PDImageXObject.createFromByteArray(
                    document, imgOut.toByteArray(), "qr.png");

            PDExtendedGraphicsState gs = new PDExtendedGraphicsState();
            gs.setNonStrokingAlphaConstant(0.85f);
            cs.setGraphicsStateParameters(gs);

            float x = cropBox.getLowerLeftX() + 24;
            float y = cropBox.getLowerLeftY() + 24;
            cs.drawImage(pdImage, x, y, 44, 44);

            cs.setGraphicsStateParameters(new PDExtendedGraphicsState());
        } catch (Exception e) {
            log.warn("QR code generation skipped for page", e);
        }
    }

    private String computeSha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 computation failed", e);
        }
    }

    public record WatermarkResult(String watermarkedPath, String checksum, int pageCount) {}
}
