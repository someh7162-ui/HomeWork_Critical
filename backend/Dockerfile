FROM openjdk:11-jre-slim

WORKDIR /app

COPY target/*.jar app.jar

ENV TZ=Asia/Shanghai
ENV DB_HOST=mysql
ENV DB_USERNAME=root
ENV DB_PASSWORD=

EXPOSE 8099

ENTRYPOINT ["java", "-jar", "app.jar", "--spring.datasource.url=jdbc:mysql://${DB_HOST}:3306/paper_critical?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai", "--spring.datasource.username=${DB_USERNAME}", "--spring.datasource.password=${DB_PASSWORD}"]
