package util

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import util.Utils.Companion.execute
import java.io.FileOutputStream
import java.io.IOException
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager


class HttpUtils {
    companion object {
        val jsonMediaType = "application/json; charset=utf-8".toMediaType()
        private val okHttpClient = OkHttpClient()
        private val okHttpClientIgnoreCertificate = configureToIgnoreCertificate(OkHttpClient.Builder()).build()

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

        fun post(url: String, body: RequestBody): String {
            val request = Request.Builder().post(body).url(url).build()
            okHttpClient.newCall(request).execute().use {
                if (it.isSuccessful && it.body != null) {
                    return it.body?.string() ?: ""
                }
            }
            return ""
        }

        fun downloadIgnoreCertificate(url: String, outputPath: String) {
            execute("Downloading $outputPath ...") {
                val request = Request.Builder().url(url).build()
                val response = okHttpClientIgnoreCertificate.newCall(request).execute()

                response.use {
                    if (it.isSuccessful && it.body != null) {
                        val input = it.body!!.byteStream()
                        val out = FileOutputStream(outputPath)
                        input.use {
                            out.write(input.readBytes())
                        }
                    }
                }
            }
        }

        /// Source: https://gist.github.com/preethamhegdes/fcab7bced52bf2520994ce232f2102ed
        private fun configureToIgnoreCertificate(builder: OkHttpClient.Builder): OkHttpClient.Builder {
            //logger.warn("Ignore Ssl Certificate")
            try {
                // Create a trust manager that does not validate certificate chains

                val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                    @Throws(CertificateException::class)
                    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
                    }

                    @Throws(CertificateException::class)
                    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                    }

                    override fun getAcceptedIssuers(): Array<X509Certificate> {
                        return arrayOf()
                    }
                })

                // Install the all-trusting trust manager
                val sslContext = SSLContext.getInstance("SSL")
                sslContext.init(null, trustAllCerts, SecureRandom())
                // Create an SSL socket factory with our all-trusting manager
                val sslSocketFactory = sslContext.socketFactory

                builder.sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
                builder.hostnameVerifier { _, _ -> true }
            } catch (e: Exception) {
                //logger.warn("Exception while configuring IgnoreSslCertificate$e", e)
            }
            return builder
        }
    }
}