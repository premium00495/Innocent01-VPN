package com.innocent.extremevpn

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.innocent.extremevpn.R
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class Tweak(val id: String, val title: String, val serverIp: String, val port: Int, val sni: String)
data class Network(val id: String, val name: String, val color: String, val tweaks: List<Tweak>)

class MainActivity : AppCompatActivity() {

    private val SERVER_URL = "http://94.237.70.217"
    private val VPN_REQUEST_CODE = 1001

    private val networksList = mutableListOf<Network>()
    private var selectedNetwork: Network? = null
    private var selectedTweak: Tweak? = null
    private var isConnected = false

    private lateinit var networkContainer: LinearLayout
    private lateinit var txtSelectedTweakName: TextView
    private lateinit var txtSelectedBugHost: TextView
    private lateinit var btnConnectToggle: Button
    private lateinit var txtWhatsappNum: TextView
    private lateinit var txtTelegramHandle: TextView
    private lateinit var txtPlanTitle: TextView
    private lateinit var txtUsedGB: TextView
    private lateinit var txtRemainingGB: TextView
    private lateinit var badgeStatus: TextView
    private lateinit var progressBarGB: ProgressBar

    private var whatsappLink = ""
    private var telegramLink = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        networkContainer = findViewById(R.id.networkContainer)
        txtSelectedTweakName = findViewById(R.id.txtSelectedTweakName)
        txtSelectedBugHost = findViewById(R.id.txtSelectedBugHost)
        btnConnectToggle = findViewById(R.id.btnConnectToggle)
        txtWhatsappNum = findViewById(R.id.txtWhatsappNum)
        txtTelegramHandle = findViewById(R.id.txtTelegramHandle)
        txtPlanTitle = findViewById(R.id.txtPlanTitle)
        txtUsedGB = findViewById(R.id.txtUsedGB)
        txtRemainingGB = findViewById(R.id.txtRemainingGB)
        badgeStatus = findViewById(R.id.badgeStatus)
        progressBarGB = findViewById(R.id.progressBarGB)

        fetchConfigAndNetworks()

        findViewById<LinearLayout>(R.id.btnSelectTweak).setOnClickListener {
            showTweakSelectionDialog()
        }

        btnConnectToggle.setOnClickListener {
            if (!isConnected) prepareAndConnectVPN() else disconnectVPN()
        }

        findViewById<TextView>(R.id.btnTrialPrompt).setOnClickListener {
            showCodeInputDialog()
        }

        findViewById<LinearLayout>(R.id.cardWhatsapp).setOnClickListener {
            if (whatsappLink.isNotEmpty()) startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(whatsappLink)))
        }

        findViewById<LinearLayout>(R.id.cardTelegram).setOnClickListener {
            if (telegramLink.isNotEmpty()) startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(telegramLink)))
        }
    }

    private fun fetchConfigAndNetworks() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val confRes = URL("$SERVER_URL/api/config").readText()
                val confJson = JSONObject(confRes)
                if (confJson.getBoolean("success")) {
                    val d = confJson.getJSONObject("data")
                    val wa = d.getString("whatsappNumber")
                    val tg = d.getString("telegramHandle")
                    whatsappLink = "https://api.whatsapp.com/send?phone=$wa"
                    telegramLink = "https://t.me/${tg.replace("@", "")}"

                    withContext(Dispatchers.Main) {
                        txtWhatsappNum.text = wa
                        txtTelegramHandle.text = tg
                    }
                }

                val netRes = URL("$SERVER_URL/api/networks").readText()
                val netJson = JSONObject(netRes)
                val netArray = netJson.getJSONArray("networks")

                networksList.clear()
                for (i in 0 until netArray.length()) {
                    val obj = netArray.getJSONObject(i)
                    val tweaksArr = obj.getJSONArray("tweaks")
                    val tweaksList = mutableListOf<Tweak>()

                    for (j in 0 until tweaksArr.length()) {
                        val t = tweaksArr.getJSONObject(j)
                        tweaksList.add(Tweak(
                            t.getString("id"),
                            t.getString("title"),
                            t.getString("serverIp"),
                            t.getInt("port"),
                            t.getString("sni")
                        ))
                    }

                    networksList.add(Network(
                        obj.getString("id"),
                        obj.getString("name"),
                        obj.getString("badgeColor"),
                        tweaksList
                    ))
                }

                withContext(Dispatchers.Main) {
                    renderNetworkButtons()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun renderNetworkButtons() {
        networkContainer.removeAllViews()
        for (net in networksList) {
            val btn = Button(this).apply {
                text = net.name
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor(net.color))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(8, 0, 8, 0)
                }
                setOnClickListener {
                    selectedNetwork = net
                    if (net.tweaks.isNotEmpty()) setTweak(net.tweaks[0])
                    Toast.makeText(this@MainActivity, "${net.name} Selected", Toast.LENGTH_SHORT).show()
                }
            }
            networkContainer.addView(btn)
        }

        if (networksList.isNotEmpty()) {
            selectedNetwork = networksList[0]
            if (networksList[0].tweaks.isNotEmpty()) setTweak(networksList[0].tweaks[0])
        }
    }

    private fun setTweak(tweak: Tweak) {
        selectedTweak = tweak
        txtSelectedTweakName.text = tweak.title
        txtSelectedBugHost.text = "Bug / SNI: ${tweak.sni}"
    }

    private fun showTweakSelectionDialog() {
        if (selectedNetwork == null || selectedNetwork!!.tweaks.isEmpty()) {
            Toast.makeText(this, "Select Network First", Toast.LENGTH_SHORT).show()
            return
        }

        val titles = selectedNetwork!!.tweaks.map { it.title }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("${selectedNetwork!!.name} - Select Bug Tweak")
            .setItems(titles) { _, which -> setTweak(selectedNetwork!!.tweaks[which]) }
            .show()
    }

    private fun showCodeInputDialog() {
        val input = EditText(this)
        input.hint = "Enter VIP / Trial Key"
        AlertDialog.Builder(this)
            .setTitle("Activate Access")
            .setView(input)
            .setPositiveButton("Activate") { _, _ ->
                val code = input.text.toString().trim()
                if (code.isNotEmpty()) verifyCode(code)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun verifyCode(code: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("$SERVER_URL/api/user/verify-code")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    doOutput = true
                }
                val body = JSONObject().put("code", code).toString()
                conn.outputStream.write(body.toByteArray())

                val res = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(res)

                if (json.getBoolean("success")) {
                    val d = json.getJSONObject("data")
                    withContext(Dispatchers.Main) {
                        txtPlanTitle.text = "${d.getString("totalGB")} GB Plan"
                        txtUsedGB.text = "${d.getString("usedGB")} GB"
                        txtRemainingGB.text = "${d.getString("remainingGB")} GB"
                        badgeStatus.text = "ACTIVE • ${d.getString("accountType")}"
                        badgeStatus.setTextColor(Color.parseColor("#10B981"))

                        val used = d.getString("usedGB").toDouble()
                        val total = d.getString("totalGB").toDouble()
                        progressBarGB.progress = ((used / total) * 100).toInt()

                        Toast.makeText(this@MainActivity, "Access Activated!", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Invalid or Expired Key!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun prepareAndConnectVPN() {
        if (selectedTweak == null) {
            Toast.makeText(this, "Select a tweak first!", Toast.LENGTH_SHORT).show()
            return
        }

        val vpnIntent = VpnService.prepare(this)
        if (vpnIntent != null) {
            startActivityForResult(vpnIntent, VPN_REQUEST_CODE)
        } else {
            onActivityResult(VPN_REQUEST_CODE, Activity.RESULT_OK, null)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VPN_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            val intent = Intent(this, InnocentVpnService::class.java).apply {
                putExtra("SERVER_IP", selectedTweak?.serverIp ?: "")
                putExtra("SNI_HOST", selectedTweak?.sni ?: "")
                putExtra("PORT", selectedTweak?.port ?: 443)
            }
            startService(intent)

            isConnected = true
            btnConnectToggle.text = "DISCONNECT"
            btnConnectToggle.setBackgroundColor(Color.parseColor("#DC2626"))
            Toast.makeText(this, "Connected: ${selectedTweak?.title}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun disconnectVPN() {
        val intent = Intent(this, InnocentVpnService::class.java).apply {
            action = "STOP"
        }
        startService(intent)

        isConnected = false
        btnConnectToggle.text = "CONNECT"
        btnConnectToggle.setBackgroundColor(Color.parseColor("#7C3AED"))
        Toast.makeText(this, "Disconnected", Toast.LENGTH_SHORT).show()
    }
}
