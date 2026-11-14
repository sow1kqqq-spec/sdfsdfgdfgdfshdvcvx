package ai.venice.nfccards

import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import ai.venice.nfccards.databinding.ActivityDetailBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DetailActivity : AppCompatActivity() {

    companion object { const val EXTRA_ID = "card_id" }

    private lateinit var b: ActivityDetailBinding
    private var nfc: NfcAdapter? = null
    private var card: Card? = null
    private var pendingWrite: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(b.root)
        nfc = NfcAdapter.getDefaultAdapter(this)

        val found = intent.getStringExtra(EXTRA_ID)?.let { Store.findById(this, it) }
        if (found == null) { finish(); return }
        card = found

        b.btnBack.setOnClickListener { finish() }
        b.uidText.text = found.uid
        b.bannerTitle.text = found.label
        b.meta.text = "Считано: " + SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("ru")).format(Date(found.createdAt))
        b.balance.text = "Баланс: " + Store.fmt(found.balance) + "   Тариф: " + Store.fmt(found.tariff)
        renderRecords(found.records)

        b.btnPass.setOnClickListener {
            Store.addLog(this, PassEvent(System.currentTimeMillis(), found.uid, found.id, true, 0.0))
            b.banner.setBackgroundResource(R.drawable.bg_banner_ok)
            b.bannerTitle.text = "ПРОХОД ЗАПИСАН"
        }
        b.btnMoney.setOnClickListener { moneyDialog() }
        b.btnRename.setOnClickListener { askName() }
        b.btnWrite.setOnClickListener { askWrite() }
        b.btnDelete.setOnClickListener {
            AlertDialog.Builder(this).setMessage("Удалить из базы?")
                .setPositiveButton("Удалить") { _, _ -> Store.delete(this, found.id); finish() }
                .setNegativeButton("Отмена", null).show()
        }
    }

    private fun moneyDialog() {
        val c = card ?: return
        val ll = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 24, 48, 0) }
        val top = EditText(this).apply { inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL; hint = "Пополнить на" }
        val tar = EditText(this).apply { inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL; setText(Store.fmt(c.tariff)) }
        ll.addView(top); ll.addView(tar)
        AlertDialog.Builder(this).setTitle("Баланс и тариф").setView(ll)
            .setPositiveButton("Сохранить") { _, _ ->
                val add = top.text.toString().toDoubleOrNull() ?: 0.0
                val t = tar.text.toString().toDoubleOrNull() ?: 0.0
                val upd = c.copy(balance = c.balance + add, tariff = t)
                Store.upsert(this, upd)
                card = upd
                b.balance.text = "Баланс: " + Store.fmt(upd.balance) + "   Тариф: " + Store.fmt(upd.tariff)
            }
            .setNegativeButton("Отмена", null).show()
    }

    private fun renderRecords(recs: List<Record>) {
        b.recordsContainer.removeAllViews()
        if (recs.isEmpty()) { b.recordsContainer.addView(cyan("NDEF-записей нет. Доступен только UID.")); return }
        for (r in recs) {
            b.recordsContainer.addView(cyan("[" + r.type + "]"))
            val tv = TextView(this)
            tv.text = r.value
            tv.setTextColor(0xFFE6EDF3.toInt())
            tv.textSize = 12f
            tv.typeface = android.graphics.Typeface.MONOSPACE
            tv.setTextIsSelectable(true)
            tv.setPadding(0, 0, 0, dp(12))
            b.recordsContainer.addView(tv)
        }
    }

    private fun cyan(t: String) = TextView(this).apply {
        text = t; setTextColor(0xFF22D3EE.toInt()); textSize = 12f; setPadding(0, dp(8), 0, dp(4))
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun askName() {
        val c = card ?: return
        val input = EditText(this).apply { setText(c.label) }
        AlertDialog.Builder(this).setTitle("Название").setView(input)
            .setPositiveButton("Сохранить") { _, _ ->
                val nl = input.text.toString().ifBlank { c.label }
                Store.upsert(this, c.copy(label = nl))
                b.bannerTitle.text = nl
            }
            .setNegativeButton("Отмена", null).show()
    }

    private fun askWrite() {
        val input = EditText(this).apply { inputType = InputType.TYPE_CLASS_TEXT; hint = "Текст записи" }
        AlertDialog.Builder(this).setTitle("Записать на метку").setMessage("Нажми Готово и приложи карту").setView(input)
            .setPositiveButton("Готово") { _, _ ->
                pendingWrite = input.text.toString()
                Toast.makeText(this, "Приложи карту к телефону", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Отмена", null).show()
    }

    override fun onResume() {
        super.onResume()
        val opts = Bundle().apply { putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250) }
        nfc?.enableReaderMode(this, ::onTag,
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V, opts)
    }

    override fun onPause() { super.onPause(); nfc?.disableReaderMode(this) }

    private fun onTag(tag: Tag) {
        val text = pendingWrite ?: return
        pendingWrite = null
        val res = TagReader.writeText(tag, text)
        val recs = TagReader.parse(tag)
        runOnUiThread {
            Toast.makeText(this, res, Toast.LENGTH_LONG).show()
            if (res == "Записано") { renderRecords(recs); card?.let { Store.upsert(this, it.copy(records = recs)) } }
        }
    }
}