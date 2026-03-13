FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app
COPY target/portfolio-platform-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", \
  "-XX:MaxRAMPercentage=60", \
  "-XX:+UseSerialGC", \
  "-Dspring.profiles.active=prod", \
  "-jar", "app.jar"]
