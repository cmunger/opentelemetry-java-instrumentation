/*
 * Copyright 2020, OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import play.libs.ws.StandaloneWSClient
import play.libs.ws.StandaloneWSRequest
import play.libs.ws.StandaloneWSResponse
import play.libs.ws.ahc.StandaloneAhcWSClient
import scala.collection.JavaConverters
import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import spock.lang.Shared

import java.util.concurrent.TimeUnit

class PlayJavaWSClientTest extends PlayWSClientTestBase {
  @Shared
  StandaloneWSClient wsClient

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers, Closure callback) {
    StandaloneWSRequest wsRequest = wsClient.url(uri.toURL().toString()).setFollowRedirects(true)

    headers.entrySet().each { entry -> wsRequest.addHeader(entry.getKey(), entry.getValue()) }
    StandaloneWSResponse wsResponse = wsRequest.setMethod(method).execute()
      .whenComplete({ response, throwable ->
        callback?.call()
      }).toCompletableFuture().get(5, TimeUnit.SECONDS)

    return wsResponse.getStatus()
  }

  def setupSpec() {
    wsClient = new StandaloneAhcWSClient(asyncHttpClient, materializer)
  }

  def cleanupSpec() {
    wsClient?.close()
  }
}

class PlayJavaStreamedWSClientTest extends PlayWSClientTestBase {
  @Shared
  StandaloneWSClient wsClient

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers, Closure callback) {
    StandaloneWSRequest wsRequest = wsClient.url(uri.toURL().toString()).setFollowRedirects(true)

    headers.entrySet().each { entry -> wsRequest.addHeader(entry.getKey(), entry.getValue()) }
    StandaloneWSResponse wsResponse = wsRequest.setMethod(method).stream()
      .whenComplete({ response, throwable ->
        callback?.call()
      }).toCompletableFuture().get(5, TimeUnit.SECONDS)

    // The status can be ready before the body so explicity call wait for body to be ready
    wsResponse.getBodyAsSource().runFold("", { acc, out -> "" }, materializer)
      .toCompletableFuture().get(5, TimeUnit.SECONDS)
    return wsResponse.getStatus()
  }

  def setupSpec() {
    wsClient = new StandaloneAhcWSClient(asyncHttpClient, materializer)
  }

  def cleanupSpec() {
    wsClient?.close()
  }
}

class PlayScalaWSClientTest extends PlayWSClientTestBase {
  @Shared
  play.api.libs.ws.StandaloneWSClient wsClient

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers, Closure callback) {
    Future<play.api.libs.ws.StandaloneWSResponse> futureResponse = wsClient.url(uri.toURL().toString())
      .withMethod(method)
      .withFollowRedirects(true)
      .withHttpHeaders(JavaConverters.mapAsScalaMap(headers).toSeq())
      .execute()
      .transform({ theTry ->
        callback?.call()
        theTry
      }, ExecutionContext.global())

    play.api.libs.ws.StandaloneWSResponse wsResponse = Await.result(futureResponse, Duration.apply(5, TimeUnit.SECONDS))

    return wsResponse.status()
  }

  def setupSpec() {
    wsClient = new play.api.libs.ws.ahc.StandaloneAhcWSClient(asyncHttpClient, materializer)
  }

  def cleanupSpec() {
    wsClient?.close()
  }
}

class PlayScalaStreamedWSClientTest extends PlayWSClientTestBase {
  @Shared
  play.api.libs.ws.StandaloneWSClient wsClient

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers, Closure callback) {
    Future<play.api.libs.ws.StandaloneWSResponse> futureResponse = wsClient.url(uri.toURL().toString())
      .withMethod(method)
      .withFollowRedirects(true)
      .withHttpHeaders(JavaConverters.mapAsScalaMap(headers).toSeq())
      .stream()
      .transform({ theTry ->
        callback?.call()
        theTry
      }, ExecutionContext.global())

    play.api.libs.ws.StandaloneWSResponse wsResponse = Await.result(futureResponse, Duration.apply(5, TimeUnit.SECONDS))

    // The status can be ready before the body so explicity call wait for body to be ready
    Await.result(
      wsResponse.bodyAsSource().runFold("", { acc, out -> "" }, materializer),
      Duration.apply(5, TimeUnit.SECONDS))
    return wsResponse.status()
  }

  def setupSpec() {
    wsClient = new play.api.libs.ws.ahc.StandaloneAhcWSClient(asyncHttpClient, materializer)
  }

  def cleanupSpec() {
    wsClient?.close()
  }
}
