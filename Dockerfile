# --
# OS/CORE : jenkinsci/blueocean:latest
# --
# VERSION 1.0.0
# --
# @see documentation  -> https://github.com/jenkinsci/blueocean-plugin/blob/master/docker/official/Dockerfile
# @see env/proxy bug  -> https://github.com/moby/moby/issues/24697#issuecomment-235592547
# @see Jenkins/UC bug -> https://github.com/jenkinsci/docker/issues/594
# --

FROM jenkinsci/blueocean

MAINTAINER Patrick Paechnatz <post@dunkelfrosch.com>

ARG DOCKER_GROUP="docker"
ARG SYSTEM_DOCKER_VERSION="18.06.3-ce"
ARG JENKINS_UPDATE_VERSION="2.222.1"
ARG JENKINS_POST_SETUP_SLEEP_MS="1000"
ARG JENKINS_INTERNAL_PORT="8080"
ARG JENKINS_INTERNAL_HOME="/var/jenkins_home"
ARG RUN_USER="jenkins"
ARG RUN_GROUP="jenkins"
ARG HELM_VERSION="v2.16.6"
ARG KUBECTL_VERSION="v1.16.3"
ARG APP_JENKINS_VERSION="1.0.0"

ENV TERM="xterm-256color" \
    SHELL="/bin/bash" \
    LC_ALL="C" \
    PATH=$PATH:/root/.linkerd2/bin:/root/.istioctl/bin \
    TIMEZONE="Europe/Berlin" \
    DOCKER_CONFIG="/var/jenkins_home/.docker" \
    DOCKER_HOST="unix:///run-data/docker.sock" \
    C_SERVICE_NAME_LONG="relicfrog/build/jenkins/blue-ocean" \
    C_SERVICE_TMP_PATH="/.docker/opt/share" \
    C_DOCKER_PATH="/usr/local/bin/docker" \
    CURL_OPTIONS="-sSfLk" \
    JENKINS_HOME="${JENKINS_INTERNAL_HOME}" \
    JENKINS_PORT="${JENKINS_INTERNAL_PORT}" \
    JENKINS_OPTS="--httpPort=${JENKINS_INTERNAL_PORT}" \
    JENKINS_URL="http://localhost" \
    JENKINS_POST_SETUP_SLEEP_TIME=${JENKINS_POST_SETUP_SLEEP_MS} \
    JENKINS_UC_DOWNLOAD="http://ftp-nyc.osuosl.org/pub/jenkins" \
    JAVA_OPTS="-Dorg.jenkinsci.plugins.gitclient.Git.timeOut=60 -Dhudson.DNSMultiCast.disabled=true -Djava.awt.headless=true -Dsun.net.inetaddr.ttl=60 -Dhudson.footerURL=https://build.relicfrog.com/ -Dgroovy.grape.report.downloads=true -Divy.message.logger.level=4 -Dgrape.config=${JENKINS_HOME}/.groovy/grapeConfig.xml -Djenkins.install.runSetupWizard=false -Djenkins.ui.refresh=true -Djenkins.model.Jenkins.workspacesDir=${C_SERVICE_EXT_WS_PATH} -Dhudson.model.Slave.workspaceRoot=${C_SERVICE_EXT_WS_PATH}"

# override default image user [jenkins]
USER root

# x-layer 1.1: create required directories
RUN mkdir -p /usr/share/jenkins/ref/plugins \
             ${C_SERVICE_TMP_PATH}

# x-layer 1.2: create plugin update lock file
RUN touch /usr/share/jenkins/ref/plugins/dummy.lock

# x-layer 2: extend apk repository by using 'edge'
RUN set -e && \
    echo "http://dl-3.alpinelinux.org/alpine/edge/community/" >> /etc/apk/repositories

# x-layer 3.1: add base tools using alpine's apk
RUN apk add --update --no-cache --virtual .persistent-deps \
        ca-certificates rsync tzdata shadow curl wget git tar unzip mc htop which sudo openssl

# x-layer 3.2: add pip/python3 tools using alpine's apk
RUN apk add --no-cache python3 && \
    if [ ! -e /usr/bin/python ]; then ln -sf python3 /usr/bin/python ; fi && \
    python3 -m ensurepip && \
    rm -r /usr/lib/python*/ensurepip && \
    pip3 install --no-cache --upgrade pip setuptools wheel && \
    if [ ! -e /usr/bin/pip ]; then ln -s pip3 /usr/bin/pip ; fi

# x-layer 4: update timezone settings/locales
RUN cp -f /usr/share/zoneinfo/${TIMEZONE:-UTC} /etc/localtime && echo ${TIMEZONE:-UTC} > /etc/timezone

# x-layer 5: add spec docker configuration (user/group)
RUN usermod -aG ${DOCKER_GROUP} ${RUN_USER}

# x-layer 6: install binary caller for docker (client socket will be used)
RUN curl -fsSL "https://download.docker.com/linux/static/stable/x86_64/docker-${SYSTEM_DOCKER_VERSION}.tgz" | tar xz > docker && \
    mv docker/docker ${C_DOCKER_PATH} && \
    chmod +x ${C_DOCKER_PATH} && \
    rm -rf docker

# x-layer 7: (re) configure jenkins identity key
RUN rm -f ${JENKINS_HOME}/identity.key.enc \
          ${JENKINS_HOME}/secrets/org.jenkinsci.main.modules.instance_identity.InstanceIdentity.KEY

# x-layer 8.1: provide jenkins ext-/xml-config and userContent to reference path (will be copied into jenkins_home during startup)
COPY /var/jenkins_home/org.codefirst.SimpleThemeDecorator.xml /usr/share/jenkins/ref/org.codefirst.SimpleThemeDecorator.xml
COPY /var/jenkins_home/scriptApproval.xml /usr/share/jenkins/ref/scriptApproval.xml
COPY /var/jenkins_home/userContent/css /usr/share/jenkins/ref/userContent/css/
COPY /var/jenkins_home/userContent/img /usr/share/jenkins/ref/userContent/img/
COPY /var/jenkins_home/init.groovy.d/init.groovy /usr/share/jenkins/ref/init.groovy.d/
COPY /var/jenkins_home/init.groovy.d/update.plugins.groovy /usr/share/jenkins/ref/init.groovy.d/
COPY /var/jenkins_home/onelogin.relicfrog.idp.metadata.xml.tpl /usr/share/jenkins/ref/

# x-layer 8.2.1: update jenkins application core + manual override primary war file + flush all init.d scripts
RUN cd ; wget -q http://updates.jenkins-ci.org/download/war/${JENKINS_UPDATE_VERSION}/jenkins.war && \
    mv -f ./jenkins.war /usr/share/jenkins/

# x-layer 8.2.2: install additional plugin requirements for jenkins using console plugin installer script (.-.)
COPY init.jenkins.plugins.${APP_JENKINS_VERSION}.txt /.docker/opt
RUN /usr/local/bin/install-plugins.sh < /.docker/opt/init.jenkins.plugins.${APP_JENKINS_VERSION}.txt

# x-layer 8.2.3: fix permissions and add jenkins user to sudoers file, allow pwd free access to docker exec
RUN chown ${RUN_USER}:${RUN_GROUP} ${JENKINS_HOME}/ /usr/share/jenkins/ref/ -R && \
    chmod -r /etc/sudoers && \
    echo -e "jenkins ALL=(ALL) NOPASSWD:ALL" > /etc/sudoers && \
    echo -e "Defaults !env_reset\nDefaults env_keep += \"DOCKER_CONFIG\"" > /etc/sudoers.d/docker && \
    chmod +r /etc/sudoers

# x-layer 8.3.1: install helm
RUN wget -q https://get.helm.sh/helm-${HELM_VERSION}-linux-amd64.tar.gz -O - | tar -xzO linux-amd64/helm > /usr/local/bin/helm && \
    chmod +x /usr/local/bin/helm

# x-layer 8.3.2: install kubectl
RUN wget -q https://storage.googleapis.com/kubernetes-release/release/${KUBECTL_VERSION}/bin/linux/amd64/kubectl -O /usr/local/bin/kubectl && \
    chmod +x /usr/local/bin/kubectl

# x-layer 8.3.3: install aws-cli
RUN pip3 install awscli --upgrade

# x-layer 8.3.4: install linkerd cli
RUN curl -sL https://run.linkerd.io/install | sh -

# x-layer 8.3.5: install istio cli
RUN curl -sL https://istio.io/downloadIstioctl | sh -

# x-layer 9: clean up this image (could be extend), fix some permissions and write image log entry
RUN rm -rf /var/cache/apk/* && \
    printf "_docker/build/info     : images created [%s] -> service [${C_SERVICE_NAME_LONG}]\n" $(date -u +"%Y-%m-%dT%H:%M:%SZ") >> /.docker/.build_info && \
    echo   "_system/core/os        : $(uname -a)" >> /.docker/.build_info && \
    echo   "_system/usr/grp/build  : $(whoami):$(whoami)" >> /.docker/.build_info && \
    echo   "_system/usr/grp/run    : ${RUN_USER}:${RUN_GROUP}" >> /.docker/.build_info && \
    echo   "_system/docker/version : $(docker --version)" >> /.docker/.build_info && \
    echo   "_system/docker/config  : ${DOCKER_CONFIG}" >> /.docker/.build_info

# x-layer 10: label definition
LABEL maintainer="post@dunkelfrosch.com" \
      com.container.vendor="RelicFrog Ltd" \
      com.container.service="relicfrog/devops/jenkins/blue-ocean" \
      com.container.project="app-jenkins-relicfrog-pub" \
      com.container.service.version="${JENKINS_UPDATE_VERSION}" \
      com.container.service.run.user="${RUN_USER}" \
      com.container.service.run.group="${RUN_GROUP}" \
      com.container.docker.version="${SYSTEM_DOCKER_VERSION}" \
      img.name="relicfrog/app-jenkins" \
      img.description="relicfrog primary jenkins docker image definition" \
      img.version="1.0.0"

#
# -- ENTRYPOINT/EXPOSE --
#

USER ${RUN_USER}:${RUN_GROUP}
EXPOSE ${JENKINS_INTERNAL_PORT}
