package org.gbif.pipelines.service;

import org.gbif.rest.client.configuration.ClientConfiguration;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.concurrent.TimeUnit;

import okhttp3.Cache;
import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Converter;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

public class ClientFactory {

  public static <S> S createRetrofitClient(String baseApiUrl,
                                           Class<S> serviceClass) {
    // create service
    return new Retrofit.Builder()
        .client(new OkHttpClient.Builder().build())
        .baseUrl(baseApiUrl)
        .addConverterFactory(JacksonConverterFactory.create())
        .validateEagerly(false)
        .build().create(serviceClass);
  }


  public static void main(String[] args) throws IOException {
    DataResourceService  dataResourceService = ClientFactory
        .createRetrofitClient("https://collections.ala.org.au/ws/", DataResourceService.class);
    Call<DataResourceResponse> call = dataResourceService.get("dr931");
    Response<DataResourceResponse> response = call.execute();
    System.out.println(response.body().getLicenseType());
  }
}
