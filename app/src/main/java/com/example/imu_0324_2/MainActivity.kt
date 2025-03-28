package com.example.imu_0324_2

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.graphics.Color
import android.widget.TextView
import kotlin.math.*

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    private lateinit var tvStatus: TextView
    private lateinit var tvAlert: TextView
    private lateinit var tvCount: TextView

    private val sampleInterval = 40L
    private val windowSize = 350
    private val slideStep = 175

    private val deltaBuffer = ArrayDeque<Double>()
    private val smoothBuffer = ArrayDeque<Double>()

    private var previousSvm: Double? = null
    private var lastUpdate: Long = 0
    private var emergencyMode = false
    private var handler = Handler(Looper.getMainLooper())
    private var slideCounter = 0
    private var dangerCounter = 0
    private var timerStartTime: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        tvAlert = findViewById(R.id.tvAlert)
        tvCount = findViewById(R.id.tvcount)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    override fun onResume() {
        super.onResume()
        accelerometer?.let {
            sensorManager.registerListener(this, it, 40000)
        }
        timerStartTime = System.currentTimeMillis()
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastUpdate < sampleInterval) return
        lastUpdate = currentTime

        val x = event.values[0].toDouble()
        val y = event.values[1].toDouble()
        val z = event.values[2].toDouble()
        val svm = sqrt(x.pow(2) + y.pow(2) + z.pow(2))

        val deltaSvm = previousSvm?.let { abs(svm - it) } ?: 0.0
        previousSvm = svm

        if (deltaBuffer.size >= windowSize) deltaBuffer.removeFirst()
        deltaBuffer.addLast(deltaSvm)

        if (smoothBuffer.size >= 5) smoothBuffer.removeFirst()
        smoothBuffer.addLast(deltaSvm)
        val smoothedDelta = smoothBuffer.average()

        val stats = computeStatistics(deltaBuffer)
        val (meanSVM, sdSVM, _, _) = stats

        val elapsedSec = (System.currentTimeMillis() - timerStartTime) / 1000.0

        tvStatus.text = """
            Time: ${"%.1f".format(elapsedSec)}s
            SVM = ${"%.2f".format(svm)}
            ΔSVM = ${"%.2f".format(smoothedDelta)}
            μ = ${"%.2f".format(meanSVM)}, σ = ${"%.2f".format(sdSVM)}
        """.trimIndent()

        if (deltaBuffer.size >= windowSize) {
            slideCounter++
            if (slideCounter >= slideStep) {
                slideCounter = 0
                timerStartTime = System.currentTimeMillis()

                val isDanger = meanSVM > 3.6 && sdSVM > 3.3
                val isWarning = meanSVM > 3.3 && sdSVM > 2.8
                val isAttention = meanSVM > 1.5

                if (isDanger) {
                    dangerCounter++
                } else {
                    dangerCounter = 0
                }

                when {
                    dangerCounter >= 2 -> {
                        if (!emergencyMode) {
                            emergencyMode = true
                            tvAlert.setBackgroundColor(Color.parseColor("#FF4444"))
                            tvAlert.setTextColor(Color.BLACK)
                            tvAlert.text = "🚨 긴급 모드 활성화"
                            tvCount.text = "긴급2차"
                            handler.postDelayed({
                                emergencyMode = false
                                tvAlert.text = ""
                                tvCount.text = ""
                                tvAlert.setBackgroundColor(Color.parseColor("#22CCCCCC"))
                            }, 5000)
                        }
                    }

                    dangerCounter == 1 -> {
                        if (!emergencyMode) {
                            tvAlert.setBackgroundColor(Color.parseColor("#FFBB33"))
                            tvAlert.setTextColor(Color.BLACK)
                            tvAlert.text = "⚠ 강한 움직임 감지"
                            tvCount.text = "긴급1차"
                        }
                    }

                    isWarning || isAttention -> {
                        if (!emergencyMode) {
                            tvAlert.setBackgroundColor(Color.parseColor("#33B5E5"))
                            tvAlert.setTextColor(Color.BLACK)
                            tvAlert.text = "🔵 주의: 움직임 감지"
                            tvCount.text = ""
                        }
                    }

                    else -> {
                        if (!emergencyMode) {
                            tvAlert.setBackgroundColor(Color.parseColor("#22CCCCCC"))
                            tvAlert.setTextColor(Color.BLACK)
                            tvAlert.text = "✔ 안전"
                            tvCount.text = ""
                        }
                    }
                }
            }
        }
    }

    private fun computeStatistics(data: Collection<Double>): Quadruple {
        val mean = data.average()
        val std = sqrt(data.map { (it - mean).pow(2) }.average())
        val sorted = data.sorted()
        val q1 = sorted[sorted.size / 4]
        val q3 = sorted[(sorted.size * 3) / 4]
        return Quadruple(mean, std, q1, q3)
    }

    data class Quadruple(
        val mean: Double,
        val std: Double,
        val q1: Double,
        val q3: Double
    )

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
