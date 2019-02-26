#Pipelines Workshop

## How to run examples

1. Clone a build the Pipelines measurement-extension:

```
  git clone https://github.com/gbif/pipelines
  git checkout measurement-extension
  mvn clean package install -U -DskipTests
```

2. Install Elasticsearch

```
docker pull docker.elastic.co/elasticsearch/elasticsearch:6.5.4
docker run -p 9200:9200 -p 9300:9300 -e "discovery.type=single-node" docker.elastic.co/elasticsearch/elasticsearch:6.5.4
```

3. Clone and build this repo:

```
git clone https://github.com/gbif/pipelines-workshop
mvn clean package install -DskipTests
```
4. Run a Pipeline

Pipeline without rest services:
```
java -cp target/pipelines-workshop-1.0-SNAPSHOT-shaded.jar org.gbif.pipelines.workshop.DwcaToEsServicelessPipeline --inputPath=src/main/resources/measurement.zip --targetPath=target/ --esHosts=http://localhost:9200  --esIndexName=occurrence
```
OR
```
java -jar target/pipelines-workshop-1.0-SNAPSHOT-shaded.jar --inputPath=src/main/resources/measurement.zip --targetPath=target/ --esHosts=http://localhost:9200  --esIndexName=occurrence
```

Pipeline with rest services:
```
java -cp target/pipelines-workshop-1.0-SNAPSHOT-shaded.jar org.gbif.pipelines.workshop.DwcaToEsPipeline --inputPath=src/main/resources/measurement.zip --targetPath=target/ --esHosts=http://localhost:9200  --esIndexName=occurrence
```

Pipeline with rest services and measurement extension:
```
java -cp target/pipelines-workshop-1.0-SNAPSHOT-shaded.jar org.gbif.pipelines.workshop.DwcaToEsMeasurementPipeline --inputPath=src/main/resources/measurement.zip --targetPath=target/ --esHosts=http://localhost:9200  --esIndexName=occurrence
```

Note: delete the Elasticsearch each time you run this example ```curl -X DELETE "http://localhost:9200/occurrence"```



