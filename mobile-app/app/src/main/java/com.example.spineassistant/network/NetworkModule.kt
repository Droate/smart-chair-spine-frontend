package com.example.spineassistant.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import com.google.gson.annotations.SerializedName
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

// 对应后端 UserProfile
data class UserProfileRequest(
    @SerializedName("user_id") val userId: String,
    @SerializedName("height_cm") val heightCm: Int,
    @SerializedName("weight_kg") val weightKg: Float,
    @SerializedName("current_mode") val currentMode: String,
    @SerializedName("upper_body_ratio") val upperBodyRatio: Float? = null,
    @SerializedName("thigh_length_cm") val thighLengthCm: Float? = null
)

data class ErgoRecommendationResponse(
    @SerializedName("recommended_height_mm") val recommendedHeightMm: Int,
    @SerializedName("recommended_angle_deg") val recommendedAngleDeg: Int,
    @SerializedName("reason") val reason: String
)

data class SingleModeRecResponse(
    @SerializedName("mode") val mode: String,
    @SerializedName("recommended_height_mm") val heightMm: Int,
    @SerializedName("recommended_angle_deg") val angleDeg: Int,
    @SerializedName("reason") val reason: String
)

data class MultiSceneRecResponse(
    @SerializedName("recommendations") val recommendations: List<SingleModeRecResponse>
)

data class UserFeedbackRequest(
    @SerializedName("user_id") val userId: String,
    @SerializedName("height_cm") val heightCm: Int,
    @SerializedName("weight_kg") val weightKg: Float,
    @SerializedName("final_height_mm") val finalHeightMm: Int,
    @SerializedName("final_angle_deg") val finalAngleDeg: Int,
    @SerializedName("problem_area") val problemArea: String,
    @SerializedName("current_mode") val currentMode: String
)

data class FeedbackResponse(
    @SerializedName("status") val status: String,
    @SerializedName("sample_count") val sampleCount: Int
)

// 🔥🔥🔥 新增：AI 报告分析请求体 (包含 7 天数据) 🔥🔥🔥
data class HealthReportRequest(
    @SerializedName("total_hours") val totalHours: Float,
    @SerializedName("sedentary_count") val sedentaryCount: Int,
    @SerializedName("posture_score") val postureScore: Int,
    @SerializedName("mode_distribution") val modeDistribution: Map<String, Float>,
    @SerializedName("weekly_trend") val weeklyTrend: List<Float> // 👈 包含 7 天历史
)

// 🔥🔥🔥 新增：AI 报告分析响应体 🔥🔥🔥
data class HealthAnalysisResponse(
    @SerializedName("advice") val advice: String
)

interface SpineApiService {
    @FormUrlEncoded
    @POST("/token")
    suspend fun login(
        @Field("username") username: String,
        @Field("password") password: String
    ): Response<TokenResponse>

    @POST("/register")
    suspend fun register(@Body request: RegisterRequest): Response<TokenResponse>

    @POST("/api/v1/recommend")
    suspend fun getRecommendation(@Body profile: UserProfileRequest): Response<MultiSceneRecResponse>

    @POST("/api/v1/feedback")
    suspend fun uploadFeedback(@Body feedback: UserFeedbackRequest): Response<FeedbackResponse>

    // 🔥🔥🔥 新增接口：获取 AI 报告分析 🔥🔥🔥
    @POST("/api/v1/report/analysis")
    suspend fun analyzeReport(@Body request: HealthReportRequest): Response<HealthAnalysisResponse>
}

class AuthInterceptor(private val tokenManager: TokenManager) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val requestBuilder = chain.request().newBuilder()
        val token = tokenManager.getToken()
        if (token != null) {
            requestBuilder.addHeader("Authorization", "Bearer $token")
        }
        return chain.proceed(requestBuilder.build())
    }
}

object NetworkModule {
    private const val TAG = "NetworkModule"
    private const val SERVICE_TYPE = "_spine-api._tcp."
    private const val SERVICE_NAME_PREFIX = "Spine Assistant API"

    @Volatile
    private var currentBaseUrl = "http://10.0.2.2:8000/"

    private var apiServiceInstance: SpineApiService? = null
    private lateinit var tokenManager: TokenManager
    private var nsdManager: NsdManager? = null

    private val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(regType: String) {
            Log.d(TAG, "NSD 扫描已启动: $regType")
        }

        override fun onServiceFound(service: NsdServiceInfo) {
            Log.d(TAG, "发现服务: ${service.serviceName}")
            if (service.serviceType.contains(SERVICE_TYPE) && service.serviceName.startsWith(SERVICE_NAME_PREFIX)) {
                nsdManager?.resolveService(service, resolveListener)
            }
        }

        override fun onServiceLost(service: NsdServiceInfo) {}
        override fun onDiscoveryStopped(serviceType: String) {}
        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            nsdManager?.stopServiceDiscovery(this)
        }
        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            nsdManager?.stopServiceDiscovery(this)
        }
    }

    private val resolveListener = object : NsdManager.ResolveListener {
        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}

        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
            val host = serviceInfo.host.hostAddress
            val port = serviceInfo.port
            val newUrl = "http://$host:$port/"
            Log.i(TAG, "✅ 成功解析服务端地址: $newUrl")

            if (currentBaseUrl != newUrl) {
                currentBaseUrl = newUrl
                synchronized(NetworkModule) {
                    apiServiceInstance = null
                }
            }
        }
    }

    fun init(context: Context) {
        if (!::tokenManager.isInitialized) {
            tokenManager = TokenManager(context)
            startServiceDiscovery(context)
        }
    }

    private fun startServiceDiscovery(context: Context) {
        try {
            nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
            nsdManager?.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.e(TAG, "启动服务发现异常: ${e.message}")
        }
    }

    fun getApiService(): SpineApiService {
        if (apiServiceInstance == null) {
            synchronized(this) {
                if (apiServiceInstance == null) {
                    if (!::tokenManager.isInitialized) {
                        throw IllegalStateException("NetworkModule must be initialized with context first!")
                    }
                    val logging = HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BODY
                    }
                    val client = OkHttpClient.Builder()
                        .addInterceptor(logging)
                        .addInterceptor(AuthInterceptor(tokenManager))
                        .connectTimeout(30, TimeUnit.SECONDS) // 🔥 DeepSeek 可能响应较慢，增加超时
                        .readTimeout(30, TimeUnit.SECONDS)
                        .build()

                    val retrofit = Retrofit.Builder()
                        .baseUrl(currentBaseUrl)
                        .client(client)
                        .addConverterFactory(GsonConverterFactory.create())
                        .build()

                    apiServiceInstance = retrofit.create(SpineApiService::class.java)
                }
            }
        }
        return apiServiceInstance!!
    }

    fun getTokenManager(): TokenManager {
        return tokenManager
    }
}
