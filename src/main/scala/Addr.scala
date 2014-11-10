package unisockets

import java.net.InetSocketAddress
import java.io.File
import jnr.unixsocket.UnixSocketAddress

object Addr {
  def apply(file: File): Addr = Addr(new UnixSocketAddress(file), Some(file))
}

case class Addr
 (addr: UnixSocketAddress, file: Option[File] = None)
  extends InetSocketAddress(file.map(_.getAbsolutePath).orNull, 0) {
  override def toString() = s"Addr($addr)"
}
