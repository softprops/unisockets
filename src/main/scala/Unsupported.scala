package unisockets

private[unisockets] object Unsupported {
  def apply(what: String) = throw new UnsupportedOperationException(
    s"$what is currently not supported")
  def bind = apply("binding")
}
