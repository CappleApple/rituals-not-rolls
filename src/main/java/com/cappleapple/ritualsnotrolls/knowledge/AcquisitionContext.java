package com.cappleapple.ritualsnotrolls.knowledge;

/** Scoped contexts are try/finally safe. Manual slot movement never enables absorption. */
public final class AcquisitionContext implements AutoCloseable {
  private static final ThreadLocal<Integer> SUPPRESSED = ThreadLocal.withInitial(() -> 0);

  public static boolean allowed() {
    return SUPPRESSED.get() == 0;
  }

  public static AcquisitionContext suppress() {
    SUPPRESSED.set(SUPPRESSED.get() + 1);
    return new AcquisitionContext();
  }

  @Override
  public void close() {
    int n = SUPPRESSED.get() - 1;
    if (n == 0) SUPPRESSED.remove();
    else SUPPRESSED.set(n);
  }
}
