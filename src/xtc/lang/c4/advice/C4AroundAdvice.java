package xtc.lang.c4.advice;

import java.util.List;

import xtc.lang.c4.util.C4XFormQuery;
import xtc.tree.GNode;

public class C4AroundAdvice extends C4Advice {

  /** The name of the function this advice is instrumenting on. */
  private String functioName = null;

  /** The function body. */
//  private GNode functionBody = null;

  /**
   * 
   * @param theNode
   * @param aspectName
   */
  public C4AroundAdvice(boolean dFlag, GNode theNode, GNode theBody, String aspectName, String funcName) {
    super(dFlag);
    this.adviceType = C4AdviceType.AROUND;
    this.node = theNode;
    this.functioName = funcName;
//    this.functionBody = theBody;

    this.parentAspect = aspectManager.getAspect(aspectName);
    this.parentAspect.addAroundAdvice(this, this.functioName);
  }

  /**
   * Returns a boolean indicating if the advice has a call to proceed.
   * 
   * @return True proceed is present. False otherwise.
   */
  public boolean hasProceed() {
    List<Object> results = xformEngine.run(C4XFormQuery.CheckForProceed, node);
    return !results.isEmpty();
  }

  @Override
  public List<GNode> transform() {
    // List<Object> results = null;

    return null;
  }

}
