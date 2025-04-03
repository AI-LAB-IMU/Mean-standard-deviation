package com.example.imu_0324_2

import android.app.*
import android.content.Context
import android.content.Intent
import android.hardware.*
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

class IMUSensorService : Service(), SensorEventListener {

    private val windowSize = 350
    private val stepSize = 175
    private val svmList = ArrayDeque<Double>()
    private val deltaList = ArrayDeque<Double>()
    private var sampleCounter = 0
    private var windowIndex = 0
    private var previousSVM: Double? = null

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private lateinit var csvWriter: FileWriter

    private var lastTimestamp: Long = 0L
    private var wakeLock: PowerManager.WakeLock? = null  // 🔋 슬립 모드 유지용

    override fun onCreate() {
        super.onCreate()

        // 🔋 Wakelock 설정 (슬립 모드에서도 CPU 유지)
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "IMU::WakeLock")
        wakeLock?.acquire()

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
        val fileName = "imu_risk_log_$timestamp.csv"
        val file = File(getExternalFilesDir(null), fileName)
        val isNew = file.createNewFile()
        csvWriter = FileWriter(file, true)
        if (isNew) {
            csvWriter.appendLine("WindowIndex,MeanSVM,MeanDeltaSVM,StdDeltaSVM,Status")
        }

        startForeground(1, createServiceNotification())

        accelerometer?.let {
            sensorManager.registerListener(this, it, 40000)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            sensorManager.registerListener(this, accelerometer, 40000)
        } else {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        }

        Log.d("IMU", "Service started")
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val now = System.currentTimeMillis()
        if (lastTimestamp != 0L) {
            val interval = now - lastTimestamp
            Log.d("IMU_FREQ", "센서 간격: ${interval}ms")
        }
        lastTimestamp = now

        val x = event.values[0].toDouble()
        val y = event.values[1].toDouble()
        val z = event.values[2].toDouble()
        val svm = sqrt(x * x + y * y + z * z)

        val delta = previousSVM?.let { abs(svm - it) } ?: 0.0
        previousSVM = svm

        svmList.add(svm)
        deltaList.add(delta)

        if (svmList.size > windowSize) svmList.removeFirst()
        if (deltaList.size > windowSize) deltaList.removeFirst()
        sampleCounter++

        if (svmList.size == windowSize && sampleCounter >= stepSize) {
            sampleCounter = 0
            val meanSVM = svmList.average()
            val meanDelta = deltaList.average()
            val stdDelta = stdDev(deltaList)

            val status = determineStatus(meanSVM, meanDelta, stdDelta)

            windowIndex++

            Log.i("IMU_WINDOW", "Window #$windowIndex ▶ 평균 SVM: %.2f, ΔSVM: %.2f, StdΔSVM: %.2f → 상태: $status"
                .format(meanSVM, meanDelta, stdDelta))

            val line = "$windowIndex,%.2f,%.2f,%.2f,$status".format(meanSVM, meanDelta, stdDelta)
            csvWriter.appendLine(line)

            sendStatusUpdate(windowIndex, status)

            if (status == "위험") {
                vibrateOnDanger()
                showDangerNotification(windowIndex, meanSVM)
            }
        }
    }

    private fun sendStatusUpdate(index: Int, status: String) {
        val intent = Intent("IMU_STATUS_UPDATE")
        intent.putExtra("index", index)
        intent.putExtra("status", status)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun vibrateOnDanger() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(600, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            vibrator.vibrate(600)
        }
        Log.d("IMU_ALERT", "🚨 진동 발생: 위험 상태 감지")
    }

    private fun showDangerNotification(windowIndex: Int, meanSVM: Double) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "danger_alert_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "위험 감지 알림", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
            }
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("🚨 위험 감지됨")
            .setContentText("Window #$windowIndex - SVM: %.2f".format(meanSVM))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        manager.notify(9999, notification)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        csvWriter.flush()
        csvWriter.close()
        wakeLock?.release()  // 🔋 슬립 모드 해제
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createServiceNotification(): Notification {
        val channelId = "imu_logger_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "IMU 수집 서비스", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("IMU 위험 감지 중")
            .setContentText("백그라운드에서 데이터를 수집합니다")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()
    }

    private fun stdDev(data: Collection<Double>): Double {
        val mean = data.average()
        return sqrt(data.map { (it - mean).pow(2) }.average())
    }

    private fun determineStatus(meanSVM: Double, meanDelta: Double, stdDelta: Double): String {
        return when {
            meanDelta >= 2.0 && stdDelta >= 2.5 && meanSVM >= 13.0 -> "위험"
            meanDelta >= 2.0 || stdDelta >= 2.5 || meanSVM >= 13.0 -> "주의"
            else -> "안전"
        }
    }
}
