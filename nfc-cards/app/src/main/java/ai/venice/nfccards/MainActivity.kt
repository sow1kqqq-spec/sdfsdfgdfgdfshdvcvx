package ai.venice.nfccards

import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.os.Vibrator
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import ai.venice.nfccards.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private var nfc: NfcAdapter? = null
    private var tone: ToneGenerator? = null
    private var scanning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        nfc = NfcAdapter.getDefaultAdapter(this)
        tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)

        b.cardsList.layoutManager = LinearLayoutManager(this)

        b.btnConnect.setOnClickListener { beginScan() }
        b.btnCancel.setOnClickListener { endScan() }
        b.btnLog.setOnClickListener { showLog() }
        b.btnClear.setOnClickListener {
            AlertDialog.Builder(this).setMessage("Очистить историю карт?")
                .setPositiveButton("Да") { _, _ -> Store.saveCards(this, emptyList()); refresh() }
                .setNegativeButton("Нет", null).show()
        }

        if (nfc == null) Toast.makeText(this, "На устройстве нет NFC", Toast.LENGTH_LONG).show()
        refresh()
    }

    private fun beginScan() {
        if (nfc == null) { Toast.makeText(this, "NFC недоступен", Toast.LENGTH_SHORT).show(); return }
        if (nfc?.isEnabled == false) {
            Toast.makeText(this, "Включи NFC в шторке", Toast.LENGTH_LONG).show()
            startActivity(Intent(android.provider.Settings.ACTION_NFC_SETTINGS))
            return
        }
        scanning = true
        b.overlay.visibility = View.VISIBLE
        b.btnConnect.isEnabled = false
        beep(880, 60); buzz(35)
    }

    private fun endScan() {
        scanning = false
        b.overlay.visibility = View.GONE
        b.btnConnect.isEnabled = true
    }

    private fun refresh() {
        val cards = Store.cards(this)
        b.cardsList.adapter = CardAdapter(
            cards,
            { c -> open(c) },
            { c ->
                AlertDialog.Builder(this).setMessage("Удалить «${c.label}»?")
                    .setPositiveButton("Удалить") { _, _ -> Store.delete(this, c.id); refresh() }
                    .setNegativeButton("Отмена", null).show()
            }
        )
        b.empty.visibility = if (cards.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun open(card: Card) {
        startActivity(Intent(this, DetailActivity::class.java).putExtra(DetailActivity.EXTRA_ID, card.id))
    }

    private fun showLog() {
        val logs = Store.logs(this)
        val fmt = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale("ru"))
        val text = if (logs.isEmpty()) "Проходов нет" else logs.take(100).joinToString("\n\n") {
            val c = it.cardId?.let { id -> Store.findById(this, id) }
            (if (it.ok) "ПРОХОД  " else "ОТКАЗ  ") + (c?.label ?: it.uid) + "\n" + fmt.format(Date(it.ts))
        }
        AlertDialog.Builder(this)
            .setTitle("Журнал")
            .setMessage(text)
            .setPositiveButton("Закрыть", null)
            .setNeutralButton("Очистить") { _, _ -> Store.clearLogs(this) }
            .show()
    }

    override fun onResume() {
        super.onResume()
        refresh()
        val opts = Bundle().apply { putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250) }
        nfc?.enableReaderMode(
            this, ::onTag,
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V, opts
        )
    }

    override fun onPause() {
        super.onPause()
        nfc?.disableReaderMode(this)
        endScan()
    }

    private fun onTag(tag: Tag) {
        if (!scanning) return
        val uid = TagReader.uidHex(tag)
        val recs = TagReader.parse(tag)
        runOnUiThread {
            endScan()
            beep(1200, 150); buzz(60)
            val card = Store.newCard(uid, "Метка " + uid.take(11), "метка", recs)
            Store.upsert(this, card)
            Store.addLog(this, PassEvent(System.currentTimeMillis(), uid, card.id, true))
            refresh()
            open(card)
        }
    }

    private fun beep(f: Int, ms: Int) {
        try { tone?.startTone(if (f > 700) ToneGenerator.TONE_PROP_BEEP2 else ToneGenerator.TONE_PROP_NACK, ms) } catch (e: Exception) { }
    }

    private fun buzz(ms: Long) {
        try { (getSystemService(VIBRATOR_SERVICE) as Vibrator).vibrate(ms) } catch (e: Exception) { }
    }
}
