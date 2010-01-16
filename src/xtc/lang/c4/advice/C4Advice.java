package xtc.lang.c4.advice;

import java.util.List;

import xtc.lang.c4.C4Aspect;
import xtc.lang.c4.C4AspectManager;
import xtc.lang.c4.C4CFactory;
import xtc.lang.c4.util.C4CFactoryWrapper;
import xtc.lang.c4.util.C4XFormEngine;
import xtc.tree.GNode;
import xtc.tree.Node;
import xtc.tree.Visitor;

/**
 * The base class for all the different types of advice.
 * 
 * @author Marco Yuen
 * @version $Revision: 1.1 $
 */
public abstract class C4Advice extends Visitor {
  /** The aspect that this aspect belongs to. */
  protected C4Aspect parentAspect;

  /** The type of the advice. */
  protected C4AdviceType adviceType;

  /** The current state of the advice. */
  protected C4AdviceState state;

  /** A reference to the generic node. */
  protected GNode node = null;

  /** The aspect manager. */
  protected C4AspectManager aspectManager = null;

  /** The C4 C factory. */
  protected C4CFactory cFactory = null;

  /** A XForm engine. */
  protected C4XFormEngine xformEngine = null;

  /** A debug flag. */
  protected boolean debug = false;

  /** A prefix the debug message. */
  protected String debugMessagePrefix = "";

  /** A prefix for the mangling declarations. */
  protected String manglingPrefix = "";

  /** The constructor. */
  protected C4Advice(boolean debugFlag) {
    super();
    this.aspectManager = C4AspectManager.getInstance();
    this.cFactory = C4CFactoryWrapper.getInstance();
    this.state = C4AdviceState.NEW;
    this.xformEngine = C4XFormEngine.getInstance();
    this.debug = debugFlag;
  }

  /**
   * Default visit method.
   * 
   * @param n
   *          The node to visit
   * @return The node.
   */
  public Node visit(Node n) {
    for (int i = 0; i < n.size(); ++i) {
      Object o = n.get(i);
      if (o instanceof Node) {
        if (debug)
          System.err.println("C4 Advice - Visiting: " + ((Node) o).getName());
        n.set(i, dispatch((Node) o));
      }
    }

    return n;
  }

  /** Transforms the Advice into C code. */
  abstract List<GNode> transform();
}
