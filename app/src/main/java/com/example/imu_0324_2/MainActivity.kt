package com.example.imu_0324_2

import android.content.*
import android.os.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var tvWindowIndex: TextView
    private lateinit var tvStatus: TextView
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var btnDownload: Button

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val index = intent?.getIntExtra("index", 0) ?: 0
            val status = intent?.getStringExtra("status") ?: "알 수 없음"
            runOnUiThread {
                tvWindowIndex.text = "슬라이딩 윈도우 #$index"
                tvStatus.text = when (status) {
                    "위험" -> "🔴 상태: 위험"
                    "주의" -> "🟡 상태: 주의"
                    else -> "🟢 상태: 안전"
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvWindowIndex = findViewById(R.id.tvWindowIndex)
        tvStatus = findViewById(R.id.tvStatus)
        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)
        btnDownload = findViewById(R.id.btnDownload)

        btnStart.setOnClickListener {
            startForegroundService(Intent(this, IMUSensorService::class.java))
            Toast.makeText(this, "수집 시작", Toast.LENGTH_SHORT).show()
        }

        btnStop.setOnClickListener {
            stopService(Intent(this, IMUSensorService::class.java))
            Toast.makeText(this, "수집 중단", Toast.LENGTH_SHORT).show()
        }

        btnDownload.setOnClickListener {
            copyLatestCsvToDownloads()
        }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter("IMU_STATUS_UPDATE")
        LocalBroadcastManager.getInstance(this).registerReceiver(statusReceiver, filter)
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(statusReceiver)
    }

    private fun copyLatestCsvToDownloads() {
        val srcDir = getExternalFilesDir(null) ?: return
        val files = srcDir.listFiles { _, name -> name.startsWith("imu_risk_log") && name.endsWith(".csv") }
        val latestFile = files?.maxByOrNull { it.lastModified() }

        if (latestFile == null) {
            Toast.makeText(this, "CSV 파일이 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val destFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), latestFile.name)
        try {
            FileInputStream(latestFile).use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            Toast.makeText(this, "다운로드 폴더로 복사 완료!", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "복사 실패: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }
}
