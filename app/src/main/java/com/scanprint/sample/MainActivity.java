package com.scanprint.sample;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import device.common.DecodeResult;
import device.common.ScanConst;
import device.sdk.ScanManager;

import jpos.POSPrinterConst;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "ScanToPrint";
    private static final int REQ_PERMS = 100;

    private static final String[] MODELS = new String[] {
            "SPP-R210", "SPP-R200II", "SPP-R200III", "SPP-R215", "SPP-R220", "SPP-R230",
            "SPP-R300", "SPP-R310", "SPP-R318", "SPP-R400", "SPP-R410", "SPP-R418",
            "SPP-C200", "SPP-C300", "SPP-100II"
    };

    private ScanManager scanner;
    private DecodeResult decodeResult;
    private BtPrinter printer;

    private final Executor worker = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());

    private TextView textPrinterStatus, textSymbology, textLog;
    private EditText editData;
    private Spinner spinnerPaired, spinnerModel;
    private Button buttonConnect, buttonDisconnect, buttonPrint, buttonRefresh, buttonScanOn, buttonScanOff;

    private final ArrayList<String> pairedItems = new ArrayList<>();
    private ArrayAdapter<String> pairedAdapter;

    private static final int MAX_AUTO_RETRIES = 2;

    private String currentSymName = null;
    private boolean autoRetryStopped = false;
    private int autoRetryCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textPrinterStatus = findViewById(R.id.textPrinterStatus);
        textSymbology     = findViewById(R.id.textSymbology);
        textLog           = findViewById(R.id.textLog);
        editData          = findViewById(R.id.editData);
        spinnerPaired     = findViewById(R.id.spinnerPaired);
        spinnerModel      = findViewById(R.id.spinnerModel);
        buttonConnect     = findViewById(R.id.buttonConnect);
        buttonDisconnect  = findViewById(R.id.buttonDisconnect);
        buttonPrint       = findViewById(R.id.buttonPrint);
        buttonRefresh     = findViewById(R.id.buttonRefresh);
        buttonScanOn      = findViewById(R.id.buttonScanOn);
        buttonScanOff     = findViewById(R.id.buttonScanOff);

        pairedAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, pairedItems);
        spinnerPaired.setAdapter(pairedAdapter);

        ArrayAdapter<String> modelAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, MODELS);
        spinnerModel.setAdapter(modelAdapter);

        spinnerPaired.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                autoSelectModel();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        buttonRefresh.setOnClickListener(v -> refreshPaired());
        buttonConnect.setOnClickListener(v -> doConnect());
        buttonDisconnect.setOnClickListener(v -> doDisconnect());
        buttonPrint.setOnClickListener(v -> doPrint());

        buttonScanOn.setOnClickListener(v -> {
            autoRetryStopped = false;
            autoRetryCount = 0;
            if (scanner != null) scanner.aDecodeSetTriggerOn(1);
        });
        buttonScanOff.setOnClickListener(v -> {
            autoRetryStopped = true;
            appendLog("Auto-retry stopped by user.");
            if (scanner != null) scanner.aDecodeSetTriggerOn(0);
        });

        try {
            scanner = new ScanManager();
            decodeResult = new DecodeResult();
        } catch (Throwable t) {
            Log.e(TAG, "ScanManager init failed", t);
            appendLog("Scanner init FAILED: " + t.getClass().getSimpleName() + " - " + t.getMessage());
        }
        try {
            printer = new BtPrinter(getApplicationContext());
        } catch (Throwable t) {
            Log.e(TAG, "BtPrinter init failed", t);
            appendLog("Printer init FAILED: " + t.getClass().getSimpleName() + " - " + t.getMessage());
        }

        setConnectionUi(ConnState.DISCONNECTED);
        requestNeededPermissions();
        refreshPaired();
        appendLog("Ready. Pair a Bixolon printer in Android Settings, then tap Refresh → Connect.");
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (scanner != null) {
            try {
                scanner.aDecodeSetResultType(ScanConst.ResultType.DCD_RESULT_USERMSG);
            } catch (Throwable t) {
                Log.e(TAG, "setResultType failed", t);
            }
        }
        IntentFilter filter = new IntentFilter();
        filter.addAction(ScanConst.INTENT_USERMSG);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(scanReceiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                registerReceiver(scanReceiver, filter);
            }
        } catch (Throwable t) {
            Log.e(TAG, "registerReceiver failed", t);
        }
    }

    @Override
    protected void onPause() {
        try { unregisterReceiver(scanReceiver); } catch (Exception ignored) {}
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        worker.execute(() -> printer.disconnect());
        scanner = null;
        super.onDestroy();
    }

    private final BroadcastReceiver scanReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (scanner == null) return;
            if (!ScanConst.INTENT_USERMSG.equals(intent.getAction())) return;
            try {
                scanner.aDecodeGetResult(decodeResult.recycle());
                String sym = decodeResult.symName;
                String data = decodeResult.toString();
                onScanReceived(sym, data);
            } catch (Exception e) {
                Log.e(TAG, "scan parse error", e);
            }
        }
    };

    private void onScanReceived(String symName, String data) {
        boolean failed = data == null
                || data.isEmpty()
                || "READ_FAIL".equalsIgnoreCase(data.trim())
                || "READ_FAIL".equalsIgnoreCase(symName);

        if (failed) {
            currentSymName = null;
            textSymbology.setText("READ FAIL");
            textSymbology.setTextColor(ContextCompat.getColor(this, R.color.bxl_orange));
            editData.setText("");
            if (autoRetryStopped) {
                appendLog("Scan failed (auto-retry stopped).");
            } else if (autoRetryCount >= MAX_AUTO_RETRIES) {
                appendLog("Scan failed — gave up after " + MAX_AUTO_RETRIES + " retries.");
                autoRetryCount = 0;
            } else {
                autoRetryCount++;
                appendLog("Scan failed — auto-retry " + autoRetryCount + "/" + MAX_AUTO_RETRIES + " in 300ms.");
                ui.postDelayed(() -> {
                    if (!autoRetryStopped && scanner != null) {
                        scanner.aDecodeSetTriggerOn(1);
                    }
                }, 300);
            }
            return;
        }

        autoRetryCount = 0;
        currentSymName = symName;
        textSymbology.setText(symName == null ? "-" : symName);
        textSymbology.setTextColor(ContextCompat.getColor(this, R.color.bxl_dark_gray));
        editData.setText(data);
        appendLog("Scanned [" + symName + "]: " + data);
        doPrint();
    }

    private void doPrint() {
        String data = editData.getText() == null ? "" : editData.getText().toString();
        if (data.isEmpty()) {
            toast("No data to print");
            return;
        }
        if (!printer.isClaimed()) {
            toast("Printer not connected");
            return;
        }
        final String symName = currentSymName;
        final String text = data;
        worker.execute(() -> {
            BarcodeSpec spec = SymbologyMapper.pick(symName, text);
            ui.post(() -> appendLog("Print as " + spec.label + " (data='" + text + "')"));
            boolean ok = printer.printBarcode(text, spec.posSymbology, spec.width, spec.height, POSPrinterConst.PTR_BC_TEXT_BELOW);
            ui.post(() -> appendLog(ok ? "Print OK" : "Print FAILED"));
        });
    }

    private void doConnect() {
        if (pairedItems.isEmpty()) {
            toast("No paired devices. Pair the printer in Bluetooth settings, then tap Refresh.");
            return;
        }
        int sel = spinnerPaired.getSelectedItemPosition();
        if (sel < 0 || sel >= pairedItems.size()) return;
        String item = pairedItems.get(sel);
        String mac = extractMac(item);
        String model = (String) spinnerModel.getSelectedItem();
        if (mac == null) {
            toast("Cannot parse MAC: " + item);
            return;
        }
        setStatusChip("Connecting…", R.color.status_warn);
        setConnectionUi(ConnState.CONNECTING);
        worker.execute(() -> {
            boolean ok = printer.connect(mac.toUpperCase(), model);
            ui.post(() -> {
                if (ok) {
                    setStatusChip("Connected", R.color.status_ok);
                    setConnectionUi(ConnState.CONNECTED);
                } else {
                    setStatusChip("Failed", R.color.bxl_orange);
                    setConnectionUi(ConnState.DISCONNECTED);
                }
                appendLog(ok ? "Printer connected: " + item : "Printer connect FAILED");
            });
        });
    }

    private void doDisconnect() {
        autoRetryStopped = true;
        setConnectionUi(ConnState.CONNECTING);
        worker.execute(() -> {
            printer.disconnect();
            ui.post(() -> {
                setStatusChip("Disconnected", R.color.status_off);
                setConnectionUi(ConnState.DISCONNECTED);
                appendLog("Printer disconnected");
            });
        });
    }

    private void setStatusChip(String text, int colorRes) {
        textPrinterStatus.setText(text);
        textPrinterStatus.setTextColor(ContextCompat.getColor(this, colorRes));
    }

    private enum ConnState { DISCONNECTED, CONNECTING, CONNECTED }

    private void setConnectionUi(ConnState s) {
        boolean idle      = (s == ConnState.DISCONNECTED);
        boolean connected = (s == ConnState.CONNECTED);
        // Connection controls
        buttonConnect.setEnabled(idle);
        buttonDisconnect.setEnabled(connected);
        buttonRefresh.setEnabled(idle);
        spinnerPaired.setEnabled(idle);
        spinnerModel.setEnabled(idle);
        // Scan / Print only make sense when connected
        buttonScanOn.setEnabled(connected);
        buttonScanOff.setEnabled(connected);
        buttonPrint.setEnabled(connected);
    }

    @SuppressLint("MissingPermission")
    private void refreshPaired() {
        pairedItems.clear();
        try {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter == null) {
                toast("No Bluetooth adapter");
            } else {
                Set<BluetoothDevice> bonded = adapter.getBondedDevices();
                for (BluetoothDevice d : bonded) {
                    pairedItems.add(d.getName() + " (" + d.getAddress() + ")");
                }
                if (pairedItems.isEmpty()) {
                    pairedItems.add("(no paired devices)");
                }
            }
        } catch (SecurityException se) {
            pairedItems.add("(permission denied)");
        }
        pairedAdapter.notifyDataSetChanged();
    }

    private void autoSelectModel() {
        int sel = spinnerPaired.getSelectedItemPosition();
        if (sel < 0 || sel >= pairedItems.size()) return;
        String item = pairedItems.get(sel);
        int idx = guessModelIndex(item);
        if (idx >= 0 && idx != spinnerModel.getSelectedItemPosition()) {
            spinnerModel.setSelection(idx);
            appendLog("Auto-detected model: " + MODELS[idx]);
        }
    }

    private static int guessModelIndex(String deviceName) {
        if (deviceName == null) return -1;
        String upper = deviceName.toUpperCase();
        int bestIdx = -1, bestLen = 0;
        for (int i = 0; i < MODELS.length; i++) {
            String m = MODELS[i].toUpperCase();
            if (upper.contains(m) && m.length() > bestLen) {
                bestIdx = i;
                bestLen = m.length();
            }
        }
        return bestIdx;
    }

    private static String extractMac(String item) {
        int s = item.lastIndexOf('(');
        int e = item.lastIndexOf(')');
        if (s < 0 || e < 0 || e <= s + 1) return null;
        String mac = item.substring(s + 1, e);
        return mac.matches("([0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}") ? mac : null;
    }

    private void requestNeededPermissions() {
        ArrayList<String> need = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                need.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                need.add(Manifest.permission.BLUETOOTH_SCAN);
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                need.add(Manifest.permission.ACCESS_FINE_LOCATION);
            }
        }
        if (!need.isEmpty()) {
            ActivityCompat.requestPermissions(this, need.toArray(new String[0]), REQ_PERMS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_PERMS) {
            refreshPaired();
        }
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private void appendLog(String line) {
        String ts = (String) DateFormat.format("HH:mm:ss", System.currentTimeMillis());
        textLog.append(ts + " " + line + "\n");
    }
}
