package unipi.mss.geomotion

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.location.Address
import android.location.Geocoder
import android.location.Location
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
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Space
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatRadioButton
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
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.Circle
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.material.slider.Slider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import java.io.File
import java.util.Locale
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import unipi.mss.geomotion.ml.SerQuant
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID


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


    // Gestione audio bottone
    private val REQUEST_LOCATION_PERMISSION = 100
    private val REQUEST_RECORD_AUDIO_PERMISSION = 200

    private val permissions = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.INTERNET,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )
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
    val storage = FirebaseStorage.getInstance()


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

    @SuppressLint("ClickableViewAccessibility", "ResourceAsColor")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        getLocationPermission()
        // Prompt the user for permission.
        if (savedInstanceState != null) {
            lastKnownLocation = savedInstanceState.getParcelable(KEY_LOCATION)
            cameraPosition = savedInstanceState.getParcelable(KEY_CAMERA_POSITION)
        }

        setContentView(R.layout.activity_main)

        Places.initialize(applicationContext, BuildConfig.MAPS_API_KEY)
        placesClient = Places.createClient(this)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment?
        mapFragment?.getMapAsync(this)

        if(locationPermissionGranted)
            fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)


        val recordButton = findViewById<ImageButton>(R.id.recordButton)

        val scaleDown = ObjectAnimator.ofPropertyValuesHolder(
            recordButton,
            PropertyValuesHolder.ofFloat("scaleX", 1.7f),
            PropertyValuesHolder.ofFloat("scaleY", 1.7f)
        )
        scaleDown.duration = 100

        val scaleUp = ObjectAnimator.ofPropertyValuesHolder(
            recordButton,
            PropertyValuesHolder.ofFloat("scaleX", 1f),
            PropertyValuesHolder.ofFloat("scaleY", 1f)
        )
        scaleUp.duration = 100

        recordButton.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    getRecordPermission()
                    scaleDown.start()
                    if (!permissionToRecordAccepted ){
                        Toast.makeText(this, "You must give permission to use microphone",Toast.LENGTH_LONG).show()
                    }else if( !locationPermissionGranted){
                        Toast.makeText(this, "You must give permission to use your location",Toast.LENGTH_LONG).show()
                    }else {     // ho entrambi i permessi
                        startRecording()
                        recordButton.setBackgroundColor(R.color.purple_material_design_3)
                        recordButton.setBackgroundResource(R.drawable.round_button)
                        mfccRecorder.InitAudioDispatcher()
                        mfccRecorder.startMfccExtraction()
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    getRecordPermission()
                    scaleUp.start()
                    if (!permissionToRecordAccepted ){
                        Toast.makeText(this, "You must give permission to use microphone",Toast.LENGTH_LONG).show()
                    }else if( !locationPermissionGranted){
                        Toast.makeText(this, "You must give permission to use your location",Toast.LENGTH_LONG).show()
                    }else {
                        mfccRecorder.StopAudioDispatcher()
                        stopRecording()

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
                        val inputFeature0 =
                            TensorBuffer.createFixedSize(intArrayOf(1, 47, 13), DataType.FLOAT32)
                        inputFeature0.loadBuffer(adjustByteBuffer(featureBuffer))

                        val outputs = model.process(inputFeature0)
                        val outputFeature0 = outputs.outputFeature0AsTensorBuffer

                        // Calcola i valori moltiplicati e visualizzali nel log come errore
                        val multipliedFeatures = listOf(
                            outputFeature0.getFloatValue(0),
                            outputFeature0.getFloatValue(1),
                            outputFeature0.getFloatValue(2),
                            outputFeature0.getFloatValue(3)
                        )

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


                        onButtonShowPopupWindowClick(recordButton.rootView, emotion)
                    }
                    recordButton.setBackgroundColor(R.color.purple_container_material_design_3)
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

    }
    private fun startRecording() {
        Log.d(TAG, "Sto iniziando a registrare")
        if (permissionToRecordAccepted && !isRecording) {
            recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setOutputFile(externalCacheDir?.absolutePath + "/audioFile.m4a")
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


    @SuppressLint("SetTextI18n")
    private fun onButtonShowPopupWindowClick(view: View?, emotion: String) {
        
        // Inflate the layout of the popup window
        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val popupView: View = inflater.inflate(R.layout.popup_window, null)

        // Create the popup window
        val width = LinearLayout.LayoutParams.WRAP_CONTENT
        val height = LinearLayout.LayoutParams.WRAP_CONTENT
        val focusable = true // Lets taps outside the popup also dismiss it
        val popupWindow = PopupWindow(popupView, width, height, focusable)

        var changedEmotion = emotion

        // Set up the RadioGroup with RadioButton click listener
        val emotionRadioGroup = popupView.findViewById<RadioGroup>(R.id.emotionRadioGroup)
        emotionRadioGroup.setOnCheckedChangeListener { group, checkedId ->
            // Update the 'emotion' variable based on the selected RadioButton
            changedEmotion = when (checkedId) {
                R.id.emotionOption1 -> "Neutral"
                R.id.emotionOption2 -> "Happy"
                R.id.emotionOption3 -> "Surprise"
                R.id.emotionOption4 -> "Unpleasant"
                else -> emotion // Fallback to the initial emotion if none selected
            }

            val emotionTextView = popupView.findViewById<TextView>(R.id.emotionText)
            emotionTextView.text = changedEmotion + "  " + emojiText(changedEmotion)

            val radioButtonColor = ContextCompat.getColorStateList(this, R.color.white)
            for (i in 0 until emotionRadioGroup.childCount) {
                val radioButton = emotionRadioGroup.getChildAt(i) as? AppCompatRadioButton
                radioButton?.buttonTintList = radioButtonColor
            }
        }

        // Set initial checked RadioButton based on the initial emotion
        val initialCheckedRadioButtonId = when (emotion) {
            "Neutral" -> R.id.emotionOption1
            "Happy" -> R.id.emotionOption2
            "Surprise" -> R.id.emotionOption3
            "Unpleasant" -> R.id.emotionOption4
            else -> -1 // No initial emotion selected
        }
        if (initialCheckedRadioButtonId != -1) {
            emotionRadioGroup.check(initialCheckedRadioButtonId)
        }


        val delButton = popupView.findViewById<Button>(R.id.deleteButton)
        delButton.setOnClickListener{
            popupWindow.dismiss()
        }

        // Show the popup window
        popupWindow.showAtLocation(view, Gravity.CENTER, 0, 0)

        // Initialize and configure the audio player inside the popup

        val filePath: String = externalCacheDir!!.absolutePath + "/audioFile.m4a"
        val mediaPlayer = MediaPlayer().apply {

            setDataSource(filePath)
            prepare()
            setOnCompletionListener {
                release()
            }
        }
        makeItPlayable(popupView, mediaPlayer)

        // Release MediaPlayer resources when popup is dismissed
        popupWindow.setOnDismissListener {
            mediaPlayer.release()
        }

        // Ottieni il riferimento alla TextView nel layout popup_window.xml
        val emotionTextView = popupView.findViewById<TextView>(R.id.emotionText)

        // Imposta il testo della TextView con l'emozione ricevuta
        emotionTextView.text = emotion + "  " + emojiText(emotion)
        val sendButton = popupView.findViewById<Button>(R.id.sendButton)


        sendButton.setOnClickListener {
            // Initialize Firebase Storage
            val storage = FirebaseStorage.getInstance()
            // Get a reference to the root of your Firebase Storage bucket
            val storageRef = storage.reference

            // Path to the audio file saved in the cache directory
            val audioFilePath = externalCacheDir!!.absolutePath + "/audioFile.m4a"

            // Create a reference to the audio file in Firebase Storage
            val audioRef = storageRef.child("audio/${UUID.randomUUID()}.m4a") // Specify the path in your bucket

            // Get a Uri for the audio file
            val audioFile = Uri.fromFile(File(audioFilePath))

            // Log the Uri of the audio file
            Log.d(TAG, audioFile.toString())

            // Upload the audio file to Firebase Storage
            val uploadTask = audioRef.putFile(audioFile)

            uploadTask.addOnSuccessListener { documentReference ->
                // Handle successful upload
                audioRef.downloadUrl.addOnSuccessListener { uri ->
                    Log.d("TAG", "Audio upload successful: ${documentReference.metadata?.path}")
                    Log.d("TAG", "Audio upload successful: ${uri.toString()}")

                    // Get the device's location
                    getDeviceLocation()
                    var lat = lastKnownLocation!!.latitude
                    var lon = lastKnownLocation!!.longitude

                    // Upload the recording to a database
                    dbManager.uploadRecording(lat, lon, changedEmotion, uri.toString(), mAuth)
                    popupWindow.dismiss()
                }
            }.addOnFailureListener { exception ->
                // Handle failed upload
                Log.e("TAG", "Audio upload failed: $exception")
                Toast.makeText(this, "AUDIO NOT UPLOADED", Toast.LENGTH_SHORT).show()
                popupWindow.dismiss()
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
            val scrollView = ScrollView(this).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            dbManager.getRecordings(latitude, longitude, chosenRadius, object : DbManager.DbCallback {
                override fun onRecordingsResultReady(recordingsResultDTO: RecordingsResultDTO) {
                    val addresses: List<Address> = geocoder.getFromLocation(latitude, longitude, 1)!!
                    if (addresses.isNotEmpty()) {
                        val address = addresses[0]
                        val addressString = address.thoroughfare ?: "Indirizzo non disponibile"
                        // Aggiungi un marker alla posizione toccata
                        val markerOptions = markerCustomed(recordingsResultDTO.getEmotion(), addressString, latLng)
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

                        val dialogLayout = layoutInflater.inflate(R.layout.custom_dialog_layout, null) as ViewGroup
                        // Rimuovi tutti i figli presenti nel dialogLayout
                        dialogLayout.removeAllViews()
                        scrollView.removeAllViews()

                        val parentViewGroup = scrollView.parent as? ViewGroup
                        parentViewGroup?.removeView(scrollView)

                        if(recordingsResultDTO.getlistOfRecordings().isEmpty())
                            builder.setTitle("No Recordings :(")
                        else
                            builder.setTitle("RECORDINGS: ")

                        val iterator = recordingsResultDTO.getlistOfRecordings().iterator()
                        addRecordingToLayout(dialogLayout, iterator)

                        // Aggiungi un pulsante per chiudere il popup
                        builder.setPositiveButton("Chiudi") { dialog, _ ->
                            dialog.dismiss() // Chiudi il popup quando il pulsante viene premuto
                        }

                        scrollView.addView(dialogLayout) // Aggiungi il dialogLayout come unico figlio diretto dello ScrollView

                        // Aggiungi il layout personalizzato al dialog
                        builder.setView(scrollView)

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
            "Happy" -> Color.argb(128, 0, 128, 0)   // Verde con 50% di opacità
            "Neutral" -> Color.argb(128, 128, 128, 128)   // Grigio con 50% di opacità
            "Surprise" -> Color.argb(128, 255, 255, 0)   // Giallo con 50% di opacità
            "Unpleasant" -> Color.argb(128, 255, 0, 0)   // Rosso con 50% di opacità
            else -> Color.argb(50, 128, 0, 128)   // Ritorna il colore di default per le emozioni non riconosciute con 20% di opacità
        }
    }

    private fun emojiText(emotion: String): String {
        return when (emotion) {
            "Happy" -> "😄"
            "Neutral" -> "\uD83D\uDE11"  // Grigio con 50% di opacità
            "Surprise" -> "\uD83D\uDE32"  // Giallo con 50% di opacità
            "Unpleasant" -> "\uD83D\uDE1E"
            else -> "❓"
        }
    }


    private fun markerCustomed(emotion: String, addressString: String, latLng: LatLng): MarkerOptions {
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

    private fun addRecordingToLayout(dialogLayout: ViewGroup, iterator: MutableIterator<HashMap<String, String>>){
        var counter = 3
        // Itera attraverso la lista di registrazioni
        while (iterator.hasNext()) {
            val recording = iterator.next()
            val userRecordLayout = layoutInflater.inflate(R.layout.user_record_layout, null)

            for ((key, value) in recording) {
                Log.d(TAG,"Chiave: $key, Valore: $value")

                if (key == "email"){
                    val textViewTitle: TextView = userRecordLayout.findViewById(R.id.textViewTitle)
                    textViewTitle.text = value
                }

                if (key == "emotion"){
                    val textViewTitle: TextView = userRecordLayout.findViewById(R.id.emotionText)
                    textViewTitle.text = emojiText(value)
                }

                if (key == "audio"){
                    val gsReference = value.let { storage.getReferenceFromUrl(it) }
                    gsReference.downloadUrl.addOnSuccessListener { uri ->
                        val mediaPlayer = MediaPlayer.create(this@MainActivity, uri)
                        makeItPlayable(userRecordLayout, mediaPlayer)
                    }
                }

            }

            dialogLayout.addView(userRecordLayout)
            // Aggiungi uno spazio bianco
            val space = Space(this@MainActivity)
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, resources.getDimensionPixelSize(R.dimen.space_height)) // Imposta l'altezza dello spazio come desiderato
            dialogLayout.addView(space, params)
            counter -= 1
            if (counter == 0){
                val buttonLoadMore = layoutInflater.inflate(R.layout.button_load_more, null)
                buttonLoadMore.findViewById<Button>(R.id.buttonLoadMore).setOnClickListener {
                    addRecordingToLayout(dialogLayout, iterator)
                    dialogLayout.removeView(buttonLoadMore)

                }
                dialogLayout.addView(buttonLoadMore)
                break
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
            map?.moveCamera(CameraUpdateFactory.newLatLngZoom(
                LatLng(0.0, 0.0), DEFAULT_ZOOM.toFloat()))
        }
    }
    // [END maps_current_place_get_device_location]

    /**
     * Prompts the user for permission to use the device location.
     */
    // [START maps_current_place_location_permission]
    private fun getLocationPermission() {
        if (ContextCompat.checkSelfPermission(
                this.applicationContext,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, permissions, REQUEST_LOCATION_PERMISSION)
        } else {
            locationPermissionGranted = true
        }
    }
    fun getRecordPermission(){
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION)
        } else {
            permissionToRecordAccepted = true
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
                if(grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    // Tutti i permessi richiesti sono stati concessi
                    locationPermissionGranted = true
                    fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
                } else {
                    locationPermissionGranted = false
                }
            }

            REQUEST_RECORD_AUDIO_PERMISSION -> {
                if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    // Tutti i permessi richiesti sono stati concessi
                    permissionToRecordAccepted = true
                } else {
                    permissionToRecordAccepted = false
                }
            }
            // Gestisci eventuali altri codici di richiesta dei permessi se necessario
            else -> super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }
    // [END maps_current_place_on_request_permissions_result]


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
                //getPermissions()
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



