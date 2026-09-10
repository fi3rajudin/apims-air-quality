FROM maven:3.9.16-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -B clean package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/target/apims-air-quality-*.jar app.jar
ENV PORT=8080
EXPOSE 8080
CMD ["sh", "-c", "java -XX:MaxRAMPercentage=70 -jar app.jar --server.port=${PORT}"]
