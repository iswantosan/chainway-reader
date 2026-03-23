package com.stok.middleware.network

import com.stok.middleware.data.model.LogExportPayload
import com.stok.middleware.data.model.RfidPayload
import com.stok.middleware.data.model.RfidResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Url

/**
 * Retrofit service untuk POST scan dan export log.
 */
interface RfidApiService {

    /** Body mentah — parse dengan [ApiResponseParser.parseRfidScanResponse] agar HTML dari server tidak mem-brok Gson. */
    @POST
    suspend fun sendRfidScan(@Url url: String, @Body payload: RfidPayload): Response<ResponseBody>

    /**
     * Kirim isi log ke API untuk disimpan ke file teks.
     * Response mentah agar kita bisa parse JSON dengan aman; server yang mengembalikan HTML tidak mem-brok Gson di Retrofit.
     */
    @POST
    suspend fun sendLog(@Url url: String, @Body payload: LogExportPayload): Response<ResponseBody>

    @POST
    suspend fun sendOpnameCompare(@Url url: String, @Body payload: OpnameComparePayload): Response<ResponseBody>
}
