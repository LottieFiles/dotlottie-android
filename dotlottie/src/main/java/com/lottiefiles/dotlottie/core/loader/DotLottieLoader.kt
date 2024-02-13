import android.content.Context
import com.lottiefiles.dotlottie.core.loader.AssetLoader
import com.lottiefiles.dotlottie.core.loader.NetworkLoader

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
