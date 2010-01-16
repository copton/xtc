package xtc.lang.c4.advice;

import java.util.ArrayList;
import java.util.List;

import xtc.lang.c4.util.C4NameMangler;
import xtc.tree.GNode;
import xtc.tree.Node;

/**
 * A before advice.
 * 
 * @author Marco Yuen
 * @version $Revision: 1.2 $
 */
public class C4BeforeAdvice extends C4Advice {

  /** The debug flag. */
  private boolean debug = false;

  /** The name of the function. */
  protected String functionName = null;

  /** The name mangler. */
  private C4NameMangler nameMangler = null;

  /**
   * Construtor
   * 
   * @param dFlag
   *          The debug flag.
   * @param theNode
   *          The before advice node.
   * @param aspectName
   *          The name of the aspect.
   * @param funcName
   *          The function's name.
   */
  public C4BeforeAdvice(boolean dFlag, GNode theNode, String aspectName, String funcName) {
    super(dFlag);
    assert theNode.hasName("BeforeAdviceStatement") : "The node should have the name 'BeforeAdviceStatement'";
    debug = dFlag;
    this.node = theNode;
    this.adviceType = C4AdviceType.BEFORE;
    this.functionName = funcName;

    this.parentAspect = aspectManager.getAspect(aspectName);
    this.parentAspect.addBeforeAdvice(this, this.functionName);
    this.nameMangler = new C4NameMangler(debug, this.parentAspect.getBindings());
  }

  /**
   * Visits a declaration.
   * 
   * @param n
   *          A declaration.
   * @return The same declaration.
   */
  public Node visitDeclaration(GNode n) {
    return n;
  }

  /**
   * General visit method
   * 
   * @param n
   *          A node.
   * @return A transformed node.
   */
  public Node visit(Node n) {
    for (int i = 0; i < n.size(); ++i) {
      Object o = n.get(i);
      if (o instanceof Node)
        n.set(i, dispatch((Node) o));
    }

    return n;
  }

  @Override
  public List<GNode> transform() {
    List<GNode> transformedNodes = new ArrayList<GNode>();
    List<Node> adviceChildren = new ArrayList<Node>();
    Node theCompound = null;

    for (int i = 0; i < node.size(); ++i) {
      Object o = node.get(i);

      if (o instanceof Node) {
        Node n = (Node) o;
        Object result = nameMangler.dispatch(n);
        if (debug)
          System.err.printf("Before mangling: %s. After mangling: %s", n, result);
        adviceChildren.add((Node) result);
      }
    }

    // Put everything in a block.
    theCompound = cFactory.createBlock(adviceChildren);
    assert GNode.test(theCompound) : "The C4CFactory should return a GNode compound statement. "
                                     + "Something is very wrong!";
    // Add the compound statement to the list of transformed node.
    transformedNodes.add(GNode.cast(theCompound));

    this.state = C4AdviceState.TRANSFORMED;
    return transformedNodes;
  } // transform
}
