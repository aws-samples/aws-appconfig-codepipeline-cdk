package com.app.config

import com.fasterxml.jackson.databind.ObjectMapper
import software.amazon.awscdk.RemovalPolicy.DESTROY
import software.amazon.awscdk.Stack
import software.amazon.awscdk.StackProps
import software.amazon.awscdk.services.appconfig.*
import software.amazon.awscdk.services.appconfig.CfnEnvironment.TagsProperty
import software.amazon.awscdk.services.codecommit.Repository
import software.amazon.awscdk.services.codecommit.RepositoryProps
import software.amazon.awscdk.services.codepipeline.CfnPipeline
import software.amazon.awscdk.services.codepipeline.CfnPipeline.*
import software.amazon.awscdk.services.codepipeline.CfnPipelineProps
import software.amazon.awscdk.services.events.CfnRule
import software.amazon.awscdk.services.events.CfnRuleProps
import software.amazon.awscdk.services.iam.*
import software.amazon.awscdk.services.s3.Bucket
import software.constructs.Construct

class AppConfigCiCdStack @JvmOverloads constructor(scope: Construct?, id: String?, props: StackProps? = null) : Stack(scope, id, props) {
    init {

        val application = CfnApplication(this, "ServerlessApplication",
                CfnApplicationProps.builder()
                        .description("ConFiguration For Serverless Application")
                        .name("ServerlessApplicationConfig")
                        .build())

        val environment = CfnEnvironment(this, "Testing",
                CfnEnvironmentProps.builder()
                        .description("Testing environment for app")
                        .name("Test")
                        .applicationId(application.ref)
                        .tags(listOf(TagsProperty.builder()
                                .key("Env")
                                .value("Test")
                                .build()))
                        .build()
        )

        val profile = CfnConfigurationProfile(this, "LoggingConfig",
                CfnConfigurationProfileProps.builder()
                        .applicationId(application.ref)
                        .description("Logging related configuration")
                        .name("LoggingConfig")
                        .locationUri("codepipeline://ConfigurationDeploymentPipeline")
                        .tags(listOf(CfnConfigurationProfile.TagsProperty.builder()
                                .key("Type")
                                .value("Logging")
                                .build()))
                        .build()
        )

        val deploymentStrategy = CfnDeploymentStrategy(this, "DeploymentStrategy", CfnDeploymentStrategyProps.builder()
                .deploymentDurationInMinutes(0)
                .finalBakeTimeInMinutes(1)
                .growthType("LINEAR")
                .growthFactor(100)
                .name("Custom.Immediate.Bake5Minutes")
                .replicateTo("NONE")
                .build()
        )

        val repository = Repository(this, "ServerlessAppConfigurations",
                RepositoryProps.builder()
                        .repositoryName("serverless-app-configurations")
                        .description("Holds applications configurations")
                        .build()
        )

        pipeline(repository, application, environment, profile, deploymentStrategy)
    }

    private fun pipeline(repository: Repository,
                         application: CfnApplication,
                         environment: CfnEnvironment,
                         profile: CfnConfigurationProfile,
                         deploymentStrategy: CfnDeploymentStrategy) {

        val pipelineArtifact = Bucket.Builder.create(this, "ServerlessAppConfigPipelineArtifact")
                .autoDeleteObjects(true)
                .removalPolicy(DESTROY)
                .build()

        val sourceConfig = mapOf(
                Pair("RepositoryName", repository.repositoryName),
                Pair("BranchName", "main"),
                Pair("PollForSourceChanges", "false"),
        )

        val configRepoSource = ActionDeclarationProperty.builder()
                .actionTypeId(ActionTypeIdProperty.builder()
                        .category("Source")
                        .owner("AWS")
                        .provider("CodeCommit")
                        .version("1")
                        .build())
                .configuration(sourceConfig)
                .name("ServerlessAppConfigSource")
                .outputArtifacts(listOf(OutputArtifactProperty.builder()
                        .name("ConfigSourceArtifact")
                        .build()))
                .build()

        val sourceStage = StageDeclarationProperty.builder()
                .name("Source")
                .actions(listOf<ActionDeclarationProperty>(configRepoSource))
                .build()

        val deployConfig = mapOf(
                Pair("Application", application.ref),
                Pair("Environment", environment.ref),
                Pair("ConfigurationProfile", profile.ref),
                Pair("InputArtifactConfigurationPath", "logging.yaml"),
                Pair("DeploymentStrategy", deploymentStrategy.ref),
        )

        val appConfigDeploy = ActionDeclarationProperty.builder()
                .actionTypeId(ActionTypeIdProperty.builder()
                        .category("Deploy")
                        .owner("AWS")
                        .provider("AppConfig")
                        .version("1")
                        .build())
                .inputArtifacts(listOf(
                        InputArtifactProperty.builder()
                                .name("ConfigSourceArtifact")
                                .build()
                ))
                .configuration(deployConfig)
                .name("ServerlessAppConfigDeploy")
                .build()

        val deployStage = StageDeclarationProperty.builder()
                .name("Deploy")
                .actions(listOf(appConfigDeploy))
                .build()

        val pattern = AppConfigCiCdStack::class.java.getResource("/asset/codeCommitRule.json")?.readText()
                ?.replace("REPO_ARN", "arn:aws:codecommit:${this.region}:${this.account}:${repository.repositoryName}")

        val policyJson = AppConfigCiCdStack::class.java.getResource("/asset/codepipeline-default-service-policy.json")?.readText()

        val document = PolicyDocument.fromJson(ObjectMapper().readTree(policyJson))

        val servicePolicy = ManagedPolicy.Builder.create(this, "ServerlessAppConfigPipelineServicePolicy")
                .document(document)
                .build()

        val role = Role(this, "ServerlessAppConfigPipelineRole", RoleProps.builder()
                .assumedBy(ServicePrincipal("codepipeline.amazonaws.com"))
                .managedPolicies(listOf<ManagedPolicy>(servicePolicy))
                .build())

        val pipeline = CfnPipeline(this, "ServerlessAppConfigPipeline", CfnPipelineProps.builder()
                .stages(listOf<StageDeclarationProperty>(sourceStage, deployStage))
                .artifactStore(ArtifactStoreProperty.builder()
                        .location(pipelineArtifact.bucketName)
                        .type("S3")
                        .build())
                .roleArn(role.roleArn)
                .name("ServerlessAppConfigPipeline")
                .build())

        val codePipelineArn = "arn:aws:codepipeline:${this.region}:${this.account}:${pipeline.ref}"

        val startPipelineRole = Role.Builder.create(this, "StartPipelineRole")
                .assumedBy(ServicePrincipal("events.amazonaws.com"))
                .build()

        CfnRule(this, "TriggerCodePipeline", CfnRuleProps.builder()
                .description("Rule to trigger code pipeline on code commit repo config")
                .name("ServerlessAppConfigSourceRule")
                .eventPattern(pattern)
                .targets(listOf(CfnRule.TargetProperty.builder()
                        .arn(codePipelineArn)
                        .id("ServerlessAppConfigSourceRuleTarget")
                        .roleArn(startPipelineRole.roleArn)
                        .build()))
                .build())
    }
}