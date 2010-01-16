package xtc.lang.c4.util;

/**
 * An immutable Pair<T, U> type.
 * 
 * @author Marco Yuen
 * 
 * @param <T>
 *          The type for the left object.
 * @param <U>
 *          The type for the right object.
 * 
 * @version $Revision: 1.1 $
 */
public class Pair<T, U> {
  /** The left object of the pair. */
  private final T left;

  /** The right object f the pair. */
  private final U right;

  public Pair(T left, U right) {
    this.left = left;
    this.right = right;
  }

  /**
   * Returns the left object.
   * 
   * @return The left object of the pair.
   */
  public T getLeft() {
    return this.left;
  }

  /**
   * Returns the right object.
   * 
   * @return The right object of the pair.
   */
  public U getRight() {
    return this.right;
  }
}
