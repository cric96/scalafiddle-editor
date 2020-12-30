package scalafiddle.compiler

/*
 * Copyright (C) 2020 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.http.scaladsl
import java.io.PrintWriter
import java.util.UUID

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import akka.stream.javadsl.Sink

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.io.{Codec, Source}

object Service {
  val Javascript = path("js" / """\d+""".r)
  val Scala      = path("compilation" / """\d+""".r)
  def main(args: Array[String]): Unit = {
    implicit val system              = ActorSystem()
    implicit val materializer        = ActorMaterializer()
    implicit val context             = system.dispatcher
    val page                         = Source.fromFile("index.html").mkString
    val code                         = Source.fromFile("index.js")(Codec("UTF-8")).mkString
    val webpack                      = code.split("""Object.freeze""")(0)
    var codeMap: Map[String, String] = Map("/js/index.js" -> code)
    val serverSource                 = Http().bind(interface = "localhost", port = 8080)
    val requestHandler: HttpRequest => HttpResponse = {
      case HttpRequest(GET, Uri.Path("/"), _, _, _) =>
        HttpResponse(entity = HttpEntity(ContentTypes.`text/html(UTF-8)`, page))

      case HttpRequest(POST, Uri.Path("/code"), _, entity, _) =>
        val result = Unmarshal(entity)
          .to[String]
          .map(ScafiCompiler.compile)
          .flatMap(Future.fromTry)
          .map(code => {
            val id = UUID.randomUUID().toString
            codeMap += (id -> (webpack + code))
            new PrintWriter(id) { write(codeMap(id)); close }
            id
          })
          .map(id => HttpResponse(entity = id))
        Await.result(result, Duration.Inf)

      case HttpRequest(GET, scala, _, _, _) if scala.toString().contains("compilation") =>
        val id = scala.path.reverse.head.toString
        //TODO Check if the page is present
        HttpResponse(entity = HttpEntity(ContentTypes.`text/html(UTF-8)`, ScalaCompiledPage.html(id)))

      case HttpRequest(GET, javascript, _, _, _) if javascript.toString().contains("js") =>
        val id = javascript.path.reverse.head.toString
        HttpResponse(entity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, codeMap(id)))

      case r: HttpRequest =>
        r.discardEntityBytes() // important to drain incoming HTTP Entity stream
        HttpResponse(404, entity = "Unknown resource!")
    }

    val bindingFuture: Future[Http.ServerBinding] =
      serverSource
        .to(Sink.foreach { connection =>
          println("Accepted new connection from " + connection.remoteAddress)

          connection handleWithSyncHandler requestHandler
        })
        .run()
    println(bindingFuture.isCompleted)
  }
}
