FROM docker-commons.zas.admin.ch/zas/imagebase/application/java:25-openjdk-headless-ubi-2.10.0
COPY target/copilot-backend.jar /app/
CMD ["java", "-jar" ,"/app/copilot-backend.jar"]
