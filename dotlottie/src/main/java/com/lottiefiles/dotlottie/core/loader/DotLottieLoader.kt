import android.content.Context
import com.lottiefiles.dotlottie.core.loader.AssetLoader
import com.lottiefiles.dotlottie.core.loader.NetworkLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.IOException

sealed class DotLottieResult<out T> {
    data class Success<out T>(val data: T) : DotLottieResult<T>()
    data class Error(val exception: Exception) : DotLottieResult<Nothing>()
}

sealed class DotLottieContent {
    data class Json(val jsonString: String) : DotLottieContent()
    data class Binary(val data: ByteArray) : DotLottieContent()
}

class DotLottieLoader(private val context: Context) {
    fun fromAsset(assetPath: String) = AssetLoader(context, assetPath)
    fun fromUrl(url: String) = NetworkLoader(context, url)

    companion object {

        /**
         * Factory method to get an instance of the loader
         * @param context context for this instantiation
         */
        @JvmStatic
        fun with(context: Context): DotLottieLoader {
            return DotLottieLoader(context)
        }
    }
}
