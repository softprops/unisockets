package unisockets

import java.net.InetSocketAddress
import java.io.File
import jnr.unixsocket.UnixSocketAddress

object Addr {
  def apply(file: File): Addr = Addr(new UnixSocketAddress(file))
}

case class Addr(addr: UnixSocketAddress) extends InetSocketAddress(0) {
  override def toString() = s"Addr($addr)"
}
