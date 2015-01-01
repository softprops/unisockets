package unisockets

import java.net.InetSocketAddress
import java.io.File
import jnr.unixsocket.UnixSocketAddress

object Addr {
  def apply(file: File): Addr = Addr(new UnixSocketAddress(file), Some(file))
  object Potential {
    def unapply(inet: InetSocketAddress): Option[Addr] =
      new File(inet.getHostName.replaceFirst("unix://", "").takeWhile(_ != ':')) match {
        case file if file.exists => Some(Addr(file))
        case _ => None
      }
  }
}

case class Addr
 (addr: UnixSocketAddress, file: Option[File] = None)
  extends InetSocketAddress(file.map(_.getAbsolutePath).orNull, 0) {
  override def toString() = s"Addr($addr)"
}
