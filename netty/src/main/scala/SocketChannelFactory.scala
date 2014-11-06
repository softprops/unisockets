package unisockets.netty

import org.jboss.netty.channel.{ Channels, ChannelException, ChannelPipeline, ChannelSink }
import org.jboss.netty.channel.socket.SocketChannel
import org.jboss.netty.channel.socket.nio.{ NioClientSocketPipelineSink, NioSocketChannel, NioClientSocketChannelFactory, NioWorker, WorkerPool }
import java.nio.channels.{ SocketChannel => JSocketChannel }
import scala.util.control.NonFatal
import unisockets.{ SocketChannel => UniSocketChannel }

// https://github.com/netty/netty/blob/netty-3.9.5.Final/src/main/java/org/jboss/netty/channel/socket/nio/NioClientSocketChannel.java

class ClientSocketChannelFactory extends NioClientSocketChannelFactory {

  private def expose[T](name: String): T = {
    val fld = getClass.getDeclaredField(name)
    fld.setAccessible(true)
    fld.get(this).asInstanceOf[T]
  }

  private[this] lazy val ourWorkerPool =
    expose[WorkerPool[NioWorker]]("workerPool")

  private[this] lazy val ourSink =
    expose[ChannelSink]("sink")

  protected def openChannel: JSocketChannel = {
    try {
      val chan = UniSocketChannel.open()
      chan.configureBlocking(false)
      chan
    } catch {
      case NonFatal(e) =>
        throw new ChannelException("failed to open channel", e)
    }
  }

  override def newChannel(pl: ChannelPipeline): SocketChannel = {
    new NioSocketChannel(null, this, pl, ourSink, null, ourWorkerPool.nextWorker) {
      Channels.fireChannelOpen(this)
    }
    // new NioClientSocketChannel(this, pipeline, sink, workerPool.nextWorker())
  }
}
