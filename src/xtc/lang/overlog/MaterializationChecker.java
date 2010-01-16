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

import java.util.Set;

import xtc.tree.GNode;
import xtc.tree.Node;
import xtc.tree.Visitor;

/**
 * A visitor to find materialized tuples.
 *
 * @author Robert Soule
 * @version $Revision: 1.5 $
 */
public class MaterializationChecker extends Visitor {

  /** The names of tuples declared materialized */
  private Set<String> materialized;

  /** The names of all tuples */
  private Set<String> tupleNames;


  /**
   * Create a new MaterializationChecker. Visits the AST
   * to ensure that the right hand side of a rule has no
   * more than one non-materialized tuple.
   *
   */
  public MaterializationChecker() {
    // do nothing
  }

  /**
   * Process the specified translation unit.
   *
   * @param unit The translation unit.
   * @param tupleNames the names of all tuples in the AST
   * @param materialized The names of materialized tuples
   */
  public void analyze(Node unit, 
                      Set<String> tupleNames, 
                      Set<String> materialized ) {
    this.tupleNames = tupleNames;
    this.materialized = materialized;
    dispatch(unit);
  }

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

  public void visitMaterialization(final GNode n) {
    final String name = n.getNode(0).getString(0);
    materialized.add(name);
  }

  public void visitTuple(final GNode n) {
    tupleNames.add(n.getNode(0).getString(0));
  }

  public void visitTupleDeclaration(final GNode n) {
    tupleNames.add(n.getNode(0).getString(0));
  }
}


