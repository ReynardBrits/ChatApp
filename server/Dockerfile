FROM maven:3.10.0-eclipse-temurin-17-noble AS build

WORKDIR /build

COPY pom.xml .
COPY src ./src

RUN mvn --batch-mode --no-transfer-progress clean package

FROM eclipse-temurin:17-jre-noble

WORKDIR /app

COPY --from=build /build/target/chatapp-server-1.0.0.jar ./chatapp-server.jar

ENV PORT=10000
ENV CHATAPP_DATA_DIR=/var/data

RUN mkdir -p /var/data

EXPOSE 10000

CMD ["java", "-Djava.awt.headless=true", "-XX:MaxRAMPercentage=65.0", "-jar", "chatapp-server.jar"]