package com.carmooddj.app

import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.carmooddj.app.signals.NaviSignals
import com.carmooddj.app.signals.VehicleSignals

class MainActivity : ComponentActivity() {

    private val state = DjState()
    private val synth = SynthPlayer()
    private val main = Handler(Looper.getMainLooper())
    private var vehicle: VehicleSignals? = null
    private var navi: NaviSignals? = null

    /** 리스너를 놓쳐도 값이 맞도록 주기적으로 현재 값을 다시 읽습니다. */
    private val poll = object : Runnable {
        override fun run() {
            vehicle?.refresh()
            navi?.refresh()
            main.postDelayed(this, POLL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestAudioFocus()
        synth.start()
        setContent { DjApp(state, synth) }
        startVehicle()
        startNavi()
        main.postDelayed(poll, 800)
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        vehicle?.release()
        navi?.release()
        synth.stop()
        super.onDestroy()
    }

    /**
     * 차량 신호를 반영하고 선곡을 바로 바꿉니다.
     * 주행 중 화면이 가려져도(화면 갱신이 멈춰도) 음악 전환은 계속되도록
     * 화면 코드가 아닌 여기서 분위기를 판단합니다.
     */
    private fun updateReal(change: (Signals) -> Signals) {
        state.real = change(state.real)
        if (!state.demoMode) {
            val mood = MoodRules.decide(state.real)
            if (mood != lastMood) {
                Log.i(TAG, "mood -> $mood (${state.real})")
                lastMood = mood
            }
            synth.setMood(mood)
        }
    }

    private var lastMood: Mood? = null

    private fun requestAudioFocus() {
        try {
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attributes)
                .build()
            getSystemService(AudioManager::class.java)?.requestAudioFocus(request)
        } catch (e: Exception) {
            Log.w(TAG, "audio focus request failed", e)
        }
    }

    private fun startVehicle() {
        val signals = VehicleSignals(this, object : VehicleSignals.Callback {
            override fun onWiper(on: Boolean?) {
                main.post { updateReal { it.copy(wiperOn = on) } }
            }

            override fun onTemperature(celsius: Float?) {
                main.post { updateReal { it.copy(tempC = celsius) } }
            }

            override fun onHeadlight(on: Boolean?) {
                main.post { updateReal { it.copy(headlightOn = on) } }
            }

            override fun onStatus(message: String?) {
                Log.i(TAG, "vehicle: $message")
                main.post { state.vehicleStatus = message ?: "" }
            }

            override fun onDiag(key: String?, message: String?) {
                if (key == null) return
                main.post { state.diag = state.diag + (key to (message ?: "")) }
            }
        })
        vehicle = signals
        signals.register()
    }

    private fun startNavi() {
        val signals = NaviSignals(this, object : NaviSignals.Callback {
            override fun onRoute(active: Boolean, remainingMin: Int?) {
                main.post {
                    updateReal { it.copy(navActive = active, remainingMin = remainingMin) }
                }
            }

            override fun onStatus(message: String) {
                Log.i(TAG, "navi: $message")
                main.post { state.naviStatus = message }
            }

            override fun onRaw(text: String) {
                main.post { state.naviRaw = text }
            }

            override fun onTrack(line: String) {
                main.post { state.naviTrack = (state.naviTrack + line).takeLast(6) }
            }
        })
        navi = signals
        signals.initialize()
    }

    private companion object {
        const val TAG = "CarDJ"
        const val POLL_MS = 2000L
    }
}
