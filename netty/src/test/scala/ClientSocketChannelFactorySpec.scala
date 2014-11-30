package unisockets.netty

import org.scalatest.FunSpec

import dispatch._
import dispatch.Defaults._

import com.ning.http.client.providers.netty.{ NettyAsyncHttpProviderConfig }
import com.ning.http.client.ProxyServer

//import org.jboss.netty.channel.socket.nio.ClientUdsSocketChannelFactory
import org.jboss.netty.logging.{ InternalLoggerFactory, Log4JLoggerFactory }
import scala.concurrent.Await
import scala.concurrent.duration._

class ClientSocketChannelFactorySpec extends FunSpec {
  describe("ClientUdsSocketChannelFactory") {
    it ("should work") {
      // for debug logging...
      InternalLoggerFactory.setDefaultFactory(new Log4JLoggerFactory)

      // https://github.com/AsyncHttpClient/async-http-client/blob/async-http-client-1.8.14/src/main/java/com/ning/http/client/providers/netty/NettyAsyncHttpProvider.java#L648

      val http = new Http().configure(_.setAsyncHttpClientProviderConfig(
        new NettyAsyncHttpProviderConfig().addProperty(
          NettyAsyncHttpProviderConfig.SOCKET_CHANNEL_FACTORY,
          new ClientUdsSocketChannelFactory()
        )
      ))

      println(Await.result(
        http((Req(identity)
             .setVirtualHost("unix:///var/run/docker.sock") // set virtual host to pick up socket attr
             .setProxyServer(new ProxyServer("unix:///var/run/docker.sock", 80))) / "images" / "json" > as.String),
        3.seconds))
    }
  }
}
