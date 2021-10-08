package com.app.config

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class DemoHandler : RequestHandler<APIGatewayProxyRequestEvent, APIGatewayV2HTTPResponse> {

    companion object {
        private val configPath = System.getenv("APP_CONFIG_PATH").split('/')
        val mapper = jacksonObjectMapper()
        val application = configPath [0]
        val environment = configPath [1]
        val configuration = configPath [2]
    }

    override fun handleRequest(input: APIGatewayProxyRequestEvent, context: Context?): APIGatewayV2HTTPResponse {
        val showAppConfigResults = input.queryStringParameters["appConfig"].toBoolean()

        val headers = mapOf(Pair("Content-Type", "application/json"))

        if(showAppConfigResults) {
            val client = HttpClient.newBuilder().build()

            val request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:2772/applications/${application}/environments/${environment}/configurations/${configuration}"))
                    .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())

            return APIGatewayV2HTTPResponse.builder()
                    .withStatusCode(200)
                    .withHeaders(headers)
                    .withIsBase64Encoded(false)
                    .withBody(response.body())
                    .build()
        }


        return APIGatewayV2HTTPResponse.builder()
                .withStatusCode(200)
                .withHeaders(headers)
                .withIsBase64Encoded(false)
                .withBody(mapper.writeValueAsString(input))
                .build()
    }
}