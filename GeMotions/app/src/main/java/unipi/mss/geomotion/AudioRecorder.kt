import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import com.github.squti.androidwaverecorder.WaveRecorder
import unipi.mss.geomotion.R
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException

class AudioRecorder {

    private val TAG = "AudioRecorder"
    private var recorder: MediaRecorder? = null
    private var isRecording: Boolean = false
    private lateinit var waveRecorder: WaveRecorder

    fun startWavRecording(wav : WaveRecorder) {
        waveRecorder = wav
        if (!isRecording) {
            // Avvio della registrazione con WaveRecorder
            waveRecorder.waveConfig.sampleRate = 44100
            waveRecorder.waveConfig.channels = AudioFormat.CHANNEL_IN_STEREO
            waveRecorder.waveConfig.audioEncoding = AudioFormat.ENCODING_PCM_8BIT
            waveRecorder.startRecording()
            isRecording = true
        }
    }

    fun stopWavRecording() {
        if (isRecording) {
            // Interruzione della registrazione con WaveRecorder
            Log.d(TAG, "Sto finendo di registrare")
            waveRecorder.stopRecording()
            isRecording = false
        }

    }

    fun startRecording(outputFilePath: String) {
        Log.d(TAG, "Sto iniziando a registrare")
        if (!isRecording) {
            recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setOutputFile(outputFilePath)
                setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT)

                try {
                    prepare()
                    start()
                    isRecording = true
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
    }


    fun stopRecording() {
        Log.d(TAG, "Ho finito registrare")
        if (isRecording) {
            try {
                recorder?.stop()
                recorder?.release()
                isRecording = false
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    fun makeItPlayable(popupView: View, mediaPlayer: MediaPlayer){

        val playPauseButton = popupView.findViewById<ImageButton>(R.id.playPauseButton)
        val seekBar = popupView.findViewById<SeekBar>(R.id.seekBar)
        val durationTextView = popupView.findViewById<TextView>(R.id.durationTextView)

        // Set up play/pause functionality
        playPauseButton.setOnClickListener {
            if (mediaPlayer.isPlaying) {
                mediaPlayer.pause()
                playPauseButton.setImageResource(android.R.drawable.ic_media_play)
            } else {
                mediaPlayer.start()
                playPauseButton.setImageResource(android.R.drawable.ic_media_pause)
            }
        }

        // Update seekBar progress and durationTextView
        mediaPlayer.setOnPreparedListener {
            seekBar.max = mediaPlayer.duration
            val duration = mediaPlayer.duration
            val minutes = duration / 1000 / 60
            val seconds = duration / 1000 % 60
            durationTextView.text = String.format("%02d:%02d", minutes, seconds)
        }

        mediaPlayer.setOnCompletionListener {
            // Reset play/pause button when playback completes
            playPauseButton.setImageResource(android.R.drawable.ic_media_play)
        }

        // Update seekBar progress during playback
        mediaPlayer.setOnSeekCompleteListener {
            seekBar.progress = mediaPlayer.currentPosition
        }

        // Update seekBar progress when user changes seekBar position
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    mediaPlayer.seekTo(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {}

        })
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