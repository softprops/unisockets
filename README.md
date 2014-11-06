# unisockets

dressing up [unix domain sockets](http://en.wikipedia.org/wiki/Unix_domain_socket) in a tcp [socket](http://docs.oracle.com/javase/7/docs/api/java/nio/channels/SocketChannel.html) shirt and tie.

## usage


A unix domain socket is typically a reference to a file descriptor. unisockets defines an implementation of a `SocketAddress` for
these file descriptors called `Addrs`.

```scala
import java.io.File
val addr = Addr(File("/var/run/unix.sock"))
```

You can create both instances of nio SocketChannels

```
val channel = unisockets.SocketChannel.open(addr)
```

and old io Sockets

```
val socket = unisockets.Socket.open(addr)
```

You can also create disconnected instances of each calling `open` without arguments and calling `connect(addr)` at a deferred time.

Doug Tangren (softprops) 2014
