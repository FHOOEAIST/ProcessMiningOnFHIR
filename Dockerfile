# Create the image to build the project
FROM maven:3.6.3-openjdk-11-slim as java-build-stage

WORKDIR /app/build
COPY ./auditlog-fhir-server/ .

RUN mvn clean package -am -DskipTests -Dmaven.javadoc.skip=true

# create the image to run the tomcat
FROM jetty:9.4.30-jdk11-slim

COPY --from=java-build-stage /app/build/target/hapi-fhir-jpaserver.war /var/lib/jetty/webapps/ROOT.war
