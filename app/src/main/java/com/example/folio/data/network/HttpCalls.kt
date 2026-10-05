package com.example.folio.data.network

import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response

/** Cancellation also stops an in-flight request or response-body read. */
internal suspend fun Call.awaitBody(): String = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            try {
                val body = response.use {
                    if (!it.isSuccessful) throw PricingException.Unavailable("HTTP ${it.code}")
                    it.body?.string()
                        ?: throw PricingException.Invalid("The provider returned an empty response.")
                }
                continuation.resume(body)
            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
        }
    })
}
