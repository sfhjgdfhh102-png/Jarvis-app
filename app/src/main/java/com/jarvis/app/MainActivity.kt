
package com.jarvis.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.RecognitionListener
import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var status: TextView
    private lateinit var tts: TextToSpeech
    private var recognizer: SpeechRecognizer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        status = findViewById(R.id.status)
        tts = TextToSpeech(this, this)

        val perms = arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
        if (perms.any { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }) {
            ActivityCompat.requestPermissions(this, perms, 1)
        }

        findViewById<Button>(R.id.micButton).setOnClickListener { listen() }
    }

    override fun onInit(s: Int) {
        tts.language = Locale("hi", "IN")
    }

    private fun listen() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status.text = "इस फोन में आवाज़ पहचान उपलब्ध नहीं है"
            return
        }
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (text.isNullOrBlank()) { status.append("\nसुनाई नहीं दिया"); return }
                status.append("\nआप: $text")
                handle(text)
            }
            override fun onError(error: Int) { status.append("\nत्रुटि: $error") }
            override fun onReadyForSpeech(p: Bundle?) { status.append("\nबोलिए...") }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(r: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(p: Bundle?) {}
            override fun onEvent(t: Int, p: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
        }
        recognizer?.startListening(intent)
    }

    private fun handle(text: String) {
        val l = text.lowercase()
        val isTorch = l.contains("torch") || l.contains("टॉर्च")
        when {
            isTorch && (l.contains("off") || l.contains("बंद")) -> {
                setTorch(false); reply("टॉर्च बंद कर दी")
            }
            isTorch && (l.contains("on") || l.contains("जला")) -> {
                setTorch(true); reply("टॉर्च जला दी")
            }
            else -> askModel(text)
        }
    }

    private fun setTorch(on: Boolean) {
        val cm = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull() ?: return
        cm.setTorchMode(id, on)
    }

    private fun askModel(q: String) {
        thread {
            val answer = try {
                val body = JSONObject().apply {
                    put("model", "qwen")
                    put("messages", JSONArray().apply {
                        put(JSONObject().put("role", "system").put("content", "Reply briefly in Hindi."))
                        put(JSONObject().put("role", "user").put("content", q))
                    })
                }
                val conn = URL("http://127.0.0.1:9931/v1/chat/completions").openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 10000
                conn.readTimeout = 120000
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
                val resp = conn.inputStream.bufferedReader().readText()
                JSONObject(resp).getJSONArray("choices").getJSONObject(0)
                    .getJSONObject("message").getString("content")
            } catch (e: Exception) {
                "सर्वर से जवाब नहीं मिला। Termux में सर्वर चालू है?"
            }
            runOnUiThread { reply(answer) }
        }
    }

    private fun reply(text: String) {
        status.append("\nJarvis: $text")
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis")
    }

    override fun onDestroy() {
        recognizer?.destroy()
        tts.shutdown()
        super.onDestroy()
    }
}
```
