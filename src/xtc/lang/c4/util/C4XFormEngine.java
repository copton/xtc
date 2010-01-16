package xtc.lang.c4.util;

import java.util.List;

import xtc.tree.GNode;
import xtc.xform.Engine;
import xtc.xform.Query;

/**
 * A XForm engine with a new function for C4 use only.
 * 
 * @author Marco Yuen
 * @version $Revision: 1.1 $
 */
public class C4XFormEngine extends Engine {

  /** A singleton pattern. */
  private static C4XFormEngine instance = null;

  /**
   * A constructor for the C4XFormEngine. It first calls the parent's construct, then add a new
   * function to the engine.
   */
  private C4XFormEngine() {
    super();
  }

  /**
   * Returns the same instance of C4XFormEngine.
   * 
   * @return C4XFormEngine.
   */
  public static C4XFormEngine getInstance() {
    if (null == instance)
      instance = new C4XFormEngine();

    return instance;
  }

  /**
   * A wrapper for the run method.
   * 
   * @param query
   *          A query of type String.
   * @param ast
   *          An AST to perform the query on.
   * @return A list of objects.
   */
  public List<Object> run(String query, GNode ast) {
    return super.run(new Query(query), ast);
  }

  /**
   * A wrapper for the run method.
   * 
   * @param q
   *          The query of type C4XFormQuery.
   * @param ast
   *          An AST to perform the query on.
   * @return A list of objects.
   */
  public List<Object> run(C4XFormQuery q, GNode ast) {
    return super.run(new Query(q.toString()), ast);
  }
}
