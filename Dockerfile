FROM ubuntu:bionic

# Base Packages
RUN apt-get update --fix-missing
RUN apt-get install --yes build-essential git libfontconfig openjdk-8-jdk curl

# SBT requires a bit more work.
# https://www.scala-sbt.org/1.x/docs/Installing-sbt-on-Linux.html#Ubuntu+and+other+Debian-based+distributions
RUN echo "deb https://dl.bintray.com/sbt/debian /" | tee -a /etc/apt/sources.list.d/sbt.list
RUN curl -sL "https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x2EE0EA64E40A89B84B2DF73499E82A75642AC823" | apt-key add
RUN apt-get update
RUN apt-get install sbt

# Pre-cache all the SBT stuff
RUN sbt exit

# Build Montage from master … feelin' lucky.
WORKDIR /opt
RUN git clone https://github.com/Caltech-IPAC/Montage.git
WORKDIR /opt/Montage
RUN make
WORKDIR /
ENV PATH="${PATH}:/opt/Montage/bin"

# Build gsp-montage
WORKDIR /opt/gsp-montage
ADD --chown=daemon:daemon modules   /opt/gsp-montage/modules
ADD --chown=daemon:daemon project   /opt/gsp-montage/project
ADD --chown=daemon:daemon build.sbt /opt/gsp-montage/
RUN sbt compile stage
RUN chmod a+x modules/core/target/universal/stage/bin/mosaic-server-core
WORKDIR /

# Set up to run
USER daemon
CMD /opt/gsp-montage/modules/core/target/universal/stage/bin/mosaic-server-core -J-Xmx256m


