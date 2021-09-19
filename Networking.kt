
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit


//region Build Retrofit
fun buildRetrofit(baseURL: String, callTimeoutInSeconds: Long) : Retrofit {
    return Retrofit.Builder()
        .baseUrl(baseURL)
        .addConverterFactory(GsonConverterFactory.create(GsonBuilder()
            .serializeNulls()
            .create()))
        .client(
            OkHttpClient.Builder()
                .callTimeout(callTimeoutInSeconds, TimeUnit.SECONDS)
                .build())
        .build()
}
//endregion

//region Retrofit Asynchronous Networking

interface RetrofitAPI {
    val fetching: Fetching

    interface Fetching {
        fun <R> fetch(endpoint: Call<R>, callback: Callback<R>)
    }

    companion object {
        val callEnqueue: Fetching get() {
            return object : Fetching {
                override fun <R> fetch(endpoint: Call<R>, callback: Callback<R>) {
                    endpoint.enqueue(callback)
                }
            }
        }
    }
}

fun <R> RetrofitAPI.fetchJson(
    endpoint: Call<R>,
    success: (R) -> Unit,
    failure: (AnyFetchError) -> Unit
) {
    val resultProcessing = object : RetrofitAsyncResultProcessing<R, AnyFetchError>{}
    fetchJson(endpoint, resultProcessing, success, failure)
}

fun <R, E: AnyFetchError> RetrofitAPI.fetchJson(
    endpoint: Call<R>,
    resultProcessing: RetrofitAsyncResultProcessing<R, E>,
    success: (R) -> Unit,
    failure: (E) -> Unit
) {
    fetchJson(endpoint = endpoint, resultProcessing = resultProcessing, callback = {
        when(it) {
            is RetrofitResult.Success -> success(it.result)
            is RetrofitResult.Failure -> failure(it.error)
        }.forceExhaustive()
    })
}

fun Any?.forceExhaustive() = Unit

private sealed class RetrofitResult<R, E: AnyFetchError> {
    data class Success<R, E: AnyFetchError>(val result: R) : RetrofitResult<R, E>()
    data class Failure<R, E: AnyFetchError>(val error: E) : RetrofitResult<R, E>()
}

private fun <R, E: AnyFetchError> RetrofitAPI.fetchJson(
    endpoint: Call<R>,
    resultProcessing: RetrofitAsyncResultProcessing<R, E>,
    callback: (RetrofitResult<R, E>) -> Unit,
) {
    fetching.fetch(endpoint, object : Callback<R> {
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

//region Retrofit Asynchronous Result Processing

interface RetrofitAsyncResultProcessing<R, E: AnyFetchError> {
    fun onFailure(call: Call<R>, t: Throwable): E {
        // TODO: Check other cases
        return AnyFetchError.notFound(
            error = AnyFetchError.NotFound.missingNetwork,
            addingDump = "(Throwable): $t\n(Call): $call"
        ) as E
    }

    fun onNoData(call: Call<R>, rawResponse: Response<R>): E {
        return AnyFetchError.badRequest(
            error = AnyFetchError.BadRequest.decode,
            addingDump = "(Response): $rawResponse\n(Call): $call"
        ) as E
    }

    fun onResponse(call: Call<R>, response: Response<R>): E? {
        // Note: subclasses could validate the response further and return error
        return if (response.isSuccessful) null else
            AnyFetchError.BadStatusCode(response.code(), response) as E
    }
}
//endregion

//region Generic Network Error Types

sealed interface AnyFetchError {

    val description: String

    enum class BadRequest(override var description: String) : AnyFetchError {
        encode("Error encoding request! $dumpKeyWord"),
        decode("Error decoding request! $dumpKeyWord"),
    }

    enum class NotFound(override var description: String) : AnyFetchError {
        missingData("No data found! $dumpKeyWord"),
        missingNetwork("No network! $dumpKeyWord")
    }

    data class BadStatusCode (val statusCode: Int, val rawResponse: Any) : AnyFetchError {
        override val description: String
            get() = "Bad status code: $statusCode. Raw response: $rawResponse"
    }

    companion object {
        const val dumpKeyWord: String = "dump:-"

        fun badRequest(error: BadRequest, addingDump: String) : BadRequest {
            error.description = error.description + addingDump
            return error
        }

        fun notFound(error: NotFound, addingDump: String) : NotFound {
            error.description = error.description + addingDump
            return error
        }
    }

}

//endregion

