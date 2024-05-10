package unipi.mss.geomotion

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Utils {
    fun adjustByteBuffer(buffer: ByteBuffer): ByteBuffer {
        val desiredSizeBytes = 2444

        // Se il buffer è più piccolo, aggiungi zeri
        if (buffer.capacity() < desiredSizeBytes) {
            val zeroBuffer = ByteBuffer.allocateDirect(desiredSizeBytes)
            zeroBuffer.order(ByteOrder.nativeOrder())
            buffer.rewind()
            zeroBuffer.put(buffer)
            zeroBuffer.position(buffer.capacity()) // Imposta la posizione per aggiungere zeri alla fine
            zeroBuffer.rewind()
            return zeroBuffer
        }
        // Se il buffer è più grande, troncalo
        else if (buffer.capacity() > desiredSizeBytes) {
            buffer.limit(desiredSizeBytes) // Imposta il limite al nuovo numero di byte
            buffer.rewind()
            return buffer.slice() // Crea una vista del buffer troncato
        }
        // Altrimenti, restituisci il buffer originale
        else {
            buffer.rewind()
            return buffer
        }
    }

    fun floatArrayListToByteBuffer(floatArrayList: ArrayList<FloatArray>): ByteBuffer {
        // Calculate total number of floats
        var totalFloats = 0
        for (floatArray in floatArrayList) {
            totalFloats += floatArray.size
        }

        // Allocate direct ByteBuffer with native byte order
        val bufferSize = totalFloats * Float.SIZE_BYTES
        val byteBuffer = ByteBuffer.allocateDirect(bufferSize)
        byteBuffer.order(ByteOrder.nativeOrder())

        // Flatten the float arrays into the ByteBuffer
        for (floatArray in floatArrayList) {
            for (value in floatArray) {
                byteBuffer.putFloat(value)
            }
        }

        // Rewind the ByteBuffer to start position and return
        byteBuffer.rewind()
        return byteBuffer
    }


    fun chooseColorRadius(emotion: String): Int {
        return when (emotion) {
            "Happy" -> Color.argb(128, 0, 128, 0)   // Verde con 50% di opacità
            "Neutral" -> Color.argb(128, 128, 128, 128)   // Grigio con 50% di opacità
            "Surprise" -> Color.argb(128, 255, 255, 0)   // Giallo con 50% di opacità
            "Unpleasant" -> Color.argb(128, 255, 0, 0)   // Rosso con 50% di opacità
            else -> Color.argb(50, 128, 0, 128)   // Ritorna il colore di default per le emozioni non riconosciute con 20% di opacità
        }
    }

    fun emojiText(emotion: String): String {
        return when (emotion) {
            "Happy" -> "😄"
            "Neutral" -> "\uD83D\uDE11"  // Grigio con 50% di opacità
            "Surprise" -> "\uD83D\uDE32"  // Giallo con 50% di opacità
            "Unpleasant" -> "\uD83D\uDE1E"
            else -> "❓"
        }
    }


    fun markerCustomed(emotion: String, addressString: String, latLng: LatLng): MarkerOptions {
        val text = emojiText(emotion)
        val textSize = 100f // dimensione del testo in pixel
        val padding = 1 // spazio intorno al testo in pixel

        // Calcola le dimensioni dell'icona
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        textPaint.textSize = textSize
        val textWidth = textPaint.measureText(text)
        val textHeight = textPaint.descent() - textPaint.ascent()
        val bitmapWidth = textWidth.toInt() + 2 * padding
        val bitmapHeight = textHeight.toInt() + 2 * padding

        // Crea un'immagine bitmap vuota
        val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        canvas.drawText(text, padding.toFloat(), (bitmapHeight / 2 - (textPaint.descent() + textPaint.ascent()) / 2) + padding, textPaint)

        // Imposta l'icona del marker
        val icon = BitmapDescriptorFactory.fromBitmap(bitmap)
        val markerOptions = MarkerOptions()
            .position(latLng)
            .title(addressString)
            .snippet("Emotion: ${emotion}")
            .icon(icon)
        return markerOptions
    }


}