package com.yourapp.wlm.data.remote.passport

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

interface PassportAuthService {
    @GET
    suspend fun getNexusRedirect(
        @Url url: String = "https://pp.login.ugnet.gay/rdr/pprdr.asp"
    ): Response<ResponseBody>

    @POST
    suspend fun loginWithPassport(
        @Url url: String,
        @Header("Authorization") authorization: String
    ): Response<ResponseBody>
}
