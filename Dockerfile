FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY instagram-reel-ai/backend/pom.xml .
RUN mvn dependency:go-offline -B
COPY instagram-reel-ai/backend/src ./src
RUN mvn package -DskipTests -B

FROM eclipse-temurin:17-jre
RUN apt-get update && apt-get install -y ffmpeg && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
RUN mkdir -p /tmp/instagram-reel-ai
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
