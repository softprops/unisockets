package unisockets

import java.io.IOException
import java.net.SocketAddress
import java.nio.ByteBuffer
import java.nio.channels.{ SocketChannel => JSocketChannel, UnsupportedAddressTypeException }
import java.nio.channels.spi.SelectorProvider
import jnr.unixsocket.{ UnixSocketAddress, UnixSocketChannel }

case class SocketChannel(chan: UnixSocketChannel) extends JSocketChannel(SelectorProvider.provider) {

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

  override def socket() = Socket(chan)

  override protected def implCloseSelectableChannel() {
    // protected
    //chan.implCloseSelectableChannel()
  }
  override protected def implConfigureBlocking(blocks: Boolean) {
    // protected
    //chan.implConfigureBlocking(blocks)
  }
}
