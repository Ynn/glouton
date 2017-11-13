FROM openjdk:8-jdk
# copy application WAR (with libraries inside)
COPY entrypoint.sh /
RUN chmod +x /entrypoint.sh
RUN mkdir /app && mkdir /app/src && mkdir /app/gradle
ADD src/ /app/src/
ADD gradlew /app/
ADD gradle/ /app/gradle/
ADD build.gradle /app
RUN mkdir /app/config && mkdir /app/data
RUN find /app
RUN (cd /app && ./gradlew shadow)

# specify default command
ENTRYPOINT ["/entrypoint.sh"]