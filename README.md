# A compact Retrofit extension for quick networking

A simple quick helper to fetch some JSON with little code. Extends retrofit with `fetch` generic API that 
enforces expected data and error types specific to each endpoint. 

The generic implementation makes it easy to extend the API with more endpoints without duplicating networking-code. 


### How to use it? 

Create an instance of your `API` class and access the endpoints in a declarative manner. e.g. 

```kotlin
val api: API = API() // Manage the lifecycle of this instance preferably inside a `ViewModel`
api.fetchItem(
    success = { item -> /* Data received and ready for use */ }
    failure = { error -> 
        /* Error received. The error object is thorough and customizable for the finest handlings */
    }
)
```

You can implement an `API` by inheriting the default implementations of `RetrofitFetching` interface 
where all the magic happens. The implementation can be as simple as the following: 

```kotlin
class API(
    // Use `enqueue` from `Call<T>` for most simple asynchronous requests
    override val executor: RetrofitFetching.Executor = RetrofitFetching.callEnqueue 
) : RetrofitFetching {

    fun fetchItem(success: (Item) -> Unit, failure: (FetchError) -> Unit) {
        // Networking and response processing is all implemented 
        // in `fetch` default implementation
        fetch(endpoint = endpoint.fetchItem(), success = success, failure = failure)
    }

    // Create your API endpoint using the 'service interface' that Retrofit requires
    private val endpoint: Endpoint = buildRetrofit(
        baseURL = baseURL,
        callTimeoutInSeconds = 10
    ).create(Endpoint::class.java)
    
    interface Endpoint {
        @GET("http://endpoint-url")
        fun fetchItem(): Call<Item> // `Item` being a data-class of the expected JSON
    }

}
```

> #### Gradle Dependencies 
>```kotlin
> val retrofit_version = "2.5.0"
> implementation("com.squareup.retrofit2:retrofit:$retrofit_version")
> implementation("com.squareup.retrofit2:converter-gson:$retrofit_version")
>```


## Advance Use cases 

#### Play with the `executor` property

By default, the asynchronous networking operation uses `RetrofitFetching.callEnqueue` which simply means 
it relies on Retrofit's `enqueue` method on a given `Call<T>` object. 
All thats promised (by Retrofit) is that the operation is asynchronous and does not block main thread - adequate 
for most use cases. The `Call<T>` instance offers `cancel()` should the request need be interrupted. 

However, by providing your own implementation for the `executor` property (required by `RetrofitFetching` interface),
you can take control and manage the request. 
e.g. see how this property is used to simulate networking in the unit tests (RetrofitFetchingTests.kt).


#### Customize the error objects and result processing 

This simple implementation contains a sealed interface `FetchError` with few concrete error cases: 
`BadRequest, NotFound and BadStatusCode`. A simple handling of these error cases could be showing the appropriate error message:

```kotlin
failure = { error -> /* of type `FetchError` */
    when(error) {
        Is
    }
    val message: String = when(error) {
        is Network -> when(error.underlyingError) {
            AnyFetchError.BadRequest.Encode -> "Encoding error"
            AnyFetchError.BadRequest.Decode -> "Decoding error"
            AnyFetchError.NotFound.MissingData -> "No data"
            AnyFetchError.NotFound.MissingNetwork -> "No network"
            is AnyFetchError.BadStatusCode -> "Bad status code"
            is AnyFetchError.Unknown -> "Unknown"
        }
    }
}
```

*Note* that the error cases can be handled exhaustively. Additional error case in the codebase will alert/demand 
handling at compile-time for a [robust codebase](http://nob.cs.ucdavis.edu/bishop/secprog/robust.html). 

To expand the error cases and handle specific error cases your backend throws, implement specific cases specific
to the request. e.g. an endpoint for `fetchPrivateDiary` might need `NotAuthorized` error case. 
And lets say we don't want this to be handled in `BadStatusCode` with just an integer error code identifying it. 

First, create the error case:
```kotlin
sealed interface PrivateDataFetchError {
    data class NotAuthorized(val description: String) : PrivateDataFetchError
    
    // Use the existing `AnyFetchError` for all common network error cases. 
    data class Network(val underlyingError: AnyFetchError) : PrivateDataFetchError
}
```

Then, override result processing and make sure `NotAuthorized` error case is thrown 
(depending on your backend, e.g. most commonly error code 401 - Unauthorized)

```kotlin
fun privateDataFetchProcessing() : RetrofitAsyncResultProcessing<Diary, PrivateDataFetchError> {
    return object : RetrofitAsyncResultProcessing<Diary, PrivateDataFetchError> {
        override fun onResponse(call: Call<Diary>, response: Response<Diary>): PrivateDataFetchError? {
            if (response.code() == 401) return NotAuthorized("not athorized")
            return super.onResponse(call, response)
        }
    }
}
```

Now the API for this endpoint should require `PrivateDataFetchError` instead of `FetchError` forcing the receiver 
to handle all error cases, including `NotAuthorized` exhaustively. 

```kotlin
fun fetchPrivateDiary(success: (Diary) -> Unit, failure: (PrivateDataFetchError) -> Unit) {
    fetch(
        endpoint = endpoint.fetchPrivateDiary(), 
        resultProcessing = privateDataFetchProcessing(), 
        success = success,
        failure = failure
    )
}
```
