package ru.ispras.microtesk.translator.mir;

public class ForwardPass extends Pass {
  @Override
  public MirContext apply(final MirContext source) {
    final MirContext ctx = Pass.copyOf(source);
    InsnRewriter.rewrite(ctx);

    return ctx;
  }
}