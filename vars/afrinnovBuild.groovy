def isMaster = env.BRANCH_NAME == 'master'

pipeline {
    agent any

    environment {
        DOCKER_HUB_CREDS = credentials('docker-afrinnov-service-credentials')
        REGISTRY_CREDENTIAL = 'docker-afrinnov-service-credentials'
        REGISTRY_HUB = "https://registry.hub.docker.com"
    }

    stages{
        stage ("start") {
            steps {
                echo "Starting..."
            }
        }
        stage ("build"){
            steps{
                script {
                    if(currentBuild.changeSets.size() == 0) {
                        currentBuild.result = 'SUCCESS'
                    } else {
                        sh "chmod +x mvnw"
                        sh "./mvnw -DskipTests clean package"
                        step([$class: 'JavadocArchiver', javadocDir: 'target/', keepAll: true])
                    }
                }

            }
        }

        stage('TestUnitaire') {
            steps{
                script {
                    if(currentBuild.changeSets.size() == 0) {
                        currentBuild.result = 'SUCCESS'
                    } else {
                        sh './mvnw surefire:test '
                        step([$class: 'JUnitResultArchiver', testResults: 'target/surefire-reports/*.xml'])
                        jacoco()
                    }
                }

            }
        }

        stage('TestIntegration') {
            steps{
                script {
                    if(currentBuild.changeSets.size() == 0) {
                        currentBuild.result = 'SUCCESS'
                    } else {
                        sh './mvnw failsafe:integration-test'
                        step([$class: 'JUnitResultArchiver', testResults: 'target/failsafe-reports/*.xml'])
                        jacoco()
                    }
                }

            }
        }

        stage('Create_and_push_container') {
            when {
                expression {
                    currentBuild.result == null || currentBuild.result == 'SUCCESS'
                }
            }
            steps {
                script {
                    if(currentBuild.changeSets.size() == 0) {
                        currentBuild.result = 'SUCCESS'
                        return
                    }

                    if(currentBuild.changeSets.size() > 0) {
                        withCredentials([usernamePassword(credentialsId: REGISTRY_CREDENTIAL, usernameVariable: 'REGISTRY_USERNAME',
                                passwordVariable: 'REGISTRY_PASSWORD')]) {
                            sh "./mvnw -DskipTests -P dev jib:build"
                        }
                    } else {
                        echo 'No Execute JIB'
                    }
                }
            }
        }

        stage("deploy") {
            when {
                expression {
                    currentBuild.result == null || currentBuild.result == 'SUCCESS'
                }
            }
            steps {
                script {
                    if(currentBuild.changeSets.size() == 0) {
                        currentBuild.result = 'SUCCESS'
                        return
                    }
                    if(currentBuild.changeSets.size() > 0) {
                        sh "chmod 777 deploy.sh"
                        sh "sh deploy.sh sysbio_app 58085 dev"
                    } else {
                        echo 'No Execute deploy'
                    }
                }
            }
        }

    }

    post {
        success {
            cleanUp();
        }
        unstable {
            cleanUp();
        }
        failure {
            cleanUp();
        }

    }
}
@NonCPS
def cleanUp() {
    cleanWs(patterns: [[pattern: 'Dockerfile*', type: 'EXCLUDE'],[pattern: 'target/*.jar', type: 'EXCLUDE'],[pattern: 'target/surefire-reports/**', type: 'EXCLUDE'],[pattern: 'target/failsafe-reports/**', type: 'EXCLUDE'], [pattern: 'target/jacoco/**', type: 'EXCLUDE']])
}