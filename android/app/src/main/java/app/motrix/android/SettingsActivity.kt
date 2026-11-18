package app.motrix.android

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import app.motrix.android.engine.Engine
import app.motrix.android.engine.EngineSettings

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        setSupportActionBar(findViewById<MaterialToolbar>(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.settings)

        val prefs = Prefs(this)
        val s = prefs.loadSettings()

        val inputDir = findViewById<TextInputEditText>(R.id.inputDir)
        val inputConcurrent = findViewById<TextInputEditText>(R.id.inputConcurrent)
        val inputDlLimit = findViewById<TextInputEditText>(R.id.inputDlLimit)
        val inputUlLimit = findViewById<TextInputEditText>(R.id.inputUlLimit)
        val inputSeed = findViewById<TextInputEditText>(R.id.inputSeed)
        val inputPort = findViewById<TextInputEditText>(R.id.inputPort)
        val inputSecret = findViewById<TextInputEditText>(R.id.inputSecret)
        val inputMaxConn = findViewById<TextInputEditText>(R.id.inputMaxConn)
        val inputSplit = findViewById<TextInputEditText>(R.id.inputSplit)
        val inputBtPeers = findViewById<TextInputEditText>(R.id.inputBtPeers)
        val inputListen = findViewById<TextInputEditText>(R.id.inputListenPort)
        val switchDht = findViewById<MaterialSwitch>(R.id.switchDht)

        inputDir.setText(s.downloadDir)
        inputConcurrent.setText(s.maxConcurrent.toString())
        inputDlLimit.setText(s.maxOverallDownloadLimitKb.toString())
        inputUlLimit.setText(s.maxOverallUploadLimitKb.toString())
        inputSeed.setText(s.seedTime.toString())
        inputPort.setText(s.rpcPort.toString())
        inputSecret.setText(s.rpcSecret)
        inputMaxConn.setText(s.maxConnectionPerServer.toString())
        inputSplit.setText(s.split.toString())
        inputBtPeers.setText(s.btMaxPeers.toString())
        inputListen.setText("${s.listenPortStart}-${s.listenPortEnd}")
        switchDht.isChecked = s.dhtEnabled

        findViewById<Button>(R.id.btnDefaultDir).setOnClickListener {
            inputDir.setText(prefs.defaultDir())
        }

        fun collect(): EngineSettings {
            val lp = inputListen.text?.toString()?.trim().orEmpty()
            val ps = lp.split("-")
            val ls = ps.getOrNull(0)?.toIntOrNull()?.coerceIn(1, 65535) ?: 6881
            val le = ps.getOrNull(1)?.toIntOrNull()?.coerceIn(ls, 65535) ?: 6999
            return EngineSettings(
                downloadDir = inputDir.text?.toString()?.trim().takeUnless { it.isNullOrBlank() } ?: prefs.defaultDir(),
                maxConcurrent = inputConcurrent.text?.toString()?.toIntOrNull()?.coerceIn(1, 16) ?: 5,
                maxOverallDownloadLimitKb = inputDlLimit.text?.toString()?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
                maxOverallUploadLimitKb = inputUlLimit.text?.toString()?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
                seedTime = inputSeed.text?.toString()?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
                rpcPort = inputPort.text?.toString()?.toIntOrNull() ?: 16800,
                rpcSecret = inputSecret.text?.toString()?.takeUnless { it.isNullOrBlank() } ?: "motrix",
                maxConnectionPerServer = inputMaxConn.text?.toString()?.toIntOrNull()?.coerceIn(1, 16) ?: 16,
                split = inputSplit.text?.toString()?.toIntOrNull()?.coerceIn(1, 16) ?: 16,
                btMaxPeers = inputBtPeers.text?.toString()?.toIntOrNull()?.coerceIn(1, 512) ?: 128,
                dhtEnabled = switchDht.isChecked,
                listenPortStart = ls,
                listenPortEnd = le
            )
        }

        findViewById<Button>(R.id.btnSave).setOnClickListener {
            val ns = collect(); prefs.save(ns)
            Engine.applyGlobalLimit(ns.maxOverallDownloadLimitKb, ns.maxOverallUploadLimitKb, ns.maxConcurrent)
            Toast.makeText(this, R.string.saved_need_restart, Toast.LENGTH_LONG).show()
            finish()
        }

        findViewById<Button>(R.id.btnRestart).setOnClickListener {
            val ns = collect(); prefs.save(ns)
            Toast.makeText(this, R.string.save_restart, Toast.LENGTH_SHORT).show()
            Engine.restart { finish() }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
