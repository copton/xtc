package xtc.lang.c4.advice;

import java.util.ArrayList;
import java.util.List;

import xtc.lang.c4.util.C4XFormQuery;
import xtc.tree.GNode;
import xtc.tree.Node;
import xtc.tree.Token;
import xtc.tree.Visitor;

/**
 * A global introduction advice.
 * 
 * @author Marco Yuen
 */
public class C4GlobalIntroAdvice extends C4Advice {

  /**
   * Visits and mangles a structure.
   */
  class StructureDeclarationVisitor extends Visitor {
    /**
     * Visits and mangles the structure tag name, if any.
     * 
     * @param n
     *          A structure type definition.
     * @return A structure type definition with mangled tag name.
     */
    public Node visitStructureTypeDefinition(GNode n) {
      // Check if there a tag name.
      if (null != n.get(1)) {
        String structTag = Token.cast(n.get(1));
        String mangledStructTag = mangleNameAndUpdateBinding(structTag);
        if (debug)
          System.err.printf("Mangling structure tag %s to %s\n", structTag, mangledStructTag);

        n.set(1, mangledStructTag);
      }

      return n;
    }

    /**
     * Visits and mangles the structure instance.
     * 
     * @param n
     *          The structure instance name.
     * @return A mangled structure instance name.
     */
    public Node visitSimpleDeclarator(GNode n) {
      mangleAndReplaceSimpleDeclarator(n);

      return n;
    }

    /**
     * General visit and mangling method
     * 
     * @param n
     *          A declaration node which contains a structure type definition.
     * @return A mangled declaration.
     */
    public Node visit(Node n) {
      for (int i = 0; i < n.size(); ++i) {
        Object o = n.get(i);

        if (o instanceof Node)
          n.set(i, dispatch((Node) o));
      }

      return n;
    }
  }

  /**
   * Constructor.
   * 
   * @param dFlag
   *          The debug flag.
   * @param theNode
   *          The global introduction advice node.
   * @param aspectName
   *          The name of the aspect this advice belongs to.
   */
  public C4GlobalIntroAdvice(boolean dFlag, GNode theNode, String aspectName) {
    super(dFlag);
    adviceType = C4AdviceType.GLOBAL;
    node = theNode;
    debugMessagePrefix = getClass().getName();
    manglingPrefix = String.format("__global_intro__advice_%s_", aspectName);

    this.parentAspect = aspectManager.getAspect(aspectName);
    this.parentAspect.addGlobalIntroAdvice(this);
  }

  /**
   * Mangled and replace the given simple declarator.
   * 
   * @param simpleDeclarator
   *          The simple declarator to be mangled.
   */
  private void mangleAndReplaceSimpleDeclarator(GNode simpleDeclarator) {
    // Get the name of the declared variable or new function name.
    String declarationName = Token.cast(simpleDeclarator.get(0));
    String mangledDeclarationName = manglingPrefix + declarationName;

    if (debug)
      System.err.printf("%s: Replacing %s with %s.\n", debugMessagePrefix, declarationName, mangledDeclarationName);

    this.parentAspect.addToBindings(declarationName, mangledDeclarationName);

    simpleDeclarator.set(0, mangledDeclarationName);
  }

  /**
   * Mangles a given string.
   * 
   * @param originalName
   *          The orignal un-mangled string.
   * @return A mangled string.
   */
  private String mangleNameAndUpdateBinding(String originalName) {
    String mangledName = manglingPrefix + originalName;
    if (debug)
      System.err.printf("Adding %s and %s to binding ...\n", originalName, mangledName);
    this.parentAspect.addToBindings(originalName, mangledName);

    return mangledName;
  }

  /**
   * Visits a simple declarator node and mangles it.
   * 
   * @param n
   *          The simple declarator node.
   * @return A mangled declarator node.
   */
  public Node visitSimpleDeclarator(GNode n) {
    if (debug)
      System.err.println("Simple declarator: " + n);
    mangleAndReplaceSimpleDeclarator(n);
    return n;
  }

  /**
   * Visits a function definition.
   * 
   * @param n
   *          The function definition node.
   * @return The function definition.
   */
  public Node visitFunctionDefinition(GNode n) {
    for (int i = 0; i < n.size(); ++i) {
      Object o = n.get(i);
      if (o instanceof Node)
        n.set(i, dispatch((Node) o));
    }

    return n;
  }

  /**
   * Visits and mangles a declaration that contains a structure type definition.
   * 
   * @param n
   *          A declaration.
   * @return A mangled declaration.
   */
  public Node visitStructureDeclaration(GNode n) {
    return (Node) new StructureDeclarationVisitor().dispatch(n);
  }

  /**
   * Visits declaration.
   * 
   * @param n
   *          The declaration node.
   * @return The declaration.
   */
  public Node visitDeclaration(GNode n) {
    List<Object> results = xformEngine.run(C4XFormQuery.GetAllStructureTypeDefinition, n);

    // This declaration contains a structure type definition; use the appropriate visitor method.
    if (!results.isEmpty())
      return visitStructureDeclaration(n);

    for (int i = 0; i < n.size(); ++i) {
      Object o = n.get(i);
      if (o instanceof Node)
        n.set(i, dispatch((Node) o));
    }

    return n;
  }

  @Override
  public List<GNode> transform() {
    List<Node> nodeList = new ArrayList<Node>();
    List<GNode> returnNodes = new ArrayList<GNode>(1);

    // Loop through the children of the global introduction advice and
    // transform the children.
    for (int i = 2; i < node.size(); ++i) {
      Object o = node.get(i);

      if (o instanceof Node) {
        Object aNode = dispatch((Node) o);
        node.set(i, aNode);
        nodeList.add((Node) aNode);
      }
    }

    GNode theNode = GNode.create(node.getName(), GNode.cast(cFactory.createBlock(nodeList)));
    returnNodes.add(theNode);

    return returnNodes;
  }
}
