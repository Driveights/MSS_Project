package unipi.mss.geomotion

import android.net.Uri
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.firestore
import unipi.mss.geomotion.MainActivity.Companion.TAG
import kotlin.math.cos

class DbManager {
    interface DbCallback {
        fun onRecordingsResultReady(recordingsResultDTO: RecordingsResultDTO)
    }

    val db = Firebase.firestore

    fun getRecordings(lat: Double, long: Double, radius: Double, callback: DbCallback):RecordingsResultDTO {

        val recordingsResultDTO = RecordingsResultDTO()
        val emotionsCounter = hashMapOf(
            "happy" to 0,
            "neutral" to 0,
            "angry" to 0,
            "unpleasant" to 0,
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
                        "audio" to document.data["audio"].toString(),
                        "emotion" to document.data["emotion"].toString()
                    )
                    Log.d(TAG,document.data["email"].toString())

                    recordingsResultDTO.addRecording(recordInfo)
                }
                // set the dominant emotion
                val maxEmotionEntry = emotionsCounter.maxByOrNull { it.value }
                val maxEmotion = maxEmotionEntry?.key
                recordingsResultDTO.setEmotion(maxEmotion.toString())
                callback.onRecordingsResultReady(recordingsResultDTO)

            }
            .addOnFailureListener { exception ->
                Log.w(TAG, "Error getting documents.", exception)
            }

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

    fun uploadRecording(
        lat: Double,
        lon: Double,
        email: String?,
        emotion: String,
        audioUri: String,
        mAuth: FirebaseAuth
    ) {
        // Create a HashMap to store recording data
        val user = hashMapOf(
            "email" to (mAuth.currentUser?.email ?: "prova@example.com"), // If mAuth.currentUser?.email is null, fallback to "prova@example.com"
            "lat" to lat,
            "long" to lon,
            "emotion" to emotion,
            "audio" to audioUri
        )

        // Add a new document to the "recordings" collection with the recording data
        db.collection("recordings")
            .add(user)
            .addOnSuccessListener { documentReference ->
                // On success, log that data has been written to the database
                Log.d(TAG, "Data written to the database")
            }
            .addOnFailureListener { e ->
                // On failure, log the error
                Log.w(TAG, "Error adding document", e)
            }
    }
}