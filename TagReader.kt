package ai.venice.nfccards

import android.nfc.NdefRecord
import android.nfc.Tag
import android.nfc.tech.MifareClassic
import android.nfc.tech.Ndef
import android.nfc.tech.NfcA
import java.nio.charset.Charset

object TagReader {

    private val DEFAULT_KEYS = listOf(
        "FFFFFFFFFFFF", "A0A1A2A3A4A5", "D3F7D3F7D3F7", "000000000000",
        "B0B1B2B3B4B5", "4D3A99C351DD", "1A982C7E459A", "AABBCCDDEEFF"
    )

    fun uidHex(tag: Tag): String = tag.id.joinToString(" ") { "%02X".format(it) }

    fun parse(tag: Tag): List<Record> {
        val out = mutableListOf<Record>()
        try {
            Ndef.get(tag)?.cachedNdefMessage?.records?.forEach { r ->
                when {
                    r.tnf == NdefRecord.TNF_WELL_KNOWN && r.type.contentEquals(NdefRecord.RTD_TEXT) ->
                        out += Record("text", decodeText(r.payload))
                    r.tnf == NdefRecord.TNF_WELL_KNOWN && r.type.contentEquals(NdefRecord.RTD_URI) ->
                        out += Record("url", decodeUri(r.payload))
                    r.tnf == NdefRecord.TNF_MIME_MEDIA ->
                        out += Record("mime:" + String(r.type, Charsets.UTF_8), String(r.payload, Charsets.UTF_8))
                    else -> out += Record("tnf" + r.tnf, hex(r.payload))
                }
            }
        } catch (e: Exception) { }

        val mc = MifareClassic.get(tag)
        if (mc != null) {
            try {
                mc.connect()
                val sectors = mc.sectorCount
                var opened = 0
                for (s in 0 until sectors) {
                    var authed = false
                    for (key in DEFAULT_KEYS) {
                        try { if (mc.authenticateSectorWithKeyA(s, key.hexToBytes())) { authed = true; break } } catch (e: Exception) { }
                    }
                    if (!authed) { out += Record("сектор $s", "закрыт (ключ не подобран)"); continue }
                    opened++
                    val sb = StringBuilder()
                    for (b in 0 until mc.getBlockCountInSector(s)) {
                        val idx = mc.sectorToBlock(s) + b
                        val data = try { mc.readBlock(idx) } catch (e: Exception) { continue }
                        sb.append("blk %02d: ".format(idx)).append(hex(data)).append("  |").append(ascii(data)).append("|\n")
                    }
                    out += Record("сектор $s", sb.toString().trim())
                }
                out += Record("итог", "открыто секторов: $opened из $sectors")
            } catch (e: Exception) {
                out += Record("ошибка", e.message ?: "нет доступа")
            } finally { try { mc.close() } catch (e: Exception) { } }
        } else if (NfcA.get(tag) != null) {
            out += Record("тип", "NfcA — доступен только UID")
        }
        out += Record("технологии", tag.techList.map { it.substringAfterLast('.') }.joinToString(", "))
        return out
    }

    fun writeText(tag: Tag, text: String): String {
        val ndef = Ndef.get(tag) ?: return "Метка не поддерживает NDEF"
        return try {
            ndef.connect()
            ndef.writeNdefMessage(android.nfc.NdefMessage(arrayOf(NdefRecord.createTextRecord("ru", text))))
            ndef.close()
            "Записано"
        } catch (e: Exception) { "Ошибка записи: ${e.message}" }
    }

    private fun decodeText(p: ByteArray): String {
        val st = p[0].toInt() and 0xFF
        val ll = st and 0x3F
        val enc = if (st and 0x80 != 0) Charset.forName("UTF-16") else Charsets.UTF_8
        return String(p, 1 + ll, p.size - 1 - ll, enc)
    }

    private fun decodeUri(p: ByteArray): String {
        val prefixes = arrayOf("", "http://www.", "https://www.", "http://", "https://", "tel:", "mailto:")
        val c = p[0].toInt() and 0xFF
        return (if (c < prefixes.size) prefixes[c] else "") + String(p, 1, p.size - 1, Charsets.UTF_8)
    }

    private fun hex(b: ByteArray) = b.joinToString(" ") { "%02X".format(it) }
    private fun ascii(b: ByteArray) = b.joinToString("") { val i = it.toInt() and 0xFF; if (i in 32..126) i.toChar().toString() else "." }
    private fun String.hexToBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}