
package com.mycios.nfcwriter

import android.app.Activity
import android.nfc.*
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import java.nio.charset.Charset
import java.util.Locale
import kotlin.math.min

class NfcTextActivity : Activity(), NfcAdapter.ReaderCallback {

    private enum class Mode { IDLE, READ, WRITE }

    private var nfcAdapter: NfcAdapter? = null
    private lateinit var inputText: EditText
    private lateinit var status: TextView
    private lateinit var lastRead: TextView
    private lateinit var tagInfo: TextView
    private lateinit var charCount: TextView

    @Volatile private var mode: Mode = Mode.IDLE
    @Volatile private var pendingText: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nfc_text)

        inputText = findViewById(R.id.inputText)
        status = findViewById(R.id.status)
        lastRead = findViewById(R.id.lastRead)
        tagInfo = findViewById(R.id.tagInfo)
        charCount = findViewById(R.id.charCount)

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter == null) {
            toast("NFC not available on this device")
            finish(); return
        }

        findViewById<Button>(R.id.btnWrite).setOnClickListener {
            val text = inputText.text.toString()
            if (text.isEmpty()) {
                toast("Enter some text to write")
                return@setOnClickListener
            }
            pendingText = text
            mode = Mode.WRITE
            setStatus("Write mode: hold a tag to the phone… (tag stays rewritable)")
        }

        findViewById<Button>(R.id.btnRead).setOnClickListener {
            mode = Mode.READ
            setStatus("Read mode: hold a tag to the phone…")
        }

        findViewById<Button>(R.id.btnClear).setOnClickListener {
            inputText.setText("")
            lastRead.text = "Last read: (tap a tag)"
            tagInfo.text = "Tag: (tap to read or write)"
            setStatus("cleared")
        }

        // Live character count
        updateCharCount()
        inputText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateCharCount()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun updateCharCount() {
        val count = inputText.text?.length ?: 0
        charCount.text = "$count characters"
    }

    override fun onResume() {
        super.onResume()
        nfcAdapter?.enableReaderMode(
            this, this,
            NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or
            NfcAdapter.FLAG_READER_NFC_V or
            NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
            null
        )
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableReaderMode(this)
        mode = Mode.IDLE
    }

    override fun onTagDiscovered(tag: Tag) {
        try {
            when (mode) {
                Mode.WRITE -> {
                    val text = pendingText ?: return
                    val msg = NdefMessage(arrayOf(createTextRecord(text)))
                    writeNdef(tag, msg)
                    val info = buildTagInfo(tag)
                    runOnUiThread {
                        tagInfo.text = info
                        setStatus("Write OK ✅ (${preview(text)}) — tag NOT locked, you can rewrite anytime.")
                        mode = Mode.IDLE
                    }
                }
                Mode.READ -> {
                    val res = readTextFromTag(tag)
                    val info = buildTagInfo(tag)
                    runOnUiThread {
                        tagInfo.text = info
                        if (res != null) {
                            lastRead.text = "Last read:\n$res"
                            setStatus("Read OK ✅")
                        } else {
                            lastRead.text = "Last read: No NDEF Text found"
                            setStatus("No NDEF Text record on tag")
                        }
                        mode = Mode.IDLE
                    }
                }
                else -> { /* idle */ }
            }
        } catch (e: Exception) {
            runOnUiThread { setStatus("NFC error: ${e.message}") }
            mode = Mode.IDLE
        }
    }

    // ---------- Tag info helpers ----------

    private fun buildTagInfo(tag: Tag): String {
        val uid = bytesToHex(tag.id)
        val techs = tag.techList.joinToString(", ") { it.substringAfterLast('.') }
        val sb = StringBuilder("Tag UID: $uid\nTech: $techs")

        // Try to add size/writable if NDEF
        try {
            val ndef = Ndef.get(tag)
            if (ndef != null) {
                ndef.connect()
                sb.append("\nNDEF size: ${ndef.maxSize} bytes")
                sb.append("\nWritable: ${ndef.isWritable}")
                ndef.close()
            }
        } catch (_: Exception) {}
        return sb.toString()
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val hexArray = "0123456789ABCDEF".toCharArray()
        val hexChars = CharArray(bytes.size * 2)
        for (j in bytes.indices) {
            val v = bytes[j].toInt() and 0xFF
            hexChars[j * 2] = hexArray[v ushr 4]
            hexChars[j * 2 + 1] = hexArray[v and 0x0F]
        }
        return String(hexChars)
    }

    // ---------- NDEF helpers ----------

    /** Create a Well-Known Text record (RTD_TEXT) per NFC Forum spec. */
    private fun createTextRecord(text: String, lang: String = Locale.getDefault().language): NdefRecord {
        val languageCode = (lang.ifBlank { "en" }).lowercase(Locale.ROOT)
        val langBytes = languageCode.toByteArray(Charset.forName("US-ASCII"))
        val textBytes = text.toByteArray(Charset.forName("UTF-8"))

        val payload = ByteArray(1 + langBytes.size + textBytes.size)
        payload[0] = langBytes.size.toByte() // bit7=0 means UTF-8
        System.arraycopy(langBytes, 0, payload, 1, langBytes.size)
        System.arraycopy(textBytes, 0, payload, 1 + langBytes.size, textBytes.size)

        return NdefRecord(
            NdefRecord.TNF_WELL_KNOWN,
            NdefRecord.RTD_TEXT,
            ByteArray(0),
            payload
        )
    }

    private fun writeNdef(tag: Tag, message: NdefMessage) {
        val ndef = Ndef.get(tag)
        if (ndef != null) {
            ndef.connect()
            if (!ndef.isWritable) {
                ndef.close()
                throw IllegalStateException("Tag is not writable")
            }
            val size = message.toByteArray().size
            if (size > ndef.maxSize) {
                ndef.close()
                throw IllegalStateException("Message too large for this tag (${size}B > ${ndef.maxSize}B)")
            }
            ndef.writeNdefMessage(message)
            // Not locking: keep rewritable
            ndef.close(); return
        }

        val format = NdefFormatable.get(tag)
        if (format != null) {
            format.connect()
            format.format(message) // formats & writes
            format.close(); return
        }

        throw IllegalStateException("Tag doesn't support NDEF")
    }

    private fun readTextFromTag(tag: Tag): String? {
        val ndef = Ndef.get(tag) ?: return null
        ndef.connect()
        val msg = ndef.cachedNdefMessage ?: ndef.ndefMessage
        ndef.close()
        if (msg == null) return null

        val texts = mutableListOf<String>();
        for (rec in msg.records) {
            if (rec.tnf == NdefRecord.TNF_WELL_KNOWN && rec.type.contentEquals(NdefRecord.RTD_TEXT)) {
                decodeTextRecord(rec)?.let { texts.add(it) }
            }
        }
        return if (texts.isNotEmpty()) texts.joinToString("\n") else null
    }

    private fun decodeTextRecord(record: NdefRecord): String? {
        return try {
            val payload = record.payload
            if (payload.isEmpty()) return ""
            val status = payload[0].toInt()
            val isUtf16 = (status and 0x80) != 0
            val langLen = status and 0x3F
            val textStart = 1 + langLen
            if (textStart > payload.size) return null
            val encoding = if (isUtf16) "UTF-16" else "UTF-8"
            String(payload, textStart, payload.size - textStart, Charset.forName(encoding))
        } catch (_: Exception) { null }
    }

    // ---------- UI helpers ----------

    private fun setStatus(msg: String) { status.text = "Status: $msg" }

    private fun preview(s: String, max: Int = 60): String {
        val end = min(max, s.length)
        return if (s.length <= max) s else s.substring(0, end) + "…"
    }

    private fun toast(msg: String) = runOnUiThread {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
