import okhttp3.*
import java.io.FileOutputStream
import java.io.IOException


class HttpHelper {
    companion object {
        private val okHttpClient = OkHttpClient()
        fun getAsync(
            url: String,
            onFailure: (call: Call) -> Unit,
            onResponse: (responseBody: String) -> Unit
        ) {
            val request = Request.Builder().url(url).build()
            okHttpClient.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    onFailure(call)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        if (response.isSuccessful && response.body != null) {
                            onResponse(response.body?.string() ?: "")
                        } else {
                            onFailure(call)
                        }
                    }
                }
            })
        }

        fun get(url: String): String {
            val request = Request.Builder().url(url).build()
            okHttpClient.newCall(request).execute().use {
                if (it.isSuccessful && it.body != null) {
                    return it.body?.string() ?: ""
                }
            }
            return ""
        }
    }
}