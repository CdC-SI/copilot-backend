pipeline {
    agent { label 'Java' }
    tools {
        jdk 'jdk21(x64)'
        maven 'Maven'
    }
    stages {
        stage('SCM') {
            steps {
                checkout scm
            }
        }
        stage('Build') {
            steps {
                withMaven(maven: 'Maven', mavenLocalRepo: '.repository') {
                    sh "mvn clean install -DskipTests"
                }
            }
        }
        stage('SonarQube Analysis') {
            steps {
                withMaven(maven: 'Maven', mavenLocalRepo: '.repository') {
                    withSonarQubeEnv('Sonar') {
                        sh "mvn sonar:sonar -Dsonar.projectKey=CdC-SI_copilot-backend_41b3042d-cf65-46b8-8b14-0a4833a621ca -Dsonar.projectName='copilot-backend'"
                    }
                }
            }
        }
    }
}
