// Copyright 2020 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package unipi.mss.geomotion

import android.Manifest
import android.annotation.SuppressLint
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.github.squti.androidwaverecorder.WaveRecorder
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.GoogleMap.InfoWindowAdapter
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FindCurrentPlaceRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.material.slider.Slider
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.FirebaseStorage
import vokaturi.vokaturisdk.entities.Voice
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale


/**
 * An activity that displays a map showing the place at the device's current location.
 */
class MainActivity : AppCompatActivity(), OnMapReadyCallback {
    private var map: GoogleMap? = null
    private var cameraPosition: CameraPosition? = null

    // The entry point to the Places API.
    private lateinit var placesClient: PlacesClient

    // The entry point to the Fused Location Provider.
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient

    // A default location (Sydney, Australia) and default zoom to use when location permission is
    // not granted.
    private val defaultLocation = LatLng(-33.8523341, 151.2106085)
    private var locationPermissionGranted = false

    // The geographical location where the device is currently located. That is, the last-known
    // location retrieved by the Fused Location Provider.
    private var lastKnownLocation: Location? = null
    private var likelyPlaceNames: Array<String?> = arrayOfNulls(0)
    private var likelyPlaceAddresses: Array<String?> = arrayOfNulls(0)
    private var likelyPlaceAttributions: Array<List<*>?> = arrayOfNulls(0)
    private var likelyPlaceLatLngs: Array<LatLng?> = arrayOfNulls(0)


    // Gestione audio bottone
    private val REQUEST_LOCATION_PERMISSION = 100
    private val REQUEST_RECORD_AUDIO_PERMISSION = 200
    private val permissions = arrayOf(Manifest.permission.RECORD_AUDIO)
    private var permissionToRecordAccepted = false
    private var recorder: MediaRecorder? = null
    private var isRecording = false
    private lateinit var waveRecorder: WaveRecorder

    // Gestione login/logout
    private lateinit var mGoogleSignInClient: GoogleSignInClient
    private val mAuth = FirebaseAuth.getInstance()

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.logout -> {
                Log.d(TAG, "Click on logout item")
                logout()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun logout(){
        mAuth.signOut()
        if (::mGoogleSignInClient.isInitialized) {
            mGoogleSignInClient.signOut().addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "Logout success")
                    val intent = Intent(this, LoginActivity::class.java)
                    startActivity(intent)
                    finish()
                } else {
                    Log.e(TAG, "Logout failed")
                    Toast.makeText(this, "Logout failed", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            // Se mGoogleSignInClient non è stato inizializzato, avvia direttamente l'attività di accesso
            Log.d(TAG, "Logout success")
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    // [START maps_current_place_on_create]
    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        /*
        val db = Firebase.firestore
        val user = hashMapOf(
            "email" to (mAuth.currentUser?.email ?: "prova@example.com"),
            "lat" to 10.2,
            "long" to 10.2,
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

        db.collection("recordings")
            .get()
            .addOnSuccessListener { result ->

                for (document in result) {
                    Log.d(TAG, "Abbiamo letto dal db, non riusciamo ad accedere")
                    Log.d(TAG, "${document.id} => ${document.data}")
                }
            }
            .addOnFailureListener { exception ->
                Log.w(TAG, "Error getting documents.", exception)
            }

         */

        val storage = FirebaseStorage.getInstance()
        var storageRef = storage.reference
        /*
        val mountainsRef = storageRef.child("kill-bill.wav")
        val audioUpload = mountainsRef.putFile(Uri.fromFile(File("kill-bill.wav")))
        var download_uri: String? = null
        audioUpload.addOnSuccessListener{ documentReference ->
            mountainsRef.downloadUrl.addOnSuccessListener { uri ->
                download_uri = uri.toString()
                Log.d(TAG, download_uri!!) }
            Log.d(TAG, "Ha caricato il file")
        }
        .addOnFailureListener { e ->
            Log.w(TAG, "Error adding file", e)
        }*/

        val download_uri = "gs://geomotion-195dc.appspot.com/kill-bill.wav"
        val gsReference = download_uri?.let { storage.getReferenceFromUrl(it) }

        if (gsReference != null) {
            gsReference.downloadUrl.addOnSuccessListener { uri ->
                val mediaPlayer = MediaPlayer.create(this@MainActivity, uri)
                mediaPlayer?.start()
            }
        }

        // Prompt the user for permission.
        getLocationPermission()
        // [START_EXCLUDE silent]
        // Retrieve location and camera position from saved instance state.
        // [START maps_current_place_on_create_save_instance_state]
        if (savedInstanceState != null) {
            lastKnownLocation = savedInstanceState.getParcelable(KEY_LOCATION)
            cameraPosition = savedInstanceState.getParcelable(KEY_CAMERA_POSITION)
        }
        // [END maps_current_place_on_create_save_instance_state]
        // [END_EXCLUDE]

        // Retrieve the content view that renders the map.
        setContentView(R.layout.activity_main)

        // [START_EXCLUDE silent]
        // Construct a PlacesClient
        Places.initialize(applicationContext, BuildConfig.MAPS_API_KEY)
        placesClient = Places.createClient(this)

        // Construct a FusedLocationProviderClient.
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)

        // Build the map.
        // [START maps_current_place_map_fragment]
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment?
        mapFragment?.getMapAsync(this)
        // [END maps_current_place_map_fragment]
        // [END_EXCLUDE]

        val filePath: String = externalCacheDir?.absolutePath + "/audioFile.wav"
        waveRecorder = WaveRecorder(filePath)


        val recordButton = findViewById<ImageButton>(R.id.recordButton)
        recordButton.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startRecording()
                    recordButton.setBackgroundColor(R.color.purple_material_design_3)
                    recordButton.setImageResource(R.drawable.microphone_down)
                    recordButton.setBackgroundResource(R.drawable.round_button)
                    true
                }

                MotionEvent.ACTION_UP -> {
                    stopRecording()
                    recordButton.setBackgroundColor(R.color.purple_container_material_design_3)
                    recordButton.setImageResource(R.drawable.microphone)
                    recordButton.setBackgroundResource(R.drawable.round_button)
                    true
                }

                else -> false
            }
        }

        val slider = findViewById<Slider>(R.id.slider)
        slider.setLabelFormatter { value: Float ->
            "${value.toInt()} m"
        }

        slider.addOnChangeListener { rangeSlider, value, fromUser ->
            Log.d(TAG, "Slider value is $value")
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO_PERMISSION)
        } else {
            permissionToRecordAccepted = true
        }


    }

    private fun startRecording() {
        Log.d(TAG, "Sto iniziando a registrare")
        if (permissionToRecordAccepted && !isRecording) {
            // Avvio della registrazione con WaveRecorder
            waveRecorder.waveConfig.sampleRate = 44100
            waveRecorder.waveConfig.channels = AudioFormat.CHANNEL_IN_STEREO
            waveRecorder.waveConfig.audioEncoding = AudioFormat.ENCODING_PCM_8BIT
            waveRecorder.startRecording()
            isRecording = true
        }
    }



    // Funzione per interrompere la registrazione
    private fun stopRecording() {
        if (isRecording) {
            // Interruzione della registrazione con WaveRecorder
            Log.d(TAG, "Sto finendo di registrare")
            waveRecorder.stopRecording()
            isRecording = false
        }

        // Percorso del file audio .wav
        val filePath: String = externalCacheDir?.absolutePath + "/audioFile.wav"

        val floatArray = wavToFloatArray(filePath)
        val voice = floatArray?.let { Voice(44100.0f, it.size) }
        voice?.fill(floatArray)
        val pr = voice?.extract()

        if (pr != null) {
            Log.d(TAG, "Validità: " + pr.isValid)
        }
        if (pr != null) {
            // Ordinare le emozioni in ordine decrescente di valore
            val emotions = listOf(
                "Neutralità" to pr.neutrality,
                "Felicità" to pr.happiness,
                "Rabbia" to pr.anger,
                "Tristezza" to pr.sadness,
                "Paura" to pr.fear
            ).sortedByDescending { it.second }

            // Costruire la stringa delle informazioni in ordine decrescente
            val emotionInfo = emotions.joinToString("\n") { "${it.first}: ${it.second}" }

            // Stampare le informazioni utilizzando Toast
            showToast(emotionInfo)
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
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

    // [END maps_current_place_on_create]

    /**
     * Saves the state of the map when the activity is paused.
     */
    // [START maps_current_place_on_save_instance_state]
    override fun onSaveInstanceState(outState: Bundle) {
        map?.let { map ->
            outState.putParcelable(KEY_CAMERA_POSITION, map.cameraPosition)
            outState.putParcelable(KEY_LOCATION, lastKnownLocation)
        }
        super.onSaveInstanceState(outState)
    }
    // [END maps_current_place_on_save_instance_state]

    /**
     * Sets up the options menu.
     * @param menu The options menu.
     * @return Boolean.
     */
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.current_place_menu, menu)
        return true
    }
    // [END maps_current_place_on_options_item_selected]

    /**
     * Manipulates the map when it's available.
     * This callback is triggered when the map is ready to be used.
     */
    // [START maps_current_place_on_map_ready]
    override fun onMapReady(map: GoogleMap) {
        this.map = map

        // Use a custom info window adapter to handle multiple lines of text in the
        // info window contents.
        this.map?.setInfoWindowAdapter(object : InfoWindowAdapter {
            // Return null here, so that getInfoContents() is called next.
            override fun getInfoWindow(arg0: Marker): View? {
                return null
            }

            override fun getInfoContents(marker: Marker): View {
                // Inflate the layouts for the info window, title and snippet.
                val infoWindow = layoutInflater.inflate(R.layout.custom_info_contents,
                    findViewById<FrameLayout>(R.id.map), false)
                val title = infoWindow.findViewById<TextView>(R.id.title)
                title.text = marker.title
                val snippet = infoWindow.findViewById<TextView>(R.id.snippet)
                snippet.text = marker.snippet
                return infoWindow
            }
        })

        // Get the current location of the device and set the position of the map.
        getDeviceLocation()

        // Turn on the My Location layer and the related control on the map.
        updateLocationUI()

        //MARCO

        var currentMarker: Marker? = null
        map.setOnMapClickListener { latLng ->
            // Rimuovi il marker precedente se presente
            currentMarker?.remove()

            // Ottieni le coordinate toccate
            val latitude = latLng.latitude
            val longitude = latLng.longitude

            // Ottieni il nome del luogo toccato utilizzando Geocoder
            val geocoder = Geocoder(this, Locale.getDefault())
            val addresses: List<Address> = geocoder.getFromLocation(latitude, longitude, 1)!!
            if (addresses.isNotEmpty()) {
                val address = addresses[0]
                val placeName = address.featureName ?: "Nome del luogo non disponibile"
                val addressString = address.thoroughfare ?: "Indirizzo non disponibile"
                // Aggiungi un marker alla posizione toccata
                val markerOptions = MarkerOptions().position(latLng).title(addressString).snippet("Emotion: HAPPY")
                currentMarker = map.addMarker(markerOptions)
                // Mostra le informazioni ottenute in un Toast
                Toast.makeText(this, "Touched at: $placeName con indirizzo $addressString", Toast.LENGTH_SHORT).show()
                currentMarker?.showInfoWindow()
            }

        }

    }


    /**
     * Gets the current location of the device, and positions the map's camera.
     */
    // [START maps_current_place_get_device_location]
    @SuppressLint("MissingPermission")
    private fun getDeviceLocation() {
        /*
         * Get the best and most recent location of the device, which may be null in rare
         * cases when a location is not available.
         */
        try {
            if (locationPermissionGranted) {
                val locationResult = fusedLocationProviderClient.lastLocation
                locationResult.addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        // Set the map's camera position to the current location of the device.
                        lastKnownLocation = task.result
                        if (lastKnownLocation != null) {
                            map?.moveCamera(CameraUpdateFactory.newLatLngZoom(
                                LatLng(lastKnownLocation!!.latitude,
                                    lastKnownLocation!!.longitude), DEFAULT_ZOOM.toFloat()))
                        }
                    } else {
                        Log.d(TAG, "Current location is null. Using defaults.")
                        Log.e(TAG, "Exception: %s", task.exception)
                        map?.moveCamera(CameraUpdateFactory
                            .newLatLngZoom(defaultLocation, DEFAULT_ZOOM.toFloat()))
                        map?.uiSettings?.isMyLocationButtonEnabled = false
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.e("Exception: %s", e.message, e)
        }
    }
    // [END maps_current_place_get_device_location]

    /**
     * Prompts the user for permission to use the device location.
     */
    // [START maps_current_place_location_permission]
    private fun getLocationPermission() {
        /*
         * Request location permission, so that we can get the location of the
         * device. The result of the permission request is handled by a callback,
         * onRequestPermissionsResult.
         */
        if (ContextCompat.checkSelfPermission(this.applicationContext,
                Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            locationPermissionGranted = true
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION
            )
        }
    }
    // [END maps_current_place_location_permission]

    /**
     * Handles the result of the request for location permissions.
     */
    // [START maps_current_place_on_request_permissions_result]
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        when (requestCode) {
            REQUEST_LOCATION_PERMISSION -> {
                // Gestisci il risultato della richiesta di permessi per l'accesso alla posizione
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permesso concesso
                    locationPermissionGranted = true
                } else {
                    // Permesso negato, puoi gestire questa situazione come preferisci
                    // Ad esempio, mostrando un messaggio di errore o disabilitando le funzionalità correlate alla posizione
                }
            }
            REQUEST_RECORD_AUDIO_PERMISSION -> {
                // Gestisci il risultato della richiesta di permessi per la registrazione audio
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    permissionToRecordAccepted = true
                    // Avvia la registrazione se i permessi sono stati concessi
                    startRecording()
                } else {
                    // Gestisci il caso in cui i permessi non sono stati concessi
                    Toast.makeText(this, "Permessi di registrazione audio non concessi", Toast.LENGTH_SHORT).show()
                }
            }
            else -> super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }
    // [END maps_current_place_on_request_permissions_result]

    /**
     * Prompts the user to select the current place from a list of likely places, and shows the
     * current place on the map - provided the user has granted location permission.
     */
    // [START maps_current_place_show_current_place]
    @SuppressLint("MissingPermission")
    private fun showCurrentPlace() {
        if (map == null) {
            return
        }
        if (locationPermissionGranted) {
            // Use fields to define the data types to return.
            val placeFields = listOf(Place.Field.NAME, Place.Field.ADDRESS, Place.Field.LAT_LNG)

            // Use the builder to create a FindCurrentPlaceRequest.
            val request = FindCurrentPlaceRequest.newInstance(placeFields)

            // Get the likely places - that is, the businesses and other points of interest that
            // are the best match for the device's current location.
            val placeResult = placesClient.findCurrentPlace(request)
            placeResult.addOnCompleteListener { task ->
                if (task.isSuccessful && task.result != null) {
                    val likelyPlaces = task.result

                    // Set the count, handling cases where less than 5 entries are returned.
                    val count = if (likelyPlaces != null && likelyPlaces.placeLikelihoods.size < M_MAX_ENTRIES) {
                        likelyPlaces.placeLikelihoods.size
                    } else {
                        M_MAX_ENTRIES
                    }
                    var i = 0
                    likelyPlaceNames = arrayOfNulls(count)
                    likelyPlaceAddresses = arrayOfNulls(count)
                    likelyPlaceAttributions = arrayOfNulls<List<*>?>(count)
                    likelyPlaceLatLngs = arrayOfNulls(count)
                    for (placeLikelihood in likelyPlaces?.placeLikelihoods ?: emptyList()) {
                        // Build a list of likely places to show the user.
                        likelyPlaceNames[i] = placeLikelihood.place.name
                        likelyPlaceAddresses[i] = placeLikelihood.place.address
                        likelyPlaceAttributions[i] = placeLikelihood.place.attributions
                        likelyPlaceLatLngs[i] = placeLikelihood.place.latLng
                        i++
                        if (i > count - 1) {
                            break
                        }
                    }

                    // Show a dialog offering the user the list of likely places, and add a
                    // marker at the selected place.
                    openPlacesDialog()
                } else {
                    Log.e(TAG, "Exception: %s", task.exception)
                }
            }
        } else {
            // The user has not granted permission.
            Log.i(TAG, "The user did not grant location permission.")

            // Add a default marker, because the user hasn't selected a place.
            map?.addMarker(MarkerOptions()
                .title(getString(R.string.default_info_title))
                .position(defaultLocation)
                .snippet(getString(R.string.default_info_snippet)))

            // Prompt the user for permission.
            getLocationPermission()
        }
    }
    // [END maps_current_place_show_current_place]

    /**
     * Displays a form allowing the user to select a place from a list of likely places.
     */
    // [START maps_current_place_open_places_dialog]
    private fun openPlacesDialog() {
        // Ask the user to choose the place where they are now.
        val listener = DialogInterface.OnClickListener { dialog, which -> // The "which" argument contains the position of the selected item.
            val markerLatLng = likelyPlaceLatLngs[which]
            var markerSnippet = likelyPlaceAddresses[which]
            if (likelyPlaceAttributions[which] != null) {
                markerSnippet = """
                    $markerSnippet
                    ${likelyPlaceAttributions[which]}
                    """.trimIndent()
            }

            if (markerLatLng == null) {
                return@OnClickListener
            }

            // Add a marker for the selected place, with an info window
            // showing information about that place.
            map?.addMarker(MarkerOptions()
                .title(likelyPlaceNames[which])
                .position(markerLatLng)
                .snippet(markerSnippet))

            // Position the map's camera at the location of the marker.
            map?.moveCamera(CameraUpdateFactory.newLatLngZoom(markerLatLng,
                DEFAULT_ZOOM.toFloat()))
        }

        // Display the dialog.
        AlertDialog.Builder(this)
            .setTitle(R.string.pick_place)
            .setItems(likelyPlaceNames, listener)
            .show()
    }
    // [END maps_current_place_open_places_dialog]

    /**
     * Updates the map's UI settings based on whether the user has granted location permission.
     */
    // [START maps_current_place_update_location_ui]
    @SuppressLint("MissingPermission")
    private fun updateLocationUI() {
        if (map == null) {
            return
        }
        try {
            if (locationPermissionGranted) {
                map?.isMyLocationEnabled = true
                map?.uiSettings?.isMyLocationButtonEnabled = true
            } else {
                map?.isMyLocationEnabled = false
                map?.uiSettings?.isMyLocationButtonEnabled = false
                lastKnownLocation = null
                getLocationPermission()
            }
        } catch (e: SecurityException) {
            Log.e("Exception: %s", e.message, e)
        }
    }
    // [END maps_current_place_update_location_ui]

    companion object {
        internal val TAG = MainActivity::class.java.simpleName
        private const val DEFAULT_ZOOM = 15
        private const val PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1

        // Keys for storing activity state.
        // [START maps_current_place_state_keys]
        private const val KEY_CAMERA_POSITION = "camera_position"
        private const val KEY_LOCATION = "location"
        // [END maps_current_place_state_keys]

        // Used for selecting the current place.
        private const val M_MAX_ENTRIES = 5
    }
}



