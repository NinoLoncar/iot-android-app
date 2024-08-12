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
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
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

    private lateinit var dataStoreManager: DataStoreManager
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback

    private var longitude: Double? = null
    private var latitude: Double? = null
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

        dataStoreManager = DataStoreManager(requireContext())

        val androidIdTextView: TextView = view.findViewById(R.id.tv_android_id)
        androidIdTextView.text = DeviceInfoHelper.getAndroidId(requireContext())

        val btnStoreAuthenticationKey: Button = view.findViewById(R.id.btn_store_authentication_key)
        btnStoreAuthenticationKey.setOnClickListener {
            showAuthenticationKeySavingDialog()
        }
        val btnStartDataCollecting: Button = view.findViewById(R.id.btn_start_data_collecting)
        btnStartDataCollecting.setOnClickListener {
            if (checkPrerequisitesForDataCollection()) {
                fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
                lifecycleScope.launch {
                    while (true) {
                        delay(10000)
                        val sensorData = collectData()
                        postData(sensorData)
                    }
                }
            }
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

    private suspend fun postData(sensorData: SensorData) {
        val ws = RetrofitClient.sensorDataService
        val authenticationKey = dataStoreManager.getAuthenticationKey().first()

        ws.postSensorData(authenticationKey ?: "", sensorData).enqueue(
            object : Callback<ServiceResponse> {
                override fun onResponse(
                    call: Call<ServiceResponse>,
                    response: Response<ServiceResponse>
                ) {
                    val message: String = when (response.code()) {
                        200 -> {
                            "Podaci dodani"
                        }

                        403 -> {
                            "Uređaj nije registirian ili je autentifikacijski ključ pogrešan"
                        }

                        else -> {
                            "Dogodila se greška"
                        }
                    }
                    showShortToast(message)
                }

                override fun onFailure(call: Call<ServiceResponse>, t: Throwable) {
                    showShortToast("Dogodila se greška")
                }
            })
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

}

