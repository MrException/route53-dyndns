FROM adoptopenjdk:11-jdk-hotspot-focal as builder

ADD . /app
WORKDIR /app
RUN ./gradlew shadowJar

FROM adoptopenjdk:11-jre-hotspot-focal

COPY --from=builder /app/app/build/libs/app-all.jar /app.jar

CMD ["java", "-jar", "/app.jar"]