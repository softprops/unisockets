package unisockets

import java.io.{ File, IOException }
import java.net.{ SocketAddress, SocketOption }
import java.nio.ByteBuffer
import java.nio.channels.{ SocketChannel => JSocketChannel, UnsupportedAddressTypeException }
import java.nio.channels.spi.AbstractSelectableChannel
import java.util.Collections
import jnr.unixsocket.{ UnixSocketAddress, UnixSocketChannel }

object SocketChannel {
  def open(file: File): SocketChannel =
    open(Addr(file))

  def open(addr: Addr): SocketChannel =
    SocketChannel(UnixSocketChannel.open(addr.addr))

  def open() =
    SocketChannel(UnixSocketChannel.open())

  private[unisockets] lazy val implCloseSelectableChannel = {
    val method = classOf[AbstractSelectableChannel].getDeclaredMethod(
      "implCloseSelectableChannel")
    method.setAccessible(true)
    method
  }

  private[unisockets] lazy val implConfigureBlocking = {
    val method = classOf[AbstractSelectableChannel].getDeclaredMethod(
      "implConfigureBlocking", classOf[Boolean])
    method.setAccessible(true)
    method
  }
}

case class SocketChannel private[unisockets](
  private val chan: UnixSocketChannel)
  extends JSocketChannel(chan.provider) {

  // AbstractSelectableChannel interface

  protected def implCloseSelectableChannel() {
    SocketChannel.implCloseSelectableChannel.invoke(chan)
  }

  protected def implConfigureBlocking(blocks: Boolean) {
    SocketChannel.implConfigureBlocking.invoke(
      chan, java.lang.Boolean.valueOf(blocks))
  }

  // SocketChannel interface

  override def connect(addr: SocketAddress): Boolean =
    addr match {
      case unix: Addr =>
        chan.connect(unix.addr)
      case _ =>
        throw new UnsupportedAddressTypeException()
    }

  override def finishConnect() =
    chan.finishConnect()

  override def isConnected() =
    chan.isConnected()

  override def isConnectionPending() =
    chan.isConnectionPending

  override def read(dst: ByteBuffer) =
    chan.read(dst)

  override def read(dsts: Array[ByteBuffer], offset: Int, len: Int) =
    throw new IOException("not supported")

  override def write(src: ByteBuffer) =
    chan.write(src)

  override def write(srcs: Array[ByteBuffer], offset: Int, len: Int) =
    throw new IOException("not supported")

  override def socket() = Socket(chan, Some(this))

  // java 7+

  def getOption[T](name: SocketOption[T]) = throw new RuntimeException("not supported")

  def setOption[T](name: SocketOption[T], value: T) = this

  def supportedOptions() = Collections.emptySet[SocketOption[_]]

  def getLocalAddress = null // never bound

  def getRemoteAddress =
    Option(chan.getRemoteSocketAddress).map(Addr(_)).orNull

  def bind(jaddr: SocketAddress) =
    throw new UnsupportedOperationException(
      "binding is currently unsupported")

  def shutdownInput() = {
    chan.shutdownInput
    this
  }

  def shutdownOutput() = {
    chan.shutdownOutput
    this
  }
}
