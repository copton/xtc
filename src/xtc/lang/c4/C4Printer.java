package xtc.lang.c4;

import xtc.lang.CPrinter;
import xtc.tree.GNode;
import xtc.tree.Node;
import xtc.tree.Printer;

/**
 * An extension to the basic C pretty printer. The C4 printer is able to handle some Aspect-related
 * nodes.
 * 
 * @author marcoy
 * @version $Revision: 1.2 $
 */
public class C4Printer extends CPrinter {
  /**
   * Create a new C4 printer.
   * 
   * @param printer
   *          The printer.
   */
  public C4Printer(Printer printer) {
    super(printer);
  }

  /**
   * Create a new C4 printer.
   * 
   * @param printer
   *          The printer.
   * @param lineUp
   *          The flag for whether to line up declarations and statements with their source
   *          locations.
   * @param gnuify
   *          The flag for whether to use GNU code formatting conventions.
   */
  public C4Printer(Printer printer, boolean lineUp, boolean gnuify) {
    super(printer, lineUp, gnuify);
  }

  /**
   * Pretty print the global introduction advice.
   * 
   * @param n
   *          The transformed global introduction advice.
   */
  public void visitGlobalIntroductionAdvice(GNode n) {
    // There is only one child after the transformation.
    GNode compStmts = GNode.cast(n.get(0));
    for (Object stmt : compStmts)
      printer.p((Node) stmt);
  }

  /**
   * A wrapper for aspect structure type definition.
   * 
   * @param n
   *          The aspect structure type definition node.
   */
  public void visitAspectStructureTypeDefinition(GNode n) {
    visitStructureTypeDefinition(n);
  }

  /**
   * A wrapper for aspect structure declaration list.
   * 
   * @param n
   *          The aspect structure declaration list.
   */
  public void visitAspectStructureDeclarationList(GNode n) {
    visitStructureDeclarationList(n);
  }

  /**
   * A wrapper for aspect structure declaration.
   * 
   * @param n
   *          The aspect structure declaration.
   */
  public void visitAspectStructureDeclaration(GNode n) {
    visitStructureDeclaration(n);
  }

  /**
   * A wrapper for aspect function definition.
   * 
   * @param n
   *          The aspect function definition.
   */
  public void visitAspectFunctionDefinition(GNode n) {
    visitFunctionDefinition(n);
  }

  /**
   * A wrapper for aspect compound statement.
   * 
   * @param n
   *          The aspect compound statement.
   */
  public void visitAspectCompoundStatement(GNode n) {
    visitCompoundStatement(n);
  }

  /**
   * A wrapper for beginning advice statement list.
   * 
   * @param n
   *          A beginning advice statement list.
   */
  public void visitBeginningAdviceStatementList(GNode n) {
    for (Object child : n) {
      if (child instanceof Node)
        dispatch((Node) child);
    }
  }

  /**
   * A wrapper for ending advice statement list.
   * 
   * @param n
   *          A ending advice statement list.
   */
  public void visitEndingAdviceStatementList(GNode n) {
    for (Object child : n) {
      if (child instanceof Node)
        dispatch((Node) child);
    }
  }

  /**
   * A wrapper for aspect function body.
   * 
   * @param n
   *          An aspect function body.
   */
  public void visitAspectFunctionBody(GNode n) {
    for (Object child : n) {
      if (child instanceof Node)
        dispatch((Node) child);
    }
  }

}
