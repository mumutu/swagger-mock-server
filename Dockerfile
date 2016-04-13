FROM java:8-jre
ARG version
COPY ./target/mock-server-$version-exec.jar /mock/main.jar
WORKDIR /mock

ENTRYPOINT ["java", "-jar", "main.jar"]
