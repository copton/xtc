/*
 * xtc - The eXTensible Compiler
 * Copyright (C) 2007 New York University
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301,
 * USA.
 */

package xtc.lang.overlog;

import xtc.tree.GNode;
import xtc.tree.Node;
import xtc.tree.Visitor;

/**
 * A pretty printer for the Overlog, adapted from
 * the simply typed lambda calculus pretty printer.
 *
 * @author Robert Soule
 * @version $Revision: 1.14 $
 */
public class OverlogPrinter extends Visitor {

  /** The printer. */
  protected final xtc.tree.Printer printer;

  /**
   * Create a new printer for Overlog
   *
   * @param printer The printer.
   */
  public OverlogPrinter(xtc.tree.Printer printer) {
    this.printer = printer;
    printer.register(this);
  }

  /**
   * Generic catch-all visit method
   */
  public void visit(final GNode n) {
    for (Object o : n) {
      if (o instanceof Node) {
        dispatch((Node)o);
      } else if (Node.isList(o)) {
        iterate(Node.toList(o));
      }
    }
  }

  public void visitRule(GNode n) {
    int index = 0;
    if (0 == n.getNode(0).getName().compareTo("RuleIdentifier")) {
        index++;
        printer.p(n.getNode(0).getString(0) + " ");
    } 
    if (n.getString(1) != null) {
        printer.p("delete ");
    }
    index++;
    // dispatch to the "event" node
    dispatch(n.getNode(index));
    printer.p(" :- ");
    index++;
    // dispatch to the "actions"
    int numActions = n.getList(index).size();
    for (Node child : n.<Node>getList(index)) {
      dispatch(child);
      numActions--;
      if (numActions > 0) {
        printer.p(", ");
      }
    }
    printer.pln(".");
  }

  public void visitMaterialization(GNode n) { 
    int index = 0;
    printer.p("materialize(" + n.getNode(index).getString(0));
    index++;
    for(; index < 4; index++) {
      printer.p(", ");
      dispatch(n.getNode(index));
    }
    printer.pln(").");
  }

  public void visitPrimaryKeys(GNode n) {
    printer.p("keys(");
    int numKeys = n.getList(0).size();
    for (Node key : n.<Node>getList(0)) {
      dispatch(key);
      numKeys--;
      if (numKeys > 0) {
        printer.p(", ");
      }
    }
    printer.p(")");
  }

  public void visitTupleObservation(GNode n) { 
    printer.pln("watch(" + n.getNode(0).getString(0) + ").");
  }

  public void visitFlowObservation(GNode n) {
    printer.pln("watchmod(" + n.getNode(0).getString(0) + ").");
  }

  public void visitExternalization(GNode n) {
    printer.pln("stage(" + ").");
  }

  public void visitGenericFact(GNode n) {
    dispatch(n.getNode(0));
    printer.pln("."); 
  }
 
  public void visitExpression(GNode n) {
    dispatch(n.getNode(0));
    printer.p(" " + n.getString(1) + " ");
    dispatch(n.getNode(2));
  } 

  public void visitLogicalOrExpression(GNode n) {
    dispatch(n.getNode(0));
    printer.p(" || ");
    dispatch(n.getNode(2));
  }

  public void visitLogicalAndExpression(GNode n) {
    dispatch(n.getNode(0));
    printer.p(" && ");
    dispatch(n.getNode(2));
  } 

  public void visitEqualityExpression(GNode n) { 
    dispatch(n.getNode(0));
    printer.p(" " + n.getString(1) + " ");
    dispatch(n.getNode(2));
  }

  public void visitRelationalExpression(GNode n) {
    dispatch(n.getNode(0));
    printer.p(" " + n.getString(1) + " ");
    dispatch(n.getNode(2));
  }

  public void visitShiftExpression(GNode n) { 
    dispatch(n.getNode(0));
    printer.p(" " + n.getString(1) + " ");
    dispatch(n.getNode(2));
  }

  public void visitAdditiveExpression(GNode n) { 
    dispatch(n.getNode(0));
    printer.p(" " + n.getString(1) + " ");
    dispatch(n.getNode(2));
  }

  public void visitMultiplicativeExpression(GNode n) {
    dispatch(n.getNode(0));
    printer.p(" " + n.getString(1) + " ");
    dispatch(n.getNode(2));
  }

  public void visitLogicalNegationExpression(GNode n) {
    printer.p("!");
    dispatch(n.getNode(0));
  }

  public void visitInclusiveExpression(GNode n) { 
    dispatch(n.getNode(0));
    printer.p(" in ");
    dispatch(n.getNode(2));
  }

  public void visitRangeExpression(GNode n) {
    printer.p(n.getString(0) + " ");
    dispatch(n.getNode(1));
    printer.p(", " );
    dispatch(n.getNode(2));
    printer.p(" " + n.getString(3));
  }

  public void visitPostfixExpression(GNode n) { 
    dispatch(n.getNode(0));
    if (n.size() > 0) {
      dispatch(n.getNode(1));
    }
  }

  public void visitArguments(GNode n) { 
    printer.p("(");
    if (n.size() == 1) {
      int numArgs = n.getList(0).size();
      for (Node arg : n.<Node>getList(0)) {
        dispatch(arg);
        numArgs--;
        if (numArgs > 0) {
          printer.p(", ");
        }
      }
    } 
    printer.p(")");
  }

  public void visitVectorExpression(GNode n) { 
    printer.p("[");
    printer.p("]");
  }

  public void visitMatrixExpression(GNode n) { 
    printer.p("{");
    printer.p("}");
  }

  public void visitMatrixEntry(GNode n) { 
    printer.p("{");
    printer.p("}");
  }

  public void visitParenthesizedExpression(GNode n) { 
    printer.p("(");
    dispatch(n.getNode(0));
    printer.p(")");
  }

  public void visitTuple(GNode n) {
    printer.p(n.getNode(0).getString(0) + "("); 
    int numTerms = n.getList(1).size();
    for (Node term : n.<Node>getList(1)) {
      dispatch(term);
      numTerms--;
      if (numTerms > 0) {
        printer.p(",");
      }
    }
    printer.p(")"); 
  }

  public void visitAggregate(GNode n) {
    dispatch(n.getNode(0));
    printer.p("<");
    if (n.get(1) instanceof String) {
       printer.p("*");
    } else {
      dispatch(n.getNode(1));
    }
    printer.p(">");
  }

  public void visitLocationIdentifier(final GNode n) {
    printer.p(n.getString(0));
  }

  public void visitFunctionIdentifier(final GNode n) {
    printer.p(n.getString(0));
  }

  public void visitAggregateIdentifier(final GNode n) {
    printer.p(n.getString(0));
  }

  public void visitVariableIdentifier(final GNode n) {
    printer.p(n.getString(0));
  }

  public void visitUnnamedIdentifier(final GNode n) {
    printer.p("_");
  }

  public void visitFloatingPointConstant(final GNode n) {
    printer.p(n.getString(0));
  }

  public void visitIntegerConstant(final GNode n) {
    printer.p(n.getString(0));
  }

  public void visitStringConstant(final GNode n) {
    printer.p("\"" + n.getString(0) + "\"");
  }

  public void visitBooleanConstant(final GNode n) {
    printer.p(n.getString(0));
  }

  public void visitInfinityConstant(final GNode n) {
    printer.p("infinity");
  }

  public void visitNullConstant(final GNode n) {
    printer.p("null");
  }

  public void visitLocationConstant(final GNode n) {
    printer.p(n.getString(0) + ":" + n.getString(1));
  }
}
