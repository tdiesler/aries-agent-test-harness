## Build the Python base image #################################################

FROM python:3.10.5-slim AS builder_01

RUN apt-get update \
   && apt-get install -y git gnupg2 software-properties-common curl \
   && apt-key adv --keyserver keyserver.ubuntu.com --recv-keys CE7709D068DB5E88 \
   && add-apt-repository 'deb https://repo.sovrin.org/sdk/deb bionic stable' \
   && apt-get update \
   && apt-get install -y libindy libnullpay

RUN mkdir -p /aries-backchannels
WORKDIR /aries-backchannels

COPY python/requirements.txt python/
RUN pip install -r python/requirements.txt
RUN pip install aries-cloudagent

# Copy the necessary files from the AATH Backchannel sub-folders
COPY python python
COPY data ./

RUN /usr/local/bin/aca-py --version > ./acapy-version.txt

## Add a Java layer ############################################################

FROM builder_01 AS builder_02

RUN apt-get install -y openjdk-17-jdk-headless

## Add a Camel layer ###########################################################

FROM builder_02

COPY camel/*.py camel/
COPY camel/target/camel-aath-backchannel/bin camel/bin
COPY camel/target/camel-aath-backchannel/lib camel/lib

ENV PYTHONPATH=/aries-backchannels
ENV RUNMODE=docker

# RUN ./bin/aca-py --version > ./acapy-version.txt

ENTRYPOINT ["python", "camel/camel_backchannel.py"]
