package com.yishijie.chuanshuo.api

import android.content.Context
import android.content.SharedPreferences
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * 异世界传说 - 网络客户端
 * 服务器地址与《开箱游戏 / 对决》一致：https://yxc.shatangju.wang
 */
object ApiClient {

    const val BASE_URL = "https://yxc.shatangju.wang/"

    private var prefs: SharedPreferences? = null

    var apiKey: String?
        get() = prefs?.getString("yishijie_api_key", null)
        set(value) {
            prefs?.edit()?.putString("yishijie_api_key", value)?.apply()
        }

    fun init(context: Context) {
        prefs = context.getSharedPreferences("yishijie_prefs", Context.MODE_PRIVATE)
    }

    private val authInterceptor = Interceptor { chain ->
        val request = chain.request()
        val key = apiKey
        val newRequest = if (key != null) {
            request.newBuilder().header("Authorization", "Bearer $key").build()
        } else {
            request
        }
        chain.proceed(newRequest)
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val api: YishijieApiService = retrofit.create(YishijieApiService::class.java)

    suspend fun <T> safeApiCall(call: suspend () -> retrofit2.Response<T>): ApiResult<T> {
        return try {
            val response = call()
            if (response.isSuccessful) {
                ApiResult.Success(response.body())
            } else {
                ApiResult.Error(response.errorBody()?.string() ?: "请求失败: ${response.code()}")
            }
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "网络请求异常")
        }
    }
}

sealed class ApiResult<out T> {
    data class Success<T>(val data: T?) : ApiResult<T>()
    data class Error(val message: String) : ApiResult<Nothing>()
}
