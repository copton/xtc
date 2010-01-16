package xtc.lang.c4;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages the list of aspects.
 * 
 * @author Marco Yuen
 */
public class C4AspectManager {

  /** A C4AspectManager instance. */
  private static C4AspectManager instance = null;

  /** The map of aspects <aspect name, aspect instance>. */
  private Map<String, C4Aspect> aspectMap = null;

  /** An instance of the xform engine. */
  // private C4XFormEngine xFormEngine = null;
  /** The debug flag. */
  private boolean debug = false;

  /**
   * Constructor.
   */
  private C4AspectManager() {
    this.aspectMap = new HashMap<String, C4Aspect>();
    // this.xFormEngine = C4XFormEngine.getInstance();
  }

  /**
   * A static method that will return the same instance of C4AspectManager.
   * 
   * @return An instance of C4AspectManager.
   */
  public static C4AspectManager getInstance() {
    if (null == instance)
      instance = new C4AspectManager();

    return instance;
  }

  /**
   * Returns an aspect based on the name given.
   * 
   * @param aspectName
   *          The aspect name.
   * @return A C4Aspect.
   */
  public C4Aspect getAspect(String aspectName) {
    if (aspectMap.containsKey(aspectName))
      return aspectMap.get(aspectName);
    else {
      if (debug)
        System.err.println("Creating a new aspect with name " + aspectName);
      C4Aspect anAspect = new C4Aspect(aspectName);
      aspectMap.put(aspectName, anAspect);
      return anAspect;
    }
  }

}
