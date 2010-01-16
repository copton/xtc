package xtc.lang.c4.transformer;

import java.util.ArrayList;
import java.util.List;

import xtc.lang.c4.advice.C4StructureIntroductionAdvice;
import xtc.tree.GNode;
import xtc.tree.Node;
import xtc.tree.Token;
import xtc.tree.Visitor;

/**
 * The transformer which is responsible for transforming structure introduction advice.
 * 
 * @author Marco Yuen
 */
public class C4AspectStructureTransformer extends Visitor implements IC4Transformer {
  /** The node. */
  private GNode node = null;

  /** A debug flag. */
  private boolean debug = false;

  /** A flag that indicates if typedef is used in the structure declaration. */
  private boolean isType = false;

  /** The tagname, if any, of the structure. */
  private String structureTagName = null;

  /** The structure introduction advice. */
  private List<C4StructureIntroductionAdvice> structIntroAdviceList = null;

  /** A list of declarators. */
  private List<String> declarators = null;

  /**
   * Constructor.
   * 
   * @param theNode
   *          The structure introduction node.
   * @param dFlag
   *          The debug flag.
   */
  public C4AspectStructureTransformer(GNode theNode, boolean dFlag) {
    assert theNode.hasName("Declaration");
    this.node = theNode;
    this.debug = dFlag;
    this.isType = false;

    structIntroAdviceList = new ArrayList<C4StructureIntroductionAdvice>();
    declarators = new ArrayList<String>();
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
      if (o instanceof Node)
        n.set(i, dispatch((Node) o));
    }

    return n;
  }

  /**
   * Transforms the introductions.
   * 
   * @param n
   * @return
   */
  public Node visitStructureAdviceDeclaration(GNode n) {
    C4StructureIntroductionAdvice theAdvice = new C4StructureIntroductionAdvice(debug, n, Token.cast(n.get(1)));
    structIntroAdviceList.add(theAdvice);

    return theAdvice.transform().get(0);
  }

  /**
   * Transforms the aspect structure declaration.
   * 
   * @param n
   * @return Either the node or a transformed node.
   */
  public Node visitAspectStructureDeclaration(GNode n) {
    if (n.getGeneric(1).hasName("StructureAdviceDeclaration")) {
      // The reason for a new aspect structure declaration is because the CPrinter expects
      // a null after the specifier list.
      GNode newAspectStructDeclaration = GNode.create("AspectStructureDeclaration", null,
                                                      visitStructureAdviceDeclaration(n.getGeneric(1)), null);

      return newAspectStructDeclaration;
    }

    return n;
  }

  /**
   * Visits method for simple declarator to get the declarators' names.
   * 
   * @param simpleDeclarator
   * @return
   */
  public Node visitSimpleDeclarator(GNode simpleDeclarator) {
    if (debug)
      System.err.println("C4AspectStructTransformer: Adding declarator " + simpleDeclarator);

    declarators.add(Token.cast(simpleDeclarator.get(0)));

    return simpleDeclarator;
  }

  /**
   * 
   * @param typedefSpecifier
   * @return
   */
  public Node visitTypedefSpecifier(GNode typedefSpecifier) {
    if (debug)
      System.err.println("Typedef present. Setting flag ...");

    this.isType = true;

    return typedefSpecifier;
  }

  public Node visitAspectStructureTypeDefinition(GNode n) {
    if (n.get(1) != null) {
      if (debug)
        System.err.println("C4AspectStructureTransformer: setting tag name to " + Token.cast(n.get(1)));
      structureTagName = Token.cast(n.get(1));
    }

    // Visit the children of the AspectStructureTypeDefinition.
    for (int i = 0; i < n.size(); ++i) {
      Object o = n.get(i);

      if (o instanceof Node)
        n.set(i, dispatch((Node) o));
    }

    return n;
  }

  /**
   * Transforms the structure with introduction advice.
   * 
   * @return A list of transformed node.
   */
  public List<GNode> transform() {
    if (debug)
      System.err.println("Transforming aspect structure ...");
    List<GNode> returnNodes = new ArrayList<GNode>();

    // Transforming the structure with introduction advice.
    for (int i = 0; i < node.size(); ++i) {
      Object o = node.get(i);

      if (o instanceof Node)
        node.set(i, dispatch((Node) o));
    }
    returnNodes.add(node);

    // Add bindings
    for (C4StructureIntroductionAdvice advice : structIntroAdviceList) {
      List<String> fields = advice.getFieldsIntroduced();
      advice.getParentAspect().addStructureIntroFields(fields, structureTagName, declarators, isType);
    }

    return returnNodes;
  }
}
