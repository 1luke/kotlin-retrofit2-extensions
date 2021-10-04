/*

MIT License

Copyright (c) [2021] [Lukas Dagne]

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

*/


import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

// Exceptions
import java.lang.IllegalStateException
import java.net.UnknownHostException


//region Retrofit Asynchronous Networking

/**
 * An interface that simplifies retrofit network requests via it's extension methods.
 *
 * The generic extension methods to `fetch` from remote facilitate scalable and unit-testable
 * APIs with minimal code. A simple example can be:
 * ```
 * class API: RetrofitFetching {
 *  // Build endpoints using your Retrofit service interface
 *  val endpoint: Endpoint = Retrofit.Builder().customBuild(baseURL).create(Endpoint::class.java)
 *
 *  fun fetchItem(success: (Item) -> Unit, failure: (FetchError) -> Unit) {
 *      // Fetching, parsing and returning through `success` or `failure` is handled
 *      this.fetch(endpoint = endpoint.fetchItem(), success = success, failure = failure)
 *  }
 * }
 * ```
 *
 * See `executor` which uses Retrofit Call<T> `enqueue` by default and `resultProcessing`
 * which uses a default processing using `FetchError` object to customize if and API requires
 * custom handling. Most fetch requests can be satisfied without a need for custom handling.
 */
interface RetrofitFetching {
    /** An object that executes given Retrofit request. Set to `Call<T>.enqueue` by default. */
    val executor: Executor get() = callEnqueue

    /** Check network connection and return `true`, `false` or `null` if unable to check. */
    val checkNetwork: (() -> Boolean)? get() = null

    /** An object that executes a Retrofit network request. */
    interface Executor {
        fun <R> fetchAndCallback(endpoint: Call<R>, callback: Callback<R>)
    }

    /**
     * An object that receives Retrofit `Callback<T>` arguments and determines whether or not
     * an error should be returned. Use to return specific error cases for a given network request.
     * */
    interface ResultProcessing<R, E> {
        /** Invoked when Retrofit callback `onFailure` is received. */
        val onFailure: (call: Call<R>, t: Throwable) -> E
        /** Invoked when Retrofit callback `onResponse` is received with no data. */
        val onNoData: (call: Call<R>, response: Response<R>) -> E
        /** Invoked when Retrofit callback `onResponse` is received. */
        val onResponse: (call: Call<R>, response: Response<R>) -> E?
    }

    companion object {
        /** Creates and returns a network request executor using Retrofit `Call<T>` enqueue. */
        val callEnqueue: Executor get() {
            return object : Executor {
                override fun <R> fetchAndCallback(endpoint: Call<R>, callback: Callback<R>) {
                    endpoint.enqueue(callback)
                }
            }
        }
    }
}

/**
 * Fetch for response type `R` and callback in `success` callback. Invokes failure with
 * an error subtype of `AnyFetchError` upon failure.
 *
 * @param usingExecutor Network request executor (set to `this.executor` by default)
 * @param endpoint The API endpoint that should be fetched
 * @param success Success callback with the response `R`
 * @param failure Failure callback with an error case from `AnyFetchError` subtypes
 */
fun <R> RetrofitFetching.fetch(
    usingExecutor: RetrofitFetching.Executor = executor,
    endpoint: Call<R>,
    success: (R) -> Unit,
    failure: (FetchError) -> Unit
) {
    val resultProcessing = StandardRetrofitResultProcessing<R, FetchError>(
        hasNetwork = checkNetwork,
        mapNetworkError = { FetchError.Network(it) }
    )
    fetch(usingExecutor, endpoint, resultProcessing, success, failure)
}

/**
 * Fetch for response type `R` and callback in `success` callback. Invokes failure with
 * an error subtype of `E : AnyFetchError` upon failure.
 *
 * @param usingExecutor Network request executor (set to `this.executor` by default)
 * @param endpoint The API endpoint that should be fetched
 * @param resultProcessing Processes response and finds corresponding error case (if needed)
 * @param success Success callback with the response `R`
 * @param failure Failure callback with an error case from given error subtype `E`
 */
fun <R, E> RetrofitFetching.fetch(
    usingExecutor: RetrofitFetching.Executor = executor,
    endpoint: Call<R>,
    resultProcessing: RetrofitFetching.ResultProcessing<R, E>,
    success: (R) -> Unit,
    failure: (E) -> Unit
) {
    fetch(usingExecutor, endpoint, resultProcessing) {
        when (it) {
            is RetrofitResult.Success -> success(it.result)
            is RetrofitResult.Failure -> failure(it.error)
        }.forceExhaustive()
    }
}

/** Use to force exhaustive switch handling (until Kotlin adds builtin support) */
fun Any.forceExhaustive() = Unit

private sealed class RetrofitResult<R, E> {
    data class Success<R, E>(val result: R) : RetrofitResult<R, E>()
    data class Failure<R, E>(val error: E) : RetrofitResult<R, E>()
}

private fun <R, E> fetch(
    usingExecutor: RetrofitFetching.Executor,
    endpoint: Call<R>,
    resultProcessing: RetrofitFetching.ResultProcessing<R, E>,
    callback: (RetrofitResult<R, E>) -> Unit,
) {
    usingExecutor.fetchAndCallback(endpoint, object : Callback<R> {

        override fun onFailure(call: Call<R>, t: Throwable) {
            callback(RetrofitResult.Failure(resultProcessing.onFailure(call, t)))
        }

        override fun onResponse(call: Call<R>, response: Response<R>) {
            callback(resultFrom(call, response))
        }

        private fun resultFrom(call: Call<R>, response: Response<R>) : RetrofitResult<R, E> {
            return response.body()?.let { json ->
                resultProcessing.onResponse(call, response)?.let { error ->
                    RetrofitResult.Failure(error)
                } ?: run {
                    RetrofitResult.Success(json)
                }
            } ?: run {
                RetrofitResult.Failure(resultProcessing.onNoData(call, response))
            }
        }

    })
}
//endregion

//region Retrofit Standard Result Processing

/**
 * Returns result processing object for given response type `R` and error type `E`
 *
 * @param hasNetwork A block that's invoked to check network connection error.
 * @param mapNetworkError Maps common network errors - `AnyFetchError`, to error-type `E`.
 *
 */
class StandardRetrofitResultProcessing<R, E>(
    hasNetwork: (() -> Boolean)?,
    mapNetworkError: (AnyFetchError) -> E
) : RetrofitFetching.ResultProcessing<R, E> {

    override var onFailure: (call: Call<R>, t: Throwable) -> E = { call, t ->
        val error: AnyFetchError = when(t) {
            is UnknownHostException -> {
                if (hasNetwork != null) {
                    if (hasNetwork()) AnyFetchError.BadRequest.Encode
                    else AnyFetchError.NotFound.MissingNetwork
                } else {
                    // Cannot distinguish the error case from `MissingNetwork` and `Encode` error.
                    AnyFetchError.Unknown()
                }
            }
            is IllegalStateException -> AnyFetchError.BadRequest.Decode

            //TODO: Check other cases
            else -> AnyFetchError.Unknown()
        }
        mapNetworkError(
            AnyFetchError.make(error = error, addingDump = "(Throwable): $t\n(Call): $call")
        )
    }

    override var onNoData: (call: Call<R>, response: Response<R>) -> E = { call, response ->
        mapNetworkError(
            AnyFetchError.make(
                error = AnyFetchError.NotFound.MissingData,
                addingDump = "(Response): $response\n(Call): $call"
            )
        )
    }

    override var onResponse: (call: Call<R>, response: Response<R>) -> E? = { call, response ->
        // Note: subclasses could validate the response further and return error
        if (response.isSuccessful) null else
            mapNetworkError(AnyFetchError.BadStatusCode(response.code(), response))
    }

}

//endregion

//region Generic Network Error Types

sealed interface FetchError {
    data class Network(val underlyingError: AnyFetchError) : FetchError
}

/** Supertype for network error types. */
sealed interface AnyFetchError {

    var description: String

    enum class BadRequest(override var description: String) : AnyFetchError {
        Encode("FIXME: Error encoding request! $dumpKeyWord"),
        Decode("FIXME: Error decoding request! $dumpKeyWord"),
    }

    enum class NotFound(override var description: String) : AnyFetchError {
        MissingData("No data found! $dumpKeyWord"),
        MissingNetwork("No network! $dumpKeyWord")
    }

    data class BadStatusCode (val statusCode: Int, val rawResponse: Any) : AnyFetchError {
        override var description: String =
            "Bad status code: $statusCode. Raw response: $rawResponse"
    }

    /**
     * Represents a vague error case typically caused by `UnknownHostException`.
     * This error case is encountered if and only if network status cannot be determined
     * while the `UnknownHostException` is received.
     */
    data class Unknown(
        override var description: String = "Unknown Error! $dumpKeyWord"
    ) : AnyFetchError

    companion object {
        const val dumpKeyWord: String = "dump:-"

        fun make(error: AnyFetchError, addingDump: String) : AnyFetchError {
            error.description = error.description + addingDump
            return error
        }
    }

}

//endregion



