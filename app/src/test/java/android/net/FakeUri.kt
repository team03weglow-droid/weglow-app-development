package android.net

import android.os.Parcel

/**
 * A minimal working [Uri] for plain JVM unit tests.
 *
 * `android.net.Uri`'s no-arg constructor is package-private, and every method on the real
 * Android platform jar throws when invoked off-device (this project uses neither Robolectric
 * nor Mockito). Declaring this class in the same `android.net` package is the only way to reach
 * that constructor and obtain a [Uri] whose `toString()`/`equals()` actually work in a test,
 * instead of every call throwing "not mocked".
 */
class FakeUri(private val value: String) : Uri() {
    override fun toString(): String = value
    override fun equals(other: Any?): Boolean = other is FakeUri && other.value == value
    override fun hashCode(): Int = value.hashCode()

    override fun buildUpon(): Builder = throw UnsupportedOperationException("not needed by tests")
    override fun getAuthority(): String? = null
    override fun getEncodedAuthority(): String? = null
    override fun getEncodedFragment(): String? = null
    override fun getEncodedPath(): String? = null
    override fun getEncodedQuery(): String? = null
    override fun getEncodedSchemeSpecificPart(): String? = null
    override fun getEncodedUserInfo(): String? = null
    override fun getFragment(): String? = null
    override fun getHost(): String? = null
    override fun getLastPathSegment(): String? = null
    override fun getPath(): String? = null
    override fun getPathSegments(): List<String> = emptyList()
    override fun getPort(): Int = -1
    override fun getQuery(): String? = null
    override fun getScheme(): String? = null
    override fun getSchemeSpecificPart(): String? = null
    override fun getUserInfo(): String? = null
    override fun isHierarchical(): Boolean = false
    override fun isRelative(): Boolean = true
    override fun describeContents(): Int = 0
    override fun writeToParcel(dest: Parcel, flags: Int) = Unit
}

fun fakeUri(value: String): Uri = FakeUri(value)
