package com.app.config

import software.amazon.awscdk.App
import software.amazon.awscdk.Environment
import software.amazon.awscdk.StackProps

object AppConfigCiCdApp {
    @JvmStatic
    fun main(args: Array<String>) {
        val app = App()

        AppConfigCiCdStack(app, "AppConfigCiCdStack", StackProps.builder()
                .env(Environment.builder()
                        .region("eu-west-1")
                        .build())
                .build())

        ServerlessAppStack(app, "ServerlessAppStack", StackProps.builder()
                .env(Environment.builder()
                        .region("eu-west-1")
                        .build())
                .tags(mapOf(Pair("environment", "Test")))
                .build())

        app.synth()
    }
}