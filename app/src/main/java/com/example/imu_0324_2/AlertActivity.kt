package com.example.imu_0324_2

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.TextView

class AlertActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ✅ 슬립 상태에서 화면 자동 켜짐
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        setContentView(R.layout.activity_alert)

        // 위험 메시지 표시
        val index = intent.getIntExtra("windowIndex", -1)
        val svm = intent.getDoubleExtra("meanSVM", -1.0)
        findViewById<TextView>(R.id.tvAlertMessage).text =
            "🚨 위험 감지됨!\n\nWindow #$index\nSVM: %.2f".format(svm)
    }

}
