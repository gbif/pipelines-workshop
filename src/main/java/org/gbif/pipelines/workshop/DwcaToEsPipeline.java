package org.gbif.pipelines.workshop;

import org.gbif.pipelines.common.PipelinesVariables.Pipeline.Indexing;
import org.gbif.pipelines.common.beam.DwcaIO;
import org.gbif.pipelines.core.converters.GbifJsonConverter;
import org.gbif.pipelines.ingest.options.DwcaPipelineOptions;
import org.gbif.pipelines.ingest.options.PipelinesOptionsFactory;
import org.gbif.pipelines.ingest.utils.EsIndexUtils;
import org.gbif.pipelines.ingest.utils.FsUtils;
import org.gbif.pipelines.io.avro.BasicRecord;
import org.gbif.pipelines.io.avro.ExtendedRecord;
import org.gbif.pipelines.io.avro.LocationRecord;
import org.gbif.pipelines.io.avro.MultimediaRecord;
import org.gbif.pipelines.io.avro.TaxonRecord;
import org.gbif.pipelines.io.avro.TemporalRecord;
import org.gbif.pipelines.parsers.config.KvConfig;
import org.gbif.pipelines.parsers.config.KvConfigFactory;
import org.gbif.pipelines.transforms.core.BasicTransform;
import org.gbif.pipelines.transforms.core.LocationTransform;
import org.gbif.pipelines.transforms.core.TaxonomyTransform;
import org.gbif.pipelines.transforms.core.TemporalTransform;
import org.gbif.pipelines.transforms.core.VerbatimTransform;
import org.gbif.pipelines.transforms.extension.MultimediaTransform;
import org.gbif.pipelines.transforms.UniqueIdTransform;

import java.nio.file.Paths;

import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.PipelineResult;
import org.apache.beam.sdk.io.elasticsearch.ElasticsearchIO;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.transforms.join.CoGbkResult;
import org.apache.beam.sdk.transforms.join.CoGroupByKey;
import org.apache.beam.sdk.transforms.join.KeyedPCollectionTuple;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.TupleTag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DwcaToEsPipeline {

  private static final Logger LOG = LoggerFactory.getLogger(DwcaToEsPipeline.class);

  private DwcaToEsPipeline() {}

  public static void main(String[] args) {
    DwcaPipelineOptions options = PipelinesOptionsFactory.create(DwcaPipelineOptions.class, args);
    run(options);
  }

  public static void run(DwcaPipelineOptions options) {


    EsIndexUtils.createIndex(options);

    LOG.info("Adding step 1: Options");
    KvConfig kvConfig = KvConfigFactory.create(options.getGbifApiUrl());

    final TupleTag<ExtendedRecord> erTag = new TupleTag<ExtendedRecord>() {};
    final TupleTag<BasicRecord> brTag = new TupleTag<BasicRecord>() {};
    final TupleTag<TemporalRecord> trTag = new TupleTag<TemporalRecord>() {};
    final TupleTag<LocationRecord> lrTag = new TupleTag<LocationRecord>() {};
    final TupleTag<TaxonRecord> txrTag = new TupleTag<TaxonRecord>() {};
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
        uniqueRecords.apply("Map Verbatim to KV", VerbatimTransform.toKv());

    PCollection<KV<String, BasicRecord>> basicCollection =
        uniqueRecords
            .apply("Interpret basic", ParDo.of(new BasicTransform.Interpreter()))
            .apply("Map Basic to KV", BasicTransform.toKv());

    PCollection<KV<String, TemporalRecord>> temporalCollection =
        uniqueRecords
            .apply("Interpret temporal", ParDo.of(new TemporalTransform.Interpreter()))
            .apply("Map Temporal to KV", TemporalTransform.toKv());


    PCollection<KV<String, LocationRecord>> locationCollection =
        uniqueRecords
            .apply("Interpret location", ParDo.of(new LocationTransform.Interpreter(kvConfig)))
            .apply("Map Location to KV", LocationTransform.toKv());


    PCollection<KV<String, TaxonRecord>> taxonCollection =
        uniqueRecords
            .apply("Interpret taxonomy", ParDo.of(new TaxonomyTransform.Interpreter(kvConfig)))
            .apply("Map Taxon to KV", TaxonomyTransform.toKv());

    PCollection<KV<String, MultimediaRecord>> multimediaCollection =
        uniqueRecords
            .apply("Interpret multimedia", ParDo.of(new MultimediaTransform.Interpreter()))
            .apply("Map Multimedia to KV", MultimediaTransform.toKv());

    LOG.info("Adding step 3: Converting to a json object");
    DoFn<KV<String, CoGbkResult>, String> doFn =
        new DoFn<KV<String, CoGbkResult>, String>() {


          @ProcessElement
          public void processElement(ProcessContext c) {
            CoGbkResult v = c.element().getValue();
            String k = c.element().getKey();

            ExtendedRecord er = v.getOnly(erTag, ExtendedRecord.newBuilder().setId(k).build());
            BasicRecord br = v.getOnly(brTag, BasicRecord.newBuilder().setId(k).build());
            TemporalRecord tr = v.getOnly(trTag, TemporalRecord.newBuilder().setId(k).build());
            LocationRecord lr = v.getOnly(lrTag, LocationRecord.newBuilder().setId(k).build());
            TaxonRecord txr = v.getOnly(txrTag, TaxonRecord.newBuilder().setId(k).build());
            MultimediaRecord mr = v.getOnly(mrTag, MultimediaRecord.newBuilder().setId(k).build());

            String json = GbifJsonConverter.create(br, tr, lr, txr, mr, er).buildJson().toString();

            c.output(json);

          }
        };

    LOG.info("Adding step 4: Converting to a json object");
    PCollection<String> jsonCollection =
        KeyedPCollectionTuple.of(brTag, basicCollection)
            .and(trTag, temporalCollection)
            .and(lrTag, locationCollection)
            .and(txrTag, taxonCollection)
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
