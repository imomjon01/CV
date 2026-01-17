FROM eclipse-temurin:17-jdk-jammy

COPY target/app.jar app.jar

EXPOSE 80

ENTRYPOINT ["java", "-jar", "app.jar"]
