package xtc.lang.c4.transformer;

import java.util.List;

import xtc.lang.c4.advice.C4GlobalIntroAdvice;
import xtc.tree.GNode;
import xtc.tree.Token;

/**
 * The transformer which is responsible for transforming global introduction advice.
 * 
 * @author Marco Yuen
 */
public class C4GlobalIntroductionTransformer implements IC4Transformer {

  /** The generic of the advice. */
  private GNode node = null;

  /** The global advice. */
  private C4GlobalIntroAdvice globalAdvice = null;

  /** The debug flag. */
  private boolean debug = false;

  /**
   * Constructor.
   * 
   * @param theNode
   *          The global introduction advice node.
   * @param debug
   *          The debug flag.
   */
  public C4GlobalIntroductionTransformer(GNode theNode, boolean debug) {
    node = theNode;
    this.debug = debug;
    globalAdvice = new C4GlobalIntroAdvice(debug, node, Token.cast(theNode.get(1)));
  }

  /**
   * Transforms the global introduction advice.
   * 
   * @return A list of transformed node.
   */
  public List<GNode> transform() {
    if (debug)
      System.err.println("Transforming global introduction ...");
    return globalAdvice.transform();
  }

}
