FROM openjdk:8

ADD . /src
WORKDIR /src

RUN ./gradlew executableJar

FROM java:8

#ENV ARTIFACT_SRC=/src/build/libs/pepk.jar

COPY --from=0 /src/build/libs/bundletool-all.jar /app/bundletool-all.jar

ENTRYPOINT ["java", "-jar", "/app/bundletool-all.jar"]
