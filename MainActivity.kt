package ai.venice.nfccards

import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.os.Vibrator
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import ai.venice.nfccards.databinding.ActivityMainBinding
import org.json.JSONObject
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
        b.btnSelf.setOnClickListener { selfDialog() }
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
        b.cardsList.adapter = CardAdapter(cards, { c -> open(c) }, { c ->
            AlertDialog.Builder(this).setMessage("Удалить «" + c.label + "»?")
                .setPositiveButton("Удалить") { _, _ -> Store.delete(this, c.id); refresh() }
                .setNegativeButton("Отмена", null).show()
        })
        b.empty.visibility = if (cards.isEmpty()) View.VISIBLE else View.GONE
        b.selfState.text = if (Store.hceOn(this)) "ЭМУЛЯЦИЯ ВКЛ" else "ЭМУЛЯЦИЯ ВЫКЛ"
    }

    private fun open(card: Card) {
        startActivity(Intent(this, DetailActivity::class.java).putExtra(DetailActivity.EXTRA_ID, card.id))
    }

    private fun showResult(card: Card, allow: Boolean, reason: String, charged: Double) {
        val i = Intent(this, ResultActivity::class.java)
        i.putExtra(ResultActivity.EXTRA_CARD_ID, card.id)
        i.putExtra(ResultActivity.EXTRA_VERDICT, if (allow) "ALLOW" else "DENY")
        i.putExtra(ResultActivity.EXTRA_REASON, reason)
        i.putExtra(ResultActivity.EXTRA_CHARGED, charged)
        startActivity(i)
    }

    private fun showLog() {
        val logs = Store.logs(this)
        val fmt = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale("ru"))
        val text = if (logs.isEmpty()) "Проходов нет" else logs.take(100).joinToString("\n\n") {
            val c = it.cardId?.let { id -> Store.findById(this, id) }
            val money = if (it.amount > 0) "  -" + Store.fmt(it.amount) else ""
            (if (it.ok) "ПРОХОД  " else "ОТКАЗ  ") + (c?.label ?: it.uid) + money + "\n" + fmt.format(Date(it.ts))
        }
        AlertDialog.Builder(this).setTitle("Журнал").setMessage(text)
            .setPositiveButton("Закрыть", null)
            .setNeutralButton("Очистить") { _, _ -> Store.clearLogs(this) }.show()
    }

    private fun selfDialog() {
        val ll = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 24, 48, 0) }
        val name = EditText(this).apply { setText(Store.selfName(this@MainActivity)); hint = "Имя владельца" }
        val sw = Switch(this).apply { text = "Эмулировать карту телефоном"; isChecked = Store.hceOn(this@MainActivity) }
        val info = TextView(this).apply {
            text = "ID: " + Store.selfId(this@MainActivity) + "\nБаланс: " + Store.fmt(Store.selfBalance(this@MainActivity)) +
                "\nТариф: " + Store.fmt(Store.defaultTariff(this@MainActivity))
            setPadding(0, 24, 0, 0); textSize = 12f
        }
        ll.addView(name); ll.addView(sw); ll.addView(info)

        AlertDialog.Builder(this).setTitle("Моя карта").setView(ll)
            .setPositiveButton("Сохранить") { _, _ ->
                Store.setSelfName(this, name.text.toString().ifBlank { "Ученик" })
                Store.setHceOn(this, sw.isChecked)
                refresh()
                Toast.makeText(this, if (sw.isChecked) "Телефон работает как карта" else "Эмуляция выключена", Toast.LENGTH_LONG).show()
            }
            .setNeutralButton("Пополнить") { _, _ -> topUpDialog() }
            .setNegativeButton("Тариф") { _, _ -> tariffDialog() }
            .show()
    }

    private fun topUpDialog() {
        val input = EditText(this).apply { inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL; hint = "Сумма" }
        AlertDialog.Builder(this).setTitle("Пополнить баланс").setView(input)
            .setPositiveButton("Пополнить") { _, _ ->
                val v = input.text.toString().toDoubleOrNull() ?: return@setPositiveButton
                Store.setSelfBalance(this, Store.selfBalance(this) + v)
                Toast.makeText(this, "Баланс: " + Store.fmt(Store.selfBalance(this)), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Отмена", null).show()
    }

    private fun tariffDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(Store.fmt(Store.defaultTariff(this@MainActivity)))
        }
        AlertDialog.Builder(this).setTitle("Тариф за проход").setView(input)
            .setPositiveButton("Сохранить") { _, _ -> Store.setDefaultTariff(this, input.text.toString().toDoubleOrNull() ?: 0.0); refresh() }
            .setNegativeButton("Отмена", null).show()
    }

    override fun onResume() {
        super.onResume()
        refresh()
        val opts = Bundle().apply { putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250) }
        nfc?.enableReaderMode(this, ::onTag,
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V, opts)
    }

    override fun onPause() { super.onPause(); nfc?.disableReaderMode(this); endScan() }

    private fun onTag(tag: Tag) {
        if (!scanning) return
        val rawUid = TagReader.uidHex(tag)
        val recs = TagReader.parse(tag)

        var uid = rawUid
        var label: String? = null
        var kind = "метка"
        var balance = 0.0
        for (r in recs) {
            if (r.type == "text" && r.value.startsWith("{\"app\":\"nfc-cards\"")) {
                try {
                    val o = JSONObject(r.value)
                    uid = "PHONE:" + o.optString("id")
                    label = o.optString("name", "Телефон")
                    kind = "телефон"
                    balance = o.optDouble("bal", 0.0)
                } catch (e: Exception) { }
                break
            }
        }

        val existing = Store.findByUid(this, uid)
        runOnUiThread {
            endScan()
            buzz(60)
            val card: Card
            val allow: Boolean
            var reason: String
            var charged = 0.0

            if (existing == null) {
                card = Store.newCard(uid, label ?: ("Метка " + uid.take(11)), kind, recs, balance, Store.defaultTariff(this))
                Store.upsert(this, card)
                allow = false
                reason = "не зарегистрирована - добавлена в базу"
                beep(320, 240)
                Store.addLog(this, PassEvent(System.currentTimeMillis(), uid, card.id, false, 0.0))
            } else {
                val t = if (existing.tariff > 0) existing.tariff else Store.defaultTariff(this)
                if (t > 0 && existing.balance < t) {
                    card = existing
                    allow = false
                    reason = "недостаточно средств: " + Store.fmt(existing.balance) + " из " + Store.fmt(t)
                    beep(320, 240)
                    Store.addLog(this, PassEvent(System.currentTimeMillis(), uid, card.id, false, 0.0))
                } else {
                    charged = t
                    card = existing.copy(balance = existing.balance - t)
                    Store.upsert(this, card)
                    allow = true
                    reason = if (t > 0) "списано " + Store.fmt(t) + ", остаток " + Store.fmt(card.balance) else "проход без оплаты"
                    beep(1200, 150)
                    Store.addLog(this, PassEvent(System.currentTimeMillis(), uid, card.id, true, t))
                }
            }
            refresh()
            showResult(card, allow, reason, charged)
        }
    }

    private fun beep(f: Int, ms: Int) {
        try { tone?.startTone(if (f > 700) ToneGenerator.TONE_PROP_BEEP2 else ToneGenerator.TONE_PROP_NACK, ms) } catch (e: Exception) { }
    }

    private fun buzz(ms: Long) {
        try { (getSystemService(VIBRATOR_SERVICE) as Vibrator).vibrate(ms) } catch (e: Exception) { }
    }
}