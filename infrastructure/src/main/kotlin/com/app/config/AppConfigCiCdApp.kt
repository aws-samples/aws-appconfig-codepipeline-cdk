package com.app.config

import software.amazon.awscdk.App
import software.amazon.awscdk.Environment
import software.amazon.awscdk.StackProps

object AppConfigCiCdApp {
    @JvmStatic
    fun main(args: Array<String>) {
        val app = App()

        val environment = makeEnv()

        AppConfigCiCdStack(app, "AppConfigCiCdStack", StackProps.builder()
                .env(environment)
                .build())

        ServerlessAppStack(app, "ServerlessAppStack", StackProps.builder()
                .env(environment)
                .tags(mapOf(Pair("environment", "Test")))
                .build())

        app.synth()
    }

    private fun makeEnv(account: String? = null, region: String? = null): Environment? {
        return Environment.builder()
            .account(account ?: System.getenv("CDK_DEFAULT_ACCOUNT"))
            .region(region ?: System.getenv("CDK_DEFAULT_REGION"))
            .build()
    }
}