FROM openjdk:8-slim
# copy application WAR (with libraries inside)
COPY entrypoint.sh /
#RUN apk add --no-cache bash
RUN chmod +x /entrypoint.sh
RUN mkdir /build && mkdir /build/src && mkdir /build/gradle
ADD src/ /build/src/
ADD gradlew /build/
ADD gradle/ /build/gradle/
ADD build.gradle /build
RUN mkdir /app && mkdir /app/config && mkdir /app/data
RUN (cd /build && ./gradlew shadow) && cp /build/build/libs/data-client-all.jar /app/data-client-all.jar && rm -fr /build && rm -fr /root/.gradle && rm -fr /root/.kotlin

# specify default command
ENTRYPOINT ["/entrypoint.sh"]