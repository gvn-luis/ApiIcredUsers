# ─── Stage 1: Build ───────────────────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-17 AS build

WORKDIR /app

# Copia só o pom.xml primeiro para aproveitar o cache do Docker nas dependências
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Copia o restante do código e compila (sem rodar testes)
COPY src ./src
RUN mvn package -DskipTests -q

# ─── Stage 2: Runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Copia apenas o JAR gerado
COPY --from=build /app/target/demo-0.0.1-SNAPSHOT.jar app.jar

# Expõe a porta padrão do Spring Boot
EXPOSE 8080

# Tuning de memória para caber no free tier do Koyeb (512 MB)
ENV JAVA_OPTS="-Xms128m -Xmx350m -XX:+UseContainerSupport -XX:MaxRAMPercentage=70.0 -XX:+UseG1GC -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
