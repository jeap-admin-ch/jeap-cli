@Library('jeap-microservice-pipeline@v2') _

def enforceOpenSourcePreconditions = { context ->
    ch.admin.bit.jeap.microservicePipeline.oss.OpenSourcePreconditionEnforcer.enforcePreconditions(context)
}

jeapBuildPipeline(
    afterSetup: enforceOpenSourcePreconditions,
    mavenImage: 'bit/eclipse-temurin:25',
    mavenDockerUser: 'jenkins',
    buildMavenDockerArgs: '-v /var/run/docker.sock:/var/run/docker.sock',
    branch: [
        MASTER: [
            systemIntegrationTest: false,
            deployStage          : null,
            nextStage            : null,
            // TODO additionalMavenArgs  : '-P maven-central-publish' // Enable maven central publication when ready
        ],
        FEATURE: [
            integrationTest     : true,
            publish             : true,
            buildNumberGenerator: ch.admin.bit.jeap.microservicePipeline.branching.BuildNumberGenerator.BRANCH_NAME_SNAPSHOT
        ]
    ]
)

