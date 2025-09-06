FROM gradle:8.4-jdk17 AS build
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
RUN gradle build --no-daemon

FROM openjdk:17-jdk-slim
RUN mkdir /app
COPY --from=build /home/gradle/src/build/libs/*.jar /app/keycloak-entitlements-service.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/keycloak-entitlements-service.jar"]