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
mvn clean package install -U -DskipTests
```
4. Run a Pipeline

```
java -jar pipelines-workshop-1.0-SNAPSHOT-shaded.jar --targetPath=/Users/xrc439/dev/gbif/pipelines-workshop/out --inputPath=
/pipelines-workshop/src/main/resources/dwca-usac_mammals-v8.1.zip  --tempLocation=temp --esHosts=http://localhost:9200  --esIndexName=occurrence 
```
Note: delete the Elasticsearch each time you run this example ```curl -X DELETE "http://localhost:9200/occurrence"```



