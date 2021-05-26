import retrofit2.Retrofit
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

/*Execute fetch request (async) and callback success or failure*/
fun <T: Any> Call<T>.fetch(success: (T) -> Unit, failure: (Throwable) -> Unit) {
    enqueue(object: Callback<T> {
        override fun onFailure(call: Call<T>, t: Throwable) {
            failure(t)
        }

        override fun onResponse(call: Call<T>, response: Response<T>) {
            if (response.body() != null) success(response.body()!!)
            else failure(Throwable("No response"))
        }
    })
}
