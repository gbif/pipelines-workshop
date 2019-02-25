package org.gbif.pipelines.workshop;

import org.gbif.pipelines.common.PipelinesVariables.Pipeline.Indexing;
import org.gbif.pipelines.common.beam.DwcaIO;
import org.gbif.pipelines.core.converters.GbifJsonConverter;
import org.gbif.pipelines.ingest.options.DwcaPipelineOptions;
import org.gbif.pipelines.ingest.options.PipelinesOptionsFactory;
import org.gbif.pipelines.ingest.utils.EsIndexUtils;
import org.gbif.pipelines.ingest.utils.FsUtils;
import org.gbif.pipelines.ingest.utils.MetricsHandler;
import org.gbif.pipelines.io.avro.BasicRecord;
import org.gbif.pipelines.io.avro.ExtendedRecord;
import org.gbif.pipelines.io.avro.LocationRecord;
import org.gbif.pipelines.io.avro.MetadataRecord;
import org.gbif.pipelines.io.avro.MultimediaRecord;
import org.gbif.pipelines.io.avro.TaxonRecord;
import org.gbif.pipelines.io.avro.TemporalRecord;
import org.gbif.pipelines.parsers.config.KvConfig;
import org.gbif.pipelines.parsers.config.KvConfigFactory;
import org.gbif.pipelines.parsers.config.WsConfig;
import org.gbif.pipelines.parsers.config.WsConfigFactory;
import org.gbif.pipelines.transforms.CoreTransforms.BasicFn;
import org.gbif.pipelines.transforms.CoreTransforms.LocationFn;
import org.gbif.pipelines.transforms.CoreTransforms.MetadataFn;
import org.gbif.pipelines.transforms.CoreTransforms.TaxonomyFn;
import org.gbif.pipelines.transforms.CoreTransforms.TemporalFn;
import org.gbif.pipelines.transforms.ExtensionTransforms.MultimediaFn;
import org.gbif.pipelines.transforms.MapTransforms;
import org.gbif.pipelines.transforms.UniqueIdTransform;

import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.PipelineResult;
import org.apache.beam.sdk.io.elasticsearch.ElasticsearchIO;
import org.apache.beam.sdk.transforms.Create;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.transforms.View;
import org.apache.beam.sdk.transforms.join.CoGbkResult;
import org.apache.beam.sdk.transforms.join.CoGroupByKey;
import org.apache.beam.sdk.transforms.join.KeyedPCollectionTuple;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.PCollectionView;
import org.apache.beam.sdk.values.TupleTag;

import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DwcaToEs {

  private static final Logger LOG = LoggerFactory.getLogger(DwcaToEs.class);

  private DwcaToEs() {}

  public static void main(String[] args) {
    DwcaPipelineOptions options = PipelinesOptionsFactory.create(DwcaPipelineOptions.class, args);
    run(options);
  }

  public static void run(DwcaPipelineOptions options) {


    EsIndexUtils.createIndex(options);

    LOG.info("Adding step 1: Options");
    KvConfig kvConfig = KvConfigFactory.create(options.getGbifApiUrl(), options.getZookeeperUrl());

    final TupleTag<ExtendedRecord> erTag = new TupleTag<ExtendedRecord>() {};
    final TupleTag<BasicRecord> brTag = new TupleTag<BasicRecord>() {};
    final TupleTag<TemporalRecord> trTag = new TupleTag<TemporalRecord>() {};
    //final TupleTag<LocationRecord> lrTag = new TupleTag<LocationRecord>() {};
    //final TupleTag<TaxonRecord> txrTag = new TupleTag<TaxonRecord>() {};
    final TupleTag<MultimediaRecord> mrTag = new TupleTag<MultimediaRecord>() {};

    String tmpDir = FsUtils.getTempDir(options);

    String inputPath = options.getInputPath();
    boolean isDirectory = Paths.get(inputPath).toFile().isDirectory();

    DwcaIO.Read reader =
        isDirectory
            ? DwcaIO.Read.fromLocation(inputPath)
            : DwcaIO.Read.fromCompressed(inputPath, tmpDir);

    Pipeline p = Pipeline.create(options);

    LOG.info("Reading avro files");
    PCollection<ExtendedRecord> uniqueRecords =
        p.apply("Read ExtendedRecords", reader)
            .apply("Filter duplicates", UniqueIdTransform.create());

    PCollection<KV<String, ExtendedRecord>> verbatimCollection =
        uniqueRecords.apply("Map Verbatim to KV", MapTransforms.extendedToKv());

    PCollection<KV<String, BasicRecord>> basicCollection =
        uniqueRecords
            .apply("Interpret basic", ParDo.of(new BasicFn()))
            .apply("Map Basic to KV", MapTransforms.basicToKv());

    PCollection<KV<String, TemporalRecord>> temporalCollection =
        uniqueRecords
            .apply("Interpret temporal", ParDo.of(new TemporalFn()))
            .apply("Map Temporal to KV", MapTransforms.temporalToKv());



    PCollection<KV<String, MultimediaRecord>> multimediaCollection =
        uniqueRecords
            .apply("Interpret multimedia", ParDo.of(new MultimediaFn()))
            .apply("Map Multimedia to KV", MapTransforms.multimediaToKv());

    LOG.info("Adding step 3: Converting to a json object");
    DoFn<KV<String, CoGbkResult>, String> doFn =
        new DoFn<KV<String, CoGbkResult>, String>() {


          @DoFn.ProcessElement
          public void processElement(ProcessContext c) {
            CoGbkResult v = c.element().getValue();
            String k = c.element().getKey();

            ExtendedRecord er = v.getOnly(erTag, ExtendedRecord.newBuilder().setId(k).build());
            BasicRecord br = v.getOnly(brTag, BasicRecord.newBuilder().setId(k).build());
            TemporalRecord tr = v.getOnly(trTag, TemporalRecord.newBuilder().setId(k).build());
            //LocationRecord lr = v.getOnly(lrTag, LocationRecord.newBuilder().setId(k).build());
            //TaxonRecord txr = v.getOnly(txrTag, TaxonRecord.newBuilder().setId(k).build());
            MultimediaRecord mr = v.getOnly(mrTag, MultimediaRecord.newBuilder().setId(k).build());

            String json = GbifJsonConverter.create(br, tr, mr, er).buildJson().toString();

            c.output(json);

          }
        };

    LOG.info("Adding step 4: Converting to a json object");
    PCollection<String> jsonCollection =
        KeyedPCollectionTuple.of(brTag, basicCollection)
            .and(trTag, temporalCollection)
            //.and(lrTag, locationCollection)
            //.and(txrTag, taxonCollection)
            .and(mrTag, multimediaCollection)
            .and(erTag, verbatimCollection)
            .apply("Grouping objects", CoGroupByKey.create())
            .apply("Merging to json", ParDo.of(doFn));

    LOG.info("Adding step 5: Elasticsearch indexing");
    ElasticsearchIO.ConnectionConfiguration esConfig =
        ElasticsearchIO.ConnectionConfiguration.create(
            options.getEsHosts(), options.getEsIndexName(), Indexing.INDEX_TYPE);

    jsonCollection.apply(
        ElasticsearchIO.write()
            .withConnectionConfiguration(esConfig)
            .withMaxBatchSizeBytes(options.getEsMaxBatchSizeBytes())
            .withMaxBatchSize(options.getEsMaxBatchSize())
            .withIdFn(input -> input.get("id").asText()));

    LOG.info("Running the pipeline");
    PipelineResult result = p.run();
    result.waitUntilFinish();

    LOG.info("Pipeline has been finished");

    FsUtils.removeTmpDirectory(options);
  }
}
