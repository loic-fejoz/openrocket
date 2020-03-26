FROM openjdk:13-alpine
RUN apk add apache-ant
COPY . /usr/src/myapp
WORKDIR /usr/src/myapp
RUN ant build
WORKDIR /usr/src/myapp/swing
CMD ["/usr/bin/ant", "build"]
