package xtc.lang.c4.util;

import xtc.lang.c4.C4CFactory;

/**
 * 
 * @author Marco Yuen
 * 
 */
public class C4CFactoryWrapper {
  /** An instance of C4CFactory. */
  private static C4CFactory instance = null;

  /**
   * Gets an instance of the C4CFactory.
   * 
   * @return C4CFactory.
   */
  public static C4CFactory getInstance() {
    if (null == instance)
      instance = new C4CFactory();

    return instance;
  }
}
