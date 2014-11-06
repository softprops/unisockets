# unisockets

dressing up [unix domain sockets](http://en.wikipedia.org/wiki/Unix_domain_socket) in a tcp [socket](http://docs.oracle.com/javase/7/docs/api/java/nio/channels/SocketChannel.html) shirt and tie.

## usage

```scala
import java.io.File

// reference to the unix file descriptor to connect to
val pipe = File("/var/run/unix.sock")

// an nio SocketChannel impl
val channel = unisockets.SocketChannel.open(pipe)

// an oio Socket impl
val socket = unisockets.Socket.open(pipe)
```

Doug Tangren (softprops) 2014
