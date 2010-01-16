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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import xtc.tree.GNode;
import xtc.tree.Node;
import xtc.tree.Visitor;

import xtc.type.Type;

import xtc.util.SymbolTable;

/**
* A visitor to perform normalization analysis on Overlog programs.
*
* @author Robert Soule
* @version $Revision: 1.28 $
*/
public final class Normalizer extends Visitor {

  /** A static counter for temporary variable names */
  private static int tempNameCount = 0;

  private Map<String, List<Node>> currentScope;

  /** the current tuple scope */
  private Node currentTuple;

  /** the current event in a rule */
  private Node currentEvent;

  /** the current index */
  private int currentTupleIndex;

  /** the mapping of variables to tuple locations */
  private SymbolTable table;

  /** The symbol table with type information of the AST before normalization */
  private SymbolTable typesTable;

  /** The symbol table after the normalization, with the new symbols. */
  private SymbolTable normalizedTypes;

  /** the rule head flag */
  private boolean inRuleHead = false;

  /** the assignment flag */
  private boolean inAssignment = false;

  /** The names of tuples declared materialized */
  private Set<String> materialized;


  /**
   * Create a new Overlog normalzer.
   *
   */
  public Normalizer() {
    // do nothing.
  }

  /**
   * Process the specified translation unit.
   *
   * @param unit The translation unit.
   * @param table The mapping of variables to tuple locations.
   * @param types The symbol table with type information of the 
   *        AST before normalization.
   * @param normalizedTypes The symbol table after the normalization, 
   *        with the new symbols.
   * @return root of the AST
   */
  public Node analyze(Node unit, 
                      SymbolTable table, 
                      SymbolTable types, 
                      SymbolTable normalizedTypes) {
    this.table = table;
    this.typesTable = types;
    this.normalizedTypes = normalizedTypes;
    materialized = new HashSet<String>();
    Set<String>  tupleNames = new HashSet<String>();
    new MaterializationChecker().analyze(unit, tupleNames, materialized);
    dispatch(unit);
    return unit;
  }

 // =========================================================================

  /**
   * Visit all nodes in the AST.
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
    String ruleName = "unknown";
    if ("RuleIdentifier".equals(n.getNode(0).getName())) {
      ruleName = n.getNode(0).getString(0);
      table.enter(ruleName);
      typesTable.enter(ruleName);
      normalizedTypes.enter(ruleName);
    } else {
      String freshname = table.freshName();
      table.enter(freshname);
      typesTable.enter(freshname);
      normalizedTypes.enter(freshname);
    }
    currentEvent = null;
    currentScope = new HashMap<String, List<Node>>();
    // dispatch to the "actions"
    for (Node child : n.<Node>getList(3)) {
      dispatch(child);
    } 
    for (List<Node> l : currentScope.values()) {
      List<Node> r = new ArrayList<Node>();
      for (Node a : l) {
        if (!a.equals(l.get(0))) {
          r.add(a);
        }
      }
      for (int i = 0; i < r.size(); i++) {
        n.getList(3).add(
          GNode.create("EqualityExpression", l.get(0), "==", r.get(i)));
      }    
    }
    // dispath to the event after the actions. 
    inRuleHead = true;
    dispatch(n.getNode(2));
    inRuleHead = false;
    currentScope = null;
    table.exit();
    typesTable.exit();
    normalizedTypes.exit();
    currentEvent = null;
  }

  public void visitTuple(final GNode n) {
    currentTuple = n;
    currentTupleIndex = 0;
    if (!materialized.contains(n.getNode(0).getString(0))) {
      currentEvent = n;
    }
    for (Node child : n.<Node>getList(1) ) {
      dispatch((Node)child);
      currentTupleIndex++;
    }
    currentTuple = null;
    currentTupleIndex = 0;
  }

  public void visitExpression(final GNode n) {
    inAssignment = true;
    dispatch(n.getNode(0));
    inAssignment = false;
    dispatch(n.getNode(2));
  }

  public void visitVariableIdentifier(final GNode n) {
    String tmp = "V" + tempNameCount;
    tempNameCount++;
    Node tmpVar = GNode.create("VariableIdentifier", tmp);
    Node m = n;
    if (!inAssignment) {
      m = tmpVar;
    }
    if (currentScope.containsKey(n.getString(0))) {
      currentScope.get(n.getString(0)).add(m); 
    } else {
      List<Node> nodes = new ArrayList<Node>();
      nodes.add(m);
      currentScope.put(n.getString(0), nodes);
    }
    String type = "Unknown";
    Type t = (Type)typesTable.current().lookup(n.getString(0)); 
    if (t != null) {
      if (t.isInteger()) {
        type = "Integer";
      } else if (t.isBoolean()) {
        type = "Boolean";
      } else if (t.isFloat()) {
        type = "Float";
      } else if (t.isInternal()) {
        if ("string constant".equals(t.getName())) {
          type = "String";
        } else if ("location".equals(t.getName())) {
          type = "NetAddr";
        }
      }
    }
    normalizedTypes.current().define(tmp, t);
    /* TODO FIX THIS MESS */
    if ((currentTuple != null) && (!inRuleHead)) {
      Node access = tupleAccess(
        GNode.create("QualifiedIdentifier", type),
        GNode.create("PrimaryIdentifier", currentTuple.getNode(0).getString(0)),
        GNode.create("IntegerLiteral", Integer.toString(currentTupleIndex))
      );
      table.current().define(tmp, access);

      // Check to see if the variable is defined in terms 
      // of the event.
      if (table.current().isDefined(n.getString(0))) {
        if (currentEvent != null) {
          String eventName = currentEvent.getNode(0).getString(0);
          if (eventName.equals(currentTuple.getNode(0).getString(0))) {
            table.current().define(n.getString(0), access);
          }
        } 
      } else {
        table.current().define(n.getString(0), access);
      }
    }
    else if (inAssignment) {
      table.current().define(n.getString(0), 
      GNode.create("VariableIdentifier", n.getString(0)));
    }
    else if (!inRuleHead) {
      table.current().define(tmp, 
        GNode.create("VariableIdentifier", n.getString(0)));
      n.set(0, tmp);
    }
  }

  private Node tupleAccess(Node type, Node name, Node index) {
    Node one = GNode.create("Type", type, null);
    Node two = GNode.create("Arguments", index);
    Node three = GNode.create("CallExpression", name, null, "getTerm", two);
    Node four = GNode.create("CastExpression", one, three);
    return four;
  }
}
