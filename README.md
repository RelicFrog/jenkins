# RelicFrog Application Jenkins Dockerfile

[![Software License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![System Version](https://img.shields.io/badge/Version-1.0.0-blue.svg)](VERSION)
[![Language](https://img.shields.io/badge/Docker-18.06.3.CE-E47911.svg)](https://www.docker.com/)
[![Documentation](https://img.shields.io/badge/DOC-link-green.svg)](https://google.com)

_The current documentation in this repository is not yet final and will be adjusted and extended over the next commits according to the extended functionality of this Jenkins Application CI/CD Dockerfile definition._

## Table of contents
* [General](#general)
* [Goals](#goals)
* [Technologies](#technologies)
* [Setup](#setup-local-test)
* [Infrastructure](#infrastructure) 
* [Environment Variables](#environment-variables)

## General

This repository is used to provide the primary build configuration of the Infrastructure CI/CD Jenkins server for RelicFrog and will be handled by our Infrastructure Jenkins infrastructure [code](https://intranet.RelicFrog.com/bb/projects/ALZ/repos/iac_jenkins_docker/browse). 

### Goals

This Jenkins server should be centrally managed and configured as a containerized application. The actual docker image should be executable as a container in any environment (in this case as a service within an elastic-beanstalk application).

_Further requirements can be found in the following list_

- Docker host process capabilities
- Matrix and SAML-2.0/SSO authentication
- dedicated init.groovy startup procedures
- AWS CodePipeline compatibility 

## Technologies

* Docker: 18.06.3-ce
* Jenkins: 2.232 (BlueOcean)

## Setup (local test)

* install docker
* switch to repository root directory
* run `docker build -t relicfrog/app-jenkins .`
* run `docker run -e JENKINS_AUTHORIZATION_STRATEGY=Matrix -e JENKINS_USER=admin -e JENKINS_PASS=123456 -d -p 80:8080 relicfrog/app-jenkins`
* access `http://localhost` on your local machine, auth using selected username and password

## Infrastructure

* Jenkins BlueOcean
* Jenkins base plugin payload

## Environment Variables

The authentication method (and some other core parameters of the Jenkins configuration) is controlled by environment variables. The following overview presents the authentication method and its default parameters:

| parameter                                      | default value                                                | description / possible values                                                |
|------------------------------------------------|--------------------------------------------------------------|------------------------------------------------------------------------------|
| `JENKINS_USER`                                 | `jenkins`                                                    | jenkins primary user (for matrix auth)                                       |
| `JENKINS_PASS`                                 | `123456`                                                     | jenkins primary users password (for matrix auth)                             |
| `JENKINS_HOME`                                 | `/var/jenkins_home`                                          | jenkins default home directory (used as EFS mounted volume)                  |
| `JENKINS_PORT`                                 | `8080`                                                       | jenkins default service port (for containerized runs)                        |
| `JENKINS_OPTS`                                 | `--httpPort=8080`                                            | additional runtime options (will be added during primary startup procedure)  |
| `JENKINS_UC_DOWNLOAD`                          | `http://ftp-nyc.osuosl.org/pub/jenkins`                      | jenkins update server override (we'll use this ftp for better reachability)  |
| `JENKINS_AUTHORIZATION_STRATEGY`               | `Matrix`                                                     | jenkins authorization strategy (options: GitHub, SAML, Matrix)               |
| `JENKINS_SAML_DISPLAY_NAME_ATTRIBUTE_NAME`     | `http://schemas.xmlsoap.org/ws/2005/05/identity/claims/name` | SAML 2.0 option for handling username visualization                          |
| `JENKINS_SAML_GROUPS_ATTRIBUTE_NAME`           | `http://schemas.xmlsoap.org/claims/Group`                    | SAML 2.0 option for handling users group attribute                           |
| `JENKINS_SAML_MAXIMUM_AUTHENTICATION_LIFETIME` | `86400`                                                      | SAML 2.0 option for handling session lifetime of authenticated users         |
| `JENKINS_SAML_USERNAME_ATTRIBUTE_NAME`         | `urn:oasis:names:tc:SAML:2.0:attrname-format:basic`          | SAML 2.0 option for handling username attribute                              |
| `JENKINS_SAML_EMAIL_ATTRIBUTE_NAME`            | `urn:oasis:names:tc:SAML:2.0:attrname-format:basic`          | SAML 2.0 option for handling users email-address attribute                   |
| `JENKINS_SAML_BINDING`                         | `urn:oasis:names:tc:SAML:2.0:bindings:HTTP-Redirect`         | SAML 2.0 option for handling http binding method                             |
| `JENKINS_SAML_IDP_META_DATA_FILE`              | `/var/jenkins_home/onelogin.relicfrog.idp.metadata.xml.tpl`  | SAML 2.0 option for the corresponding IDP mata-data file (xml)               |
| `JENKINS_SAML_LOGOUT_URL`                      | `https://blackfrog-sso.onelogin.com//portal`                 | SAML 2.0 option for logout URL (will be used as target after jenkins-logout) |
| `JAVA_OPTS`                                    | `-Dorg.jenkinsci.plugins.gitclient.Git.timeOut=60 ...`       | additional JVM runtime options used during core jenkins startup preparations |

The Jenkins-Dockerfile uses a lot of additional build arguments to simplify the parameter passing during image creation. the used arguments and their default values can be found in the table below.

| argument                      | default      | description                                        |
|-------------------------------|--------------|----------------------------------------------------|
| `DOCKER_GROUP`                | `docker`     | linux docker group                                 |
| `SYSTEM_DOCKER_VERSION`       | `18.06.3-ce` | docker version                                     |
| `JENKINS_UPDATE_VERSION`      | `2.222.1`    | target jenkins version                             |
| `JENKINS_POST_SETUP_SLEEP_MS` | `1000`       | pre-init timer before starting the jenkins service |
| `JENKINS_INTERNAL_PORT`       | `8080`       | internal port for our jenkins instance             |
| `RUN_USER`                    | `jenkins`    | runtime container user                             |
| `RUN_GROUP`                   | `jenkins`    | runtime container group                            |
| `HELM_VERSION`                | `v2.16.6`    | target helm version                                |
| `KUBECTL_VERSION`             | `v1.16.3`    | target kubectl version                             |
| `APP_JENKINS_VERSION`         | `1.0.0`      | internal service version identifier                |

## links

* https://github.com/Mirantis/ccp-docker-jenkins
* https://github.com/Accenture/adop-jenkins
* https://github.com/target/jenkins-docker-master/blob/master/examples/Dockerfile
