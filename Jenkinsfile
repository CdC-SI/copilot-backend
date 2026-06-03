@Library('phenix-pipeline-library') _

mavenDockerBuild{
    displayParameters=true
    jdk='jdk21(x64)'
    mvnArgs='-B clean verify'
    dockerConfig = [
        dockerfilePath: 'Dockerfile',
        imageRoot: 'zas/copilot',
        imageName: 'java-backend',
        buildParams: [HTTP_PROXY: this.env.HTTP_PROXY_OCP, HTTPS_PROXY: this.env.HTTP_PROXY_OCP, no_proxy: this.env.NO_PROXY],
        options: ['--rm']
    ]
    email = [recipients: '']
    triggerDevPromotion = [ repositoryName : 'copilot-ocp-promote', versionProperty: 'java-backend.image.version' ]
}
