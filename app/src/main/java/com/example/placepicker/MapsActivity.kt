package com.example.placepicker

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.location.Location
import android.os.*
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.animation.Interpolator
import android.view.animation.LinearInterpolator
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.Nullable
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.beust.klaxon.*
import com.firebase.geofire.GeoFire
import com.firebase.geofire.GeoLocation
import com.firebase.geofire.GeoQuery
import com.firebase.geofire.GeoQueryEventListener
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener
import com.google.android.gms.common.api.Status
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationListener
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.AutocompleteActivity
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.maps.android.SphericalUtil
import org.jetbrains.anko.async
import org.jetbrains.anko.uiThread
import java.net.URL
import java.util.*


class MapsActivity : AppCompatActivity()
    , OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private var mPlacesClient: PlacesClient? = null
    private var mFusedLocationProviderClient: FusedLocationProviderClient? = null

    val MY_PERMISSIONS_REQUEST_LOCATION1 = 99
    private val AUTOCOMPLETE_REQUEST_CODE = 22
    private val AUTOCOMPLETE_REQUEST_CODE2 = 23
    private var mCurrLocationMarker: Marker? = null
    private var mDestLocationMarker: Marker? = null
    private var mAnimateMarker: Marker? = null
    private var line: Polyline? = null

    lateinit var txt_dest: TextView
    lateinit var txt_source: TextView

    var dest_latlng: LatLng? = null
    lateinit var source_latlng: LatLng

    private lateinit var geoService: GeoService
    private var serviceBound = false

    val PLAY_SERVICE_RESULATION_REQUEST = 300193
    var polypts: List<LatLng>? = null

    var mIndexCurrentPoint = 0

    var mMarkerIcon: Bitmap? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)


        val apiKey = resources.getString(R.string.google_maps_key)
        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
        txt_dest = findViewById(R.id.txt_dest)
        txt_source = findViewById(R.id.txt_source)

        if (!Places.isInitialized()) {
            Places.initialize(applicationContext, apiKey)
        }
        mPlacesClient = Places.createClient(this)
        mMarkerIcon = BitmapFactory.decodeResource(
            getResources(),
            R.drawable.img_car_animation
        )
        txt_source.setOnClickListener {

            val intent: Intent = Autocomplete.IntentBuilder(
                AutocompleteActivityMode.OVERLAY,
                Arrays.asList(
                    Place.Field.ID,
                    Place.Field.NAME,
                    Place.Field.ADDRESS,
                    Place.Field.LAT_LNG
                )
            )
                .build(this@MapsActivity)
            startActivityForResult(intent, AUTOCOMPLETE_REQUEST_CODE)
        }
        txt_dest.setOnClickListener {

            if (txt_source.text.toString().trim().isEmpty()) {
                Toast.makeText(this, "Please Select Source Location First", Toast.LENGTH_SHORT)
                    .show()
            } else {
                val intent: Intent = Autocomplete.IntentBuilder(
                    AutocompleteActivityMode.OVERLAY,
                    Arrays.asList(
                        Place.Field.ID,
                        Place.Field.NAME,
                        Place.Field.ADDRESS,
                        Place.Field.LAT_LNG
                    )
                )
                    .build(this@MapsActivity)
                startActivityForResult(intent, AUTOCOMPLETE_REQUEST_CODE2)
            }
        }


        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)


    }


    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_geolocate -> {

                return true
            }

        }
        return super.onOptionsItemSelected(item)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                mMap.isMyLocationEnabled = true
            } else {
                requestPermissions(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    MY_PERMISSIONS_REQUEST_LOCATION1
                )
            }
        } else {
            geoService.buildGoogleApiClient()
            mMap.isMyLocationEnabled = true


            mFusedLocationProviderClient!!.lastLocation
                .addOnSuccessListener { location: Location? ->
                    // Got last known location. In some rare situations this can be null.
                    if (location != null) {

//                        val latLng = LatLng(location.latitude, location.longitude)
//                        val animateMarkerOptions = MarkerOptions()
//                        animateMarkerOptions.position(latLng)
//                        animateMarkerOptions.icon(
//                            BitmapDescriptorFactory.defaultMarker(
//                                BitmapDescriptorFactory.HUE_RED
//                            )
//                        )
//                        mCurrLocationMarker = mMap.addMarker(animateMarkerOptions)
//
//
//                        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
//                        mMap.animateCamera(CameraUpdateFactory.zoomTo(15f))


                    }
                }
        }

    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            MY_PERMISSIONS_REQUEST_LOCATION1 -> {

                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    if (ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {

                        if (checkPlayService()) {
                            geoService.buildGoogleApiClient()
                            geoService.createLocationRequest()
                            geoService.displayLocation()
                            geoService.setLocationChangeListener(object :
                                GeoService.LocationChangeListener {

                                override fun onLocationChange(location: Location?) {

                                    if (location != null) {

                                        if (mCurrLocationMarker != null) {
                                            mCurrLocationMarker!!.remove()
                                        }

                                        source_latlng =
                                            LatLng(location.latitude, location.longitude)
                                        mCurrLocationMarker = mMap.addMarker(
                                            MarkerOptions()
                                                .icon(
                                                    BitmapDescriptorFactory.defaultMarker(
                                                        BitmapDescriptorFactory.HUE_RED
                                                    )
                                                )
                                                .position(
                                                    LatLng(
                                                        location.latitude,
                                                        location.longitude
                                                    )
                                                )
                                                .title("You")
                                        )

//                                        val cameraUpdate =
//                                            CameraUpdateFactory.newLatLngZoom(source_latlng, 15f)
//                                        mMap.animateCamera(cameraUpdate)
                                        mMap.moveCamera(
                                            CameraUpdateFactory.newLatLngZoom(
                                                source_latlng,
                                                15f
                                            )
                                        )
                                        mMap.animateCamera(CameraUpdateFactory.zoomTo(15f))
                                    }
                                }
                            })
                        }
                    }
                }
                return
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == AUTOCOMPLETE_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                var place: Place? = null
                if (data != null) {
                    place = Autocomplete.getPlaceFromIntent(data)
                }

                txt_source.setText(place!!.name)
                Log.e("latlng", "" + place)
                source_latlng = place.latLng!!
                Log.e("source_latlng", "" + source_latlng)
                val latLng = LatLng(source_latlng.latitude, source_latlng.longitude)
                val animateMarkerOptions = MarkerOptions()
                animateMarkerOptions.position(latLng)

                animateMarkerOptions.icon(
                    BitmapDescriptorFactory.defaultMarker(
                        BitmapDescriptorFactory.HUE_RED
                    )
                )

                if (mCurrLocationMarker != null) {
                    mCurrLocationMarker!!.remove()
                }
                mCurrLocationMarker = mMap.addMarker(animateMarkerOptions)

                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                mMap.animateCamera(CameraUpdateFactory.zoomTo(15f))

                if (dest_latlng != null) {

                    val LatLongB = LatLngBounds.Builder()

                    val options = PolylineOptions()
                    options.color(Color.RED)
                    options.width(5f)


                    //We need to pass Source and Destination To get Map Direction with Route

                    val url = getURL(source_latlng, dest_latlng!!)
                    async {
                        // Connect to URL, download content and convert into string asynchronously
                        val result = URL(url).readText()
                        uiThread {
                            // When API call is done, create parser and convert into JsonObjec
                            val parser: Parser = Parser()
                            val stringBuilder: StringBuilder = StringBuilder(result)
                            val json: JsonObject = parser.parse(stringBuilder) as JsonObject
                            // get to the correct element in JsonObject
                            val routes = json.array<JsonObject>("routes")
                            val points = routes!!["legs"]["steps"][0] as JsonArray<JsonObject>
                            // For every element in the JsonArray, decode the polyline string and pass all points to a List
                            polypts =
                                points.flatMap {
                                    decodePoly(
                                        it.obj("polyline")?.string("points")!!
                                    )
                                }
                            // Add  points to polyline and bounds

                            options.add(source_latlng)
                            LatLongB.include(source_latlng)
                            for (point in polypts!!) {
                                options.add(point)
                                LatLongB.include(point)
                            }

                            options.add(dest_latlng)
                            LatLongB.include(dest_latlng)
                            // build bounds
                            val bounds = LatLongB.build()
                            // add polyline to the map

                            //If Polyline is Already there then we need to remove it
                            //else there will be multiplepolyline
                            if (line != null) {
                                line!!.remove()
                            }
                            line = mMap!!.addPolyline(options)

                            // show map with route centered
                            mMap!!.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100))

                            //start Service.
                            //To Call Current LatLng and Update into the map.
                            geoService.startService(dest_latlng!!, 0.1)
                        }
                    }

                }
            } else if (resultCode == AutocompleteActivity.RESULT_ERROR) {
                // TODO: Handle the error.
                var status: Status? = null
                if (data != null) {
                    status = Autocomplete.getStatusFromIntent(data)
                }

            } else if (resultCode == RESULT_CANCELED) {
                // The user canceled the operation.
            }
        }
        if (requestCode == AUTOCOMPLETE_REQUEST_CODE2) {
            if (resultCode == RESULT_OK) {
                var place: Place? = null
                if (data != null) {
                    place = Autocomplete.getPlaceFromIntent(data)
                }

                txt_dest.setText(place!!.name)
                dest_latlng = place.latLng!!
                Log.e("Dest_latlng", "" + dest_latlng)
                val latLng = LatLng(dest_latlng!!.latitude, dest_latlng!!.longitude)
                val animateMarkerOptions = MarkerOptions()
                animateMarkerOptions.position(latLng)

                animateMarkerOptions.icon(
                    BitmapDescriptorFactory.defaultMarker(
                        BitmapDescriptorFactory.HUE_GREEN
                    )
                )

                if (mDestLocationMarker != null) {
                    mDestLocationMarker!!.remove()
                }
                mDestLocationMarker = mMap.addMarker(animateMarkerOptions)

                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                mMap.animateCamera(CameraUpdateFactory.zoomTo(15f))

                //Route Starting Here
                val LatLongB = LatLngBounds.Builder()

                val options = PolylineOptions()
                options.color(Color.RED)
                options.width(5f)

                val url = getURL(source_latlng, dest_latlng!!)
                async {
                    // Connect to URL, download content and convert into string asynchronously
                    val result = URL(url).readText()
                    uiThread {
                        // When API call is done, create parser and convert into JsonObjec
                        val parser: Parser = Parser()
                        val stringBuilder: StringBuilder = StringBuilder(result)
                        val json: JsonObject = parser.parse(stringBuilder) as JsonObject
                        // get to the c
                        // orrect element in JsonObject
                        val routes = json.array<JsonObject>("routes")
                        val points = routes!!["legs"]["steps"][0] as JsonArray<JsonObject>
                        // For every element in the JsonArray, decode the polyline string and pass all points to a List
                        polypts =
                            points.flatMap { decodePoly(it.obj("polyline")?.string("points")!!) }
                        // Add  points to polyline and bounds
                        options.add(source_latlng)
                        LatLongB.include(source_latlng)
                        for (point in polypts!!) {
                            options.add(point)
                            LatLongB.include(point)
                        }

                        options.add(dest_latlng)
                        LatLongB.include(dest_latlng)
                        // build bounds
                        val bounds = LatLongB.build()
                        // add polyline to the map

                        if (line != null) {
                            line!!.remove()
                        }
                        line = mMap.addPolyline(options)

                        // show map with route centered
                        mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100))

                        //Distance

                        val Distance =
                            SphericalUtil.computeDistanceBetween(source_latlng, dest_latlng!!)
                        Log.e("Distance", "" + Distance)

                        geoService.startService(dest_latlng!!, 0.1)
                    }
                }


            } else if (resultCode == AutocompleteActivity.RESULT_ERROR) {
                // TODO: Handle the error.
                var status: Status? = null
                if (data != null) {
                    status = Autocomplete.getStatusFromIntent(data)
                }

            } else if (resultCode == RESULT_CANCELED) {
                // The user canceled the operation.
            }
        }

    }

    private fun getURL(from: LatLng, to: LatLng): String {
        val origin = "origin=" + from.latitude + "," + from.longitude
        val dest = "destination=" + to.latitude + "," + to.longitude
        val sensor = "sensor=false"
        val params = "$origin&$dest&$sensor"
        return "https://maps.googleapis.com/maps/api/directions/json?$params&key=AIzaSyBTH_dyBbthf89iPZMs0b3c_F3K-QYcS_M"
    }


    //To Draw Route Between SOurce and Dest
    private fun decodePoly(encoded: String): List<LatLng> {
        val poly = ArrayList<LatLng>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0

        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].toInt() - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dlat

            shift = 0
            result = 0
            do {
                b = encoded[index++].toInt() - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dlng

            val p = LatLng(
                lat.toDouble() / 1E5,
                lng.toDouble() / 1E5
            )
            poly.add(p)
        }

        return poly
    }

    override fun onStart() {
        super.onStart()
        val i = Intent(this, GeoService::class.java)
        startService(i)
        bindService(i, mConnection, 0)
    }

    override fun onStop() {
        super.onStop()
        if (serviceBound) {
            // If a timer is active, foreground the service, otherwise kill the service
            if (geoService.isServiceRunning) {
                geoService.foreground()
            } else {
                stopService(Intent(this, GeoService::class.java))
            }
            // Unbind the service
            unbindService(mConnection)
            serviceBound = false
        }
    }

    class GeoService : Service(), ConnectionCallbacks,
        OnConnectionFailedListener, LocationListener {
        private var mLocationRequestService: LocationRequest? = null
        private var mGoogleApiClientService: GoogleApiClient? = null
        private var mLastLocation: Location? = null
        private var ref: DatabaseReference? = null
        private var geoFire: GeoFire? = null
        private var mLocationChangeListener: LocationChangeListener? = null

        /**
         * @return whether the service is running
         */
        // Is the service tracking time?
        var isServiceRunning = false
            private set

        // Service binder
        private val serviceBinder: IBinder = RunServiceBinder()
        private var geoQuery: GeoQuery? = null

        inner class RunServiceBinder : Binder() {
            val service: GeoService
                get() = this@GeoService
        }

        override fun onCreate() {
            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "Creating service")
            }
            ref = FirebaseDatabase.getInstance().getReference("MyLocation")
            geoFire = GeoFire(ref)
            isServiceRunning = false
        }

        override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "Starting service")
            }
            return START_STICKY
        }

        override fun onBind(intent: Intent): IBinder? {
            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "Binding service")
            }
            return serviceBinder
        }

        override fun onDestroy() {
            super.onDestroy()
            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "Destroying service")
                //                stopService();
            }
        }

        /**
         * Starts the timer
         */
        fun startService(latLng: LatLng, radius: Double) {
            if (!isServiceRunning) {
                isServiceRunning = true
            } else {
                Log.e(
                    TAG,
                    "startService request for an already running Service"
                )
            }
            if (geoQuery != null) {
                geoQuery!!.removeAllListeners()
            }
            geoQuery =
                geoFire!!.queryAtLocation(GeoLocation(latLng.latitude, latLng.longitude), radius)
            Log.e("geoLocationLat", "" + latLng.latitude)
            Log.e("geoLocationLng", "" + latLng.longitude)
            geoQuery!!.addGeoQueryEventListener(object : GeoQueryEventListener {
                override fun onKeyEntered(key: String?, location: GeoLocation?) {
                    sendNotification(
                        "" + resources.getString(R.string.app_name),
                        String.format("You are Reached to Destination", key)
                    )
                }

                override fun onKeyExited(key: String?) {
//                    sendNotification("MRF", String.format("%s exit the dangerous area", key))
                }

                override fun onKeyMoved(key: String?, location: GeoLocation) {
                    Log.e(
                        "MOVE",
                        java.lang.String.format(
                            "%s move within the dangerous area [%f/%f]",
                            key,
                            location.latitude,
                            location.longitude
                        )
                    )
//                    sendNotification(
//                        "MOVE",
//                        java.lang.String.format(
//                            "%s move within the dangerous area [%f/%f]",
//                            key,
//                            location.latitude,
//                            location.longitude
//                        )
//                    )
                }

                override fun onGeoQueryReady() {}
                override fun onGeoQueryError(error: DatabaseError) {
                    Log.e("ERROR", "" + error)
                }
            })
        }

        /**
         * Stops the timer
         */
        fun stopService() {
            if (isServiceRunning) {
                isServiceRunning = false
                geoQuery!!.removeAllListeners()
            } else {
                Log.e(
                    TAG,
                    "stopTimer request for a timer that isn't running"
                )
            }
        }

        /**
         * Place the service into the foreground
         */
        fun foreground() {

//            startForeground(NOTIFICATION_ID, createNotification())
        }

        /**
         * Return the service to the background
         */
        fun background() {
            stopForeground(true)
        }

        /**
         * Creates a notification for placing the service into the foreground
         *
         * @return a notification for interacting with the service when in the foreground
         */
        private fun createNotification(): Notification {
            val builder: NotificationCompat.Builder = NotificationCompat.Builder(this)
                .setContentTitle("Service is Active")
                .setContentText("Tap to return to the Map")
                .setSmallIcon(R.mipmap.ic_launcher)
            val resultIntent = Intent(this, MapsActivity::class.java)
            val resultPendingIntent = PendingIntent.getActivity(
                this, 0, resultIntent,
                PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.setContentIntent(resultPendingIntent)
            return builder.build()
        }

        private fun sendNotification(title: String, content: String) {
            val builder = Notification.Builder(this)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(content)
            val manager =
                this.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val intent = Intent(this, MapsActivity::class.java)
            val contentIntent =
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            builder.setContentIntent(contentIntent)
            val notification = builder.build()
            notification.flags = notification.flags or Notification.FLAG_AUTO_CANCEL
            notification.defaults = notification.defaults or Notification.DEFAULT_SOUND
            manager.notify(Random().nextInt(), notification)
        }

        override fun onConnected(@Nullable bundle: Bundle?) {
            displayLocation()
            startLocationUpdate()
        }

        private fun startLocationUpdate() {
            if (
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            LocationServices.FusedLocationApi.requestLocationUpdates(
                mGoogleApiClientService,
                mLocationRequestService,
                this
            )
        }

        override fun onConnectionSuspended(i: Int) {
            mGoogleApiClientService!!.connect()
        }

        override fun onConnectionFailed(@Nullable connectionResult: ConnectionResult) {}
        override fun onLocationChanged(location: Location) {
            mLastLocation = location
            displayLocation()
        }

        interface LocationChangeListener {
            fun onLocationChange(location: Location?)
        }

        internal fun createLocationRequest() {
            mLocationRequestService = LocationRequest()
            mLocationRequestService!!.interval = 5000
            mLocationRequestService!!.fastestInterval = 3000
            mLocationRequestService!!.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            mLocationRequestService!!.smallestDisplacement = 0F
        }

        internal fun buildGoogleApiClient() {
            mGoogleApiClientService = GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API).build()
            mGoogleApiClientService!!.connect()
        }

        internal fun displayLocation() {
            if (
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            mLastLocation =
                LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClientService)
            Log.e("mLastLocation", "" + mLastLocation)
            if (mLastLocation != null) {
                val latitude = mLastLocation!!.latitude
                val longitude = mLastLocation!!.longitude
                Log.e("latitude", "" + latitude)
                Log.e("longitude", "" + longitude)
                mLocationChangeListener!!.onLocationChange(mLastLocation)
                geoFire!!.setLocation(
                    "You",
                    GeoLocation(latitude, longitude),
                    object : GeoFire.CompletionListener {
                        override fun onComplete(key: String?, error: DatabaseError?) {
                            if (mLocationChangeListener != null) {
                                mLocationChangeListener!!.onLocationChange(mLastLocation)
                            }
                        }
                    })
                Log.e(
                    "MRF",
                    String.format(
                        "Your last location was changed: %f / %f",
                        latitude,
                        longitude
                    )
                )
            } else {
                Log.e("MRF", "Can not get your location.")
            }
        }

        fun setLocationChangeListener(mLocationChangeListener: LocationChangeListener?) {
            this.mLocationChangeListener = mLocationChangeListener
        }

        companion object {
            private val TAG = GeoService::class.java.simpleName

            // Foreground notification id
            private const val NOTIFICATION_ID = 1
        }
    }

    private val mConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {

            val binder: GeoService.RunServiceBinder = service as GeoService.RunServiceBinder
            geoService = binder.service
            serviceBound = true
            // Ensure the service is not in the foreground when bound
            geoService!!.background()
            setUpdateLocation()
            val mapFragment = supportFragmentManager
                .findFragmentById(R.id.map) as SupportMapFragment?
            mapFragment!!.getMapAsync(this@MapsActivity)
        }

        override fun onServiceDisconnected(name: ComponentName) {

            serviceBound = false
        }
    }

    private fun setUpdateLocation() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                MY_PERMISSIONS_REQUEST_LOCATION1
            )
        } else {
            if (checkPlayService()) {
                geoService.buildGoogleApiClient()
                geoService.createLocationRequest()
                geoService.displayLocation()
                geoService.setLocationChangeListener(object : GeoService.LocationChangeListener {


                    override fun onLocationChange(location: Location?) {

                        if (location != null) {

                            if (mCurrLocationMarker != null) {

                                mCurrLocationMarker!!.remove()
                            }

                            source_latlng =
                                LatLng(location.latitude, location.longitude)

                            //CHeck IF Destination is Added or not.
                            //If added then enter the condition and Draw Route
                            //else After destination added it will display the route


                            if (dest_latlng != null) {

                                val LatLongB = LatLngBounds.Builder()

                                val options = PolylineOptions()
                                options.color(Color.RED)
                                options.width(5f)

                                val url = getURL(source_latlng, dest_latlng!!)
                                async {
                                    // Connect to URL, download content and convert into string asynchronously
                                    val result = URL(url).readText()
                                    uiThread {
                                        // When API call is done, create parser and convert into JsonObjec
                                        val parser: Parser = Parser()
                                        val stringBuilder: StringBuilder = StringBuilder(result)
                                        val json: JsonObject =
                                            parser.parse(stringBuilder) as JsonObject
                                        // get to the
                                        // correct element in JsonObject
                                        val routes = json.array<JsonObject>("routes")
                                        val points =
                                            routes!!["legs"]["steps"][0] as JsonArray<JsonObject>
                                        // For every element in the JsonArray, decode the polyline string and pass all points to a List
                                        polypts =
                                            points.flatMap {
                                                decodePoly(
                                                    it.obj("polyline")?.string("points")!!
                                                )
                                            }
                                        // Add  points to polyline and bounds
                                        options.add(source_latlng)
                                        LatLongB.include(source_latlng)
                                        for (point in polypts!!) {
                                            options.add(point)
                                            LatLongB.include(point)
                                        }

                                        options.add(dest_latlng)
                                        LatLongB.include(dest_latlng)
                                        // build bounds
                                        val bounds = LatLongB.build()
                                        // add polyline to the map

//                                        if (line != null) {
//                                            line!!.remove()
//                                        }
//                                        line = mMap.addPolyline(options)

                                        if (mCurrLocationMarker != null) {

                                            mCurrLocationMarker!!.remove()
                                        }

                                        //Animation Code

                                        if (mAnimateMarker != null) {
                                            mAnimateMarker!!.remove()
                                        }

                                        mAnimateMarker = mMap.addMarker(
                                            MarkerOptions().position(source_latlng)
                                                .icon(BitmapDescriptorFactory.fromBitmap(mMarkerIcon))
                                        )
                                        animateCarMove(
                                            mAnimateMarker, polypts!!.get(0),
                                            polypts!!.get(1), 3000
                                        )


                                        mMap.moveCamera(
                                            CameraUpdateFactory.newLatLngBounds(
                                                bounds,
                                                100
                                            )
                                        )
                                    }
                                }
                            }
//                            //In the Service IF Marker is added then remove first then add again
//                            //In Destination is not added then display the position
//                            //of Current LatLng Or Source LatLng
                            else {
                                if (mCurrLocationMarker != null) {
                                    mCurrLocationMarker!!.remove()
                                }
                                mCurrLocationMarker = mMap.addMarker(
                                    MarkerOptions()
                                        .icon(
                                            BitmapDescriptorFactory.defaultMarker(
                                                BitmapDescriptorFactory.HUE_RED
                                            )
                                        )
                                        .position(
                                            LatLng(
                                                location.latitude,
                                                location.longitude
                                            )
                                        )
                                        .title("You")
                                )

                                mMap.moveCamera(
                                    CameraUpdateFactory.newLatLngZoom(
                                        source_latlng,
                                        15f
                                    )
                                )
                                mMap.animateCamera(CameraUpdateFactory.zoomTo(15f))
                            }
                        }
                    }
                })
            }
        }
    }

    private fun animateCarMove(
        marker: Marker?,
        beginLatLng: LatLng,
        endLatLng: LatLng,
        duration: Long
    ) {

        val handler = Handler()
        val startTime = SystemClock.uptimeMillis()

        val interpolator: Interpolator = LinearInterpolator()

        // set car bearing for current part of path

        // set car bearing for current part of path
        val angleDeg =
            (180 * getAngle(beginLatLng, endLatLng) / Math.PI).toFloat()
        val matrix = Matrix()
        matrix.postRotate(angleDeg)


        val rotatedBitmap = Bitmap.createBitmap(
            mMarkerIcon!!,
            0,
            0,
            mMarkerIcon!!.width,
            mMarkerIcon!!.height,
            matrix,
            true
        )

        marker!!.setIcon(BitmapDescriptorFactory.fromBitmap(rotatedBitmap))

        handler.post(object : Runnable {
            override fun run() {
                // calculate phase of animation
                val elapsed = SystemClock.uptimeMillis() - startTime
                val t: Float =
                    interpolator.getInterpolation(elapsed.toFloat() / duration)
                // calculate new position for marker
                val lat =
                    (endLatLng.latitude - beginLatLng.latitude) * t + beginLatLng.latitude
                var lngDelta = endLatLng.longitude - beginLatLng.longitude
                if (Math.abs(lngDelta) > 180) {
                    lngDelta -= Math.signum(lngDelta) * 360
                }
                val lng = lngDelta * t + beginLatLng.longitude
                marker.setPosition(LatLng(lat, lng))

                // if not end of line segment of path
                if (t < 1.0) {
                    // call next marker position
                    handler.postDelayed(this, 16)
                } else {
                    // call turn animation
                    nextTurnAnimation()
                }
            }
        })


    }

    private fun getAngle(beginLatLng: LatLng, endLatLng: LatLng): Double {
        val f1 = Math.PI * beginLatLng.latitude / 180
        val f2 = Math.PI * endLatLng.latitude / 180
        val dl =
            Math.PI * (endLatLng.longitude - beginLatLng.longitude) / 180
        return Math.atan2(
            Math.sin(dl) * Math.cos(f2),
            Math.cos(f1) * Math.sin(f2) - Math.sin(f1) * Math.cos(
                f2
            ) * Math.cos(dl)
        )
    }

    private fun nextTurnAnimation() {
        mIndexCurrentPoint++

        if (mIndexCurrentPoint < polypts!!.size - 1) {
            val prevLatLng: LatLng = polypts!!.get(mIndexCurrentPoint - 1)
            val currLatLng: LatLng = polypts!!.get(mIndexCurrentPoint)
            val nextLatLng: LatLng = polypts!!.get(mIndexCurrentPoint + 1)
            val beginAngle =
                (180 * getAngle(prevLatLng, currLatLng) / Math.PI).toFloat()
            val endAngle =
                (180 * getAngle(currLatLng, nextLatLng) / Math.PI).toFloat()
            animateCarTurn(mAnimateMarker, beginAngle, endAngle, 3000)
        }
    }

    private fun animateCarTurn(
        marker: Marker?,
        startAngle: Float,
        endAngle: Float,
        duration: Long
    ) {

        val handler = Handler()
        val startTime = SystemClock.uptimeMillis()
        val interpolator: Interpolator = LinearInterpolator()

        val dAndgle = endAngle - startAngle

        val matrix = Matrix()
        matrix.postRotate(startAngle)
        val rotatedBitmap = Bitmap.createBitmap(
            mMarkerIcon!!,
            0,
            0,
            mMarkerIcon!!.getWidth(),
            mMarkerIcon!!.getHeight(),
            matrix,
            true
        )
//        marker!!.setIcon(BitmapDescriptorFactory.fromBitmap(rotatedBitmap))

        handler.post(object : Runnable {
            override fun run() {
                val elapsed = SystemClock.uptimeMillis() - startTime
                val t =
                    interpolator.getInterpolation(elapsed.toFloat() / duration)
                val m = Matrix()
                m.postRotate(startAngle + dAndgle * t)
//                marker!!.setIcon(
//                    BitmapDescriptorFactory.fromBitmap(
//                        Bitmap.createBitmap(
//                            mMarkerIcon!!,
//                            0,
//                            0,
//                            mMarkerIcon!!.getWidth(),
//                            mMarkerIcon!!.getHeight(),
//                            m,
//                            true
//                        )
//                    )
//                )
                if (t < 1.0) {
                    handler.postDelayed(this, 16)
                } else {
//                      nextMoveAnimation()
                }
            }
        })


    }

    private fun nextMoveAnimation() {
        if (mIndexCurrentPoint < polypts!!.size - 1) {
            animateCarMove(
                mAnimateMarker,
                polypts!!.get(mIndexCurrentPoint),
                polypts!!.get(mIndexCurrentPoint + 1),
                3000
            )
        }
    }


    private fun checkPlayService(): Boolean {
        val googleAPI = GoogleApiAvailability.getInstance()
        val result = googleAPI.isGooglePlayServicesAvailable(this)
        if (result != ConnectionResult.SUCCESS) {
            if (googleAPI.isUserResolvableError(result)) {
                googleAPI.getErrorDialog(this, result, PLAY_SERVICE_RESULATION_REQUEST)
                    .show()
            } else {
                Toast.makeText(this, "This Device is not supported.", Toast.LENGTH_SHORT).show()
            }
            return false
        }
        return true
    }

    override fun onTopResumedActivityChanged(isTopResumedActivity: Boolean) {
        if (isTopResumedActivity) {


        } else {

        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
    }
}