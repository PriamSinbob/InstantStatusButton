package com.instantstatusbutton.app.auth

import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

object Http {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}

/** [retryable] is true for network problems, rate limits and server errors; false for auth/validation errors. */
class PostException(message: String, val retryable: Boolean = false, cause: Throwable? = null) :
    Exception(message, cause)
