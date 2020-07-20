package nl.jolanrensen.htmlcrown

import android.os.Bundle
import android.support.wearable.activity.WearableActivity
import android.support.wearable.input.RotaryEncoder
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.MotionEvent.ACTION_SCROLL
import com.soywiz.klock.milliseconds
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.*
import nl.jolanrensen.kHomeAssistant.*
import nl.jolanrensen.kHomeAssistant.core.KHomeAssistantInstance
import nl.jolanrensen.kHomeAssistant.core.Mode
import nl.jolanrensen.kHomeAssistant.domains.MediaPlayer
import java.lang.Exception
import java.lang.Math.floor
import java.lang.Thread.MAX_PRIORITY
import kotlin.concurrent.thread
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

class MainActivity : WearableActivity() {

    /** Fill in these for yourself */
    private val kha = KHomeAssistantInstance(
        host = "XXX", // TODO REMOVE
        accessToken = "XXX",
        port = 8123,
        secure = true,
        debug = true
    )

    private var connectionJob: Thread? = null

    private val mediaPlayer: MediaPlayer.Entity = kha.MediaPlayer["denon_avrx2200w"]

    private val soundMode1 = "MOVIE" // sound_mode that is activated upon button 1 press
    private val soundMode2 = "MUSIC" // sound_mode that is activated upon button 2 press

    private var running = false

    // In between 0 and 100
    @Volatile
    var progress: Float = -1f

    /** accepts values between 0 and 100, returns values between 0 and 1 with steps of 0.005 */
    private fun Float.roundForStereo() = (
            when {
                this > 100f -> 100f
                this < 0f -> 0f
                else -> this
            } * 2f
            ).roundToInt() / 2f / 100f


    private val main: FunctionalAutomation = automation("initial") {
        running = true
        progress = mediaPlayer.volume_level * 100f

        runEverySecond {
            try {
                val roundedProgress = progress.roundForStereo()
                if (mediaPlayer.volume_level != roundedProgress)
                    mediaPlayer.volume_level = roundedProgress
            } catch (e: Exception) {
            }
            updateVisual()
        }

        updateVisual()
    }

    private fun updateVisual() = MainScope().launch {
        try {
            volume.isIndeterminate = false
            volume.setProgress((mediaPlayer.volume_level * 100f).toInt(), true)
            percent.text = "${mediaPlayer.volume_level * 100f}%"
            progress = mediaPlayer.volume_level * 100f
        } catch (e: Exception) {
            volume.isIndeterminate = true
            percent.text = "Device is off."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Enables Always-on
        setAmbientEnabled()

        volume.isIndeterminate = true
        percent.text = "Loading..."

        connectionJob = thread(priority = MAX_PRIORITY) {
            runBlocking {
                kha.run(main, mode = Mode.KEEP_RUNNING)
            }
        }
    }

    override fun onDestroy() {
        running = false
        connectionJob?.stop()
        super.onDestroy()
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (!running) return true
        if (event.action == ACTION_SCROLL && RotaryEncoder.isFromRotaryEncoder(event)) {
            val delta = (
                    -RotaryEncoder.getRotaryAxisValue(event)
                            * RotaryEncoder.getScaledScrollFactor(applicationContext)
                    ) / 50

            progress += delta
            if (progress > 100f) progress = 100f
            if (progress < 0f) progress = 0f

            Log.d("HtmlCrown", "new progress is ${progress.roundForStereo()}")
            return true
        }
        return false
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent) =
        if (event.repeatCount == 0) {
            if (!running) super.onKeyDown(keyCode, event)
            else when (keyCode) {
                KeyEvent.KEYCODE_STEM_1 -> {
                    Log.d("HtmlCrown", "1 pressed")
                    runBlocking {
                        mediaPlayer.sound_mode = soundMode1
                    }
                    true
                }
                KeyEvent.KEYCODE_STEM_2 -> {
                    Log.d("HtmlCrown", "2 pressed")
                    runBlocking {
                        mediaPlayer.sound_mode = soundMode2
                    }
                    true
                }
                KeyEvent.KEYCODE_NAVIGATE_NEXT -> {
                    Log.d("HtmlCrown", "Wrist flick out")
                    runBlocking {
                        mediaPlayer.turnOff()
                        updateVisual()
                    }
                    true
                }
                KeyEvent.KEYCODE_NAVIGATE_PREVIOUS -> {
                    Log.d("HtmlCrown", "Wrist flick in")
                    runBlocking {
                        mediaPlayer.turnOn()
                        updateVisual()
                    }
                    true
                }
                else -> super.onKeyDown(keyCode, event)
            }
        } else {
            Log.d("HtmlCrown", "event: $event, repeatcount: ${event.repeatCount}")
            super.onKeyDown(keyCode, event)
        }
}

