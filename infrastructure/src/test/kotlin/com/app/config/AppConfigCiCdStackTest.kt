package com.app.config

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import software.amazon.awscdk.App
import software.amazon.awscdk.Environment
import software.amazon.awscdk.StackProps

internal class AppConfigCiCdStackTest {

    @Test
    internal fun testStack() {
        val app = App()

        val stack = AppConfigCiCdStack(app, "test", StackProps.builder()
                .env(Environment.builder()
                        .region("eu-west-1")
                        .build())
                .tags(mapOf(Pair("environment", "Test")))
                .build())

        val actual: JsonNode = ObjectMapper().valueToTree(
            app.synth().getStackArtifact(stack.artifactId).template
        )

        Assertions.assertThat(actual.toString())
            .contains("AWS::AppConfig::Application")
    }
}