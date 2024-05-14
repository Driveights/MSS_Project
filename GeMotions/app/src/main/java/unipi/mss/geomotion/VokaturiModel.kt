package unipi.mss.geomotion

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import unipi.mss.geomotion.MainActivity.Companion.TAG
import vokaturi.vokaturisdk.entities.Voice
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException

class VokaturiModel {
    public fun processAudio(filePath: String): String {
        val floatArray = wavToFloatArray(filePath)
        val voice = floatArray?.let { Voice(44100.0f, it.size) }
        voice?.fill(floatArray)
        val pr = voice?.extract()
        var emotion = ""

        if (pr != null) {
            Log.d(TAG, "Validità: " + pr.isValid)
        }
        if (pr != null) {
            // Ordinare le emozioni in ordine decrescente di valore
            val emotions = listOf(
                "Neutral" to pr.neutrality,
                "Happy" to pr.happiness,
                "Unpleasant" to pr.anger,
                "Unpleasant" to pr.sadness,
                "Surprise" to pr.fear
            ).sortedByDescending { it.second }

            // Selezionare solo l'emozione con il valore più alto
            val highestEmotion = emotions.firstOrNull()

            // Costruire la stringa solo con l'emozione di valore più alto
            emotion = highestEmotion?.first ?: "No emotion detected"
        }

        return emotion
    }
    private fun wavToFloatArray(filePath: String): FloatArray? {
        val file = File(filePath)

        // Verifica che il file esista
        if (!file.exists()) {
            return null
        }

        // Impostazioni per AudioRecord
        val sampleRate = 44100
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

        // Leggi i dati audio dal file WAV
        val floatData = mutableListOf<Float>()
        val inputStream = FileInputStream(file)
        val dataInputStream = DataInputStream(inputStream)


        // Leggi i dati audio e convertili in float
        val data = ByteArray(bufferSize)
        while (dataInputStream.available() > 0) {
            val read = dataInputStream.read(data)
            for (i in 0 until read / 2) { // Assuming 16-bit audio
                val sample = (data[i * 2 + 1].toInt() shl 8) or (data[i * 2].toInt() and 0xFF)
                floatData.add(sample.toFloat() / Short.MAX_VALUE) // Normalizza a [-1, 1]
            }
        }

        // Chiudi i flussi
        dataInputStream.close()
        inputStream.close()

        // Converte i dati float in FloatArray
        return floatData.toFloatArray()
    }
}