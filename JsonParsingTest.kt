
import okhttp3.MediaType
import okhttp3.ResponseBody
import org.junit.Test
import java.io.IOException

//region Json Parsing Tests

class RetrofitJsonParsingTest {

    @Test fun testEmptyJsonIsParsedToNull() {
        var emptyJsonParseResult: TestData.Nonnull? = null
        val inputJson = "{}"
        try {
            emptyJsonParseResult = retrofit
                .responseBodyConverter<TestData.Nonnull>(TestData.Nonnull::class.java, emptyArray())
                .convert(inputJson.toJsonResponseBody)
        } catch (e: IOException) { }

        assert(emptyJsonParseResult != null)
    }

    @Test fun testNonEmptyJsonIsParsedToDataClass() {
        var expectedData: TestData.Nonnull? = null
        val inputJson = "{\"string\":\"Not Null\"}"
        try {
            expectedData = retrofit
                .responseBodyConverter<TestData.Nonnull>(TestData.Nonnull::class.java, emptyArray())
                .convert(inputJson.toJsonResponseBody)
        } catch (e: IOException) { }

        assert(expectedData != null && expectedData.string == "Not Null")
    }

    // TODO: Test more scenarios

    object TestData {
        data class Nonnull(val string: String)
        data class Nullable(val nullableString: String?)
        data class NullableMixed(val string: String, val nullableString: String?)
        data class AllDefaults(val default: String = "Default Value")
        data class DefaultsMixed(val string: String, val default: String = "Default Value")
    }

}

private val String.toJsonResponseBody: ResponseBody
    get() = ResponseBody.create(MediaType.parse("application/json"), this)

//endregion