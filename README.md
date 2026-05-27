# ScanToPrint

Scan a barcode on a PM86 handheld → print it on a paired BIXOLON Bluetooth printer, automatically.

- **Scanner:** PM86 SDK (`device.sdk`)
- **Printer:** BIXOLON UPOS SDK 2.2.10 (Bluetooth, SPP-R series)
- **Min SDK:** 21 · **Target SDK:** 33 · **AGP:** 7.4.2 · **Gradle:** 7.5 · **JDK:** 17

## Features

- Auto-detects printer model from paired Bluetooth device name
- Maps scanned symbology → matching printer barcode type (UPC/EAN/Code128/QR/...)
- Auto-prints on every successful scan
- Auto-retries `READ_FAIL` scans up to 2 times
- BIXOLON-branded UI per April 2026 brand guidelines

## Required vendor SDKs (NOT in this repo)

The following files are excluded from version control and must be obtained separately:

| Path | Source |
|---|---|
| `app/libs/bixolon_printer_V2.2.10.jar` | BIXOLON UPOS SDK |
| `app/libs/libcommon_V1.4.4.jar` | BIXOLON UPOS SDK |
| `app/src/main/jniLibs/<abi>/libbxl_common.so` | BIXOLON UPOS SDK (4 ABIs) |
| `app/sdk/device.sdk.jar` | PM86 device SDK |

Drop them at the paths above before building.

## Build

1. Copy `gradle.properties.example` → `gradle.properties` and adjust `org.gradle.java.home` if your default JDK is > 18.
2. Open the project in Android Studio.
3. Set Gradle JDK to 17 (Settings → Build Tools → Gradle → Gradle JDK).
4. Sync → Run on a PM86 device.

## Usage

1. Pair the BIXOLON printer in Android Bluetooth settings.
2. Launch the app → **Refresh** → pick paired device → **Connect**.
3. Pull the PM86 hardware trigger (or tap **Scan ON**) → the scanned barcode prints automatically.

## License

MIT for application code; BIXOLON branding assets and vendor SDKs are excluded.
See [LICENSE](LICENSE) for details.
