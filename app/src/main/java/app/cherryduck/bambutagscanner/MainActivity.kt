package app.cherryduck.bambutagscanner

import android.app.Activity
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Color
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.view.View
import java.io.File
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.digests.SHA256Digest
import android.widget.*
import android.nfc.tech.MifareClassic
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import android.util.Log
import android.view.ViewGroup
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.pow
import kotlin.math.sqrt

class MainActivity : Activity() {

    // Declare variables for NFC adapter and UI components
    private lateinit var nfcAdapter: NfcAdapter // NFC adapter for handling NFC functionality
    private lateinit var createDumpButton: Button // Button for creating a data dump
    private lateinit var viewDumpsButton: Button // Button for viewing the list of data dumps
    private lateinit var dumpsListView: ListView // ListView to display available dumps
    private lateinit var detailsText: TextView // TextView for displaying details about a selected dump
    private lateinit var colorSwatch: View // A visual indicator, potentially for status or selection
    private lateinit var exportButton: Button // Button for exporting data

    // Declare variables for managing the data and state
    private var dumpsListAdapter: ArrayAdapter<String>? = null // Adapter for populating the dumpsListView
    private var isDumpsListVisible = false // Boolean to track whether the dumps list is currently visible
    private var currentDumpFileName: String? = null // Name of the currently selected dump file
    private var currentDumpContent: ByteArray? = null // Content of the currently selected dump file
    private var isWaitingForTag = false // Boolean to track whether the app is waiting for an NFC tag

    // Declare a variable for the waiting screen view
    private lateinit var waitingScreen: View // View displayed while waiting for NFC tag interaction

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Inflate the waiting screen layout
        val rootLayout = findViewById<FrameLayout>(R.id.rootLayout) // Root layout of the activity
        waitingScreen = layoutInflater.inflate(R.layout.waiting_screen, rootLayout, false) // Inflate the waiting screen view
        rootLayout.addView(waitingScreen) // Add the waiting screen to the root layout
        waitingScreen.visibility = View.GONE // Initially hide the waiting screen

        // Initialize the NFC adapter
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        // Initialize UI components
        createDumpButton = findViewById(R.id.createDumpButton) // Button for creating a new dump
        viewDumpsButton = findViewById(R.id.viewDumpsButton) // Button for viewing existing dumps
        dumpsListView = findViewById(R.id.dumpsListView) // ListView to display dumps
        detailsText = findViewById(R.id.detailsText) // TextView for displaying details of a selected dump
        colorSwatch = findViewById(R.id.colorSwatch) // View for displaying a color swatch
        exportButton = findViewById(R.id.exportButton) // Button for exporting the current dump

        // Initially hide the export button
        exportButton.isVisible = false

        // Set click listener for the "Create Dump" button
        createDumpButton.setOnClickListener {
            // Check NFC is enabled
            if (isNfcEnabled()) {
                isWaitingForTag = true // Set the flag to indicate waiting for an NFC tag
                waitingScreen.visibility = View.VISIBLE // Show the waiting screen
                enableForegroundDispatch() // Enable NFC foreground dispatch
            } else {
                showNfcDisabledDialog() // Warn that NFC is not enabled
            }
        }

        // Set click listener for the "View Dumps" button
        viewDumpsButton.setOnClickListener {
            toggleExistingDumps() // Toggle the visibility of the dumps list
        }

        // Set click listener for the "Export" button
        exportButton.setOnClickListener {
            exportCurrentDump() // Export the currently loaded dump
        }

        // Set item click listener for the dumps list
        dumpsListView.setOnItemClickListener { _, _, position, _ ->
            val fileName = dumpsListAdapter?.getItem(position) // Get the file name of the selected dump
            fileName?.let { loadDumpDetails(it) } // Load and display details of the selected dump
        }
    }

    override fun onResume() {
        super.onResume()
        if (isNfcEnabled()) {
            enableForegroundDispatch() // Enable NFC foreground dispatch when the activity is resumed
        } else {
            showNfcDisabledDialog() // Warn that NFC is not enabled
        }
    }

    override fun onPause() {
        super.onPause()
        disableForegroundDispatch() // Disable NFC foreground dispatch when the activity is paused
    }

    private fun isNfcEnabled(): Boolean {
        // Use the NFC adapter to determine if NFC is enabled
        return nfcAdapter.isEnabled
    }

    private fun showNfcDisabledDialog() {
        // Create and display an alert dialog to inform the user
        AlertDialog.Builder(this)
            .setTitle("NFC Disabled") // Set the title of the dialog
            .setMessage("NFC is disabled. Please enable it in the settings to use this feature.") // Inform the user
            .setPositiveButton("Open Settings") { _, _ ->
                // Open the NFC settings screen when the user clicks "Open Settings"
                val intent = Intent(Settings.ACTION_NFC_SETTINGS)
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null) // Allow the user to cancel the dialog
            .show() // Show the dialog to the user
    }
    private fun enableForegroundDispatch() {
        // Create an intent for the current activity to handle NFC interactions
        val intent = Intent(this, javaClass).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP) // Ensure the activity is not duplicated
        }

        // Create a pending intent for the NFC adapter to use
        val pendingIntent = PendingIntent.getActivity(
            this, // Context
            0, // Request code
            intent, // Intent for the activity
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE // Flags for updating and mutability
        )

        // Define the list of NFC technologies to detect
        val techListsArray = arrayOf(arrayOf(MifareClassic::class.java.name)) // Detect Mifare Classic tags

        // Enable NFC foreground dispatch with the pending intent and technology filter
        nfcAdapter.enableForegroundDispatch(this, pendingIntent, null, techListsArray)
    }

    private fun disableForegroundDispatch() {
        // Disable NFC foreground dispatch to stop intercepting NFC intents
        nfcAdapter.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent) // Call the superclass method to handle the intent

        if (isWaitingForTag) {
            isWaitingForTag = false // Reset the flag as we are no longer waiting for a tag
            waitingScreen.visibility = View.GONE // Hide the waiting screen

            try {
                // Extract the NFC tag from the intent
                val tag: Tag? = intent?.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)

                if (tag != null) {
                    // Log the detected tag ID in hexadecimal format
                    Log.d("MainActivity", "Tag detected: ${tag.id.joinToString("") { "%02X".format(it) }}")
                    handleTag(tag) // Process the detected tag
                } else {
                    // Log an error if no tag is found in the intent
                    Log.e("MainActivity", "No NFC tag detected in the intent")
                }
            } catch (e: Exception) {
                // Log an error if an exception occurs during tag handling
                Log.e("MainActivity", "Error in onNewIntent: ${e.message}", e)
            }
        }
    }

    private fun handleTag(tag: Tag) {
        // Retrieve the UID of the tag
        val uid = tag.id ?: throw IllegalArgumentException("Tag UID is null") // Ensure UID is not null
        val uidHex = uid.joinToString("") { "%02X".format(it) } // Convert UID to hexadecimal format
        Log.d("MainActivity", "Processing tag UID: $uidHex") // Log the UID for debugging

        // Get the MifareClassic object for the tag
        val mifare = MifareClassic.get(tag)
        val sectorCount = mifare.sectorCount // Get the number of sectors in the tag

        // Derive sector-specific keys for the tag
        val keys = deriveKeys(uid, sectorCount)

        // Dump tag data using the derived keys
        val tagData = dumpTagData(tag, keys)

        // Parse the tag data to extract filament type and color name
        val (filamentType, colorName) = parseTagDetails(tagData)

        // Update the UI with the parsed details
        detailsText.text = getString(R.string.details_text, uidHex, filamentType, colorName) // Display details
        colorSwatch.setBackgroundColor(getColorFromName(colorName)) // Update color swatch based on color name

        // Construct a sanitized file name for the dump
        val fileName = "${uidHex}-${sanitizeString("${filamentType}-${colorName}")}.bin" // Include UID, filament type, and color name
        Log.d("MainActivity", "Constructed file name: $fileName") // Log the file name

        // Build the complete dump data, including keys and tag data
        val fullDump = buildFullDump(tagData, keys)
        saveInternalDump(fileName, fullDump) // Save the dump internally

        // Save the key file with a matching name
        saveKeyFile(fileName, keys) // Save the key file

        // Refresh the list of existing dumps to include the new one
        displayExistingDumps()

        // Make the export button visible
        exportButton.isVisible = true
        currentDumpFileName = fileName // Update the current dump file name
        currentDumpContent = fullDump // Update the current dump content

        // Show a toast message indicating the dump was successfully created
        Toast.makeText(this, "Dump created: $fileName", Toast.LENGTH_SHORT).show()
    }

    private fun deriveKeys(uid: ByteArray, sectorCount: Int): List<ByteArray> {
        // Master key used for deriving sector-specific keys, obtained from the Python script
        val masterKey = byteArrayOf(
            0x9a.toByte(), 0x75.toByte(), 0x9c.toByte(), 0xf2.toByte(),
            0xc4.toByte(), 0xf7.toByte(), 0xca.toByte(), 0xff.toByte(),
            0x22.toByte(), 0x2c.toByte(), 0xb9.toByte(), 0x76.toByte(),
            0x9b.toByte(), 0x41.toByte(), 0xbc.toByte(), 0x96.toByte()
        )

        // Fixed context string for HKDF derivation, from the Python script
        val context = "RFID-A\u0000".toByteArray(Charsets.UTF_8)

        // Initialize the HKDFBytesGenerator with a SHA-256 digest
        val hkdf = HKDFBytesGenerator(SHA256Digest())
        val keys = mutableListOf<ByteArray>() // List to store derived keys

        // Calculate the total key length required (6 bytes per sector)
        val totalKeyLength = sectorCount * 6
        val derivedBuffer = ByteArray(totalKeyLength) // Buffer to hold the derived keys

        // Initialize the HKDF with the UID (salt), master key, and context
        hkdf.init(HKDFParameters(uid, masterKey, context))

        // Generate the derived keys and fill the buffer
        hkdf.generateBytes(derivedBuffer, 0, totalKeyLength)

        // Split the derived buffer into individual keys (6 bytes each) for each sector
        for (i in 0 until sectorCount) {
            val start = i * 6
            val key = derivedBuffer.copyOfRange(start, start + 6) // Extract a 6-byte key
            keys.add(key) // Add the key to the list
            Log.d("MainActivity", "Derived Key A for sector $i: ${key.joinToString("") { "%02X".format(it) }}") // Log the key
        }

        return keys // Return the list of derived keys
    }

    private fun dumpTagData(tag: Tag, keys: List<ByteArray>): ByteArray {
        // Get an instance of MifareClassic for the provided tag
        val mifare = MifareClassic.get(tag)

        // Create a ByteArray to hold the entire tag data, initialized to the tag size
        val tagData = ByteArray(mifare.size)

        // Use the MifareClassic instance within a try-with-resources block
        mifare.use {
            mifare.connect() // Connect to the tag

            // Loop through all sectors of the tag
            for (sector in 0 until mifare.sectorCount) {
                // Authenticate the sector using the provided key
                val authenticated = mifare.authenticateSectorWithKeyA(sector, keys[sector])
                if (!authenticated) {
                    // Throw an exception if authentication fails for a sector
                    throw IllegalArgumentException("Authentication failed for sector $sector")
                }

                // Loop through all blocks in the authenticated sector
                for (block in 0 until mifare.getBlockCountInSector(sector)) {
                    val absoluteBlockIndex = mifare.sectorToBlock(sector) + block // Get the absolute block index
                    val blockData = mifare.readBlock(absoluteBlockIndex) // Read the block data
                    System.arraycopy(blockData, 0, tagData, absoluteBlockIndex * 16, 16) // Copy the block data to the tagData array
                }
            }
        }

        return tagData // Return the complete tag data
    }

    private fun parseTagDetails(tagData: ByteArray): Pair<String, String> {
        // Check if the tag data contains enough bytes to extract the required details
        if (tagData.size < 80) {
            throw IllegalArgumentException("Insufficient data in tag dump") // Throw an exception if data is insufficient
        }

        // Block 4 contains the filament type
        val block4Start = 16 * 4 // Calculate the starting index of block 4
        val filamentTypeBytes = tagData.copyOfRange(block4Start, block4Start + 16) // Extract 16 bytes for the filament type
        val filamentType = String(filamentTypeBytes).trim() // Convert the bytes to a string and trim whitespace

        // Block 5 contains the color, interpreted as a name
        val block5Start = 16 * 5 // Calculate the starting index of block 5
        val colorBytes = tagData.copyOfRange(block5Start, block5Start + 4) // Extract 4 bytes for the color
        val colorName = getColorName(colorBytes) // Convert the color bytes to a readable name

        // Return the filament type and color name as a pair
        return Pair(filamentType, colorName)
    }

    private fun getColorName(colorBytes: ByteArray): String {
        // Extract RGB components from the color byte array
        val red = colorBytes[0].toInt() and 0xFF
        val green = colorBytes[1].toInt() and 0xFF
        val blue = colorBytes[2].toInt() and 0xFF

        // Map of predefined colors and their RGB values
        val predefinedColors = mapOf(
            "Jade White" to Color.rgb(255, 255, 255),
            "Beige" to Color.rgb(247, 230, 222),
            "Gold" to Color.rgb(228, 189, 104),
            "Silver" to Color.rgb(166, 169, 170),
            "Gray" to Color.rgb(142, 144, 137),
            "Bronze" to Color.rgb(132, 125, 72),
            "Brown" to Color.rgb(157, 67, 44),
            "Red" to Color.rgb(193, 46, 31),
            "Magenta" to Color.rgb(236, 0, 140),
            "Pink" to Color.rgb(245, 90, 116),
            "Orange" to Color.rgb(255, 106, 19),
            "Yellow" to Color.rgb(244, 238, 42),
            "Bambu Green" to Color.rgb(0, 174, 66),
            "Mistletoe Green" to Color.rgb(63, 142, 67),
            "Cyan" to Color.rgb(0, 134, 214),
            "Blue" to Color.rgb(10, 41, 137),
            "Purple" to Color.rgb(94, 67, 183),
            "Blue Gray" to Color.rgb(91, 101, 121),
            "Light Gray" to Color.rgb(209, 211, 213),
            "Dark Gray" to Color.rgb(84, 84, 84),
            "Black" to Color.rgb(0, 0, 0)
        )

        // Find the predefined color with the smallest Euclidean distance to the input color
        return predefinedColors.minByOrNull { (_, predefinedColor) ->
            val predefinedRed = Color.red(predefinedColor)
            val predefinedGreen = Color.green(predefinedColor)
            val predefinedBlue = Color.blue(predefinedColor)

            // Calculate Euclidean distance in RGB color space
            sqrt(
                (predefinedRed - red).toDouble().pow(2) +
                        (predefinedGreen - green).toDouble().pow(2) +
                        (predefinedBlue - blue).toDouble().pow(2)
            )
        }?.key ?: "Unknown" // Return the name of the closest color, or "Unknown" if no match is found
    }

    private fun getColorFromName(colorName: String): Int {
        // Return the RGB value of the color based on its name
        return when (colorName.lowercase()) {
            "jade white" -> Color.rgb(255, 255, 255)
            "beige" -> Color.rgb(247, 230, 222)
            "gold" -> Color.rgb(228, 189, 104)
            "silver" -> Color.rgb(166, 169, 170)
            "gray" -> Color.rgb(142, 144, 137)
            "bronze" -> Color.rgb(132, 125, 72)
            "brown" -> Color.rgb(157, 67, 44)
            "red" -> Color.rgb(193, 46, 31)
            "magenta" -> Color.rgb(236, 0, 140)
            "pink" -> Color.rgb(245, 90, 116)
            "orange" -> Color.rgb(255, 106, 19)
            "yellow" -> Color.rgb(244, 238, 42)
            "bambu green" -> Color.rgb(0, 174, 66)
            "mistletoe green" -> Color.rgb(63, 142, 67)
            "cyan" -> Color.rgb(0, 134, 214)
            "blue" -> Color.rgb(10, 41, 137)
            "purple" -> Color.rgb(94, 67, 183)
            "blue gray" -> Color.rgb(91, 101, 121)
            "light gray" -> Color.rgb(209, 211, 213)
            "dark gray" -> Color.rgb(84, 84, 84)
            "black" -> Color.rgb(0, 0, 0)
            else -> Color.rgb(128, 128, 128) // Default to medium gray for unknown colors
        }
    }

    private fun sanitizeString(input: String): String {
        // Remove all non-alphanumeric characters except spaces and dashes, then replace spaces with underscores
        return input.replace(Regex("[^a-zA-Z0-9 -]"), "").replace(Regex("\\s+"), "_")
    }

    private fun buildFullDump(tagData: ByteArray, keys: List<ByteArray>): ByteArray {
        val sectorSize = 16 * 4 // Each sector consists of 4 blocks, each block being 16 bytes
        val fullDump = tagData.toMutableList() // Convert the tag data to a mutable list for modification

        // Loop through all sectors and insert the respective keys
        for ((sectorIndex, key) in keys.withIndex()) {
            val keyPosition = sectorIndex * sectorSize + 48 // Keys are stored in the last block of each sector
            if (keyPosition + key.size <= fullDump.size) {
                // Copy the key bytes into the appropriate position in the full dump
                for (i in key.indices) {
                    fullDump[keyPosition + i] = key[i]
                }
            }
        }

        return fullDump.toByteArray() // Convert the modified list back to a byte array and return it
    }

    private fun saveInternalDump(fileName: String, content: ByteArray) {
        try {
            // Create a file in the app's internal storage directory
            val file = File(filesDir, fileName)

            // Write the content to the file
            file.outputStream().use { it.write(content) }

            // Log the success message with the file's absolute path
            Log.d("MainActivity", "Dump saved to internal storage: ${file.absolutePath}")
        } catch (e: Exception) {
            // Log an error message if saving the dump fails
            Log.e("MainActivity", "Failed to save internal dump: ${e.message}", e)
        }
    }

    private fun saveKeyFile(fileName: String, keys: List<ByteArray>) {
        val keyFileName = fileName.replace(".bin", ".dic") // Derive the key file name
        val rawKeyFileName = fileName.replace(".bin", "-key.bin") // Derive the raw key file name

        // Create a file in the app's internal storage directory for storing keys
        val keyFile = File(filesDir, keyFileName)

        // Write the keys to the file as hexadecimal strings, one per line
        keyFile.printWriter().use { writer ->
            keys.forEach { key ->
                val keyString = key.joinToString("") { "%02X".format(it) } // Convert key bytes to hexadecimal
                writer.println(keyString) // Write each key to a new line
            }
        }

        // Log the success message with the file's absolute path
        Log.d("MainActivity", "Key file saved: ${keyFile.absolutePath}")

        // Create a second file for raw byte data with padding
        val rawKeyFile = File(filesDir, rawKeyFileName)

        rawKeyFile.outputStream().use { outputStream ->
            // Write all raw keys first
            keys.forEach { key ->
                outputStream.write(key)
            }

            // Write all padding (00s) after the keys
            val totalPaddingSize = keys.sumOf { it.size }
            val padding = ByteArray(totalPaddingSize) { 0x00 }
            outputStream.write(padding)
        }

        // Log the success message for the raw file
        Log.d("MainActivity", "Raw key file saved: ${rawKeyFile.absolutePath}")
    }

    private fun toggleExistingDumps() {
        if (isDumpsListVisible) {
            // Hide the dumps list if it is currently visible
            dumpsListView.visibility = View.GONE
            isDumpsListVisible = false // Update the visibility state
        } else {
            // Display the dumps list if it is currently hidden
            displayExistingDumps() // Refresh the dumps list content
            dumpsListView.visibility = View.VISIBLE
            isDumpsListVisible = true // Update the visibility state
        }
    }

    private fun displayExistingDumps() {
        // Get the current list of dumps
        val dumpsDir = filesDir
        val dumps = dumpsDir.listFiles()
            ?.filter { it.name.endsWith(".bin") && !it.name.endsWith("-key.bin") }
            ?.map { it.name } ?: emptyList()

        // Always rebuild the adapter to ensure synchronization
        val updatedDumpNames = dumps.map { it.removeSuffix(".bin") } // Remove ".bin" from filenames
        dumpsListAdapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, updatedDumpNames) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView ?: layoutInflater.inflate(R.layout.dump_list_item, parent, false)
                val dumpNameTextView = view.findViewById<TextView>(R.id.dumpNameTextView)
                val deleteButton = view.findViewById<Button>(R.id.deleteButton)

                // Bind the dump name correctly
                dumpNameTextView.text = updatedDumpNames[position]

                // Handle delete button click
                deleteButton.setOnClickListener {
                    val fullFileName = dumps[position] // Get full filename
                    showDeleteConfirmation(fullFileName)
                }

                return view
            }
        }

        // Assign the new adapter to the ListView
        dumpsListView.adapter = dumpsListAdapter

        // Set the click listener for each dump
        dumpsListView.setOnItemClickListener { _, _, position, _ ->
            if (position < dumps.size) {
                loadDumpDetails(dumps[position])
            } else {
                Toast.makeText(this, "Invalid selection. Please refresh the list.", Toast.LENGTH_SHORT).show()
            }
        }

        // Show or hide the ListView based on the dumps list
        dumpsListView.visibility = if (dumps.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun loadDumpDetails(fileName: String) {
        try {
            // Create a File object for the specified file name
            val file = File(filesDir, fileName)

            // Check if the file exists, throw an exception if it doesn't
            if (!file.exists()) {
                throw IllegalArgumentException("File not found: $fileName")
            }

            // Read the content of the file as bytes
            val content = file.readBytes()

            // Parse the file content to extract filament type and color name
            val (filamentType, colorName) = parseTagDetails(content)

            // Update the UI with the parsed details
            detailsText.text = getString(R.string.details_text, fileName.split("-")[0], filamentType, colorName)
            colorSwatch.setBackgroundColor(getColorFromName(colorName)) // Set the color swatch background
            exportButton.isVisible = true // Make the export button visible

            // Update the current dump file and content references
            currentDumpFileName = fileName
            currentDumpContent = content

            // Show a success toast message
            Toast.makeText(this, "Loaded dump: $fileName", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            // Handle any exceptions by resetting the UI
            currentDumpFileName = null // Clear the current file name reference
            currentDumpContent = null // Clear the current content reference
            detailsText.text = "" // Clear the details text
            colorSwatch.setBackgroundColor(Color.TRANSPARENT) // Reset the color swatch
            exportButton.isVisible = false // Hide the export button

            // Show an error toast message
            Toast.makeText(this, "Error loading dump: ${e.message}", Toast.LENGTH_SHORT).show()

            // Log the error details for debugging purposes
            Log.e("MainActivity", "Error in loadDumpDetails: ${e.message}", e)
        }
    }

    private fun showDeleteConfirmation(fileName: String) {
        val baseName = fileName.substringBeforeLast('.') // Extract base name for the dialog

        AlertDialog.Builder(this)
            .setTitle("Delete Dump")
            .setMessage("Are you sure you want to delete $baseName?") // Show only the base name
            .setPositiveButton("Yes") { _, _ ->
                deleteDump(fileName) // Pass the full file name to deleteDump
            }
            .setNegativeButton("No", null) // Do nothing on cancellation
            .show()
    }

    // Function to delete a specified dump file and associated files
    private fun deleteDump(fileName: String) {
        try {
            // Extract the base name of the file (excluding the extension)
            val baseName = fileName.substringBeforeLast('.')

            // Get the directory containing the files
            val directory = filesDir

            // List all files in the directory
            val files = directory.listFiles()

            // Check if there are files in the directory
            if (files != null) {
                // Filter files that start with the base name
                val matchingFiles = files.filter { it.name.startsWith(baseName) }

                if (matchingFiles.isNotEmpty()) {
                    // Delete all matching files
                    matchingFiles.forEach { it.delete() }

                    // Show a success message
                    Toast.makeText(this, "$baseName and associated files deleted successfully", Toast.LENGTH_SHORT).show()

                    // Clear current dump details if the deleted dump was the one loaded
                    if (currentDumpFileName == fileName) {
                        currentDumpFileName = null // Clear the current file name reference
                        currentDumpContent = null // Clear the current content reference
                        detailsText.text = "" // Clear the details text
                        colorSwatch.setBackgroundColor(Color.TRANSPARENT) // Reset the color swatch
                        exportButton.isVisible = false // Hide the export button
                    }

                    // Refresh the list of dumps and update the adapter
                    displayExistingDumps()
                } else {
                    // Show a message if no matching files were found
                    Toast.makeText(this, "No files found matching $baseName", Toast.LENGTH_SHORT).show()
                }
            } else {
                // Show a message if the directory has no files
                Toast.makeText(this, "No files found in the directory", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            // Handle any exceptions and show an error message
            Toast.makeText(this, "Error deleting files: ${e.message}", Toast.LENGTH_SHORT).show()

            // Log the error details for debugging purposes
            Log.e("MainActivity", "Error deleting files: ${e.message}", e)
        }
    }

    private fun exportCurrentDump() {
        try {
            // Ensure a dump is currently loaded
            val dumpFileName = currentDumpFileName ?: throw IllegalStateException("No dump loaded")
            val keyFileName = dumpFileName.replace(".bin", ".dic") // Derive the key file name
            val rawKeyFileName = dumpFileName.replace(".bin", "-key.bin") // Derive the raw key file name

            // Locate the required files in internal storage
            val dumpFile = File(filesDir, dumpFileName)
            val keyFile = File(filesDir, keyFileName)
            val rawKeyFile = File(filesDir, rawKeyFileName)
            if (!dumpFile.exists() || !keyFile.exists() || !rawKeyFile.exists()) {
                throw IllegalStateException("Required files not found") // Throw error if files are missing
            }

            // Create a ZIP file containing the dump and key files
            val zipFile = createZipFile(dumpFileName, keyFileName, rawKeyFileName)

            // Generate a content URI for the ZIP file using FileProvider
            val uri = FileProvider.getUriForFile(
                this,
                "app.cherryduck.bambutagscanner.fileprovider", // FileProvider authority
                zipFile
            )

            // Create an intent to share the ZIP file
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip" // Set the MIME type to ZIP
                putExtra(Intent.EXTRA_STREAM, uri) // Attach the ZIP file URI
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) // Grant temporary read permission
            }

            // Launch the share intent with a chooser dialog
            startActivity(Intent.createChooser(shareIntent, "Export Dump and Keys"))
        } catch (e: Exception) {
            // Handle errors during export
            Toast.makeText(this, "Error exporting dump: ${e.message}", Toast.LENGTH_SHORT).show()
            Log.e("MainActivity", "Error in exportCurrentDump: ${e.message}", e) // Log the error
        }
    }

    private fun createZipFile(dumpFileName: String, keyFileName: String, rawKeyFileName: String): File {
        // Derive the ZIP file name from the dump file name
        val zipFileName = dumpFileName.replace(".bin", ".zip")
        val zipFile = File(cacheDir, zipFileName) // Create the ZIP file in the cache directory

        // Use a ZipOutputStream to create the ZIP file
        ZipOutputStream(zipFile.outputStream()).use { zipOut ->
            // List of files to be added to the ZIP archive, with their respective entry names
            val filesToZip = listOf(
                File(filesDir, dumpFileName) to dumpFileName, // Preserve the original dump file name
                File(filesDir, keyFileName) to keyFileName,  // Preserve the original key file name
                File(filesDir, rawKeyFileName) to rawKeyFileName  // Preserve the original raw key file name
            )

            // Iterate through the files and add them to the ZIP
            for ((file, zipEntryName) in filesToZip) {
                zipOut.putNextEntry(ZipEntry(zipEntryName)) // Create a new entry in the ZIP
                file.inputStream().use { it.copyTo(zipOut) } // Copy the file contents to the ZIP
                zipOut.closeEntry() // Close the current entry
            }
        }

        // Log the success of ZIP file creation
        Log.d("MainActivity", "ZIP file created: ${zipFile.absolutePath}")

        // Return the created ZIP file
        return zipFile
    }

}
