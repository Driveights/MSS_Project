import android.media.MediaPlayer
import android.media.MediaRecorder
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import unipi.mss.geomotion.R
import java.io.IOException

class AudioRecorder {

    private val TAG = "AudioRecorder"
    private var recorder: MediaRecorder? = null
    private var isRecording: Boolean = false

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

    private fun makeItPlayable(popupView: View, mediaPlayer: MediaPlayer){

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
}