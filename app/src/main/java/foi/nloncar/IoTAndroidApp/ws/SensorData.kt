package foi.nloncar.IoTAndroidApp.ws

data class SensorData(
    var androidId: String,
    val longitude: Double?,
    val latitude: Double?,
    val stepCount: Int,
    val accelerationX: Float,
    val accelerationY: Float,
    val accelerationZ: Float,
    val time: String
)