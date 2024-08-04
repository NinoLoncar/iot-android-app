package foi.nloncar.IoTAndroidApp.fragments

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import foi.nloncar.IoTAndroidApp.R
import foi.nloncar.IoTAndroidApp.managers.DataStoreManager
import foi.nloncar.IoTAndroidApp.utilities.DeviceUtils
import foi.nloncar.IoTAndroidApp.ws.RetrofitClient
import foi.nloncar.IoTAndroidApp.ws.SensorData
import foi.nloncar.IoTAndroidApp.ws.ServiceResponse
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response


class DataCollectionFragment : Fragment() {

    private lateinit var dataStoreManager: DataStoreManager
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_data_collection, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        dataStoreManager = DataStoreManager(requireContext())

        val androidIdTextView: TextView = view.findViewById(R.id.tv_android_id)
        androidIdTextView.text = DeviceUtils.getAndroidId(requireContext())

        val btnStoreApiKey: Button = view.findViewById(R.id.btn_store_api_key)
        btnStoreApiKey.setOnClickListener {
            showDialog()
        }
        val btnStartDataCollecting: Button = view.findViewById(R.id.btn_start_data_collecting)
        btnStartDataCollecting.setOnClickListener {
            lifecycleScope.launch {
                postData()
            }
        }

    }

    private fun showDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_store_api_key, null)
        val builder = AlertDialog.Builder(requireContext())
        builder.setView(dialogView)
        builder.setPositiveButton("Spremi") { dialog, _ ->
            val editText: EditText = dialogView.findViewById(R.id.dialogEditText)
            val inputText = editText.text.toString()
            lifecycleScope.launch {
                dataStoreManager.updateApiKey(inputText)
            }
            dialog.dismiss()
        }
        builder.setNegativeButton("Odustani") { dialog, _ ->
            dialog.dismiss()
        }
        val dialog = builder.create()
        dialog.show()
    }

    private suspend fun postData() {
        val ws = RetrofitClient.sensorDataService
        val apiKey = dataStoreManager.getApiKey().first()

        val sensorData = SensorData(DeviceUtils.getAndroidId(requireContext()))

        ws.postSensorData(apiKey ?: "", sensorData).enqueue(
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
                            "Uređaj nije registirian ili je API ključ pogrešan"
                        }

                        else -> {
                            "Dogodila se greška"
                        }
                    }
                    Toast.makeText(
                        requireContext(),
                        message,
                        Toast.LENGTH_SHORT
                    )
                        .show()
                }

                override fun onFailure(call: Call<ServiceResponse>, t: Throwable) {
                    Toast.makeText(requireContext(), "Dogodila se greška", Toast.LENGTH_SHORT)
                        .show()
                }
            })
    }
}

