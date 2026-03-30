# Production Dockerfile - portfolio-platform
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/portfolio-platform-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", \
  "-XX:MaxRAMPercentage=60", \
  "-XX:+UseSerialGC", \
  "-Dspring.profiles.active=prod", \
  "-Dlogging.level.dev.chinh.portfolio=DEBUG", \
  "-jar", "app.jar"]