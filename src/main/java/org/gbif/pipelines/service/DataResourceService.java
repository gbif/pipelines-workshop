package org.gbif.pipelines.service;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;

public interface DataResourceService {

  @GET("dataResource/{id}")
  Call<DataResourceResponse> get(@Path("id") String id);
}
