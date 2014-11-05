package unisockets

import org.scalatest.FunSpec
import java.io.{ InputStreamReader, File, PrintWriter }
import java.nio.CharBuffer

class SocketChannelSpec extends FunSpec {
  describe("SocketChannel") {
    it ("should work") {
      val req = "GET /images/json HTTP/1.1\r\n\r\n"
      val socket = SocketChannel.open(new File("/var/run/docker.sock")).socket()
      val out = socket.getOutputStream
      new PrintWriter(out) {
        print(req)
        flush()
      }
      val in = socket.getInputStream
      val r = new InputStreamReader(in)
      val result = CharBuffer.allocate(1024)
      r.read(result)
      result.flip()
      val response = result.toString
      println(response)
      socket.close()
      assert(response.nonEmpty)
    }
  }
}
