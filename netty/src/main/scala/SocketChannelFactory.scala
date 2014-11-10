//package org.jboss.netty.channel.socket.nio//unisockets.netty
//package unisockets.netty

// package exists here so we can access some members of netty's socket.nio package as well
package org.jboss.netty.channel.socket.nio
import org.jboss.netty.channel.{
  Channel, Channels, ChannelException, ChannelFuture, ChannelFutureListener, ChannelPipeline, ChannelSink,
  ChannelState, ChannelEvent, MessageEvent, ChannelStateEvent
}
import org.jboss.netty.channel.socket.SocketChannel
//import org.jboss.netty.channel.socket.nio.{ AbstractNioChannelSink, NioClientBossPool, NioSocketChannel, NioClientSocketChannelFactory, NioWorker, WorkerPool, SelectorUtil, NioWorkerPool }
import org.jboss.netty.util.{ HashedWheelTimer, ThreadRenamingRunnable, ThreadNameDeterminer }
import java.net.SocketAddress
import java.nio.channels.{ ClosedChannelException, SelectionKey, Selector, SocketChannel => JSocketChannel }
import java.util.concurrent.{ Executor, Executors, TimeUnit }
import scala.util.control.NonFatal
import scala.collection.JavaConverters._
import unisockets.{ SocketChannel => UniSocketChannel }

// https://github.com/netty/netty/blob/netty-3.9.5.Final/src/main/java/org/jboss/netty/channel/socket/nio/NioClientSocketChannel.java

class ClientSocketChannelFactory
 (bossExec: Executor = Executors.newCachedThreadPool,
  workerExec: Executor = Executors.newCachedThreadPool)
  extends NioClientSocketChannelFactory {

  private val DefaultIOThreads = Runtime.getRuntime().availableProcessors() * 2

  private[this] lazy val ourWorkerPool: NioWorkerPool =
    new NioWorkerPool(workerExec, DefaultIOThreads)

  private[this] lazy val bosses = {
    val timer = new HashedWheelTimer()
    class UDSBoss extends AbstractNioSelector(bossExec, null) with Boss {
      override protected def newThreadRenamingRunnable(id: Int, determiner: ThreadNameDeterminer):ThreadRenamingRunnable =
        new ThreadRenamingRunnable(this, "New I/O boss #" + id, determiner)

      override protected def createRegisterTask(chan: Channel, future: ChannelFuture): Runnable = new Runnable {
        def run() {
          val channel = chan.asInstanceOf[NioSocketChannel]
          val timeout = channel.getConfig().getConnectTimeoutMillis()
          if (timeout > 0) {
            if (!channel.isConnected()) {
              //channel.timoutTimer = timer.newTimeout(wakeupTask,
              //              timeout, TimeUnit.MILLISECONDS)
            }
          }
          try {
            //channel.channel.register(
            //  boss.selector, SelectionKey.OP_CONNECT, channel)
          } catch {
            case e:ClosedChannelException =>
              channel.getWorker.close(channel, Channels.succeededFuture(channel))
          }

          val connectTimeout = channel.getConfig().getConnectTimeoutMillis()
          if (connectTimeout > 0) {
            //channel.connectDeadlineNanos = System.nanoTime() + connectTimeout * 1000000L
          }
        }
      }

      override protected def process(selector: Selector) {
        processSelectedKeys(selector.selectedKeys())
        processConnectTimeout(selector.keys(), System.nanoTime())
      }

      override protected def close(key: SelectionKey) = key.attachment() match {
        case chan: NioSocketChannel =>
          chan.getWorker.close(chan, Channels.succeededFuture(chan))
      }

      private def processConnectTimeout(keys: java.util.Set[SelectionKey], currentTimeNanos: Long) =
        for (key <- keys.asScala) {
          if (key.isValid) key.attachment() match {
            case ch: NioSocketChannel =>
              
          }
        }

      private def processSelectedKeys(selectedKeys: java.util.Set[SelectionKey]) =
        selectedKeys match {
          case nonEmpty if !nonEmpty.isEmpty =>
            for (key <- nonEmpty.asScala) {
              if (!key.isValid) close(key)
              else try {
                if (key.isConnectable) key.attachment() match {
                  case chan: NioSocketChannel =>
                    if (chan.channel.finishConnect) {
                      key.cancel()
                      // todo: connectFuture
                      //chan.getWorker.register(chan, chan.connectFuture)
                    }
                }
              } catch {
                case NonFatal(e) =>
                  key.attachment() match {
                    case chan: NioSocketChannel =>
                      // todo: add connectFuture
                      //chan.connectFuture.setFailure(t)
                      Channels.fireExceptionCaught(chan, e)
                      key.cancel() // Some JDK implementations run into an infinite loop without this.
                      chan.getWorker.close(chan, Channels.succeededFuture(chan))
                  }
              }
            }
          case _ =>
        }
    }
  }

  // NioClientBoss is final and explicitly casts channel
  private[this] lazy val ourBossPool: NioClientBossPool =  {
    val timer = new HashedWheelTimer()
    new NioClientBossPool(bossExec, 1, timer, null) {
      override def newBoss(executor: Executor): NioClientBoss =
        new NioClientBoss(executor, timer, null/*thread name determiner*/) /*{
          */
    }
  }

  private[this] lazy val ourSink: ChannelSink = new AbstractNioChannelSink {
    override def eventSunk(pipeline: ChannelPipeline, e: ChannelEvent) = e match {
      case cse: ChannelStateEvent =>
        val chan = cse.getChannel.asInstanceOf[NioSocketChannel]
        val future = cse.getFuture
        val state = cse.getState
        val value = cse.getValue
        state match {
          case ChannelState.OPEN =>
            if (java.lang.Boolean.FALSE == value) {
              chan.worker.close(chan, future)
            }
          case ChannelState.BOUND =>
            Option(value) match {
              case Some(addr) =>                
                // todo: server sockets
                //bind(chan, future, addr.asInstanceOf[SocketAddress])
              case _ =>
                chan.getWorker.close(chan, future)
            }
          case ChannelState.CONNECTED =>
            println(s"connected $value")
            Option(value) match {
              case Some(addr) =>
                connect(chan, future, addr.asInstanceOf[SocketAddress])
              case _ =>
                chan.getWorker.close(chan, future)
            }
          case ChannelState.INTEREST_OPS =>
             chan.getWorker.setInterestOps(chan, future, value.asInstanceOf[java.lang.Integer])
        }
      case me: MessageEvent =>
        val chan = me.getChannel.asInstanceOf[NioSocketChannel]
        val offered = chan.writeBufferQueue.offer(me)
        chan.getWorker.writeFromUserCode(chan)       
    }

   private def connect(
     socketChannel: NioSocketChannel, future: ChannelFuture, addr: SocketAddress) {
     if (socketChannel.channel.connect(addr)) socketChannel.getWorker.register(socketChannel, future)
     else {
       socketChannel.getCloseFuture().addListener(new ChannelFutureListener {
         def operationComplete(f: ChannelFuture) {
           if (!future.isDone) future.setFailure(new ClosedChannelException)
         }
       })
     }
     future.addListener(ChannelFutureListener.CLOSE_ON_FAILURE)
     //socketChannel.connectFuture = future
     ourBossPool.nextBoss().register(socketChannel, future)
   }
  }

 // private[this] lazy val ourWorkerPool =
 //   expose[WorkerPool[NioWorker]]("workerPool")

  // can not use NioClientSocketPipelineSink as it will blindly cast NioSocketChannel to NioClientSocketChannel
 // private[this] lazy val ourSink =
 //   expose[ChannelSink]("sink")

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

  override def newChannel(pipeline: ChannelPipeline): SocketChannel = {
    println(s"making a new channel for pipeline $pipeline")
    new NioSocketChannel(null, this, pipeline, ourSink, openChannel, ourWorkerPool.nextWorker) {
      Channels.fireChannelOpen(this)
    }
    //new NioClientSocketChannel(this, pipeline, ourSink, ourWorkerPool.nextWorker())
  }

  // todo shutdown/release sequence

  private def expose[T](name: String): T = {
    val fld = classOf[NioClientSocketChannelFactory].getDeclaredField(name)
    fld.setAccessible(true)
    fld.get(this).asInstanceOf[T]
  }
}
