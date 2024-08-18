package foi.nloncar.IoTAndroidApp.fragments

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import foi.nloncar.IoTAndroidApp.R
import foi.nloncar.IoTAndroidApp.helpers.DeviceInfoHelper
import foi.nloncar.IoTAndroidApp.helpers.LocationHelper
import foi.nloncar.IoTAndroidApp.managers.DataStoreManager
import foi.nloncar.IoTAndroidApp.ws.RetrofitClient
import foi.nloncar.IoTAndroidApp.ws.SensorData
import foi.nloncar.IoTAndroidApp.ws.ServiceResponse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class DataCollectionFragment : Fragment(), SensorEventListener {

    private lateinit var loadingCircle: ProgressBar
    private lateinit var tvNumberOfDataTransfersDesc: TextView
    private lateinit var tvNumberOfDataTransfers: TextView
    private lateinit var tvAndroidId: TextView
    private lateinit var tvAndroidIdDesc: TextView
    private lateinit var btnStopDataCollection: Button
    private lateinit var btnStoreAuthenticationKey: Button
    private lateinit var btnStartDataCollecting: Button

    private lateinit var dataStoreManager: DataStoreManager
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private var dataCollectionJob: Job? = null

    private lateinit var sensorManager: SensorManager
    private var stepDetector: Sensor? = null
    private var accelerometer: Sensor? = null

    private var longitude: Double? = null
    private var latitude: Double? = null
    private var dataCollectionInProgress: Boolean = false
    private var stepCount: Int = 0
    private var accelerationX: Float = 0f
    private var accelerationY: Float = 0f
    private var accelerationZ: Float = 0f

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        dataStoreManager = DataStoreManager(requireContext())
        lifecycleScope.launch {
            val storedStepCount = dataStoreManager.getStepCount().first()
            if (storedStepCount != null) {
                stepCount = storedStepCount.toInt()
            }
        }

        setupRequestPermissionLauncher()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        locationRequest = LocationRequest.Builder(5000)
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(p0: LocationResult) {
                p0 ?: return
                for (location in p0.locations) {
                    longitude = location.longitude
                    latitude = location.latitude
                }
            }
        }
        sensorManager = requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepDetector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

        return inflater.inflate(R.layout.fragment_data_collection, container, false)
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        initializeUiComponents(view)

        tvAndroidId.text = DeviceInfoHelper.getAndroidId(requireContext())

        btnStoreAuthenticationKey.setOnClickListener {
            showAuthenticationKeySavingDialog()
        }

        btnStartDataCollecting.setOnClickListener {
            if (checkPrerequisitesForDataCollection()) {
                dataCollectionInProgress = true
                changeDisplay()
                startDataCollection()
            }
        }

        btnStopDataCollection.setOnClickListener {
            dataCollectionJob?.cancel()
            dataCollectionInProgress = false
            changeDisplay()
        }
    }

    private fun setupRequestPermissionLauncher() {
        requestPermissionLauncher = registerForActivityResult(
            RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.ready_for_data_collecting),
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                showRequiredPermissionsDialog()
            }
        }
    }

    private fun initializeUiComponents(view: View) {
        loadingCircle = view.findViewById(R.id.pb_data_collection_in_progress)
        tvNumberOfDataTransfersDesc = view.findViewById(R.id.tv_number_of_data_transfers_desc)
        tvNumberOfDataTransfers = view.findViewById(R.id.tv_number_of_data_transfers)
        tvAndroidId = view.findViewById(R.id.tv_android_id)
        tvAndroidIdDesc = view.findViewById(R.id.tv_android_id_desc)
        btnStopDataCollection = view.findViewById(R.id.btn_stop_data_collection)
        btnStoreAuthenticationKey = view.findViewById(R.id.btn_store_authentication_key)
        btnStartDataCollecting = view.findViewById(R.id.btn_start_data_collection)
    }

    private fun checkPrerequisitesForDataCollection(): Boolean {
        if (!DeviceInfoHelper.checkInternetConnection(requireContext())) {
            showLongToast(getString(R.string.no_internet_connection))
            return false
        }
        if (!LocationHelper.checkLocationPermission(requireContext())) {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            return false
        }
        if (!LocationHelper.isLocationEnabled(requireContext())) {
            showLocationDisabledDialog()
            return false
        }
        if (!DeviceInfoHelper.checkActivityPermission(requireContext())) {
            requestPermissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
            return false
        }
        return true
    }

    @SuppressLint("MissingPermission")
    private fun startDataCollection() {
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
        registerSensorListeners()
        dataCollectionJob = lifecycleScope.launch {
            runDataCollectionLoop()
        }
    }

    private suspend fun runDataCollectionLoop() {
        var numberOfDataTransfers = 0
        tvNumberOfDataTransfers.text = "0"
        delay(10000)
        while (dataCollectionInProgress) {
            val sensorData = collectData()
            val success = postData(sensorData)
            if (success) {
                numberOfDataTransfers++
                tvNumberOfDataTransfers.text = numberOfDataTransfers.toString()
                delay(10000)
            } else
                dataCollectionInProgress = false
        }
        changeDisplay()
    }

    private fun collectData(): SensorData {
        val androidId = DeviceInfoHelper.getAndroidId(requireContext())
        val time = getCurrentTime()
        return SensorData(
            androidId,
            longitude,
            latitude,
            stepCount,
            accelerationX,
            accelerationY,
            accelerationZ,
            time
        )
    }

    private suspend fun postData(sensorData: SensorData): Boolean {
        val ws = RetrofitClient.sensorDataService
        val authenticationKey = dataStoreManager.getAuthenticationKey().first()
        val deferred = CompletableDeferred<Boolean>()

        ws.postSensorData(authenticationKey ?: "", sensorData).enqueue(
            object : Callback<ServiceResponse> {
                override fun onResponse(
                    call: Call<ServiceResponse>,
                    response: Response<ServiceResponse>
                ) {
                    val success: Boolean
                    when (response.code()) {
                        200 -> {
                            success = true
                        }

                        403 -> {
                            success = false
                            showLongToast(getString(R.string.failed_authentication))
                        }

                        else -> {
                            success = false
                            showLongToast(getString(R.string.error))
                        }
                    }
                    deferred.complete(success)
                }

                override fun onFailure(call: Call<ServiceResponse>, t: Throwable) {
                    showLongToast(getString(R.string.error))
                    deferred.complete(false)
                }
            })
        return deferred.await()
    }

    private fun showLocationDisabledDialog() {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle(getString(R.string.location))
            .setMessage(getString(R.string.enable_location))
            .setPositiveButton(getString(R.string.ok)) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun showRequiredPermissionsDialog() {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle(getString(R.string.location_and_activtiy_tracking))
            .setMessage(getString(R.string.required_permissions))
            .setPositiveButton(getString(R.string.go_to_settings)) { _, _ ->
                openAppSettings()
            }
            .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun showAuthenticationKeySavingDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_store_authentication_key, null)
        val builder = AlertDialog.Builder(requireContext())

        builder.setView(dialogView)
        builder.setPositiveButton(getString(R.string.save)) { dialog, _ ->
            val editText: EditText = dialogView.findViewById(R.id.dialogEditText)
            val inputText = editText.text.toString()
            lifecycleScope.launch {
                dataStoreManager.updateAuthenticationKey(inputText)
            }
            dialog.dismiss()
        }
        builder.setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
            dialog.dismiss()
        }

        val dialog = builder.create()
        dialog.show()
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        val uri = Uri.fromParts("package", requireContext().packageName, null)
        intent.data = uri
        startActivity(intent)
    }

    private fun showLongToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG)
            .show()
    }

    private fun getCurrentTime(): String {
        val currentTime = System.currentTimeMillis()
        return SimpleDateFormat(
            "HH:mm:ss dd.MM.yyyy",
            Locale.getDefault()
        ).format(Date(currentTime))
    }

    private fun changeDisplay() {
        loadingCircle.isVisible = dataCollectionInProgress
        tvNumberOfDataTransfersDesc.isVisible = dataCollectionInProgress
        tvNumberOfDataTransfers.isVisible = dataCollectionInProgress
        btnStopDataCollection.isVisible = dataCollectionInProgress

        tvAndroidId.isVisible = !dataCollectionInProgress
        tvAndroidIdDesc.isVisible = !dataCollectionInProgress
        btnStoreAuthenticationKey.isVisible = !dataCollectionInProgress
        btnStartDataCollecting.isVisible = !dataCollectionInProgress
    }

    private fun registerSensorListeners() {
        if (stepDetector != null) {
            sensorManager.registerListener(
                this,
                stepDetector,
                SensorManager.SENSOR_DELAY_NORMAL
            )
        }
        if (accelerometer != null) {
            sensorManager.registerListener(
                this,
                accelerometer,
                SensorManager.SENSOR_DELAY_NORMAL
            )
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event != null) {
            if (event.sensor.type == Sensor.TYPE_STEP_DETECTOR) {
                stepCount++
                lifecycleScope.launch {
                    dataStoreManager.updateStepCount(stepCount)
                }
            }
            if (event.sensor.type == Sensor.TYPE_LINEAR_ACCELERATION) {
                accelerationX = event.values[0]
                accelerationY = event.values[1]
                accelerationZ = event.values[2]
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }

    override fun onPause() {
        sensorManager.unregisterListener(this)
        super.onPause()
    }

}

