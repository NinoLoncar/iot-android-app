package foi.nloncar.IoTAndroidApp.ws

data class SensorData(
    var androidId: String,
    val longitude: Double?,
    val altitude: Double?,
    val time: String
)