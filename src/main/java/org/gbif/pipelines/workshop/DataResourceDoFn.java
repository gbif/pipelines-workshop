package org.gbif.pipelines.workshop;

import org.gbif.pipelines.service.ClientFactory;
import org.gbif.pipelines.service.DataResourceResponse;
import org.gbif.pipelines.service.DataResourceService;
import org.gbif.pipelines.workshop.avro.DataResourceRecord;

import java.io.IOException;

import org.apache.beam.sdk.transforms.DoFn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DataResourceDoFn extends DoFn<String, DataResourceRecord> {

  private static final Logger LOG = LoggerFactory.getLogger(DataResourceDoFn.class);

  private DataResourceService dataResourceService;

  @Setup
  public void setup() {
    dataResourceService = ClientFactory
        .createRetrofitClient("https://collections.ala.org.au/ws/",DataResourceService.class);
  }

  @DoFn.Teardown
  public void stop() {
    //CLOSE CLIENTS
  }


  @ProcessElement
  public void processElement(ProcessContext context) {
    try {
      String dataResourceId = context.element();
      DataResourceResponse response = dataResourceService.get(dataResourceId).execute().body();
      DataResourceRecord dataResourceRecord = DataResourceRecord.newBuilder()
          .setId(dataResourceId)
          .setDataResourceId(dataResourceId)
          .setName(response.getName())
          .build();
      context.output(dataResourceRecord);
    } catch (IOException ex) {
      LOG.error("Error contacting WS", ex);
    }
  }
}
