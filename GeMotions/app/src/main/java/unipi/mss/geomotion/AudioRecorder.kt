import android.media.MediaRecorder
import android.util.Log
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
}