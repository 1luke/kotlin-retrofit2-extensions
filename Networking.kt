import retrofit2.Retrofit
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

data class HTTPRequest<R, E>(
    val retrofitCall: Call<R>,
    var requestError: (Call<R>, Throwable) -> E,
    var decodeError: (Call<R>, Response<R>) -> E
)

interface RestClient {
    fun <R, E> fetch(httpRequest: HTTPRequest<R, E>, success: (R) -> Unit, failure: (E) -> Unit) {
        httpRequest.retrofitCall.enqueue(object : Callback<R> {
            override fun onFailure(call: Call<R>, t: Throwable) {
                failure(httpRequest.requestError(call, t))
            }

            override fun onResponse(call: Call<R>, response: Response<R>) {
                response.body()?.let { success(it) } ?: run {
                    httpRequest.decodeError(call, response)
                }
            }
        })
    }
}
