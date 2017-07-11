FROM frolvlad/alpine-oraclejdk8:slim
VOLUME /tmp
ADD hazelcast-code-challenge-1.0-SNAPSHOT.jar hc-app.jar
RUN sh -c 'touch /hc-app.jar'
ENTRYPOINT [ "sh", "-c", "java -Dapp.cluster.size=$HCC_CLUSTER_SIZE -Dapp.cluster.waitToBecomeActive=$HCC_WAIT -jar /hc-app.jar" ]
