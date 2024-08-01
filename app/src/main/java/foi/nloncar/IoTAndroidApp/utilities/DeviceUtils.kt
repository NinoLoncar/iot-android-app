package foi.nloncar.IoTAndroidApp.utilities

import android.content.Context
import android.provider.Settings

object DeviceUtils {
    fun getAndroidId(context: Context): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    }
}