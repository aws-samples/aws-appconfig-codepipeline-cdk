package com.app.config

import software.amazon.awscdk.App
import software.amazon.awscdk.StackProps

object AppConfigCiCdApp {
    @JvmStatic
    fun main(args: Array<String>) {
        val app = App()

        AppConfigCiCdStack(app, "AppConfigCiCdStack", StackProps.builder()
                .build())

        app.synth()
    }
}