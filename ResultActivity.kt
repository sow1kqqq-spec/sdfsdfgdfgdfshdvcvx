package ai.venice.nfccards

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import ai.venice.nfccards.databinding.ActivityResultBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ResultActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CARD_ID = "card_id"
        const val EXTRA_VERDICT = "verdict"
        const val EXTRA_REASON = "reason"
        const val EXTRA_CHARGED = "charged"
    }

    private lateinit var b: ActivityResultBinding
    private var card: Card? = null
    private var verdict = "DENY"
    private var reason = ""
    private var charged = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityResultBinding.inflate(layoutInflater)
        setContentView(b.root)

        card = intent.getStringExtra(EXTRA_CARD_ID)?.let { Store.findById(this, it) }
        val c = card ?: run { finish(); return }
        verdict = intent.getStringExtra(EXTRA_VERDICT) ?: "DENY"
        reason = intent.getStringExtra(EXTRA_REASON) ?: ""
        charged = intent.getDoubleExtra(EXTRA_CHARGED, 0.0)

        b.label.text = c.label
        b.uid.text = c.uid
        b.time.text = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale("ru")).format(Date())
        paint()

        b.btnConfirm.setOnClickListener {
            Store.addLog(this, PassEvent(System.currentTimeMillis(), c.uid, c.id, true, charged))
            Toast.makeText(this, "Проход подтверждён", Toast.LENGTH_SHORT).show()
        }
        b.btnCharge.setOnClickListener { chargeDialog() }
        b.btnCopy.setOnClickListener {
            (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                .setPrimaryClip(ClipData.newPlainText("payload", b.payload.text))
            Toast.makeText(this, "JSON скопирован", Toast.LENGTH_SHORT).show()
        }
        b.btnClose.setOnClickListener { finish() }
    }

    private fun paint() {
        val c = card ?: return
        val allow = verdict == "ALLOW"
        b.banner.setBackgroundResource(if (allow) R.drawable.bg_banner_ok else R.drawable.bg_banner_bad)
        b.verdict.text = if (allow) "ДОСТУП РАЗРЕШЁН" else "ОТКАЗ"
        b.verdict.setTextColor(if (allow) 0xFF22C55E.toInt() else 0xFFEF4444.toInt())
        b.reason.text = reason
        b.balance.text = "Баланс: " + Store.fmt(c.balance) + "   Тариф: " + Store.fmt(c.tariff)
        b.payload.text = Payload.build(c, verdict, reason, charged)
    }

    private fun chargeDialog() {
        val c = card ?: return
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = "Сумма"
        }
        AlertDialog.Builder(this).setTitle("Списать произвольно").setView(input)
            .setPositiveButton("Списать") { _, _ ->
                val v = input.text.toString().toDoubleOrNull() ?: return@setPositiveButton
                if (v <= 0) return@setPositiveButton
                charged += v
                val updated = c.copy(balance = c.balance - v)
                Store.upsert(this, updated)
                card = updated
                Store.addLog(this, PassEvent(System.currentTimeMillis(), c.uid, c.id, true, v))
                reason = "списано " + Store.fmt(v) + ", остаток " + Store.fmt(updated.balance)
                verdict = "ALLOW"
                b.label.text = updated.label
                paint()
            }
            .setNegativeButton("Отмена", null).show()
    }
}