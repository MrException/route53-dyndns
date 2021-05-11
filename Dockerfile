FROM openjdk:11 as builder

ADD . /app
WORKDIR /app
RUN ./gradlew shadowJar

FROM openjdk:11-jre-slim

COPY --from=builder /app/app/build/libs/app-all.jar /app.jar

CMD ["java", "-jar", "/app.jar"]