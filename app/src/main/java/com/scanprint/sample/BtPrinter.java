package com.scanprint.sample;

import android.content.Context;
import android.util.Log;

import com.bxl.config.editor.BXLConfigLoader;

import jpos.JposException;
import jpos.POSPrinter;
import jpos.POSPrinterConst;
import jpos.config.JposEntry;

public class BtPrinter {

    private static final String TAG = "BtPrinter";
    private static final String LOGICAL_NAME = "ScanToPrintBt";

    private final Context context;
    private final BXLConfigLoader configLoader;
    private final POSPrinter printer;
    private boolean claimed = false;

    public BtPrinter(Context context) {
        this.context = context;
        this.printer = new POSPrinter(context);
        this.configLoader = new BXLConfigLoader(context);
        try {
            configLoader.openFile();
        } catch (Exception e) {
            configLoader.newFile();
        }
    }

    public synchronized boolean connect(String bluetoothMacUpper, String modelName) {
        try {
            String productName = resolveProductName(modelName);

            for (Object entry : configLoader.getEntries()) {
                JposEntry je = (JposEntry) entry;
                if (LOGICAL_NAME.equals(je.getLogicalName())) {
                    configLoader.removeEntry(LOGICAL_NAME);
                    break;
                }
            }

            configLoader.addEntry(
                    LOGICAL_NAME,
                    BXLConfigLoader.DEVICE_CATEGORY_POS_PRINTER,
                    productName,
                    BXLConfigLoader.DEVICE_BUS_BLUETOOTH,
                    bluetoothMacUpper);
            configLoader.saveFile();

            printer.open(LOGICAL_NAME);
            printer.claim(10_000);
            printer.setDeviceEnabled(true);
            printer.setAsyncMode(false);
            claimed = true;
            return true;
        } catch (JposException e) {
            Log.e(TAG, "connect failed: " + e.getErrorCode() + " " + e.getMessage(), e);
            safeClose();
            return false;
        } catch (Exception e) {
            Log.e(TAG, "connect unexpected: " + e.getMessage(), e);
            safeClose();
            return false;
        }
    }

    public synchronized boolean isClaimed() {
        return claimed;
    }

    public synchronized boolean printBarcode(String data, int symbology, int width, int height, int hri) {
        if (!claimed) {
            Log.w(TAG, "printBarcode called but printer not claimed");
            return false;
        }
        try {
            printer.printBarCode(
                    POSPrinterConst.PTR_S_RECEIPT,
                    data,
                    symbology,
                    height,
                    width,
                    POSPrinterConst.PTR_BC_CENTER,
                    hri);
            printer.printNormal(POSPrinterConst.PTR_S_RECEIPT, "\n\n\n");
            return true;
        } catch (JposException e) {
            Log.e(TAG, "printBarcode failed: " + e.getMessage(), e);
            return false;
        }
    }

    public synchronized boolean printText(String text) {
        if (!claimed) return false;
        try {
            String body = text.endsWith("\n") ? text : text + "\n";
            printer.printNormal(POSPrinterConst.PTR_S_RECEIPT, body);
            return true;
        } catch (JposException e) {
            Log.e(TAG, "printText failed: " + e.getMessage(), e);
            return false;
        }
    }

    public synchronized void disconnect() {
        safeClose();
    }

    private void safeClose() {
        try {
            if (printer.getClaimed()) {
                try { printer.setDeviceEnabled(false); } catch (JposException ignored) {}
                try { printer.release(); }             catch (JposException ignored) {}
            }
            try { printer.close(); } catch (JposException ignored) {}
        } catch (JposException e) {
            Log.w(TAG, "close error: " + e.getMessage());
        } finally {
            claimed = false;
        }
    }

    private static String resolveProductName(String modelName) {
        if (modelName == null) return BXLConfigLoader.PRODUCT_NAME_SPP_R210;
        String n = modelName.trim().toUpperCase();
        if (n.contains("R200III")) return BXLConfigLoader.PRODUCT_NAME_SPP_R200III;
        if (n.contains("R200"))    return BXLConfigLoader.PRODUCT_NAME_SPP_R200II;
        if (n.contains("R210"))    return BXLConfigLoader.PRODUCT_NAME_SPP_R210;
        if (n.contains("R215"))    return BXLConfigLoader.PRODUCT_NAME_SPP_R215;
        if (n.contains("R220"))    return BXLConfigLoader.PRODUCT_NAME_SPP_R220;
        if (n.contains("R230"))    return BXLConfigLoader.PRODUCT_NAME_SPP_R230;
        if (n.contains("R300"))    return BXLConfigLoader.PRODUCT_NAME_SPP_R300;
        if (n.contains("R310"))    return BXLConfigLoader.PRODUCT_NAME_SPP_R310;
        if (n.contains("R318"))    return BXLConfigLoader.PRODUCT_NAME_SPP_R318;
        if (n.contains("R400"))    return BXLConfigLoader.PRODUCT_NAME_SPP_R400;
        if (n.contains("R410"))    return BXLConfigLoader.PRODUCT_NAME_SPP_R410;
        if (n.contains("R418"))    return BXLConfigLoader.PRODUCT_NAME_SPP_R418;
        if (n.contains("C200"))    return BXLConfigLoader.PRODUCT_NAME_SPP_C200;
        if (n.contains("C300"))    return BXLConfigLoader.PRODUCT_NAME_SPP_C300;
        if (n.contains("100II"))   return BXLConfigLoader.PRODUCT_NAME_SPP_100II;
        return BXLConfigLoader.PRODUCT_NAME_SPP_R210;
    }
}
