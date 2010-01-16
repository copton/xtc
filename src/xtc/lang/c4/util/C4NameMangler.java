package xtc.lang.c4.util;

import java.util.List;

import xtc.lang.c4.C4CFactory;
import xtc.tree.GNode;
import xtc.tree.Node;
import xtc.tree.Token;
import xtc.tree.Visitor;

/**
 * Mangles symbols using visitors.
 * 
 * @author Marco Yuen
 */
public class C4NameMangler extends Visitor {

  /** The debug flag. */
  private boolean debug = false;

  /** The binding to use for name mangling. */
  private C4Binding bindings = null;

  /** The C4 C factory. */
  private C4CFactory cFactory = null;

  /** The look ahead visitor. */
  private C4LookAheadVisitor lookAhead = null;

  /**
   * Visits a structure instance declaration.
   * 
   * @author Marco Yuen
   */
  class StructureTypeReferenceVisitor extends Visitor {
    /** The fields that was introduced. */
    List<String> fields = null;

    /**
     * Visits a StructureTypeReference and tries to get a list of introduced fields.
     * 
     * @param n
     *          A StructureTypeReference node.
     * @return Same node.
     */
    public Node visitStructureTypeReference(GNode n) {
      String structTag = Token.cast(n.get(1));
      // XXX This is a little sketchy. I am considering a new bindings for struct.
      // Check whether the structure tag is a structure type introduced by global advice or struct
      // intro advice.
      if (bindings.isStructType(structTag)) // struct intro
        fields = bindings.getStructTypeFields(structTag);
      else if (bindings.isDefined(structTag)) // global advice
        n.set(1, bindings.getMangledDeclarator(structTag));

      return n;
    }

    /**
     * Visits the new structure instances and adds the new instances to the bindings.
     * 
     * @param n
     *          The name of the new structure instance.
     * @return The same node.
     */
    public Node visitSimpleDeclarator(GNode n) {
      if (null != fields) {
        String instName = Token.cast(n.get(0));
        if (debug)
          System.err.printf("C4NameManger - adding new structure instance %s ...", instName);
        bindings.addStructInstBinding(instName, fields);
      }

      return n;
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
      } // for

      return n;
    }

  } // class StructureTypeReferenceVisitor

  /**
   * A visitor for structure access.
   * 
   * @author Marco Yuen
   */
  class StructureComponentSelectionVisitor extends Visitor {
    /** The number of levels of component selection. */
    private int componentLevel = 0;

    /** The current field name. */
    private String fieldName = "";

    /** The current identifier. */
    private boolean isDirect = false;

    /**
     * Visits and expands a direct component selection.
     * 
     * @param n
     *          A direct component selection GNode.
     * @return An expanded direct component selection.
     */
    public Node visitDirectComponentSelection(GNode n) {
      ++componentLevel;
      this.isDirect = true;
      this.fieldName = Token.cast(n.get(1));

      for (int i = 0; i < n.size(); ++i) {
        Object o = n.get(i);

        if (o instanceof Node)
          n.set(i, dispatch((Node) o));
      }

      return n;
    }

    /**
     * Visits and expands an indirect component selection.
     * 
     * @param n
     *          An indirect component selection GNode.
     * @return An expaned indirect component selection.
     */
    public Node visitIndirectComponentSelection(GNode n) {
      ++componentLevel;
      this.isDirect = false;
      this.fieldName = Token.cast(n.get(1));

      for (int i = 0; i < n.size(); ++i) {
        Object o = n.get(i);

        if (o instanceof Node) {
          Node theNode = (Node) o;
          n.set(i, dispatch(theNode));
          if (theNode.hasName("PrimaryIdentifier")) {
            String structInst = Token.cast(theNode.get(0));
            String fieldName = Token.cast(n.get(1));
            List<String> introducedFields = bindings.getStructInstFields(structInst);

            if (null != introducedFields && introducedFields.contains(fieldName)) {
              if (debug)
                System.err.printf("C4NameManger: Trying to access a field, %s, that was introduced by an advice.\n",
                                  fieldName);
              // Create an indirect structure access first then a direct access. Utimately this
              // transforms x->n to x->a.n
              GNode indirectStructAccess = GNode
                                                .cast(cFactory
                                                              .createIndirectStructFieldAccess(theNode,
                                                                                               bindings.getAspectName())
                                                              .get(0));
              GNode structAccess = GNode.cast(cFactory.createDirectStructFieldAccess(indirectStructAccess, fieldName)
                                                      .get(0));

              // Completely replace the indirect component selection.
              return structAccess;
            }
          } // if (theNode.hasName("PrimaryIdentifier"))
        } // if (o instanceof Node)
      }

      return n;
    }

    /**
     * Visits and mangles primary identifier.
     * 
     * @param n
     *          The primary identifier.
     * @return The mangled primary identifier.
     */
    public Node visitPrimaryIdentifier(GNode n) {
      String structInst = Token.cast(n.get(0));
      List<String> introducedFields = bindings.getStructInstFields(structInst);

      if (bindings.isDefined(structInst))
        n.set(0, bindings.getMangledDeclarator(structInst));
      else {
        if (null != introducedFields && introducedFields.contains(fieldName)) {
          if (isDirect) {
            GNode structAccess = GNode.cast(cFactory.createDirectStructFieldAccess(n, bindings.getAspectName()).get(0));

            return structAccess;
          } // if (isDirect)
        } // if (null != introducedFields ...)
      }

      return n;
    }

    /**
     * General visit method.
     * 
     * @param n
     *          The node to visit.
     * @return The expanded component selection.
     */
    public Node visit(Node n) {
      for (int i = 0; i < n.size(); ++i) {
        Object o = n.get(i);

        if (o instanceof Node) {
          n.set(i, dispatch((Node) o));
        }
      } // for

      return n;
    }
  } // class DirectComponentVisitor

  /**
   * A visitor for expression statements.
   * 
   * @author Marco Yuen
   */
  class ExpressionVisitor extends Visitor {
    /**
     * Mangles the simple declarator in a declaration.
     * 
     * @param n
     *          The simple declarator gnode.
     * @return A mangled declarator.
     */
    public Node visitPrimaryIdentifier(GNode n) {
      String declarator = Token.cast(n.get(0));

      if (bindings.isDefined(declarator)) {
        String mangledDeclarator = bindings.getMangledDeclarator(declarator);
        if (debug)
          System.err.printf("Replacing %s with %s", declarator, mangledDeclarator);
        n.set(0, mangledDeclarator);
      }

      return n;
    }

    /**
     * Visits and expands a direct structure selection (e.g. someStruct.field).
     * 
     * @param n
     *          The direct component selection.
     * @return Expanded direct component selection.
     */
    public Node visitDirectComponentSelection(GNode n) {
      return (Node) new StructureComponentSelectionVisitor().dispatch(n);
    }

    /**
     * Visits and expands an indirect structure selection (e.g. someStruct->field).
     * 
     * @param n
     *          The indirect component selection.
     * @return Expanded indirect component selection.
     */
    public Node visitIndirectComponentSelection(GNode n) {
      return (Node) new StructureComponentSelectionVisitor().dispatch(n);
    }

    /**
     * The general visit method.
     * 
     * @param n
     *          A node.
     * @return The same node.
     */
    public Node visit(Node n) {
      for (int i = 0; i < n.size(); ++i) {
        Object o = n.get(i);

        if (o instanceof Node) {
          n.set(i, dispatch((Node) o));
        }
      } // for

      return n;
    } // visit
  } // class ExpressionVisitor

  /**
   * Constructor
   * 
   * @param dFlag
   *          The debug flag.
   * @param theBinding
   *          The binding to use for name mangling.
   */
  public C4NameMangler(boolean dFlag, C4Binding theBinding) {
    this.debug = dFlag;
    this.bindings = theBinding;
    this.cFactory = C4CFactoryWrapper.getInstance();
    this.lookAhead = new C4LookAheadVisitor();
  }

  /**
   * Visits the expression statement and tries to mangle the primary identifier.
   * 
   * @param n
   *          The expression statement node.
   * @return An expression statement node with mangled symbols (if any).
   */
  public Node visitExpressionStatement(GNode n) {
    Node mangledNode = (Node) new ExpressionVisitor().dispatch(n);

    return mangledNode;
  }

  /**
   * Visits a declaration.
   * 
   * @param n
   *          A declaration node
   * @return Same node.
   */
  public Node visitDeclaration(GNode n) {
    // Checks if there is a structure instance declaration. Only record the struct instance when it
    // is of one of the struct's with advice.
    if (lookAhead.hasChild(n, "StructureTypeReference"))
      return (Node) new StructureTypeReferenceVisitor().dispatch(n);

    return n;
  }

  /**
   * Visits and mangles all other primary identifiers.
   * 
   * @param n
   *          Primary identifier.
   * @return Mangled primary identifier.
   */
  public Node visitPrimaryIdentifier(GNode n) {
    String declarator = Token.cast(n.get(0));

    if (bindings.isDefined(declarator)) {
      String mangledDeclarator = bindings.getMangledDeclarator(declarator);
      if (debug)
        System.err.printf("Replacing %s with %s", declarator, mangledDeclarator);
      n.set(0, mangledDeclarator);
    }

    return n;
  }

  /**
   * General visit method.
   * 
   * @param n
   *          The node to visit.
   * @return A node with all the names mangled.
   */
  public Node visit(Node n) {
    for (int i = 0; i < n.size(); ++i) {
      Object o = n.get(i);

      if (o instanceof Node) {
        if (debug)
          System.err.println("C4NameManger - Mangling: " + ((Node) o).getName());

        n.set(i, dispatch((Node) o));
      }
    }

    return n;
  }
}
