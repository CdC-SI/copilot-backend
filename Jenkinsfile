node {
  stage('SCM') {
    checkout scm
  }
  stage('SonarQube Analysis') {
    def mvn = tool 'Maven';
    withSonarQubeEnv() {
      sh "${mvn}/bin/mvn clean verify sonar:sonar -Dsonar.projectKey=CdC-SI_copilot-backend_41b3042d-cf65-46b8-8b14-0a4833a621ca -Dsonar.projectName='copilot-backend'"
    }
  }
}
