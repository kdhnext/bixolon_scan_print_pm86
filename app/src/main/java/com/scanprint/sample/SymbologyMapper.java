package com.scanprint.sample;

import java.nio.charset.StandardCharsets;

import jpos.POSPrinterConst;

final class BarcodeSpec {
    final int posSymbology;
    final int width;
    final int height;
    final String label;
    BarcodeSpec(int sym, int w, int h, String label) {
        this.posSymbology = sym;
        this.width = w;
        this.height = h;
        this.label = label;
    }
}

/**
 * Maps a PM86 scanner symbology name → Bixolon POS printer barcode type.
 *
 * If the input data doesn't fit the scanned type (e.g. scanner returned UPC-A but the
 * data has letters), we fall back to a type that can encode it: Code128 for ASCII,
 * QR Code for everything else.
 */
public final class SymbologyMapper {

    private SymbologyMapper() {}

    static BarcodeSpec pick(String symName, String data) {
        String n = symName == null ? "" : symName.toUpperCase().replace(" ", "").replace("-", "");
        if (data == null) data = "";

        // 1D numeric
        if (n.contains("UPCA") && isDigits(data, 11, 12))
            return new BarcodeSpec(POSPrinterConst.PTR_BCS_UPCA, 2, 150, "UPC-A");
        if (n.contains("UPCE") && isDigits(data, 6, 8))
            return new BarcodeSpec(POSPrinterConst.PTR_BCS_UPCE, 2, 150, "UPC-E");
        if (n.contains("EAN13") && isDigits(data, 12, 13))
            return new BarcodeSpec(POSPrinterConst.PTR_BCS_EAN13, 2, 150, "EAN-13");
        if (n.contains("EAN8") && isDigits(data, 7, 8))
            return new BarcodeSpec(POSPrinterConst.PTR_BCS_EAN8, 2, 150, "EAN-8");
        if ((n.contains("ITF") || n.contains("I25") || n.contains("INTERLEAVED")) && isDigits(data, 2, 80))
            return new BarcodeSpec(POSPrinterConst.PTR_BCS_ITF, 2, 150, "ITF");

        // 1D alphanumeric
        if (n.contains("CODE39") && isAscii(data))
            return new BarcodeSpec(POSPrinterConst.PTR_BCS_Code39, 2, 150, "Code39");
        if (n.contains("CODE93") && isAscii(data))
            return new BarcodeSpec(POSPrinterConst.PTR_BCS_Code93, 2, 150, "Code93");
        if (n.contains("CODABAR") && isAscii(data))
            return new BarcodeSpec(POSPrinterConst.PTR_BCS_Codabar, 2, 150, "Codabar");
        if ((n.contains("EAN128") || n.contains("GS1128")) && isAscii(data))
            return new BarcodeSpec(POSPrinterConst.PTR_BCS_EAN128, 2, 150, "GS1-128");
        if (n.contains("CODE128") && isAscii(data))
            return new BarcodeSpec(POSPrinterConst.PTR_BCS_Code128, 2, 150, "Code128");

        // 2D
        if (n.contains("QR"))
            return new BarcodeSpec(POSPrinterConst.PTR_BCS_QRCODE, 3, 3, "QR Code");
        if (n.contains("PDF417"))
            return new BarcodeSpec(POSPrinterConst.PTR_BCS_PDF417, 3, 3, "PDF417");
        if (n.contains("DATAMATRIX"))
            return new BarcodeSpec(POSPrinterConst.PTR_BCS_DATAMATRIX, 3, 3, "DataMatrix");
        if (n.contains("MAXI"))
            return new BarcodeSpec(POSPrinterConst.PTR_BCS_MAXICODE, 3, 3, "MaxiCode");

        // Fallback by data shape
        if (isAscii(data) && data.length() <= 80)
            return new BarcodeSpec(POSPrinterConst.PTR_BCS_Code128, 2, 150, "Code128 (fallback)");

        return new BarcodeSpec(POSPrinterConst.PTR_BCS_QRCODE, 3, 3, "QR Code (fallback)");
    }

    private static boolean isDigits(String s, int minLen, int maxLen) {
        if (s.length() < minLen || s.length() > maxLen) return false;
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) return false;
        }
        return true;
    }

    private static boolean isAscii(String s) {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        if (b.length != s.length()) return false;
        for (byte x : b) {
            if (x < 0x20 || x > 0x7E) return false;
        }
        return true;
    }
}
