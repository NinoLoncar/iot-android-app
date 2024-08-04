package foi.nloncar.IoTAndroidApp.ws

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {

    private const val BASE_URL =
        "https://eu-central-1.aws.data.mongodb-api.com/app/application-0-cpcnkro/endpoint/"

    private val instance: Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val sensorDataService = instance.create(SensorDataService::class.java)
}