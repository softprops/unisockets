# unisockets

> dressing up [unix domain sockets](http://en.wikipedia.org/wiki/Unix_domain_socket) in a tcp [socket](http://docs.oracle.com/javase/7/docs/api/java/nio/channels/SocketChannel.html) shirt and tie.

<p>
  <img height="175" src="https://rawgit.com/softprops/unisockets/master/us.svg"/>
</p>

## install

Add the following to your ivy resolver chain

```scala
resolvers += "softprops-maven" at "http://dl.bintray.com/content/softprops/maven"
```

### core

Adding the following to your sbt build definition.

```scala
libraryDependencies += "me.lessis" %% "unisockets-core" % "0.1.0"
```

### netty

Adding the following to your sbt build definition

```scala
libraryDependencies += "me.lessis" %% "unisockets-netty" % "0.1.0"
```

## usage

_note_: This library requires at a minimum a java 7 jre, as the [SocketChannel](http://docs.oracle.com/javase/7/docs/api/java/nio/channels/SocketChannel.html) class changed to implement a new [NetworkChannel](http://docs.oracle.com/javase/7/docs/api/java/nio/channels/NetworkChannel.html) interface in java 7.

A Unix domain socket facilitates inter-process communication between different processes on a host machine via data streamed through a local file descriptor.

Unisockets, like tcp sockets, need to be addressable. unisockets defines an implementation of a `SocketAddress` for these file descriptors called an `Addr`.

```scala
import java.io.File
val addr = unisockets.Addr(new File("/var/run/unix.sock"))
```

You can get the path of the file an `Addr` refers to by invoking `Addr#getHostName`.

With an `Addr`, you can create instances of both nio SocketChannels

```scala
val channel = unisockets.SocketChannel.open(addr)
```

and old io Sockets.

```scala
val socket = unisockets.Socket.open(addr)
```

You can also create disconnected instances of each calling `open` without arguments and calling `connect(addr)` at a deferred time. This library aims to stay close to familiar factory methods defined in their [std lib counterparts](http://docs.oracle.com/javase/7/docs/api/java/nio/channels/SocketChannel.html#open())

### netty

The `unisockets-netty` module provides a [Netty](http://netty.io/) `NioSocketChannel` backed by a `unisockets.SocketChannel`, enabling you to
build netty clients for UNIX domain socket servers.

```scala 
val sockets = new unisockets.ClientUdsSocketChannelFactory()
```

This nio socket channel factory share's many similarities with [NioClientSocketChannelFactories](http://netty.io/3.10/api/org/jboss/netty/channel/socket/nio/NioClientSocketChannelFactory.html)

It's constructor takes an optional Executor for accepting connections, an optional Executor for handling requests, and an optional Timer for task scheduling.

Client's using this interface should make sure they call `ClientUdsSocketChannelFactory#releaseExternalResources` to release any resources 
acquired during request processing.

note: The Netty interface has only been tested with a Netty client pipeline with version `3.9.6.Final` newer versions ( Netty 4+ ) are not supported yet but support is planned to be added in the future.

Doug Tangren (softprops) 2014-2015
