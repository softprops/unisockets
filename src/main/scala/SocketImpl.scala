package unisockets

import java.io.{ FileDescriptor, IOException }
import java.net.{ InetAddress, SocketAddress, SocketException, SocketImpl => JSocketImpl, SocketOptions }
import java.nio.channels.Channels
import jnr.unixsocket.UnixSocketChannel

case class SocketImpl(chan: UnixSocketChannel) extends JSocketImpl {
  this.fd = new FileDescriptor()

  private[this] lazy val in = Channels.newInputStream(chan)
  private[this] lazy val out = Channels.newOutputStream(chan)

  override def accept(jimpl: JSocketImpl) = ()
  override def available(): Int = in.available

  override protected def bind(addr: InetAddress, port: Int) =
    throw new SocketException(s"can not bind to this type of address: $addr")

  override protected def close() = ()

  override protected def connect(jaddr: InetAddress, port: Int) =
    throw new SocketException("not supported")

  override protected def connect(jaddr: SocketAddress, timeout: Int) =
    jaddr match {
      case unix: Addr =>
        chan.connect(unix.addr)
      case _ =>
        throw new SocketException("not supported")
    }

  override protected def connect(host: String, port: Int) =
    throw new SocketException("not supported")

  override protected def create(stream: Boolean) = ()

  override protected def getInputStream() =
    if (chan.isConnected) in else throw new IOException()

  override protected def getOutputStream() =
    if (chan.isConnected) out else throw new IOException()

  override def getOption(id: Int): Object =
    id match {
      case _ => null
    }

  override def setOption(id: Int, value: Object) =
    id match {
      case SocketOptions.SO_KEEPALIVE =>
        // supported...
      case _ =>
    }

  override def listen(backlog: Int) = ()

  override def sendUrgentData(data: Int) = ()

  override protected def getFileDescriptor() = fd
}
