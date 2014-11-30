package unisockets

import org.jboss.netty.channel.socket.nio.{
  ClientUdsSocketChannelFactory => Factory
}

package object netty {
  val ClientUdsSocketChannelFactory = Factory
  type ClientUdsSocketChannelFactory = Factory
}
