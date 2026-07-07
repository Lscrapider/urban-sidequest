FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /workspace

COPY backend/pom.xml backend/pom.xml
RUN mvn -B -f backend/pom.xml -DskipTests dependency:go-offline

COPY backend/src backend/src
RUN mvn -B -f backend/pom.xml package

FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=build /workspace/backend/target/urban-sidequest-backend-*.jar /app/app.jar

EXPOSE 8082

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
