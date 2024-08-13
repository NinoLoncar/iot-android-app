package foi.nloncar.IoTAndroidApp.fragments

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
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


class DataCollectionFragment : Fragment() {

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

    private var longitude: Double? = null
    private var latitude: Double? = null
    private var dataCollectionInProgress: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        setupRequestPermissionLauncher()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        locationRequest = LocationRequest.Builder(10000)
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setMinUpdateIntervalMillis(5000)
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

        return inflater.inflate(R.layout.fragment_data_collection, container, false)
    }

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        loadingCircle = view.findViewById(R.id.pb_data_collection_in_progress)
        tvNumberOfDataTransfersDesc = view.findViewById(R.id.tv_number_of_data_transfers_desc)
        tvNumberOfDataTransfers = view.findViewById(R.id.tv_number_of_data_transfers)
        tvAndroidId = view.findViewById(R.id.tv_android_id)
        tvAndroidIdDesc = view.findViewById(R.id.tv_android_id_desc)
        btnStopDataCollection = view.findViewById(R.id.btn_stop_data_collection)
        btnStoreAuthenticationKey = view.findViewById(R.id.btn_store_authentication_key)
        btnStartDataCollecting = view.findViewById(R.id.btn_start_data_collection)

        dataStoreManager = DataStoreManager(requireContext())

        tvAndroidId.text = DeviceInfoHelper.getAndroidId(requireContext())

        btnStoreAuthenticationKey.setOnClickListener {
            showAuthenticationKeySavingDialog()
        }

        btnStartDataCollecting.setOnClickListener {
            if (checkPrerequisitesForDataCollection()) {
                dataCollectionInProgress = true
                changeDisplay()
                fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
                dataCollectionJob = lifecycleScope.launch {
                    var numberOfDataTransfers = 0
                    tvNumberOfDataTransfers.text = "0"
                    delay(2000)
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
            }
        }

        btnStopDataCollection.setOnClickListener {
            dataCollectionJob?.cancel()
            dataCollectionInProgress = false
            changeDisplay()
        }

    }

    private fun checkPrerequisitesForDataCollection(): Boolean {
        if (!DeviceInfoHelper.checkInternetConnection(requireContext())) {
            showShortToast("Niste povezani na internet")
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
        return true
    }

    private fun setupRequestPermissionLauncher() {
        requestPermissionLauncher = registerForActivityResult(
            RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                Toast.makeText(requireContext(), "Dozvola odobrena", Toast.LENGTH_SHORT).show()
            } else {
                showLocationRequiredDialog()
            }
        }
    }

    private fun showLocationDisabledDialog() {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Lokacija")
            .setMessage("Za prikupljanje podatka, uključite lokaciju.")
            .setPositiveButton("U redu") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun showLocationRequiredDialog() {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Lokacija")
            .setMessage("Za prikupljanje podatka potrebna je lokacija uređaja.")
            .setPositiveButton("Idi na postavke") { _, _ ->
                openAppSettings()
            }
            .setNegativeButton("Odustani") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun showAuthenticationKeySavingDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_store_authentication_key, null)
        val builder = AlertDialog.Builder(requireContext())
        builder.setView(dialogView)
        builder.setPositiveButton("Spremi") { dialog, _ ->
            val editText: EditText = dialogView.findViewById(R.id.dialogEditText)
            val inputText = editText.text.toString()
            lifecycleScope.launch {
                dataStoreManager.updateAuthenticationKey(inputText)
            }
            dialog.dismiss()
        }
        builder.setNegativeButton("Odustani") { dialog, _ ->
            dialog.dismiss()
        }
        val dialog = builder.create()
        dialog.show()
    }

    private fun collectData(): SensorData {
        val androidId = DeviceInfoHelper.getAndroidId(requireContext())
        val time = getCurrentTime()
        return SensorData(androidId, longitude, latitude, time)
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
                            showShortToast("Uređaj nije registirian ili je autentifikacijski ključ pogrešan")

                        }

                        else -> {
                            success = false
                            showShortToast("Dogodila se greška")
                        }
                    }

                    deferred.complete(success)
                }

                override fun onFailure(call: Call<ServiceResponse>, t: Throwable) {
                    showShortToast("Dogodila se greška")
                    deferred.complete(false)
                }
            })
        return deferred.await()
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        val uri = Uri.fromParts("package", requireContext().packageName, null)
        intent.data = uri
        startActivity(intent)
    }

    private fun showShortToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT)
            .show()
    }

    private fun getCurrentTime(): String {
        val currentTime = System.currentTimeMillis()
        return SimpleDateFormat(
            " HH:mm:ss dd.MM.yyyy",
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

}

