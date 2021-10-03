
import org.junit.Test
import retrofit2.*
import retrofit2.http.GET

//region Networking request and callback tests

/**
 * Note: Use the `customBuild` factory or apply your own customization
 * used in the app to allow the unit-tests test the right `retrofit` configuration.
 */
val retrofit: Retrofit get() = Retrofit.Builder().customBuild("http://test/")

class RetrofitFetchingTest {

    private val api: TestEndpoint = retrofit.create(TestEndpoint::class.java)

    @Test fun testOnSuccessReturnsData() {
        val expectedData = TestData()
        var receivedData: TestData? = null
        NetworkingSimulation(expectedData).fetch(
            endpoint = api.fetchTestData(),
            success = { data -> receivedData = data },
            failure = { }
        )
        assert(receivedData === expectedData) { "$assertKeyWord Success must return data" }
    }

    @Test fun testOnFailureReturnsError() {
        var receivedError: AnyFetchError? = null
        NetworkingSimulation(Throwable("-")).fetch(
            endpoint = api.fetchTestData(),
            success = { },
            failure = { error -> receivedError = error }
        )
        assert(receivedError != null) { "$assertKeyWord Failure must return error" }
    }

    @Test fun testOnSuccessInvokesOnlySuccess() {
        var receivedData = false
        var receivedError = false
        NetworkingSimulation(TestData()).fetch(
            endpoint = api.fetchTestData(),
            success = { receivedData = true },
            failure = { receivedError = true }
        )

        assert(receivedData) { "$assertKeyWord Success response must invoke success" }
        assert(!receivedError) { "$assertKeyWord Success response must not invoke failure" }
    }

    @Test fun testOnFailureInvokesOnlyFailure() {
        var receivedData = false
        var receivedError = false
        NetworkingSimulation(Throwable("-")).fetch(
            endpoint = api.fetchTestData(),
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

//region Helper - Networking Simulation
/**
 * Allows for networking simulation by exposing `onResponse` callback.
 * Use to test networking (sync/async) for success and failure results.
 */
class NetworkingSimulation(
    private val onResponse: (Call<Any>, callback: Callback<Any>) -> Unit,
) : RetrofitFetching, RetrofitFetching.Executor {
    override val executor get() = this

    override fun <R> fetchAndCallback(endpoint: Call<R>, callback: Callback<R>) {
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