##FROM openjdk:11-buster
FROM ubuntu:bionic

USER root
WORKDIR /root

SHELL [ "/bin/bash", "-c" ]

RUN apt-get -qq -y update && apt-get -qq -y install sudo vim default-jre python3.8 python3-pip docker.io

# Create user with sudo powers
RUN useradd -m tasksrunner && \
    usermod -aG sudo tasksrunner && \
    echo '%sudo ALL=(ALL) NOPASSWD: ALL' >> /etc/sudoers && \
    cp /root/.bashrc /home/tasksrunner/ && \
    chown -R --from=root tasksrunner /home/tasksrunner

# Use C.UTF-8 locale to avoid issues with ASCII encoding
ENV LC_ALL=C.UTF-8
ENV LANG=C.UTF-8

ENV HOME /home/tasksrunner
ENV USER tasksrunner
USER tasksrunner
# Avoid first use of sudo warning. c.f. https://askubuntu.com/a/22614/781671
RUN touch $HOME/.sudo_as_admin_successful

WORKDIR /home/tasksrunner
ADD . .
RUN sudo chown -R tasksrunner .

