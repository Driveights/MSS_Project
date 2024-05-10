package unipi.mss.geomotion

import android.content.Context
import android.util.Log
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import unipi.mss.geomotion.ml.SerQuant

class QuantizedModel {

    var utils:Utils = Utils()
    public fun processAudio(context: Context, mfccRecorder: MFCCRecorder): String {

        val model = SerQuant.newInstance(context)

        val mfccFeatures = mfccRecorder.getMfccList().transpose()
        val transposedMfccFeatures = ArrayList<FloatArray>().apply {
            if (mfccFeatures.isNotEmpty()) {
                val height = mfccFeatures[0].size
                val width = mfccFeatures.size
                for (i in 0 until height) {
                    val newArray = FloatArray(width)
                    for (j in mfccFeatures.indices) {
                        newArray[j] = mfccFeatures[j][i]
                    }
                    add(newArray)
                }
            }
        }
        val featureBuffer = utils.floatArrayListToByteBuffer(transposedMfccFeatures)
        val inputFeature0 =
            TensorBuffer.createFixedSize(intArrayOf(1, 47, 13), DataType.FLOAT32)
        inputFeature0.loadBuffer(utils.adjustByteBuffer(featureBuffer))

        val outputs = model.process(inputFeature0)
        val outputFeature0 = outputs.outputFeature0AsTensorBuffer

        // Calcola i valori moltiplicati e visualizzali nel log come errore
        val multipliedFeatures = listOf(
            outputFeature0.getFloatValue(0) * 2,
            outputFeature0.getFloatValue(1) * 70,
            outputFeature0.getFloatValue(2) * 6,
            outputFeature0.getFloatValue(3)
        )

        Log.e(MainActivity.TAG, "Valori moltiplicati: $multipliedFeatures")

        // Trova il valore massimo e il suo indice
        val maxValue = multipliedFeatures.maxOrNull()
        val maxIndex = multipliedFeatures.indexOf(maxValue)

        val emotion: String = when (maxIndex) {
            0 -> "Neutral"
            1 -> "Happy"
            2 -> "Surprise"
            3 -> "Unpleasant"
            else -> "Unknown"
        }
        model.close()

        return emotion

    }

    fun List<FloatArray>.transpose(): List<FloatArray> {
        if (isEmpty()) return emptyList()
        val height = first().size
        val width = size
        val transposed = Array(height) { FloatArray(width) }
        for (i in indices) {
            val row = this[i]
            for (j in row.indices) {
                transposed[j][i] = row[j]
            }
        }
        return transposed.toList()
    }
}