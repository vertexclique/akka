/*
 * Copyright (C) 2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.actor.testkit.typed.internal

import java.util.function.Supplier

import scala.concurrent.duration.Duration
import scala.reflect.ClassTag
import scala.util.matching.Regex

import akka.actor.testkit.typed.LoggingEvent
import akka.actor.testkit.typed.TestKitSettings
import akka.actor.testkit.typed.javadsl
import akka.actor.testkit.typed.scaladsl
import akka.actor.typed.ActorSystem
import akka.annotation.InternalApi
import akka.testkit.TestKit
import org.slf4j.event.Level

/**
 * INTERNAL API
 */
@InternalApi private[akka] object LoggingEventFilterImpl {
  def empty: LoggingEventFilterImpl = new LoggingEventFilterImpl(1, None, None, None, None, None, None, Map.empty, None)
}

/**
 * INTERNAL API
 */
@InternalApi private[akka] final case class LoggingEventFilterImpl(
    occurrences: Int,
    logLevel: Option[Level],
    loggerName: Option[String],
    source: Option[String],
    messageContains: Option[String],
    messageRegex: Option[Regex],
    cause: Option[Class[_ <: Throwable]],
    mdc: Map[String, String],
    custom: Option[Function[LoggingEvent, Boolean]])
    extends javadsl.LoggingEventFilter
    with scaladsl.LoggingEventFilter {

  @volatile // JMM does not guarantee visibility for non-final fields
  private var todo = occurrences

  def matches(event: LoggingEvent): Boolean = {
    logLevel.forall(_ == event.level) &&
    source.forall(_ == sourceOrEmpty(event)) &&
    messageContains.forall(messageOrEmpty(event).contains) &&
    messageRegex.forall(_.findFirstIn(messageOrEmpty(event)).isDefined) &&
    cause.forall(c => event.throwable.isDefined && c.isInstance(event.throwable.get)) &&
    mdc.forall { case (key, value) => event.mdc.contains(key) && event.mdc(key) == value } &&
    custom.forall(f => f(event))

    // loggerName is handled when installing the filter, in `intercept`
  }

  private def messageOrEmpty(event: LoggingEvent): String =
    if (event.message == null) "" else event.message

  private def sourceOrEmpty(event: LoggingEvent): String =
    event.mdc.getOrElse("akkaSource", "")

  def apply(event: LoggingEvent): Boolean = {
    if (matches(event)) {
      if (todo != Int.MaxValue) todo -= 1
      true
    } else false
  }

  private def awaitDone(max: Duration): Boolean = {
    if (todo != Int.MaxValue && todo > 0) TestKit.awaitCond(todo <= 0, max, noThrow = true)
    todo == Int.MaxValue || todo == 0
  }

  override def intercept[T](code: => T)(implicit system: ActorSystem[_]): T = {
    val effectiveLoggerName = loggerName.getOrElse("")
    checkLogback(system)
    TestAppender.setupTestAppender(effectiveLoggerName)
    TestAppender.addFilter(effectiveLoggerName, this)
    val leeway = TestKitSettings(system).FilterLeeway
    try {
      val result = code
      if (!awaitDone(leeway))
        if (todo > 0)
          throw new AssertionError(s"Timeout ($leeway) waiting for $todo messages on $this.")
        else
          throw new AssertionError(s"Received ${-todo} excess messages on $this.")
      result
    } finally {
      todo = occurrences
      TestAppender.removeFilter(effectiveLoggerName, this)
    }
  }

  private def checkLogback(system: ActorSystem[_]): Unit = {
    if (!system.dynamicAccess.classIsOnClasspath("ch.qos.logback.classic.spi.ILoggingEvent")) {
      throw new IllegalStateException("LoggingEventFilter requires logback-classic dependency in classpath.")
    }
  }

  override def withOccurrences(newOccurrences: Int): LoggingEventFilterImpl =
    copy(occurrences = newOccurrences)

  override def withLogLevel(newLogLevel: Level): LoggingEventFilterImpl =
    copy(logLevel = Option(newLogLevel))

  def withLoggerName(newLoggerName: String): LoggingEventFilterImpl =
    copy(loggerName = Some(newLoggerName))

  override def withSource(newSource: String): LoggingEventFilterImpl =
    copy(source = Option(newSource))

  override def withMessageContains(newMessageContains: String): LoggingEventFilterImpl =
    copy(messageContains = Option(newMessageContains))

  def withMessageRegex(newMessageRegex: String): LoggingEventFilterImpl =
    copy(messageRegex = Option(new Regex(newMessageRegex)))

  override def withCause[A <: Throwable: ClassTag]: LoggingEventFilterImpl = {
    val causeClass = implicitly[ClassTag[A]].runtimeClass.asInstanceOf[Class[Throwable]]
    copy(cause = Option(causeClass))
  }

  override def withMdc(newMdc: Map[String, String]): LoggingEventFilterImpl =
    copy(mdc = newMdc)

  override def withMdc(newMdc: java.util.Map[String, String]): javadsl.LoggingEventFilter = {
    import akka.util.ccompat.JavaConverters._
    withMdc(newMdc.asScala.toMap)
  }

  override def withCustom(newCustom: Function[LoggingEvent, Boolean]): LoggingEventFilterImpl =
    copy(custom = Option(newCustom))

  override def withCause(newCause: Class[_ <: Throwable]): javadsl.LoggingEventFilter =
    copy(cause = Option(newCause))

  override def intercept[T](system: ActorSystem[_], code: Supplier[T]): T =
    intercept(code.get())(system)

}
