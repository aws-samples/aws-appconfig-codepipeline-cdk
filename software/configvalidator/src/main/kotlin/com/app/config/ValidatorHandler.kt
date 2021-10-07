package com.app.config

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.util.*

class ValidatorHandler: RequestHandler<Map<String, String>, String> {
    companion object{
        val mapper = ObjectMapper(YAMLFactory())

        init {
            mapper.registerKotlinModule()
        }

    }

    override fun handleRequest(input: Map<String, String>, context: Context?): String {
        println(input)

        val decodedBytes = Base64.getDecoder().decode(input["content"])

        val node = mapper.readValue<JsonNode>(decodedBytes)

        if(node.has("application.logging").not()) {
            throw IllegalArgumentException("Missing application.logging")
        }

        if (node.get("application.logging").has("level").not()){
            throw IllegalArgumentException("Missing level config")
        }

        if((node.get("application.logging").get("level").asText() in  setOf("INFO", "TRACE", "DEBUG")).not()) {
            throw IllegalArgumentException("Allowed values are INFO, TRACE or DEBUG")
        }

        return "Success"
    }
}