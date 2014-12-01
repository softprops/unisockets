// package exists here so we can access some package private members of netty's socket.nio package as well
package org.jboss.netty.channel.socket.nio

import org.jboss.netty.channel.{
  Channel, Channels, ChannelException, ChannelFuture, ChannelFutureListener, ChannelPipeline, ChannelSink,
  ChannelState, ChannelEvent, MessageEvent, ChannelStateEvent, ReceiveBufferSizePredictor
}
import org.jboss.netty.channel.socket.SocketChannel
import org.jboss.netty.util.{ ExternalResourceReleasable, HashedWheelTimer, ThreadRenamingRunnable, ThreadNameDeterminer, Timeout, TimerTask }
import org.jboss.netty.logging.InternalLoggerFactory

import java.io.IOException
import java.net.SocketAddress
import java.nio.channels.{ ClosedChannelException, SelectionKey, Selector, SocketChannel => JSocketChannel }
import java.util.{ Set => JSet }
import java.util.concurrent.{ Executor, Executors, TimeUnit }

import jnr.enxio.channels.NativeSelectorProvider
import jnr.unixsocket.UnixSocketChannel

import scala.util.control.NonFatal
import scala.collection.JavaConverters._

import unisockets.{ SocketChannel => UniSocketChannel }

// https://github.com/netty/netty/blob/netty-3.9.5.Final/src/main/java/org/jboss/netty/channel/socket/nio/NioClientSocketChannel.java

object ClientUdsSocketChannelFactory {
  val log = InternalLoggerFactory.getInstance(getClass)
  val DefaultIOThreads = Runtime.getRuntime.availableProcessors * 2
} 

/** An NioClientSocketChannelFactory that reads and reads from SocketChannel backed by a unix domain socket */
class ClientUdsSocketChannelFactory
 (bossExec: Executor   = Executors.newCachedThreadPool,
  workerExec: Executor = Executors.newCachedThreadPool)
  extends NioClientSocketChannelFactory {
  import ClientUdsSocketChannelFactory._

  private[this] lazy val workers: NioWorkerPool =
    new NioWorkerPool(workerExec, DefaultIOThreads) {

      override def releaseExternalResources() {
        log.debug("workers#releaseExternalResources()")
        super.releaseExternalResources()
      }

      override def shutdown() {
        log.debug("workers#shutdown()")
        super.shutdown()
      }

      override protected def createWorker(executor: Executor): NioWorker =
        new NioWorker(executor, null/*threadNameDeterminer*/) {

          private[this] val recvPool = new SocketReceiveBufferAllocator()

          // use uds selector, not the default jdk selector
          selector = NativeSelectorProvider.getInstance().openSelector

          override def run() {
            super.run()
            recvPool.releaseExternalResources()
          }

          override def shutdown() {
            log.debug("worker#shutdown()")
            super.shutdown()
          }

          override def close(k: SelectionKey) = Option(k).foreach { // options because selector.keys was observed to contain a null (pdi)
            _.attachment match {
              case chan: AbstractNioChannel[_] =>
                log.debug("worker#close(k)")
                close(chan, Channels.succeededFuture(chan))
              case other =>
                log.debug(s"worker.close(k) with a non AbstractNioChannel att $other")
            }
          }

          // this method reassigns selector to channels with a value returned from SelectorUtil.open
          // todo: we want maintain a uds selector ( NativeSelectorProvider.getInstance().openSelector )
          override def rebuildSelector() {
            log.debug("worker#rebuildSelector() - rebuilding selector")
            super.rebuildSelector()
          }

          //https://github.com/netty/netty/blob/netty-3.9.5.Final/src/main/java/org/jboss/netty/channel/socket/nio/NioWorker.java#L49
          override protected def read(k: SelectionKey): Boolean = (k.channel, k.attachment) match {
            case (unix: UnixSocketChannel, chan: NioSocketChannel) =>
              val predictor = chan.getConfig.getReceiveBufferSizePredictor
              val size = predictor.nextReceiveBufferSize
              val bufferFactory = chan.getConfig().getBufferFactory
              val buf = recvPool.get(size).order(bufferFactory.getDefaultOrder)
              def readBytes: (Boolean, Int) = try {
                def readAll(readCount: Int = 0): Int =
                  unix.read(buf) match {
                    case count if count > 0 =>
                      if (buf.hasRemaining) {
                        readAll(readCount + count)
                      } else {
                        val fin = readCount + count
                        log.debug(s"worker#read() fin reading $fin bytes")
                        fin
                      }
                    case _ =>
                      log.debug(s"worker#read() fin reading $readCount bytes")
                      readCount
                  }
                (true, readAll())
              } catch {
                case e: ClosedChannelException =>
                  log.error(s"read fail. netty says this doesn't require user attn ${e.getMessage}")
                  (false, 0)
                case e: Throwable =>
                  log.error(s"read fail! ${e.getMessage}")
                  e.printStackTrace
                  Channels.fireExceptionCaught(chan, e)
                  (false, 0)
              }
              val (success, bytes) = readBytes
              if (bytes > 0) {
                buf.flip()
                val channelBuf = bufferFactory.getBuffer(bytes)
                channelBuf.setBytes(0, buf)
                channelBuf.writerIndex(bytes)
                predictor.previousReceiveBufferSize(bytes)
                log.debug("worker#read() message rec!")
                Channels.fireMessageReceived(chan, channelBuf)
              }
          
              if (bytes < 0 || !success) {
                log.debug(s"worker#read() failed bytes read $bytes")
                k.cancel()
                close(chan, Channels.succeededFuture(chan))
                false
              } else true

            case (chan, att) =>
              log.error("worker#read() unexpected chan $chan and attachment $att")
              false
          }

          /** register the channel */
          override def createRegisterTask(channel: Channel, future: ChannelFuture): Runnable = {
            channel match {
              case chan: UdsNioSocketChannel =>
                log.debug(s"worker: createRegisterTask(local ${chan.getLocalAddress} remote ${chan.getRemoteAddress})")
                new Runnable {
                  def run() = try {
                    // https://github.com/netty/netty/blob/netty-3.9.5.Final/src/main/java/org/jboss/netty/channel/socket/nio/NioWorker.java#L151-L157
                    log.debug(
                      s"worker: ${chan.channel} registering selector ${selector} with att $chan")
                    log.debug(s"selector selected keys ${selector.selectedKeys} keys ${selector.keys}")
                    chan.channel match {
                      case unix: unisockets.SocketChannel =>
                        log.debug(s"worker: it's unix")
                        unix.chan.register(
                          selector, chan.getRawInterestOps(), chan)
                      case other =>
                        log.error(s"worker: it's not unix")
                        other.register(
                          selector, chan.getRawInterestOps(), chan)
                    }
                    
                    log.debug("worker: registered!")
                    if (future != null) {
                      log.debug("set connected")
                      chan.setConnected()
                      future.setSuccess()
                    }
                    Channels.fireChannelConnected(channel, channel.getRemoteAddress)

                  } catch {
                    case e: Throwable =>
                      log.error(s"worker: reg failed with ${e}")
                      future.setFailure(e)
                      close(chan, Channels.succeededFuture(chan))
                  }
                }
              case _ =>
                log.error("worker: createRegisterTask(...) didn't rec an niosocketchan!")
                super.createRegisterTask(channel, future)
            }
          }
        }
    }

  private[this] lazy val bosses = {
    val timer = new HashedWheelTimer()

    /** see https://github.com/netty/netty/blob/netty-3.9.5.Final/src/main/java/org/jboss/netty/channel/socket/nio/NioClientBoss.java */
    class UdsBoss extends AbstractNioSelector(bossExec, null) with Boss { boss =>

      /** use uds channel selector */
      selector = jnr.enxio.channels.NativeSelectorProvider.getInstance().openSelector

     // @volatile private var timeoutTimer: Option[Timeout] = None

      private[this] val wakeupTask = new TimerTask() {
        def run(timeout: Timeout) {
          val selector = boss.selector
          if (selector != null) {
            if (wakenUp.compareAndSet(false, true)) {
              selector.wakeup()
            }
          }
        }
      }

      /** thread naming */
      override protected def newThreadRenamingRunnable
       (id: Int, determiner: ThreadNameDeterminer): ThreadRenamingRunnable =
        new ThreadRenamingRunnable(this, "New I/O boss #" + id, determiner)

      /** register channel */
      override protected def createRegisterTask
       (chan: Channel, future: ChannelFuture): Runnable = new Runnable {
        def run() {
          log.debug(s"boss#createRegisterTask() registering channel $chan")
          val channel = chan.asInstanceOf[NioSocketChannel]
          val timeout = channel.getConfig().getConnectTimeoutMillis()
          if (timeout > 0) {
            if (!channel.isConnected()) {
              log.debug("boss#createRegisterTask() should schedule wake up task")
              /*channel.timeoutTimer = Some(*/timer.newTimeout(
                wakeupTask,
                timeout, TimeUnit.MILLISECONDS)
            }
          }
          try {
            val selector = boss.selector
            log.debug(s"boss#createRegisterTask() should register here for ${channel.channel} boss selector ${boss.selector}")
            // https://github.com/netty/netty/blob/netty-3.9.5.Final/src/main/java/org/jboss/netty/channel/socket/nio/NioClientBoss.java#L190-L191
            channel.channel match {
              case unix: unisockets.SocketChannel =>
                // throws java.nio.channels.IllegalBlockingModeException ?
                unix.chan.configureBlocking(false)
                log.debug(s"boss#createRegisterTask() unix chan ${unix.chan} registering connect select key with att $channel. selector keys ${boss.selector.keys}")
                unix.chan.register(
                  boss.selector, SelectionKey.OP_CONNECT, channel)
                log.debug(s"boss#createRegisterTask() registered ${boss.selector} op connect with channel $channel")
              case chan =>
                log.error(s"boss#createRegisterTask() not provided with a unisocket")
            }
          } catch {
            case e: ClosedChannelException =>
              log.error(s"boss#createRegisterTask() error registering channel")
              e.printStackTrace()
              log.error("boss#createRegisterTask() asking worker ${channel.getWorker} to close")
              channel.getWorker.close(channel, Channels.succeededFuture(channel))
          }

          val connectTimeout = channel.getConfig().getConnectTimeoutMillis()
          if (connectTimeout > 0) {
            //channel.connectDeadlineNanos = System.nanoTime() + connectTimeout * 1000000L
          }
        }
      }

      override protected def process(selector: Selector) {
        log.debug(s"boss#process($selector)")
        val selected = selector.selectedKeys
        val keys = selector.keys
        log.debug(s"boss#process() selected $selected keys $keys")
        processSelectedKeys(selected)
        processConnectTimeout(keys, System.nanoTime())
      }

      override protected def close(key: SelectionKey) = Option(key).foreach { // option because select was observed to contain a null key (pdi)
        _.attachment match {
          case null =>
            log.debug("boss#close() att was null?")
          case chan: NioSocketChannel =>
            log.debug(s"boss#close() worker ${chan.getWorker} closing channel $chan")
            chan.getWorker.close(chan, Channels.succeededFuture(chan))
          case att =>
            log.error(s"boss#close() $att not nio socket channel")
        }
      }

      private def processConnectTimeout(keys: JSet[SelectionKey], currentTimeNanos: Long) =
        for (key <- keys.asScala) {
          if (key != null/*hrm*/ && key.isValid) key.attachment match {
            case ch: NioSocketChannel =>
              log.error("boss#processConnectTimeout() connection timeout")
            case att =>
              log.error(s"boss#processConnectTimeout() processConnectTimeout($key): $att not nio socket channel")
          }
        }

      private def processSelectedKeys(selectedKeys: JSet[SelectionKey]) =
        if (!selectedKeys.isEmpty) { // avoid garbage -> https://github.com/netty/netty/issues/597
          for (key <- selectedKeys.asScala) {
            if (!key.isValid) {
              log.debug("boss#processSelectedKeys() connection close")
              close(key)
            } else try {
              if (key.isConnectable) key.attachment match {
                case chan: UdsNioSocketChannel =>
                  if (chan.channel.finishConnect) {
                    key.cancel()
                    log.debug(s"boss#processSelectedKeys() connect finished here. need to connect the chan here, asking worker ${chan.getWorker} to do so")
                    chan.getWorker.register(chan, chan.connectFuture)
                  }
                case unix: UnixSocketChannel =>
                  if (unix.finishConnect) {
                    key.cancel()
                    log.debug("boss#processSelectedKeys() unix connect finished here. need to connect the chan here")
                  }
                case att =>
                  log.error(s"boss#processSelectedKeys($key): $att not nio socket channel")
              }
            } catch {
              case e: Throwable =>
                log.error("boss#processSelectedKeys() error throwing while processing selection keys")
                e.printStackTrace()
                key.attachment match {
                  case chan: UdsNioSocketChannel =>
                    log.error("boss#processSelectedKeys() error thrown. should throw here")
                    chan.connectFuture.setFailure(e)
                    Channels.fireExceptionCaught(chan, e)
                    key.cancel() // Some JDK implementations run into an infinite loop without this.
                    log.error(s"boss#processSelectedKeys() exception: asking worker to close ${chan.getWorker}")
                    chan.getWorker.close(chan, Channels.succeededFuture(chan))
                  case att =>
                    log.error(s"boss#processSelectedKeys() error thrown. $key attachment $att not nio socket channel")
                }
            }
        }
      }
    }

    (new AbstractNioBossPool[UdsBoss](bossExec, 1) {

      override def newBoss(executor: Executor): UdsBoss =
        new UdsBoss()

      override def shutdown() {
        log.debug("bosses#shutdown()")
        super.shutdown()
      }

      override def releaseExternalResources() {
        log.debug("bosses#releaseExternalResources()")
        super.releaseExternalResources()
        timer.stop()
      }

    }: BossPool[UdsBoss])
  }

  private[this] lazy val sink: ChannelSink = new AbstractNioChannelSink {
    override def eventSunk(pipeline: ChannelPipeline, e: ChannelEvent) = e match {
      case cse: ChannelStateEvent =>
        log.debug(s"sink#eventSunk() rec event $cse for channel ${cse.getChannel}")
        val chan = cse.getChannel.asInstanceOf[NioSocketChannel]
        val future = cse.getFuture
        val value = cse.getValue
        cse.getState match {
          case ChannelState.OPEN =>
            log.debug(s"sink#eventSunk() state open $value")
            if (java.lang.Boolean.FALSE == value) {
              chan.worker.close(chan, future)
            }
          case ChannelState.BOUND =>
            log.debug(s"sink#eventSunk() state bound $value")
            Option(value) match {
              case Some(addr) =>                
                // todo: server sockets
                log.debug("sink#eventSunk() should bind")
                //bind(chan, future, addr.asInstanceOf[SocketAddress])
              case _ =>
                log.debug("sink#eventSunk() no value so closing")
                chan.getWorker.close(chan, future)
            }
          case ChannelState.CONNECTED =>
            log.debug(s"sink#eventSunk() state connected $value")
            Option(value) match {
              case Some(addr) =>
                connect(chan, future, addr.asInstanceOf[SocketAddress])
              case _ =>
                log.debug("sink#eventSunk() value so closing")
                chan.getWorker.close(chan, future)
            }
          case ChannelState.INTEREST_OPS =>
            log.debug(s"sink#eventSunk() state interest opts $value")
            chan.getWorker.setInterestOps(chan, future, value.asInstanceOf[java.lang.Integer])
        }
      case me: MessageEvent =>
        me.getChannel match {
          case chan: NioSocketChannel =>
            log.debug("sink#eventSunk() message event ... write from user code")
            val offered = chan.writeBufferQueue.offer(me)
            chan.getWorker.writeFromUserCode(chan)
          case _ =>
            log.error("sink#eventSunk() message event but not nio socket channel")
        }        
    }

   private def connect(
     socketChannel: NioSocketChannel, future: ChannelFuture, addr: SocketAddress) {
     log.debug(s"sink: connecting to addr $addr...")
     if (socketChannel.channel.connect(addr)) {
       log.debug(s"sink: scf.connect(...) - asking worker (${socketChannel.getWorker}) to register. channel open ${socketChannel.isOpen}")
       socketChannel.getWorker.register(socketChannel, future)
     } else {
       log.debug("sink: failed to connect???")
       socketChannel.getCloseFuture().addListener(new ChannelFutureListener {
         def operationComplete(f: ChannelFuture) {
           if (!future.isDone) future.setFailure(new ClosedChannelException)
         }
       })
     }
     future.addListener(ChannelFutureListener.CLOSE_ON_FAILURE)
     socketChannel match {
       case uds: UdsNioSocketChannel =>
         log.debug(s"assigning socketChannel $socketChannel connectFuture to $future")
       uds.connectFuture = future
       bosses.nextBoss().register(socketChannel, future)
     }     
   }
  }

  protected def openChannel: JSocketChannel = {
    try {
      val chan = UniSocketChannel.open()
      log.debug(s"set non blocking")
      chan.configureBlocking(false)
      chan
    } catch {
      case NonFatal(e) =>
        throw new ChannelException("failed to open channel", e)
    }
  }

  class UdsNioSocketChannel(pipeline: ChannelPipeline)
    extends NioSocketChannel(null, this, pipeline, sink, openChannel, workers.nextWorker) {
    @volatile var connectFuture: ChannelFuture = null
    Channels.fireChannelOpen(this)
  }

  override def newChannel(pipeline: ChannelPipeline): SocketChannel = {
    log.debug(s"factory: making a new channel for pipeline $pipeline")
    new UdsNioSocketChannel(pipeline)
  }

  override def shutdown() {
    log.debug("factory#shutdown()")
    bosses.shutdown()
    workers.shutdown()
    releasePools()
    super.shutdown()
  }

  override def releaseExternalResources() {
    log.debug("factory#releaseExternalResources()")
    shutdown()
    releasePools()
  }

  def releasePools() =
    Seq(bosses, workers).foreach {
      case rel: ExternalResourceReleasable =>
        rel.releaseExternalResources
      case _ =>
    }
}
