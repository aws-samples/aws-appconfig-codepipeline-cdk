package com.app.config

import software.amazon.awscdk.*
import software.amazon.awscdk.BundlingOutput.ARCHIVED
import software.amazon.awscdk.regioninfo.RegionInfo
import software.amazon.awscdk.services.apigateway.LambdaRestApi
import software.amazon.awscdk.services.apigateway.LambdaRestApiProps
import software.amazon.awscdk.services.iam.Effect
import software.amazon.awscdk.services.iam.PolicyStatement
import software.amazon.awscdk.services.iam.PolicyStatementProps
import software.amazon.awscdk.services.lambda.*
import software.amazon.awscdk.services.lambda.Function
import software.amazon.awscdk.services.logs.RetentionDays
import software.amazon.awscdk.services.s3.assets.AssetOptions
import software.constructs.Construct
import java.lang.IllegalStateException


class ServerlessAppStack constructor(scope: Construct?, id: String?, props: StackProps) : Stack(scope, id, props) {
    companion object {
        // https://docs.aws.amazon.com/appconfig/latest/userguide/appconfig-integration-lambda-extensions.html
        val  appConfigExtension = mapOf(
                Pair("eu-west-1", "arn:aws:lambda:eu-west-1:434848589818:layer:AWS-AppConfig-Extension:41"),
                Pair("us-east-1", "arn:aws:lambda:us-east-1:027255383542:layer:AWS-AppConfig-Extension:44")
        )

        val envConfigMap = mapOf(
                Pair("Test", mapOf(
                        Pair("configPath", "ServerlessApplicationConfig/Test/LoggingConfiguration"),
                        Pair("configEnvironmentId", "TestEnvironmentId")
                ))
        )
    }

    init {

        val demoFunctionPackagingInstructions = listOf(
                "/bin/sh",
                "-c",
                "cd demofunction " +
                "&& mvn clean install " +
                "&& cp /asset-input/demofunction/target/demo-function.jar /asset-output/"
        )

        val builderOptions: BundlingOptions.Builder = BundlingOptions.builder()
                .command(demoFunctionPackagingInstructions)
                .image(Runtime.JAVA_11.bundlingImage)
                .volumes(listOf( // Mount local .m2 repo to avoid download all the dependencies again inside the container
                        DockerVolume.builder()
                                .hostPath(System.getProperty("user.home") + "/.m2/")
                                .containerPath("/root/.m2/")
                                .build()
                ))
                .user("root")
                .outputType(ARCHIVED)

        val demoFunction = Function(this, "demoFunction", FunctionProps.builder()
                .runtime(Runtime.JAVA_11)
                .code(Code.fromAsset("../software/", AssetOptions.builder()
                        .bundling(builderOptions
                                .build())
                        .build()))
                .environment(mapOf(
                        Pair("APP_CONFIG_PATH", envConfigMap[props.tags?.get("environment")]?.get("configPath")),
                        Pair("AWS_APPCONFIG_EXTENSION_LOG_LEVEL", "debug"),
                ))
                .layers(listOf(LayerVersion.fromLayerVersionArn(this, "appConfigExtension",
                        appConfigExtension[props.env?.region] ?: throw IllegalStateException("Missing ARN mapping for the region."))))
                .handler("com.app.config.DemoHandler")
                .memorySize(1024)
                .timeout(Duration.seconds(10))
                .logRetention(RetentionDays.ONE_WEEK)
                .build())

        demoFunction.addToRolePolicy(PolicyStatement(PolicyStatementProps.builder()
                .actions(listOf("appconfig:GetConfiguration"))
                .effect(Effect.ALLOW)
                .resources(listOf(
                        "arn:aws:appconfig:${this.region}:${this.account}:application/${Fn.importValue("ServerlessApplicationConfig")}",
                        "arn:aws:appconfig:${this.region}:${this.account}:application/${Fn.importValue("ServerlessApplicationConfig")}/environment/${Fn.importValue(envConfigMap[props.tags?.get("environment")]?.get("configEnvironmentId") ?: "")}",
                        "arn:aws:appconfig:${this.region}:${this.account}:application/${Fn.importValue("ServerlessApplicationConfig")}/configurationprofile/${Fn.importValue("LoggingConfigurationId")}"
                ))
                .build()))

        LambdaRestApi(this, "serverless-api",
                LambdaRestApiProps.builder()
                        .handler(demoFunction)
                        .build()
        )
    }
}