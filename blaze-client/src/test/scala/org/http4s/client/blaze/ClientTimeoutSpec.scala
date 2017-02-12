package org.http4s
package client
package blaze

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

import org.http4s.blaze.{SeqTestHead, SlowTestHead}
import org.http4s.blaze.pipeline.HeadStage
import scodec.bits.ByteVector

import scala.concurrent.TimeoutException
import scala.concurrent.duration._
import fs2._
import fs2.Task._

class ClientTimeoutSpec extends Http4sSpec {

  val ec = scala.concurrent.ExecutionContext.global
  val es = impl.DefaultExecutor.newClientDefaultExecutorService("Here")
  implicit val strategy = Strategy.fromExecutor(es)

  val www_foo_com = Uri.uri("http://www.foo.com")
  val FooRequest = Request(uri = www_foo_com)
  val FooRequestKey = RequestKey.fromRequest(FooRequest)
  val resp = "HTTP/1.1 200 OK\r\nContent-Length: 4\r\n\r\ndone"

  // The executor in here needs to be shut down manually because the `BlazeClient` class won't do it for us
  private val defaultConfig = BlazeClientConfig.defaultConfig

  private def mkConnection() = new Http1Connection(FooRequestKey, defaultConfig, es, ec)

  private def mkBuffer(s: String): ByteBuffer =
    ByteBuffer.wrap(s.getBytes(StandardCharsets.ISO_8859_1))
  
  private def mkClient(head: => HeadStage[ByteBuffer], tail: => BlazeConnection)
              (idleTimeout: Duration, requestTimeout: Duration): Client = {
    val manager = MockClientBuilder.manager(head, tail)
    BlazeClient(manager, defaultConfig.copy(idleTimeout = idleTimeout, requestTimeout = requestTimeout), Task.now(()))
  }

  "Http1ClientStage responses" should {
    "Timeout immediately with an idle timeout of 0 seconds" in {
      val c = mkClient(new SlowTestHead(List(mkBuffer(resp)), 0.seconds),
                       mkConnection())(0.milli, Duration.Inf)

      c.fetchAs[String](FooRequest).unsafeRun() must throwA[TimeoutException]
    }

    "Timeout immediately with a request timeout of 0 seconds" in {
      val tail = mkConnection()
      val h = new SlowTestHead(List(mkBuffer(resp)), 0.seconds)
      val c = mkClient(h, tail)(Duration.Inf, 0.milli)

      c.fetchAs[String](FooRequest).unsafeRun() must throwA[TimeoutException]
    }

    "Idle timeout on slow response" in {
      val tail = mkConnection()
      val h = new SlowTestHead(List(mkBuffer(resp)), 10.seconds)
      val c = mkClient(h, tail)(1.second, Duration.Inf)

      c.fetchAs[String](FooRequest).unsafeRun() must throwA[TimeoutException]
    }

    "Request timeout on slow response" in {
      val tail = mkConnection()
      val h = new SlowTestHead(List(mkBuffer(resp)), 10.seconds)
      val c = mkClient(h, tail)(Duration.Inf, 1.second)

      c.fetchAs[String](FooRequest).unsafeRun() must throwA[TimeoutException]
    }

    "Request timeout on slow POST body" in {

      def dataStream(n: Int): EntityBody = {
        implicit val defaultScheduler = Scheduler.fromFixedDaemonPool(2)
        val interval = 1000.millis
        time.awakeEvery(interval)
          .map(_ => "1".toByte)
          .take(n.toLong)
      }

      val req = Request(method = Method.POST, uri = www_foo_com, body = dataStream(4))

      val tail = new Http1Connection(RequestKey.fromRequest(req), defaultConfig, es, ec)
      val (f,b) = resp.splitAt(resp.length - 1)
      val h = new SeqTestHead(Seq(f,b).map(mkBuffer))
      val c = mkClient(h, tail)(Duration.Inf, 1.second)

      c.fetchAs[String](req).unsafeRun() must throwA[TimeoutException]
    }

    "Idle timeout on slow POST body" in {

      def dataStream(n: Int): EntityBody = {
        implicit val defaultScheduler = Scheduler.fromFixedDaemonPool(2)
        val interval = 2.seconds
        time.awakeEvery(interval)
          .map(_ => "1".toByte)
          .take(n.toLong)
      }

      val req = Request(method = Method.POST, uri = www_foo_com, body = dataStream(4))

      val tail = new Http1Connection(RequestKey.fromRequest(req), defaultConfig, es, ec)
      val (f,b) = resp.splitAt(resp.length - 1)
      val h = new SeqTestHead(Seq(f,b).map(mkBuffer))
      val c = mkClient(h, tail)(1.second, Duration.Inf)

      c.fetchAs[String](req).unsafeRun() must throwA[TimeoutException]
    }

    "Not timeout on only marginally slow POST body" in {

      def dataStream(n: Int): EntityBody = {
        implicit val defaultSecheduler = Scheduler.fromFixedDaemonPool(2)
        val interval = 100.millis
        time.awakeEvery(interval)
          .map(_ => "1".toByte)
          .take(n.toLong)
      }

      val req = Request(method = Method.POST, uri = www_foo_com, body = dataStream(4))

      val tail = new Http1Connection(RequestKey.fromRequest(req), defaultConfig, es, ec)
      val (f,b) = resp.splitAt(resp.length - 1)
      val h = new SeqTestHead(Seq(f,b).map(mkBuffer))
      val c = mkClient(h, tail)(10.second, 30.seconds)

      c.fetchAs[String](req).unsafeRun() must_== ("done")
    }

    "Request timeout on slow response body" in {
      val tail = mkConnection()
      val (f,b) = resp.splitAt(resp.length - 1)
      val h = new SlowTestHead(Seq(f,b).map(mkBuffer), 1500.millis)
      val c = mkClient(h, tail)(Duration.Inf, 1.second)

      val result = tail.runRequest(FooRequest).as[String]

      c.fetchAs[String](FooRequest).unsafeRun must throwA[TimeoutException]
    }

    "Idle timeout on slow response body" in {
      val tail = mkConnection()
      val (f,b) = resp.splitAt(resp.length - 1)
      val h = new SlowTestHead(Seq(f,b).map(mkBuffer), 1500.millis)
      val c = mkClient(h, tail)(1.second, Duration.Inf)

      val result = tail.runRequest(FooRequest).as[String]

      c.fetchAs[String](FooRequest).unsafeRun must throwA[TimeoutException]
    }
  }
}
