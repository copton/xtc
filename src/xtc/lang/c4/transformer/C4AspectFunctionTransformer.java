package xtc.lang.c4.transformer;

import java.util.ArrayList;
import java.util.List;

import xtc.lang.c4.C4CFactory;
import xtc.lang.c4.advice.C4AfterAdvice;
import xtc.lang.c4.advice.C4AroundAdvice;
import xtc.lang.c4.advice.C4BeforeAdvice;
import xtc.lang.c4.util.C4XFormEngine;
import xtc.lang.c4.util.C4XFormQuery;
import xtc.tree.GNode;
import xtc.tree.Node;
import xtc.tree.Token;
import xtc.tree.Visitor;

/**
 * A representation of an aspect function.
 * 
 * @author Marco Yuen
 * @version $Revision: 1.2 $
 */
public class C4AspectFunctionTransformer extends Visitor implements IC4Transformer {
  /** A reference to the GNode. */
  private GNode node = null;

  /** The debug flag. */
  private boolean debug = false;

  /** A XForm engine. */
  private C4XFormEngine xformEngine = null;

  /** The C Factory. */
  private C4CFactory cFactory = null;

  /** The name of the aspect function. */
  private String functionName = null;

  /** The function declarator node. It includes the function's name and its parameters (If any). */
  private GNode declarator = null;

  /** The compound statement that contains the advice and function body. */
  private GNode aspectCompoundStatement = null;

  /**
   * The generic node that contains all the before advice. (Please note: around advice can appear at
   * both the beginning and the end)
   */
  private GNode beginningAdviceStatements = null;

  /**
   * The generic node that contains all the after advice. (Please note: around advice can appear at
   * both the beginning and the end)
   */
  private GNode endingAdviceStatements = null;

  /** The body of the aspect function. */
  private GNode aspectFunctionBody = null;

  /** The declaration specifiers of the function. */
  private GNode declarationSpecifiers = null;

  /** A list of before advice instrumenting on the aspect function. */
  private List<C4BeforeAdvice> beforeAdvice = null;

  /** A list of around advice instrumenting on the aspect function. */
  private List<C4AroundAdvice> aroundAdvice = null;

  /** Is the function's return type void? */
  private boolean isVoidReturnType = false;

  /** The names of the aspect that have before advice. */
  List<String> beforeAdviceAspectNames = null;

  /** The names of the aspect that have after advice. */
  List<String> afterAdviceAspectNames = null;

  /** The names of the aspect that have around advice. */
  List<String> aroundAdviceAspectNames = null;

  /** The variable name of the return value. */
  private final String returnValVariableName = "__return_val";

  /** The name of the label that will be placed at the beginning of after advicc. */
  private final String afterAdviceBeginLabel = "__after_advice_begin";

  /**
   * A visitor to visit the declarator of a function.
   */
  class DeclaratorVisitor extends Visitor {
    /** The function name. */
    private String functionName = null;

    /** The number of pointer (*) appears iin the function delcarator. */
    private int pointerCount = 0;

    /** Determines if this is a void function. */
    private boolean isVoid = false;

    /**
     * Extracts the function name.
     * 
     * @param n
     *          The simple declarator node.
     * @return The node that was passed in without modification.
     */
    public Node visitSimpleDeclarator(GNode n) {
      this.functionName = Token.cast(n.get(0));
      return n;
    }

    /**
     * Visits the pointer node and increment the pointer count.
     * 
     * @param n
     *          The pointer node.
     * @return The same pointer node without modification.
     */
    public Node visitPointer(GNode n) {
      this.pointerCount++;
      this.isVoid = false;

      return n;
    }

    /**
     * Visists a declaration specifiers. This is used to look for the VoidTypeSpecifier node. If it
     * finds a void type, it sets the flag.
     * 
     * @param n
     *          A declaration specifier.
     * @return The declaration specifier node.
     */
    public Node visitDeclarationSpecifiers(GNode n) {
      for (Object child : n) {
        if (child instanceof Node)
          if (((Node) child).hasName("VoidTypeSpecifier"))
            this.isVoid = true;

      }

      return n;
    }

    /**
     * General visit method. It dispatches visit methods to visit the children.
     * 
     * @param n
     *          A node.
     * @return Same node that was passed in.
     */
    public Node visit(Node n) {
      for (Object child : n) {
        if (child instanceof Node)
          dispatch((Node) child);
      }

      return n;
    }
    
    /**
     * Stops traversing into the parameter list. It's not of interest.
     * 
     * @param n The parameter type list.
     * @return Unmodified parameter type list.
     */
    public Node visitParameterTypeList(GNode n) {
      return n;
    }
    
    /**
     * A wrapper dispatch method in order to dispatch on two nodes.
     * @param functionDeclarator
     * @param functionDeclarationSpecifier
     */
    public void dispatch(Node functionDeclarator, Node functionDeclarationSpecifier) {
      dispatch(functionDeclarationSpecifier);
      dispatch(functionDeclarator);
    }

    /**
     * Returns the function name.
     * 
     * @return The function name.
     */
    public String getFunctionName() {
      return this.functionName;
    }

    /**
     * Returns the pointer count.
     * 
     * @return An integer indicating the pointer count.
     */
    public int getPointerCount() {
      return this.pointerCount;
    }

    /**
     * Returns true if this function returns void.
     * 
     * @return True or false.
     */
    public boolean isVoid() {
      return this.isVoid;
    }

  } // class DeclaratorVisitor

  /**
   * Responsibles for creating a return value varaible declaration.
   */
  class ReturnValueVariableCreator extends Visitor {

    /** The declaration specifier for the declaration. */
    private GNode declarationSpecifier = null;

    /** The declarator for the declaration. */
    private GNode declarator = null;

    /**
     * Extracts and renames the simple declarator from a FunctionDeclarator.
     * 
     * @param n
     *          A function declarator.
     * @return A simple declarator.
     */
    public GNode visitFunctionDeclarator(GNode n) {
      GNode simpleDeclarator = GNode.cast(n.get(0));
      assert simpleDeclarator.hasName("SimpleDeclarator") : "The first child of the function declarator must be a "
                                                            + "simple declarator. Something has changed! File a bug.";
      return GNode.create("SimpleDeclarator", returnValVariableName);
    }

    /**
     * Replaces the declaration specifier with a new one.
     * 
     * @param n
     *          The old declaratioin specifier.
     * @return A new declaration specifier.
     */
    public GNode visitDeclarationSpecifiers(GNode n) {
      return this.declarationSpecifier;
    }

    /**
     * Replaces the declarator with a new one.
     * 
     * @param n
     *          The old declarator.
     * @return A new declarator.
     */
    public GNode visitInitializedDeclarator(GNode n) {
      n.set(1, declarator);

      return n;
    }

    /**
     * Creates a return value variable declaration.
     * 
     * @param declarationSpecifier
     *          The declaration specifier.
     * @param theDeclarator
     *          The declarator.
     * @return A declaration.
     */
    public GNode createReturnVariable(final GNode declarationSpecifier, final GNode theDeclarator) {
      GNode declaration = GNode.cast(cFactory.createDeclaration(returnValVariableName));
      this.declarationSpecifier = declarationSpecifier;
      // Converts the function declarator into something this visitor can use.
      GNode workingCopyDeclarator = GNode.create(theDeclarator);
      this.declarator = GNode.cast(dispatch(workingCopyDeclarator));

      // Replace the declaration specifier and delcarator.
      declaration = GNode.cast(dispatch(declaration));

      return declaration;
    }

    /**
     * General visit method.
     * 
     * @param n
     * @return
     */
    public Node visit(Node n) {
      for (int i = 0; i < n.size(); ++i) {
        Object o = n.get(i);

        if (o instanceof Node) {
          n.set(i, dispatch((Node) o));
        }
      }

      return n;
    } // visit

  } // class ReturnValueVariableCreator

  /**
   * The transformer responsible for transforming the function body.
   */
  class AspectFunctionBodyTransformer extends Visitor implements IC4Transformer {

    /** The aspect function body node. */
    GNode node = null;

    /**
     * Constructor.
     * 
     * @param theNode
     *          The aspect function body node.
     */
    public AspectFunctionBodyTransformer(GNode theNode) {
      assert theNode.hasName("AspectFunctionBody") : "The node should have name `AspectFunctionBody'";
      this.node = theNode;
    }

    /**
     * Visits and transforms return statement.
     * 
     * @param n
     *          A return statement.
     * @return A transformed return statement.
     */
    public Node visitReturnStatement(GNode n) {
      if (hasAfterAdvice()) {
        // There is after advice and the function does NOT return void. As a result,
        // transforms all return statements with an assignment and a goto.
        if (!isVoidReturnType) {
          assert !n.isEmpty() : "Missing return value.";
          GNode leftVal = GNode.create("PrimaryIdentifier", returnValVariableName);
          GNode replacement = GNode.cast(cFactory.createAfterAdviceReturnReplacement(leftVal, (Node) n.get(0),
                                                                                     afterAdviceBeginLabel));
          return replacement;
        } else {
          assert n.isEmpty() : "There should not be any return value.";
          // Skip the assignment. Replace the return statement with a goto.
          GNode replace = GNode.cast(cFactory.createGoto(afterAdviceBeginLabel));

          return replace;
        }
      }

      return n;
    }

    /**
     * General visit method.
     * 
     * @param n
     *          A node.
     * @return Transformed or original node.
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
     * Transforms the aspect function body.
     * 
     * @return A list of transformed nodes (usually just one).
     */
    public List<GNode> transform() {
      List<GNode> transformedNode = new ArrayList<GNode>();
      Object result = dispatch(node);
      assert GNode.test(result) : "The return type must be a GNode.";

      transformedNode.add(GNode.cast(result));

      return transformedNode;
    }

  } // class AspectFunctionBodyTransformer

  /**
   * The default constructor.
   * 
   * @param theNode
   *          A reference to the generic node.
   */
  public C4AspectFunctionTransformer(GNode theNode, boolean debug) {
    assert theNode.hasName("AspectFunctionDefinition") : "The incoming node should have the name "
                                                         + "'AspectFunctionDefinition'";
    this.node = theNode;
    this.declarationSpecifiers = node.getGeneric(1);
    this.declarator = node.getGeneric(2);
    this.aspectCompoundStatement = node.getGeneric(4);
    this.beginningAdviceStatements = aspectCompoundStatement.getGeneric(0);
    this.aspectFunctionBody = aspectCompoundStatement.getGeneric(1);
    this.endingAdviceStatements = aspectCompoundStatement.getGeneric(2);
    // this.xformEngine = C4XFormEngine.getInstance();
    this.cFactory = new C4CFactory();

    // The names of aspects that the advice are associated with.
    beforeAdviceAspectNames = new ArrayList<String>();
    afterAdviceAspectNames = new ArrayList<String>();
    aroundAdviceAspectNames = new ArrayList<String>();

    beforeAdvice = new ArrayList<C4BeforeAdvice>();
    aroundAdvice = new ArrayList<C4AroundAdvice>();
  }

  /**
   * Extracts function detail.
   */
  private void extractFunctionDetails() {
    List<Object> results = null;

    // First get the function name.
    results = xformEngine.run(C4XFormQuery.GetFunctionName, this.declarator);
    assert results.size() == 1;
    functionName = Token.cast(GNode.cast(results.get(0)).get(0));
    System.out.println("Function Name: " + functionName);

    // Determine if this function returns void or not.
    assert (null != this.declarationSpecifiers && this.declarationSpecifiers.hasName("DeclarationSpecifiers"));
    GNode theType = GNode.cast(declarationSpecifiers.get(0));
    // Due to the fact that declarationSpecifiers does NOT reveal the complete type information.
    // So, the declarator is checked to make sure the function is returning a void but not a void *.
    isVoidReturnType = (theType.hasName("VoidTypeSpecifier") && this.declarator.hasName("FunctionDeclarator"));
    if (debug && isVoidReturnType)
      System.err.println("Function: " + functionName + " returns void.");
  }

  /**
   * Checks if there is any after advice.
   * 
   * @return True if there is one or more after advice. False, otherwise.
   */
  private boolean hasAfterAdvice() {
    return !this.endingAdviceStatements.isEmpty();
  }

  /**
   * Creates a variable declaration for the return value.
   * 
   * @return A declaration.
   */
  private GNode createReturnValueDeclaration() {
    GNode declaration = new ReturnValueVariableCreator().createReturnVariable(this.declarationSpecifiers,
                                                                              this.declarator);
    return declaration;
  }

  /**
   * Creates a label.
   * 
   * @return A label node.
   */
  private GNode createAfterAdviceLabel() {
    // GNode gotoLabel = GNode.cast(cFactory.createLabel(this.afterAdviceBeginLabel));
    return GNode.cast(cFactory.createLabel(this.afterAdviceBeginLabel));
    // return gotoLabel;
  }

  /**
   * Visits and transforms a before advice.
   * 
   * @param n
   *          The before advice.
   * @return A transformed before advice.
   */
  public Node visitBeforeAdviceStatement(GNode n) {
    C4BeforeAdvice beforeAdvice = new C4BeforeAdvice(debug, n, Token.cast(n.get(1)), this.functionName);

    return beforeAdvice.transform().get(0);
  }

  /**
   * Visits and transforms an after advice.
   * 
   * @param n
   *          The after advice
   * @return A transformed after advice.
   */
  public Node visitAfterAdviceStatement(GNode n) {
    C4AfterAdvice afterAdvice = new C4AfterAdvice(debug, n, Token.cast(n.get(1)), this.functionName);

    return afterAdvice.transform().get(0);
  }

  /**
   * Visits the advice inside the beginning advice statement list.
   * 
   * @param n
   *          The beginning advice statement list gnode.
   * @return Transformed before or around advice.
   */
  public Node visitBeginningAdviceStatementList(GNode n) {
    if (debug)
      System.err.println("Transforming beginning advice statement list ...");

    for (int i = 0; i < n.size(); ++i) {
      Object o = n.get(i);

      if (o instanceof Node)
        n.set(i, dispatch((Node) o));
    }

    return n;
  }

  /**
   * Visits the after advice inside the ending advice statement list.
   * 
   * @param n
   *          The ending advice statement list gnode.
   * @return Transformed after advice.
   */
  public Node visitEndingAdviceStatementList(GNode n) {
    if (debug)
      System.err.println("Transforming ending advice statement list ...");

    // Create a copy of the
    GNode newEndingAdviceStatement = GNode.create("EndingAdviceStatementList");
    if (!n.isEmpty())
      newEndingAdviceStatement.add(createAfterAdviceLabel());

    for (int i = 0; i < n.size(); ++i) {
      Object o = n.get(i);

      if (o instanceof Node)
        newEndingAdviceStatement.add(dispatch((Node) o));
    }

    // Add a return statement.
    if (!isVoidReturnType) {
      newEndingAdviceStatement.add(GNode.cast(cFactory.createReturnWithVal(GNode.create("PrimaryIdentifier",
                                                                                        returnValVariableName))));
    }

    return newEndingAdviceStatement;
  }

  /**
   * Visits and transforms the function body.
   * 
   * @param n
   *          The function body.
   * @return A transformed function body.
   */
  public GNode visitAspectFunctionBody(GNode n) {
    AspectFunctionBodyTransformer bodyTransformer = new AspectFunctionBodyTransformer(n);

    return bodyTransformer.transform().get(0);
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
        n.set(i, dispatch((Node) o));
      }
    }

    return n;
  }

  /**
   * Visits and transforms an aspect compound statement.
   * 
   * @param n
   *          An aspect compound statement node.
   * @return A transformed compound statement.
   */
  public Node visitAspectCompoundStatement(GNode n) {
    // Add a variable declaration for the return value only if there is after advice and
    // the function does not return void.
    if (hasAfterAdvice() && !isVoidReturnType)
      n.add(0, createReturnValueDeclaration());

    for (int i = 0; i < n.size(); ++i) {
      Object o = n.get(i);

      if (o instanceof Node)
        n.set(i, dispatch((Node) o));
    }

    return n;
  }

  /**
   * Performs the transformation from aspect C to pure C.
   * 
   * @return
   */
  public List<GNode> transform() {
    if (debug)
      System.err.println("Transforming aspect function definition.");

    List<GNode> transformedNode = new ArrayList<GNode>();

    // Extract information about the funciton using visitor.
    DeclaratorVisitor dc = new DeclaratorVisitor();
    dc.dispatch(this.declarator, this.declarationSpecifiers);
    this.functionName = dc.getFunctionName();
    this.isVoidReturnType = dc.isVoid();
    if (debug)
      System.err.println("Function Name: " + this.functionName);

    // Dispatch the visitors to visit the compound statement and transform the advice.
    dispatch(this.aspectCompoundStatement);

    transformedNode.add(this.node);
    return transformedNode;
  }

  /**
   * Performs the transformation from aspect C to pure C.
   */
  public List<GNode> oldTransform() {
    String aspectName = null;
    String curNodeName = null;
    List<GNode> transformedNodes = null;

    // Getting the function's detail.
    extractFunctionDetails();

    if (debug)
      System.err.println("Starting to transform beginning advice statements.");

    // Loop over the beginning advice statements.
    for (int i = 0; i < beginningAdviceStatements.size(); ++i) {
      GNode anAdviceNode = GNode.cast(beginningAdviceStatements.get(i));
      curNodeName = anAdviceNode.getName();
      aspectName = anAdviceNode.getString(1);

      // Look for either before advice or around advice.
      if (curNodeName.equals("BeforeAdviceStatement")) {
        C4BeforeAdvice theBeforeAdvice = new C4BeforeAdvice(debug, anAdviceNode, aspectName, functionName);
        beforeAdvice.add(theBeforeAdvice);
        transformedNodes = theBeforeAdvice.transform();
        beginningAdviceStatements.set(i, transformedNodes.get(0));
      } else if (curNodeName.equals("AroundAdviceStatement")) {
        C4AroundAdvice theAroundAdvice = new C4AroundAdvice(debug, anAdviceNode, aspectFunctionBody, aspectName,
                                                            functionName);
        aroundAdvice.add(theAroundAdvice);
        transformedNodes = theAroundAdvice.transform();
      }
    } // for

    // TODO Add a label so around advice can jump to the list of the after advice.

    for (int i = 0; i < endingAdviceStatements.size(); ++i) {
    } // for

    return null;
  } // transform()
}
