package unisockets.netty

import org.scalatest.FunSpec

import dispatch._
import dispatch.Defaults._

import com.ning.http.client.providers.netty.{ NettyAsyncHttpProviderConfig }
import com.ning.http.client.ProxyServer

import org.jboss.netty.logging.{ InternalLoggerFactory, Log4JLoggerFactory }
import scala.concurrent.Await
import scala.concurrent.duration._

class ClientSocketChannelFactorySpec extends FunSpec {
  describe("ClientUdsSocketChannelFactory") {
    it ("should work") {
      // for debug logging...
      //InternalLoggerFactory.setDefaultFactory(new Log4JLoggerFactory)
      val sockets = new ClientUdsSocketChannelFactory()
      val http = new Http().configure(_.setAsyncHttpClientProviderConfig(
        new NettyAsyncHttpProviderConfig().addProperty(
          NettyAsyncHttpProviderConfig.SOCKET_CHANNEL_FACTORY,
          sockets
        )
      ))
      val docker = (Req(identity)
             .setVirtualHost("unix:///var/run/docker.sock")
             .setProxyServer(new ProxyServer("unix:///var/run/docker.sock", 80)))

      val images = http(docker / "images" / "json" > as.String)
      println(Await.result(images, 3.seconds))

      val create = http(docker.POST / "containers" / "create" << """{"Image":"redis:latest"}""" > as.String)
      println(Await.result(create, 3.seconds))

      val containers = http(docker / "containers" / "json" > as.String)
      println(Await.result(containers, 3.seconds))
      // ahc won't call this on close - https://github.com/AsyncHttpClient/async-http-client/blob/async-http-client-1.8.14/src/main/java/com/ning/http/client/providers/netty/NettyAsyncHttpProvider.java#L912
      sockets.releaseExternalResources()
      http.shutdown()
    }
  }
}
