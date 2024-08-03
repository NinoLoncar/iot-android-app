package foi.nloncar.IoTAndroidApp.fragments

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import foi.nloncar.IoTAndroidApp.R
import foi.nloncar.IoTAndroidApp.utilities.DeviceUtils


class DataCollectionFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_data_collection, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val androidIdTextView: TextView = view.findViewById(R.id.tv_android_id)
        androidIdTextView.text = DeviceUtils.getAndroidId(requireContext())

        val btnStoreApiKey: Button = view.findViewById(R.id.btn_store_api_key)
        btnStoreApiKey.setOnClickListener {
            showDialog()
        }
    }

    private fun showDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_store_api_key, null)
        val builder = AlertDialog.Builder(requireContext())
        builder.setView(dialogView)
        builder.setPositiveButton("Spremi") { dialog, _ ->
            val editText: EditText = dialogView.findViewById(R.id.dialogEditText)
            val inputText = editText.text.toString()
            dialog.dismiss()
        }
        builder.setNegativeButton("Odustani") { dialog, _ ->
            dialog.dismiss()
        }
        val dialog = builder.create()
        dialog.show()

    }
}