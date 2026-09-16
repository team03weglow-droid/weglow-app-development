package com.example.weglow.app

import android.content.Context
import com.example.weglow.core.image.FaceImageValidator
import com.example.weglow.core.image.FaceValidator
import com.example.weglow.data.location.AndroidLocationProvider
import com.example.weglow.data.remote.chat.ChatApiProvider
import com.example.weglow.data.remote.supabase.SupabaseClientProvider
import com.example.weglow.data.repository.LocalAcneScanRepository
import com.example.weglow.data.repository.LocalHairstyleRepository
import com.example.weglow.data.repository.RetrofitChatRepository
import com.example.weglow.data.repository.SupabaseAcneScanRepository
import com.example.weglow.data.repository.SupabaseAuthRepository
import com.example.weglow.data.repository.SupabaseCatalogRepository
import com.example.weglow.data.repository.SupabaseHairstyleRepository
import com.example.weglow.data.repository.SupabaseProfileImageRepository
import com.example.weglow.data.repository.SupabaseProfileRepository
import com.example.weglow.data.repository.WeatherApiEnvironmentRepository
import com.example.weglow.domain.repository.AcneScanRepository
import com.example.weglow.domain.repository.AuthRepository
import com.example.weglow.domain.repository.CatalogRepository
import com.example.weglow.domain.repository.ChatRepository
import com.example.weglow.domain.repository.HairstyleRepository
import com.example.weglow.domain.repository.ProfileImageRepository
import com.example.weglow.domain.repository.ProfileRepository
import com.example.weglow.domain.repository.EnvironmentRepository

/**
 * Application-level dependency container.
 *
 * Dependencies are created here and passed into presentation code.
 * Repositories do not reach into global infrastructure singletons themselves,
 * keeping domain and presentation layers replaceable and testable.
 */
class AppContainer {

    private val supabaseClient by lazy {
        SupabaseClientProvider.client
    }

    /**
     * [LocalAcneScanRepository] owns an ONNX Runtime [ai.onnxruntime.OrtEnvironment] and
     * [ai.onnxruntime.OrtSession] - expensive native resources that are already built once
     * and reused internally by that class. This function must therefore behave like the
     * `by lazy` dependencies below it (one instance for this container's lifetime) rather
     * than a factory: resolving it repeatedly must never construct a second, independent
     * inference engine. The instance is intentionally never closed - see
     * [hairstyleRepository]'s doc comment for why.
     */
    private var acneScanRepositoryInstance: AcneScanRepository? = null

    @Synchronized
    fun acneScanRepository(context: Context): AcneScanRepository =
        acneScanRepositoryInstance ?: SupabaseAcneScanRepository(
            onDevice = LocalAcneScanRepository(context.applicationContext),
            authRepository = authRepository,
            profileRepository = profileRepository,
        ).also {
            acneScanRepositoryInstance = it
        }

    fun faceValidator(context: Context): FaceValidator = FaceImageValidator(context)

    val authRepository: AuthRepository by lazy {
        SupabaseAuthRepository(supabaseClient)
    }

    val profileRepository: ProfileRepository by lazy {
        SupabaseProfileRepository(supabaseClient)
    }

    val profileImageRepository: ProfileImageRepository by lazy {
        SupabaseProfileImageRepository(supabaseClient)
    }

    val catalogRepository: CatalogRepository by lazy {
        SupabaseCatalogRepository(supabaseClient)
    }

    val chatRepository: ChatRepository by lazy {
        RetrofitChatRepository({ ChatApiProvider.api }, supabaseClient)
    }

    /**
     * The injected [LocalHairstyleRepository] classifier owns a LiteRT/TFLite [org.tensorflow.lite.Interpreter]
     * - an expensive native resource, already built once (`by lazy`) and reused internally
     * by that class. As with [acneScanRepository], this function must return the SAME
     * repository (and therefore the same classifier/Interpreter) on every call, never a
     * fresh one, so no code path can silently double the number of loaded native models.
     *
     * Neither the Interpreter nor the ONNX session above it is ever explicitly closed.
     * Both are intentionally process-scoped: they are cheap to keep alive for the life of
     * the app process and expensive to reload, Android provides no reliable "app is
     * shutting down" callback to hook a close() into (`Application.onTerminate()` is
     * documented as never called on a real device), and the OS reclaims all native memory
     * when the process is killed. Introducing a fake shutdown hook just to call close()
     * would add complexity without a real correctness or resource-pressure benefit.
     */
    private var hairstyleRepositoryInstance: HairstyleRepository? = null

    @Synchronized
    fun hairstyleRepository(context: Context): HairstyleRepository =
        hairstyleRepositoryInstance ?: SupabaseHairstyleRepository(
            classifier = LocalHairstyleRepository(context.applicationContext),
            client = supabaseClient,
            authRepository = authRepository,
            profileRepository = profileRepository,
        ).also {
            hairstyleRepositoryInstance = it
        }

    fun environmentRepository(): EnvironmentRepository = WeatherApiEnvironmentRepository()

    fun locationProvider(context: Context) = AndroidLocationProvider(context)
}