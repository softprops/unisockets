package unisockets

import java.net.InetSocketAddress
import java.io.File
import jnr.unixsocket.UnixSocketAddress

case class Addr(addr: UnixSocketAddress) extends InetSocketAddress(0) {
  def this(file: File) = this(new UnixSocketAddress(file))
}
