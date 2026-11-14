package ai.venice.nfccards

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.cardemulation.HostApduService
import org.json.JSONObject

class HceService : HostApduService() {

    companion object {
        private val SW_OK = byteArrayOf(0x90.toByte(), 0x00)
        private val SW_NOT_FOUND = byteArrayOf(0x6A.toByte(), 0x82.toByte())
        private val SW_WRONG = byteArrayOf(0x67.toByte(), 0x00.toByte())
        private val SW_STATE = byteArrayOf(0x69.toByte(), 0x86.toByte())
        private val SW_INS = byteArrayOf(0x6D.toByte(), 0x00.toByte())
        private val CC = byteArrayOf(
            0x00, 0x0F, 0x20, 0x00, 0x3B, 0x00, 0x34, 0x04,
            0x06, 0xE1.toByte(), 0x04, 0x00, 0x00, 0x00, 0x00
        )
    }

    private var state = 0
    private var ndefFile = ByteArray(0)

    override fun processCommand(apdu: ByteArray): ByteArray {
        if (apdu.size < 4) return SW_INS
        val cla = apdu[0].toInt() and 0xFF
        val ins = apdu[1].toInt() and 0xFF
        val p1 = apdu[2].toInt() and 0xFF
        val p2 = apdu[3].toInt() and 0xFF

        if (cla == 0x00 && ins == 0xA4) {
            if (p1 == 0x04) {
                if (apdu.size < 5) return SW_WRONG
                val len = apdu[4].toInt() and 0xFF
                if (apdu.size < 5 + len) return SW_WRONG
                val name = apdu.copyOfRange(5, 5 + len).joinToString("") { "%02X".format(it) }
                return if (name.startsWith("D27600008501")) { state = 1; ndefFile = buildNdef(); SW_OK } else SW_NOT_FOUND
            }
            if (p1 == 0x00 && p2 == 0x0C && apdu.size >= 7) {
                val fid = apdu.copyOfRange(5, 7).joinToString("") { "%02X".format(it) }
                state = when (fid) { "E103" -> 2; "E104" -> 3; else -> return SW_NOT_FOUND }
                return SW_OK
            }
            return SW_NOT_FOUND
        }

        if (cla == 0x00 && ins == 0xB0) {
            val off = (p1 shl 8) or p2
            val le = if (apdu.size >= 5) apdu[4].toInt() and 0xFF else 0
            val src = when (state) { 2 -> CC; 3 -> ndefFile; else -> return SW_STATE }
            if (le == 0) return SW_WRONG
            if (off >= src.size) return byteArrayOf(0x6B.toByte(), 0x00)
            return src.copyOfRange(off, minOf(off + le, src.size)) + SW_OK
        }

        if (cla == 0x00 && ins == 0xD6) return SW_OK
        return SW_INS
    }

    private fun buildNdef(): ByteArray {
        val json = JSONObject()
            .put("app", "nfc-cards")
            .put("id", Store.selfId(this))
            .put("name", Store.selfName(this))
            .put("bal", Store.selfBalance(this))
            .put("ver", 1)
            .toString()
        val msg = NdefMessage(arrayOf(NdefRecord.createTextRecord("en", json))).toByteArray()
        val out = ByteArray(msg.size + 2)
        out[0] = ((msg.size shr 8) and 0xFF).toByte()
        out[1] = (msg.size and 0xFF).toByte()
        System.arraycopy(msg, 0, out, 2, msg.size)
        return out
    }

    override fun onDeactivated(reason: Int) { state = 0 }
}