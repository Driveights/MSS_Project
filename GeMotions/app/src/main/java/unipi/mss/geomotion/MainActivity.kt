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
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
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
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.github.squti.androidwaverecorder.WaveRecorder
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.GoogleMap.InfoWindowAdapter
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory.defaultMarker
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.Circle
import com.google.android.gms.maps.model.CircleOptions
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
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import unipi.mss.geomotion.ml.SerQuant
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder


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
    private var mfccRecorder: MFCCRecorder = MFCCRecorder()

    // Gestione login/logout
    private lateinit var mGoogleSignInClient: GoogleSignInClient
    private val mAuth = FirebaseAuth.getInstance()

    // Gestione db
    private val dbManager = DbManager()

    private var chosenRadius = 100.0;

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

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
                //mediaPlayer?.start()
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


        setContentView(R.layout.activity_main)

        Places.initialize(applicationContext, BuildConfig.MAPS_API_KEY)
        placesClient = Places.createClient(this)

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment?
        mapFragment?.getMapAsync(this)



        val recordButton = findViewById<ImageButton>(R.id.recordButton)
        recordButton.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startRecording()
                    // TODO check
                    recordButton.setBackgroundColor(R.color.purple_material_design_3)
                    recordButton.setImageResource(R.drawable.microphone_down)
                    recordButton.setBackgroundResource(R.drawable.round_button)
                    mfccRecorder.InitAudioDispatcher()
                    mfccRecorder.startMfccExtraction()
                    true
                }

                MotionEvent.ACTION_UP -> {
                    stopRecording()
                    mfccRecorder.StopAudioDispatcher()

                    val model = SerQuant.newInstance(this)

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
                    val featureBuffer = floatArrayListToByteBuffer(transposedMfccFeatures)
                    val inputFeature0 = TensorBuffer.createFixedSize(intArrayOf(1, 47, 13), DataType.FLOAT32)
                    inputFeature0.loadBuffer(adjustByteBuffer(featureBuffer))

                    val outputs = model.process(inputFeature0)
                    val outputFeature0 = outputs.outputFeature0AsTensorBuffer

                    val maxValue = listOf(
                        outputFeature0.getFloatValue(0) * 1,
                        outputFeature0.getFloatValue(1) * 30000,
                        outputFeature0.getFloatValue(2) * 40000,
                        outputFeature0.getFloatValue(3) * 500
                    ).maxOrNull()

                    val maxIndex = listOf(
                        outputFeature0.getFloatValue(0) * 1,
                        outputFeature0.getFloatValue(1) * 30000,
                        outputFeature0.getFloatValue(2) * 40000,
                        outputFeature0.getFloatValue(3) * 500
                    ).indexOf(maxValue)

                    val emotion: String = when (maxIndex) {
                        0 -> "Neutral"
                        1 -> "Happy"
                        2 -> "Surprise"
                        3 -> "Unpleasant"
                        else -> "Unknown"
                    }


                    model.close()

                    onButtonShowPopupWindowClick(recordButton.rootView, emotion)

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
            chosenRadius = value.toDouble()
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
            recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.DEFAULT)
                setOutputFile(externalCacheDir?.absolutePath + "/audioFile.mp3")
                setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT)

                try {
                    prepare()
                    start()
                    isRecording = true
                }
                catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }

    }



    private fun stopRecording() {
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



    private fun onButtonShowPopupWindowClick(view: View?, emotion: String) {


        // Inflate the layout of the popup window
        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val popupView: View = inflater.inflate(R.layout.popup_window, null)

        // Create the popup window
        val width = LinearLayout.LayoutParams.WRAP_CONTENT
        val height = LinearLayout.LayoutParams.WRAP_CONTENT
        val focusable = true // Lets taps outside the popup also dismiss it
        val popupWindow = PopupWindow(popupView, width, height, focusable)

        // Show the popup window
        // The view parameter is used as the anchor view for the popup
        popupWindow.showAtLocation(view, Gravity.CENTER, 0, 0)

        // Initialize and configure the audio player inside the popup

        val filePath: String = externalCacheDir!!.absolutePath + "/audioFile.mp3"
        val mediaPlayer = MediaPlayer().apply {

            setDataSource(filePath)
            prepare()
            setOnCompletionListener {
                release()
            }
        }
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

        // Release MediaPlayer resources when popup is dismissed
        popupWindow.setOnDismissListener {
            mediaPlayer.release()
        }

        Log.e(TAG,emotion)

        // Ottieni il riferimento alla TextView nel layout popup_window.xml
        val emotionTextView = popupView.findViewById<TextView>(R.id.emotionText)

        // Imposta il testo della TextView con l'emozione ricevuta
        emotionTextView.text = emotion
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
        var currentCircle: Circle? = null

        map.setOnMapClickListener { latLng ->
            // Rimuovi il marker precedente se presente
            currentMarker?.remove()

            // Ottieni le coordinate toccate
            val latitude = latLng.latitude
            val longitude = latLng.longitude
            // Ottieni il nome del luogo toccato utilizzando Geocoder
            val geocoder = Geocoder(this, Locale.getDefault())
            val builder = AlertDialog.Builder(this, R.style.RoundedAlertDialog)


            dbManager.getRecordings(latitude, longitude, chosenRadius, object : DbManager.DbCallback {
                override fun onRecordingsResultReady(recordingsResultDTO: RecordingsResultDTO) {
                    Log.d(TAG, recordingsResultDTO.getEmotion())
                    val addresses: List<Address> = geocoder.getFromLocation(latitude, longitude, 1)!!
                    if (addresses.isNotEmpty()) {
                        val address = addresses[0]
                        val addressString = address.thoroughfare ?: "Indirizzo non disponibile"
                        // Aggiungi un marker alla posizione toccata
                        val markerOptions = MarkerOptions().position(latLng).title(addressString).snippet("Emotion: ${recordingsResultDTO.getEmotion()}")
                            .icon(defaultMarker(BitmapDescriptorFactory.HUE_VIOLET)) // Set the icon for the marker
                        currentMarker = map.addMarker(markerOptions)
                        // Mostra le informazioni ottenute in un Toast
                    }


                    // Aggiungi il cerchio
                    currentCircle?.remove()
                    val circleOptions = CircleOptions()
                        .center(LatLng(latitude, longitude))
                        .radius(chosenRadius) // Imposta il raggio in metri
                        .strokeWidth(2f)
                        .strokeColor(R.color.white)
                        .fillColor(chooseColorRadius(recordingsResultDTO.getEmotion())) // Viola con un livello di opacità del 70%) // Opzionale: Imposta il colore di riempimento
                    currentCircle = map.addCircle(circleOptions)
                    currentMarker?.showInfoWindow()

                    map.setOnInfoWindowClickListener {
                        builder.setTitle("RECORDINGS")

                        // Creazione del layout personalizzato per il dialog
                        val dialogLayout = layoutInflater.inflate(R.layout.custom_dialog_layout, null)

                        // Itera attraverso la lista di registrazioni
                        for (recording in recordingsResultDTO.getlistOfRecordings()) {
                            for ((user, url) in recording) {
                                Log.d(TAG,"Chiave: $user, Valore: $url")
                            }                            // Infla il layout del player audio per ogni registrazione


                            /*val playerLayout = layoutInflater.inflate(R.layout.audio_player_layout, null)

                            // Trova le viste nel layout del player audio
                            val textViewTitle: TextView = playerLayout.findViewById(R.id.textViewTitle)
                            val seekBar: SeekBar = playerLayout.findViewById(R.id.seekBar)
                            val buttonPlayPause: ImageButton = playerLayout.findViewById(R.id.buttonPlayPause)
                            val buttonStop: ImageButton = playerLayout.findViewById(R.id.buttonStop)

                            // Imposta il titolo del player audio
                            textViewTitle.text = recording.keys

                            // Aggiungi il layout del player audio al contenitore
                            playersContainer.addView(playerLayout)*/
                        }


                        // Aggiungi un pulsante per chiudere il popup
                        builder.setPositiveButton("Chiudi") { dialog, _ ->
                            dialog.dismiss() // Chiudi il popup quando il pulsante viene premuto
                        }

                        // Aggiungi il layout personalizzato al dialog
                        builder.setView(dialogLayout)

                        // Mostra il dialogo
                        val dialog = builder.create()
                        dialog.show()

                    }
                }
            })
        }
    }

    private fun chooseColorRadius(emotion: String): Int {
        return when (emotion) {
            "happy" -> Color.argb(128, 0, 128, 0)   // Verde con 50% di opacità
            "neutral" -> Color.argb(128, 128, 128, 128)   // Grigio con 50% di opacità
            "surprise" -> Color.argb(128, 255, 255, 0)   // Giallo con 50% di opacità
            "unpleasant" -> Color.argb(128, 255, 0, 0)   // Rosso con 50% di opacità
            else -> Color.argb(50, 128, 0, 128)   // Ritorna il colore di default per le emozioni non riconosciute con 20% di opacità
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



