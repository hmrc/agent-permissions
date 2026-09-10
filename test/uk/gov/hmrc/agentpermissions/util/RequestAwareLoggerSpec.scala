/*
 * Copyright 2026 HM Revenue & Customs
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

package uk.gov.hmrc.agentpermissions.util

import ch.qos.logback.classic.Level
import org.scalatest.LoneElement.convertToCollectionLoneElementWrapper
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.Logger
import play.api.http.HeaderNames
import play.api.mvc.{Request, RequestHeader}
import play.api.test.FakeRequest
import uk.gov.hmrc.play.bootstrap.tools.LogCapturing

class RequestAwareLoggerSpec extends AnyWordSpecLike with Matchers with LogCapturing:

  private val requestAwareLogger = new RequestAwareLogger(Logger("request.aware.logging.spec"))

  "RequestAwareLogger" when:
    "logging at info level" should:
      "log with request context when headers are present" in:
        testLoggerDefault(Level.INFO, requestAwareLogger.info)

      "log with request context when headers are not present" in:
        testLoggerNoHeaders(Level.INFO, requestAwareLogger.info)

      "log with request context and exceptions" in:
        testLoggerWithException(Level.INFO, requestAwareLogger.info)

      "log with no request context" in:
        testLoggerWithNoRequest(Level.INFO, requestAwareLogger.info)

    "logging at warn level" should:
      "log with request context when headers are present" in:
        testLoggerDefault(Level.WARN, requestAwareLogger.warn)

      "log with request context when headers are not present" in:
        testLoggerNoHeaders(Level.WARN, requestAwareLogger.warn)

      "log with request context and exceptions" in:
        testLoggerWithException(Level.WARN, requestAwareLogger.warn)

      "log with no request context" in:
        testLoggerWithNoRequest(Level.WARN, requestAwareLogger.warn)

    "logging at error level" should:
      "log with request context when headers are present" in:
        testLoggerDefault(Level.ERROR, requestAwareLogger.error)

      "log with request context when headers are not present" in:
        testLoggerNoHeaders(Level.ERROR, requestAwareLogger.error)

      "log with request context and exceptions" in:
        testLoggerWithException(Level.ERROR, requestAwareLogger.error)

      "log with no request context" in:
        testLoggerWithNoRequest(Level.ERROR, requestAwareLogger.error)

    "logging at debug level" should:
      "log with request context when headers are present" in:
        testLoggerDefault(Level.DEBUG, requestAwareLogger.debug)

      "log with request context when headers are not present" in:
        testLoggerNoHeaders(Level.DEBUG, requestAwareLogger.debug)

      "log with request context and exceptions" in:
        testLoggerWithException(Level.DEBUG, requestAwareLogger.debug)

      "log with no request context" in:
        testLoggerWithNoRequest(Level.DEBUG, requestAwareLogger.debug)

  private inline def testLoggerDefault(
    level: Level,
    doLog: RequestHeader ?=> String => Unit
  ): Unit =
    withCaptureOfLoggingFrom(requestAwareLogger): events =>
      given Request[?] = FakeRequest("GET", "/agent-overseas-application/test")
        .withHeaders(
          HeaderNames.USER_AGENT -> "test-agent",
          HeaderNames.REFERER    -> "https://example.com/ref"
        )

      doLog("lookup complete")

      val log = events.loneElement
      log.getLevel shouldBe level

      val msg = log.getFormattedMessage

      msg should startWith("lookup complete")
      msg should include("[Context: GET /agent-overseas-application/test]")
      msg should include("[UserAgent: test-agent]")
      msg should include("[Referer: https://example.com/ref]")
      msg should include("[SessionId: ]")
      msg should include("[RequestId: ]")
      msg should include("[DeviceId: ]")

  private inline def testLoggerNoHeaders(
    level: Level,
    doLog: RequestHeader ?=> String => Unit
  ): Unit =
    withCaptureOfLoggingFrom(requestAwareLogger): events =>
      given Request[?] = FakeRequest("GET", "/agent-overseas-application/test")

      doLog("lookup complete")

      val log = events.loneElement
      log.getLevel shouldBe level

      val msg = log.getFormattedMessage

      msg should startWith("lookup complete")
      msg should include("[Context: GET /agent-overseas-application/test]")
      msg should include("[UserAgent: ]")
      msg should include("[Referer: ]")
      msg should include("[SessionId: ]")
      msg should include("[RequestId: ]")
      msg should include("[DeviceId: ]")

  private inline def testLoggerWithException(
    level: Level,
    doLog: RequestHeader ?=> (
      String,
      Throwable
    ) => Unit
  ): Unit =
    withCaptureOfLoggingFrom(requestAwareLogger): events =>
      given Request[?] = FakeRequest("GET", "/agent-overseas-application/test")
      val ex = new RuntimeException("boom")

      doLog("error message", ex)

      val log = events.loneElement
      log.getLevel shouldBe level

      val msg = log.getFormattedMessage

      msg should startWith("error message")
      msg should include("[Context: GET /agent-overseas-application/test]")

      val throwable = log.getThrowableProxy
      throwable should not be null
      throwable.getClassName shouldBe classOf[RuntimeException].getName
      throwable.getMessage shouldBe "boom"

  private inline def testLoggerWithNoRequest(
    level: Level,
    doLog: RequestHeader ?=> String => Unit
  ): Unit =
    withCaptureOfLoggingFrom(requestAwareLogger): events =>
      given Request[?] = NoRequest

      doLog("lookup complete")

      val log = events.loneElement
      log.getLevel shouldBe level

      val msg = log.getFormattedMessage

      msg should startWith("lookup complete")
      msg should not include "[Context: GET /agent-overseas-application/test]"
      msg should not include "[UserAgent: test-agent]"
      msg should not include "[Referer: https://example.com/ref]"
      msg should not include "[SessionId: ]"
      msg should not include "[RequestId: ]"
      msg should not include "[DeviceId: ]"
