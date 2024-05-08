package unipi.mss.geomotion

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import unipi.mss.geomotion.MainActivity.Companion.TAG
import kotlin.math.cos

class DbManager {

    val db = Firebase.firestore

    fun getRecordings(lat: Double, long: Double, radius: Double):RecordingsResultDTO {

        val recordingsResultDTO = RecordingsResultDTO()
        val emotionsCounter = hashMapOf(
            "Neutralità" to 0,
            "Felicità" to 0,
            "Rabbia" to 0,
            "Tristezza" to 0,
            "Paura" to 0
        )

        val rapportoLat = radius / 111320 // Approssimazione del rapporto della differenza di latitudine

        val lunghezzaGradoLongitudine = 111320 * cos(Math.toRadians(lat))

        // Calcola l'approssimazione della differenza di longitudine corrispondente alla distanza in metri
        val rapportoLon = radius / lunghezzaGradoLongitudine

        Log.d(TAG, "Query to db")
        db.collection("recordings")
            .whereGreaterThanOrEqualTo("long", long - rapportoLon)
            .whereLessThanOrEqualTo("long", long + rapportoLon)
            .whereGreaterThanOrEqualTo("lat", lat - rapportoLat)
            .whereLessThanOrEqualTo("lat", lat + rapportoLat)
            .get()
            .addOnSuccessListener { result ->
                for (document in result) {
                    Log.d(TAG, "${document.id} => ${document.data}")
                    val emotion = document.data["emotion"]
                    val n = emotionsCounter[emotion] ?: 0
                    emotionsCounter[emotion.toString()] = n+1
                    Log.d(TAG, emotion.toString())
                    val recordInfo = hashMapOf(
                        "email" to document.data["email"].toString(),
                        "audio" to document.data["audio"].toString()
                    )
                    Log.d(TAG,document.data["email"].toString())

                    recordingsResultDTO.addRecording(recordInfo)
                }
                // set the dominant emotion
                val maxEmotionEntry = emotionsCounter.maxByOrNull { it.value }
                val maxEmotion = maxEmotionEntry?.key
                recordingsResultDTO.setEmotion(maxEmotion.toString())
            }
            .addOnFailureListener { exception ->
                Log.w(TAG, "Error getting documents.", exception)
            }
        Log.d(TAG, recordingsResultDTO.getEmotion())


        return recordingsResultDTO

    }


    /*
    val user = hashMapOf(
                "email" to (mAuth.currentUser?.email ?: "prova@example.com"),
                "lat" to latitude,
                "long" to longitude,
                "emotion" to "happy",
                "audio" to "gs://geomotion-195dc.appspot.com/kill-bill.wav"
            )

            // Add a new document with a generated ID
            db.collection("recordings")
                .add(user)
                .addOnSuccessListener { documentReference ->
                    Log.d(TAG, "Ha scritto sul db")
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Error adding document", e)
                }
     */
}