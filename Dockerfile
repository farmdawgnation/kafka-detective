FROM openjdk:8-jre-slim

MAINTAINER Matt Farmer <matt@frmr.me>

# Config files
ENV APP_USER="detective"
ENV APP_GROUP="detective"
ENV APP_DIR="/opt/kafka-detective"
ENV CONFIG_DIR="${APP_DIR}/conf"
ENV LIB_DIR="${LIB_DIR}/lib"
ENV APP_CONFIG="${CONFIG_DIR}/application.conf"

# Java options
ENV JAVA_OPTS="-XX:+HeapDumpOnOutOfMemoryError -Xmx4g"

RUN mkdir -p $APP_DIR
RUN mkdir -p $CONFIG_DIR
RUN mkdir -p $LIB_DIR

COPY ./daemon/target/scala-2.12/kafka-detective.jar /opt/kafka-detective/

# Create the user
RUN useradd -ms /bin/bash $APP_USER

# Update file permissions and ownership
RUN chown $APP_USER:$APP_GROUP -R $LIB_DIR
RUN chown $APP_USER:$APP_GROUP -R $CONFIG_DIR
RUN chown $APP_USER:$APP_GROUP -R $APP_DIR

COPY ./docker-command.sh /opt/kafka-detective/
RUN chmod +x /opt/kafka-detective/docker-command.sh

# Every command after this block, as well as interactive sessions,
# will be executed as $APP_USER
USER $APP_USER
WORKDIR /opt/kafka-detective

CMD ["/opt/kafka-detective/docker-command.sh"]
