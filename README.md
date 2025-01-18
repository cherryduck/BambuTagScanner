# Bambu Tag Scanner

Bambu Tag Scanner is an Android application designed to read Bambu filament NFC tags, extract data, and manage dumps of the extracted content.

## Features

- **NFC Tag Scanning**: Detect and process NFC tags, specifically Bambu filament tags.
- **Data Dump Creation**: Extract and save tag data, including sector-specific keys.
- **View and Manage Dumps**: Browse, view details, and delete saved dumps.
- **Color Recognition**: Extract and interpret RGB values from NFC tag data, mapping them to predefined color names.
- **Export Functionality**: Package dumps and associated keys into a ZIP file for easy sharing.

## Getting Started

### Prerequisites

- Android 13 device with NFC capability.

### Installation

1. Download the latest APK from releases.
2. Install
3. Profit???

## Usage

### NFC Tag Scanning
1. Ensure NFC is enabled on your device.
2. Tap the "CREATE NEW DUMP" button to start scanning.
3. Bring a Bambu tag close to your device.
4. The app will process the tag and create a data dump.

### View Dumps
1. Tap the "VIEW EXISTING DUMPS" button to toggle the list of saved dumps.
2. Select a dump to view its details, including extracted tag data and color.

### Export Dumps
1. After selecting a dump, tap the "EXPORT" button.
2. The app packages the dump and associated key files into a ZIP file and opens the the Android share dialog.
3. The tag dump is in Proxmark3 compatible format. There are two key files, a .dic with the keys in plain text, and a Proxmark3 compatible key file.

### Manage Dumps
- Delete a dump by selecting it from the list and confirming deletion.

## File Structure

- **Dump Files**: Contain extracted tag data (`.bin`).
- **Key Files**: Contain sector-specific keys (`.dic` and raw keys as `-key.bin`).
- **ZIP Files**: Packaged dumps and keys for export.

## Technical Details

### NFC Functionality
- Supports Bambu filament tags with sector-based authentication.

### Key Derivation
- Uses HKDF with SHA-256 to derive sector-specific keys from the tag UID.

### Color Recognition
- RGB values extracted from tag data are matched against the Bambu Labs hex colour table for available PLA colours. If there isn't an exact match, it maps to the nearest colour using Euclidean distance.

### Export Mechanism
- Creates a ZIP file containing:
  - Tag dump file.
  - Key files (hex and raw formats).

## Permissions

The app requires the following permissions:

- NFC access for reading NFC tags.
- Read and write storage for saving and exporting files.

## License

This project is licensed under the GPL-3.0 license. See the LICENSE file for details.

