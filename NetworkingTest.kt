
import com.google.gson.GsonBuilder
import okhttp3.MediaType
import okhttp3.ResponseBody
import org.junit.Test
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.http.GET
import java.io.IOException

//region Networking request and callback tests

class RetrofitAPITest {

    private val api: TestEndpoint = buildRetrofit("http://test/", callTimeoutInSeconds = 1)
        .create(TestEndpoint::class.java)

    @Test fun testOnSuccessReturnsData() {
        val expectedData = TestData()
        var receivedData: TestData? = null
        NetworkingSimulation(expectedData).fetchJson(
            api.fetchTestData(),
            success = { data -> receivedData = data },
            failure = { }
        )
        assert(receivedData === expectedData) { "$assertKeyWord Success must return data" }
    }

    @Test fun testOnFailureReturnsError() {
        var receivedError: AnyFetchError? = null
        NetworkingSimulation(Throwable("-")).fetchJson(
            api.fetchTestData(),
            success = { },
            failure = { error -> receivedError = error }
        )
        assert(receivedError != null) { "$assertKeyWord Failure must return error" }
    }

    @Test fun testOnSuccessInvokesOnlySuccess() {
        var receivedData = false
        var receivedError = false
        NetworkingSimulation(TestData()).fetchJson(
            api.fetchTestData(),
            success = { receivedData = true },
            failure = { receivedError = true }
        )

        assert(receivedData) { "$assertKeyWord Success response must invoke success" }
        assert(!receivedError) { "$assertKeyWord Success response must not invoke failure" }
    }

    @Test fun testOnFailureInvokesOnlyFailure() {
        var receivedData = false
        var receivedError = false
        NetworkingSimulation(Throwable("-")).fetchJson(
            api.fetchTestData(),
            success = { receivedData = true },
            failure = { receivedError = true }
        )

        assert(receivedError) { "$assertKeyWord Failure response must invoke failure" }
        assert(!receivedData) { "$assertKeyWord Failure response must not invoke success" }
    }

    interface TestEndpoint {
        @GET("http://test/")
        fun fetchTestData(): Call<TestData>
    }

    data class TestData (val id: Int = 1)

    companion object {
        const val assertKeyWord = "Assert!!"
    }
}
//endregion

//region Json Parsing Tests

class RetrofitJsonParsingTest {

    private val retrofit = buildRetrofit("http://test/", callTimeoutInSeconds = 10)

    @Test fun testEmptyJsonReturnsDataWithDefaultAndNullValues() {
        var emptyJsonParseResult: NullFieldTestData? = null
        try {
            emptyJsonParseResult = retrofit.responseBodyConverter<NullFieldTestData>(
                NullFieldTestData::class.java, emptyArray()
            ).convert(
                ResponseBody.create(
                    MediaType.parse("application/json"),
                    GsonBuilder().create().toJson(emptyMap<String, Any>()).toString()
                )
            )
        } catch (e: IOException) { }

        assert(emptyJsonParseResult?.nonnullValue == 0)
        assert(emptyJsonParseResult?.nullableValue == null)
    }

    // TODO: Test more scenarios

    data class NullFieldTestData(val nonnullValue: Int, val nullableValue: String?)
}
//endregion

//region Helper - Networking Simulation
/**
 * Allows for networking simulation by exposing `onResponse` callback.
 * Use to test networking (sync/async) for success and failure results.
 */
class NetworkingSimulation(
    private val onResponse: (Call<Any>, callback: Callback<Any>) -> Unit,
) : RetrofitAPI, RetrofitAPI.Fetching {
    override val fetching get() = this

    override fun <R> fetch(endpoint: Call<R>, callback: Callback<R>) {
        val badTest: () -> Nothing = {
            throw Throwable("Bad test! Set matching types for expected response")
        }
        val endpoint = endpoint as? Call<Any> ?: badTest()
        val callback = callback as? Callback<Any> ?: badTest()
        onResponse.invoke(endpoint, callback)
    }

    /** Fakes synchronous success response */
    constructor(data: Any) : this({ call, callback ->
        callback.onResponse(call, Response.success(data))
    })

    /** Fakes synchronous failure response */
    constructor(t: Throwable) : this({ call, callback -> callback.onFailure(call, t) })
}
//endregion