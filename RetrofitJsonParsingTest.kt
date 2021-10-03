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