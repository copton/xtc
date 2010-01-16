package xtc.lang.c4.transformer;

import java.util.List;

import xtc.lang.c4.C4CFactory;
import xtc.lang.c4.util.C4XFormEngine;
import xtc.lang.c4.util.C4XFormQuery;
import xtc.tree.GNode;
import xtc.tree.Node;
import xtc.tree.Visitor;

/**
 * Transforms aspect constructs to pure C.
 * 
 * @author Marco Yuen
 * @version $Revision: 1.2 $
 */
public class C4AspectTransformer extends Visitor {

  /** The root node of the AST. */
  private Node root = null;

  /** A XForm engine. */
  C4XFormEngine xFormEngine = null;

  /** A debug flag. */
  protected boolean debug = false;

  /** A C factory. */
  protected C4CFactory cFactory = null;

  /**
   * Default constructor.
   * 
   * @param root
   *          The root of the AST.
   * @param debug
   *          The debug flag.
   */
  public C4AspectTransformer(GNode root, boolean debug) {
    this.root = root;
    xFormEngine = C4XFormEngine.getInstance();
    this.cFactory = new C4CFactory();
    this.debug = debug;
  }

  /**
   * Default constructor.
   * 
   * @param root
   *          The root of the AST.
   * @param debug
   *          The debug flag.
   */
  public C4AspectTransformer(Node root, boolean debug) {
    this.root = root;
    xFormEngine = C4XFormEngine.getInstance();
    this.cFactory = new C4CFactory();
    this.debug = debug;
  }

  /**
   * Default visit method to visit node in the AST.
   * 
   * @param n
   *          The node to visit.
   * @return The same node with transformation (if required).
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
   * The visit method for global introduction advice. This method will also
   * transform the advice into C code.
   * 
   * @param n
   *          The global introduction advice node.
   * @return The transformed global introduction advice.
   */
  public Node visitGlobalIntroductionAdvice(GNode globalIntroAdvice) {
    GNode transformedNode = null;

    C4GlobalIntroductionTransformer theGlobalTransformer = new C4GlobalIntroductionTransformer(globalIntroAdvice, debug);
    transformedNode = theGlobalTransformer.transform().get(0);

    return transformedNode;
  }

  /**
   * Transform the declaration of aspect structure type definition into pure C.
   * 
   * @param declaration
   * @return
   */
  public Node visitAspectStructureDeclaration(GNode declaration) {
    C4AspectStructureTransformer transformer = new C4AspectStructureTransformer(declaration, debug);

    return transformer.transform().get(0);
    // return declaration;
  }

  /**
   * 
   * @param aspectFuncDef
   * @return
   */
  public Node visitAspectFunctionDefinition(GNode aspectFuncDef) {
    C4AspectFunctionTransformer transformer = new C4AspectFunctionTransformer(aspectFuncDef, debug);

    return transformer.transform().get(0);
  }

  /**
   * 
   * @param declaration
   * @return
   */
  public Node visitDeclaration(GNode declaration) {
    List<Object> result = xFormEngine.run(C4XFormQuery.GetAllAspectStructureTypeDefinitions, declaration);

    if (!result.isEmpty()) {
      return visitAspectStructureDeclaration(declaration);
    }

    return declaration;
  }

  /**
   * Perform transformation on the AST.
   */
  public List<GNode> transform() {
    if (null == root) {
      System.err.println("The root node is null. Quitting ...");
      System.exit(-1);
    }

    dispatch(root);

    return null;
  }
}
