package foi.nloncar.IoTAndroidApp.ws

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface SensorDataService {
    @POST("sensorData")
    fun postSensorData(
        @Header("Api-Key") apiKey: String,
        @Body data: SensorData
    ): Call<ServiceResponse>
}