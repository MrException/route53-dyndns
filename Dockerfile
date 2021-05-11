FROM adoptopenjdk:11-jre-hotspot-focal

COPY ./app/build/libs/app-all.jar /app.jar

CMD ["java", "-jar", "/app.jar"]