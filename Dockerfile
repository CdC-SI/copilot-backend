FROM docker-commons.zas.admin.ch/zas/imagebase/application/java:21-openjdk-headless-ubi-2.7.0
COPY target/copilot-backend.jar /app/
CMD ["java", "-jar" ,"/app/zia-translation.jar"]
