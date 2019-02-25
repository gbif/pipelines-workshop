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
docker pull docker.elastic.co/elasticsearch/elasticsearch:6.6.1
docker run -p 9200:9200 -p 9300:9300 -e "discovery.type=single-node" docker.elastic.co/elasticsearch/elasticsearch:6.6.1
```

3. Clone and build this repo:

```
git clone https://github.com/gbif/pipelines-workshop
mvn clean package install -U -DskipTests
java -jar pipelines-workshop-1.0-SNAPSHOT-shaded.jar --runner=SparkRunner  --targetPath=/Users/xrc439/dev/gbif/pipelines-workshop/out --inputPath=/Users/xrc439/dev/gbif/pipelines-workshop/src/main/resources/example.zip  --tempLocation=temp --esHosts=localhost:9200  --esIndexName=occurrence
```

Note: delete the Elasticsearch each time you run this example ```curl -X DELETE "localhost:9200/occurrence"```



