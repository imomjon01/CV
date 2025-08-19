FROM openjdk:17-jdk-slim
COPY target/app.jar app.jar
EXPOSE 80
ENTRYPOINT ["java", "-jar", "app.jar"]