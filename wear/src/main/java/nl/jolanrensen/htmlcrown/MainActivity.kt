package nl.jolanrensen.htmlcrown

import android.os.Bundle
import android.support.wearable.activity.WearableActivity
import android.support.wearable.input.RotaryEncoder
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.MotionEvent.ACTION_SCROLL
import com.github.kittinunf.fuel.Fuel
import com.google.gson.JsonParser
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.concurrent.thread
import kotlin.math.floor

class MainActivity : WearableActivity() {

    /** Fill in these for yourself */
    val HAUrl = "XXXXXX" // aka "https://yourserver.com"
    val HAPassword = "XXXXXX"
    val mediaPlayer = "XXXXXX" // the name of the media_player you want to control
    val soundMode1 = "MOVIE" // sound_mode that is activated upon button 1 press
    val soundMode2 = "MUSIC" // sound_mode that is activated upon button 2 press

    @Volatile
    var progress: Float = 0f

    @Volatile
    var oldRoundedProgress: Int = 0 // 0 to 200
    @Volatile
    var roundedProgress: Int = 0 // 0 to 200

    @Volatile
    var oldStereoVolume: Int = -1 // 0 to 200

    @Volatile
    var runThread = true
    @Volatile
    var calling = false

    @Volatile
    var downloadingPaused = false

    @Volatile
    var waitUntilDownload = 0

    fun pauseDownloading() {
        if (!downloadingPaused) Log.d("HtmlCrown", "Downloading paused")
        waitUntilDownload = 500
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Enables Always-on
        setAmbientEnabled()

        volume.isIndeterminate = true
        percent.text = "Loading..."

        // pause download thread thread
        GlobalScope.launch {
            while (runThread) {
                if (waitUntilDownload == 0) {
                    if (downloadingPaused) {
                        downloadingPaused = false
                        Log.d("HtmlCrown", "Downloading resumed")
                    }
                }
                if (!runThread) break
                while (waitUntilDownload != 0) {
                    downloadingPaused = true
                    delay(1)
                    waitUntilDownload--
                    if (!runThread) break
                }
            }
        }

        // download thread
        thread(start = true) {
            while (runThread) {
                if (!downloadingPaused) {
                    if (!calling) {
                        calling = true
                        GlobalScope.launch {
                            Fuel.get("$HAUrl/api/states/media_player.$mediaPlayer")
                                .header("X-HA-Access" to HAPassword)
                                .responseString { request, response, result ->
                                    val (string, error) = result
                                    if (error != null) {
                                        Log.e("HtmlCrown", error.toString())
                                        return@responseString
                                    }
                                    val json = JsonParser().parse(string).asJsonObject
                                    if (json["state"].asString == "off") {
                                        oldStereoVolume = -1
                                        runOnUiThread {
                                            volume.isIndeterminate = true
                                            percent.text = "Device is off"
                                        }
                                    } else {
                                        val rawVolumeStereo =
                                            json["attributes"].asJsonObject["volume_level"].asDouble.toFloat() * 100f
                                        val volumeStereo = floor(rawVolumeStereo * 2f).toInt()
                                        if (volumeStereo != oldStereoVolume) {
                                            oldStereoVolume = volumeStereo
                                            Log.d(
                                                "HtmlCrown",
                                                "Read device volume as ${volumeStereo / 2.0}"
                                            )

                                            roundedProgress = volumeStereo
                                            oldRoundedProgress = roundedProgress
                                            progress = roundedProgress / 2f
                                            runOnUiThread {
                                                percent.text = "${roundedProgress / 2.0}%"
                                                volume.isIndeterminate = false
                                                volume.progress = roundedProgress / 2
                                            }
                                        }
                                    }
                                    calling = false
                                }
                        }
                    }
                }
            }
        }

        // upload thread
        thread(start = true) {
            while (runThread) {
                if (!calling) {
                    if (oldRoundedProgress != roundedProgress) { // if we changed volume locally
                        oldRoundedProgress = roundedProgress
                        calling = true
                        pauseDownloading()
                        GlobalScope.launch {
                            Fuel.post("$HAUrl/api/services/media_player/volume_set")
                                .header("X-HA-Access" to HAPassword)
                                .jsonBody(
                                    jsonObjectOf(
                                        "entity_id" to "media_player.$mediaPlayer",
                                        "volume_level" to "${roundedProgress / 200.0}"
                                    ).toString()
                                )
                                .response { request, response, result ->
                                    //Log.d("HtmlCrown", "request: $request\nresponse: $response")
                                    val (bytes, error) = result
                                    if (bytes != null) {
                                        //Log.d("HtmlCrown", String(bytes))
                                    }
                                    Log.d(
                                        "HtmlCrown",
                                        "Set volume of device to ${roundedProgress / 2.0}"
                                    )
                                    calling = false
                                }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        runThread = false
        super.onDestroy()
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.action == ACTION_SCROLL && RotaryEncoder.isFromRotaryEncoder(event)) {
            pauseDownloading()
            val delta =
                (-RotaryEncoder.getRotaryAxisValue(event) * RotaryEncoder.getScaledScrollFactor(
                    applicationContext
                )) / 50

            progress += delta
            if (progress > 100f) progress = 100f
            if (progress < 0f) progress = 0f

            val oldRoundedProcess = roundedProgress
            roundedProgress = floor(progress * 2).toInt()

            if (oldRoundedProcess != roundedProgress) {
                percent.text = "${roundedProgress / 2.0}%"
                volume.progress = roundedProgress / 2
            }

            return true
        }
        return false
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent) =
        if (event.repeatCount == 0) {
            when (keyCode) {
                KeyEvent.KEYCODE_STEM_1 -> {
                    Log.d("HtmlCrown", "1 pressed")
                    GlobalScope.launch {
                        Fuel.post("$HAUrl/api/services/media_player/select_sound_mode")
                            .header("X-HA-Access" to HAPassword)
                            .jsonBody(
                                jsonObjectOf(
                                    "entity_id" to "media_player.$mediaPlayer",
                                    "sound_mode" to soundMode1
                                ).toString()
                            )
                            .response { request, response, result ->
                                //Log.d("HtmlCrown", "request: $request\nresponse: $response")
                                val (bytes, error) = result
                                if (bytes != null) {
                                    //Log.d("HtmlCrown", String(bytes))
                                }
                            }
                    }
                    true
                }
                KeyEvent.KEYCODE_STEM_2 -> {
                    Log.d("HtmlCrown", "2 pressed")
                    GlobalScope.launch {
                        Fuel.post("$HAUrl/api/services/media_player/select_sound_mode")
                            .header("X-HA-Access" to HAPassword)
                            .jsonBody(
                                jsonObjectOf(
                                    "entity_id" to "media_player.$mediaPlayer",
                                    "sound_mode" to soundMode2
                                ).toString()
                            )
                            .response { request, response, result ->
                                //Log.d("HtmlCrown", "request: $request\nresponse: $response")
                                val (bytes, error) = result
                                if (bytes != null) {
                                    //Log.d("HtmlCrown", String(bytes))
                                }
                            }
                    }
                    true
                }
                KeyEvent.KEYCODE_NAVIGATE_NEXT -> {
                    Log.d("HtmlCrown", "Wrist flick out")
                    GlobalScope.launch {
                        Fuel.post("$HAUrl/api/services/media_player/turn_off")
                            .header("X-HA-Access" to HAPassword)
                            .jsonBody(
                                jsonObjectOf(
                                    "entity_id" to "media_player.$mediaPlayer"
                                ).toString()
                            )
                            .response { request, response, result ->
                                //Log.d("HtmlCrown", "request: $request\nresponse: $response")
                                val (bytes, error) = result
                                if (bytes != null) {
                                    //Log.d("HtmlCrown", String(bytes))
                                }
                            }
                    }
                    true
                }
                KeyEvent.KEYCODE_NAVIGATE_PREVIOUS -> {
                    Log.d("HtmlCrown", "Wrist flick in")
                    GlobalScope.launch {
                        Fuel.post("$HAUrl/api/services/media_player/turn_on")
                            .header("X-HA-Access" to HAPassword)
                            .jsonBody(
                                jsonObjectOf(
                                    "entity_id" to "media_player.$mediaPlayer"
                                ).toString()
                            )
                            .response { request, response, result ->
                                //Log.d("HtmlCrown", "request: $request\nresponse: $response")
                                val (bytes, error) = result
                                if (bytes != null) {
                                    //Log.d("HtmlCrown", String(bytes))
                                }
                            }
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

