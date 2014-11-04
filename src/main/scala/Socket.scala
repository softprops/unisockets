package unisockets

import java.net.{
  Socket => JSocket,
  SocketAddress => JSocketAddress
}
import java.io.{ File, IOException }
import jnr.unixsocket.{ UnixSocketAddress, UnixSocketChannel }

// http://www.docjar.com/html/api/sun/nio/ch/SocketAdaptor.java.html

case class Socket(chan: UnixSocketChannel) extends JSocket(SocketImpl(chan)) {
  def this(file: File) = this(UnixSocketChannel.open(new UnixSocketAddress(file)))

  override def bind(jaddr: JSocketAddress) =
    jaddr match {
      case unix: Addr =>
        super.bind(jaddr)
      case _ =>
        throw new IOException(
          s"$jaddr is not an instance of socketeer.Addr")
    }

  override def connect(jaddr: JSocketAddress) =
    connect(jaddr, 0)

  override def connect(jaddr: JSocketAddress, timeout: Int) =
    jaddr match {
      case unix: Addr =>
        chan.connect(unix.addr) // timeout not supported
        //impl.connect(jaddr, timeout)
      case _ =>
        throw new IOException(
          s"$jaddr is not an instance of socketeer.Addr")
    }

  override def getRemoteSocketAddress  =
    Option(chan.getRemoteSocketAddress).map(Addr(_)).orNull

  override def getLocalSocketAddress =
    Option(chan.getLocalSocketAddress).map(Addr(_)).orNull
}
