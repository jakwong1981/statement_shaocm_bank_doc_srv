package com.reportcentre.watermark;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.awt.image.BufferedImage;
import java.util.Map;

public class QrCodeGenerator {

    public static BufferedImage generate(String content, int width, int height) {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(content, BarcodeFormat.QR_CODE, width, height,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M));
            return MatrixToImageWriter.toBufferedImage(matrix);
        } catch (Exception e) {
            throw new RuntimeException("QR code generation failed", e);
        }
    }
}
