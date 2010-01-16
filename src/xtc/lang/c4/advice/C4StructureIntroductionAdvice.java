package xtc.lang.c4.advice;

import java.util.ArrayList;
import java.util.List;

import xtc.lang.c4.C4Aspect;
import xtc.tree.GNode;
import xtc.tree.Node;
import xtc.tree.Token;

/**
 * A structure introduction advice.
 * 
 * @author Marco Yuen
 */
public class C4StructureIntroductionAdvice extends C4Advice {

  /** A list of fields introduced. */
  private List<String> fieldsIntroduced = null;

  /**
   * Constructor.
   * 
   * @param dFlag
   *          The debug flag.
   * @param theNode
   *          The structure introduction advice.
   * @param aspectName
   *          The name of the aspect this advice belongs to.
   */
  public C4StructureIntroductionAdvice(boolean dFlag, GNode theNode, String aspectName) {
    super(dFlag);
    // Sanity check.
    assert theNode.hasName("StructureAdviceDeclaration");
    adviceType = C4AdviceType.INTRO;
    node = theNode;
    debugMessagePrefix = getClass().getName();
    manglingPrefix = String.format("__struct_intro__advice_%s_", aspectName);
    fieldsIntroduced = new ArrayList<String>();

    this.parentAspect = aspectManager.getAspect(aspectName);
    this.parentAspect.addStructIntroAdvice(this);
  }

  /**
   * Gets the name of the fields being introduced.
   * 
   * @param n
   * @return
   */
  public Node visitSimpleDeclarator(GNode n) {
    if (debug)
      System.err.printf("%s: %s being introduced\n", debugMessagePrefix, n);

    fieldsIntroduced.add(Token.cast(n.get(0)));

    return n;
  }

  /**
   * Returns a list of fields introduced by this advice.
   * 
   * @return A list of fields' names.
   */
  public List<String> getFieldsIntroduced() {
    return this.fieldsIntroduced;
  }

  /**
   * Returns the aspect which the current advice belongs to.
   * 
   * @return An aspect.
   */
  public C4Aspect getParentAspect() {
    return this.parentAspect;
  }

  @Override
  public List<GNode> transform() {
    List<GNode> returndNodes = new ArrayList<GNode>(1);
    List<Node> fields = new ArrayList<Node>();
    GNode structDeclaration = null, structTypeDefinition = null;

    // Loop through the fields being introduced.
    GNode adviceFields = GNode.cast(node.get(2));
    for (int i = 0; i < adviceFields.size(); ++i) {
      Node n = adviceFields.getNode(i);
      if (n != null)
        fields.add((Node) dispatch(adviceFields.getNode(i)));
    }

    // Extract structure type definition from the definition.
    structDeclaration = GNode.cast(cFactory.createStructDeclaration(parentAspect.getAspectName(), fields));
    structTypeDefinition = structDeclaration.getGeneric(1).getGeneric(0);
    assert structTypeDefinition.hasName("StructureTypeDefinition");

    // Put the structure type definition into a specifier qualifier list.
    returndNodes.add(GNode.create("SpecifierQualifierList", structTypeDefinition));

    // Update the state.
    state = C4AdviceState.TRANSFORMED;

    return returndNodes;
  }

}
