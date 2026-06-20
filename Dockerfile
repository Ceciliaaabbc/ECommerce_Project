FROM maven:3.9.9-eclipse-temurin-17

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY . .

RUN mvn clean package -DskipTests

EXPOSE 8080

CMD sh -c "java -jar target/ecommerce-0.0.1-SNAPSHOT.jar"
