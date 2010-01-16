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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import xtc.tree.GNode;
import xtc.tree.Node;
import xtc.tree.Visitor;

import xtc.util.Pair;
import xtc.util.Runtime;

/**
 * A visitor to perform concurrency analysis on Overlog programs.
 *
 * @author Robert Soule
 * @version $Revision: 1.11 $
 */
public final class ConcurrencyAnalyzer extends Visitor {

 // =========================================================================

  public class MaterializationChecker extends Visitor {

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
     */
    public void analyze(Node unit) {
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

    public void visitRule(final GNode n) {
      String ruleName = "unknown";
      if ("RuleIdentifier".equals(n.getNode(0).getName())) {
        ruleName = n.getNode(0).getString(0); 
      } 
      int numNonMaterialized = 0;
      Node fixpointNodeStart = null;
      // dispatch to the "actions"
      for (Node child : n.<Node>getList(3)) {
        if ("Tuple".equals(child.getName())) {
          String name = child.getNode(0).getString(0);
          if (!materialized.contains(name)) {
            // special case for periodic
            if (!name.equals("periodic")) {
              fixpointNodeStart = child;
              numNonMaterialized++;
            }
          }
        }
      }
      if (numNonMaterialized > 1) {
        runtime.error("Rule " + ruleName + " has " + 
          numNonMaterialized + " non-materialized tuples", n);  
      } else if (numNonMaterialized != 0) {
        fixpointInitiaters.add(fixpointNodeStart);
      }
    }
  }

 // =========================================================================

  /** The runtime. */
  private final Runtime runtime;

  /** The names of tuples declared materialized */
  private Set<String> materialized;

  /** The nodes that can initiate a fixpoint (i.e. events) */
  private Set<Node> fixpointInitiaters;

  /** Map a node to the tuples involved in its fixpoint computation */
  private Map<Node, List<Node>> closureMap;

  /** Map a node to the tuples in its read set */
  private Map<Node, Set<Node>> readSets;

  /** Map a node to the tuples in its write set */
  private Map<Node, Set<Node>> writeSets; 
 
  /**
   * Create a new Overlog analyzer.
   *
   * @param runtime The runtime.
   */
  public ConcurrencyAnalyzer(Runtime runtime) {
    this.runtime = runtime;
    materialized = new HashSet<String>();
    fixpointInitiaters = new HashSet<Node>();
    closureMap = new HashMap<Node, List<Node>>();
    readSets = new HashMap<Node, Set<Node>>();
    writeSets = new HashMap<Node, Set<Node>>();
  }

  /**
   * Process the specified translation unit.
   *
   * @param unit The translation unit.
   * @return root of the AST
   */
  public Node analyze(Node unit) {
    dispatch(unit);
    new MaterializationChecker().analyze(unit);
    unit = computeFixpoints(unit);
    return unit;
  }

 // =========================================================================

  /**
   * Return the event node of a rule if the rule is an external rule.
   * An external rule is a rule that results in a tuple being sent to
   * a non-local node. If the rule is not an external rule, this function
   * returns null.
   *
   * @param n a rule tuple node
   * @return the event node or null
   */
  private Node getExternal(final Node n) {
    // @fixme this method of recognizing external tuples only recognizes if the
    // names have changed. It doesn't check to see if the value has been passed.
    // i.e. tuple1(@NI, C) :- (tuple2(@SI, C), NI := SI.
    final String eventLocation = n.getNode(2).<Node>getList(1).
      get(0).getNode(0).getString(0);
    // dispatch to the "actions"
    for (Node child : n.<Node>getList(3)) {
      if ("Tuple".equals(child.getName())) {
        String actionLocation = child.<Node>getList(1).
          get(0).getNode(0).getString(0);
        if (!eventLocation.equals(actionLocation)) {
          return n.getNode(1);
        }
      }
    }
    return null;
  }

  /**
   * Return the event node of a rule if the rule is a materialized rule.
   * An materialized rule is a rule that results in a tuple being stored to
   * a local node. If the rule is not an materialized rule, this function
   * returns null.
   *
   * @param n a rule tuple node
   * @return the event node or null
   */
  private Node getMaterialized(final Node n) {
    final String name = n.getNode(2).getNode(0).getString(0);
    if (!materialized.contains(name)) {
      return null;
    }
    return n.getNode(2).<Node>getList(1).get(0);
  }

  /**
   * Modifies the root node of an Overlog AST by adding fact tuples 
   * for the read and write set of all the program's fixpoints.
   * 
   * @param root The root node of the AST
   * @param The modified root of the AST
   *
   */
  private Node computeFixpoints(Node root) {
    Iterator<Node> fixpointIterator = fixpointInitiaters.iterator();
    while (fixpointIterator.hasNext()) {
      Node node = fixpointIterator.next();
      Set<Node> visited = new HashSet<Node>();
      computeFixpoint(node, node, visited);
      root = addReadSet(root, node);
      root = addWriteSet(root, node);
    }
    return root;
  }
  
  /**
   * Modifies the root node of an Overlog AST by adding fact tuples 
   * for the read set of a single fixpoint.
   * 
   * @param root The root node of the AST
   * @param n The tuple that initiates the fixpoint
   * @param The modified root of the AST
   *
   */
  private Node addReadSet(Node root, final Node n) {
    if (!readSets.containsKey(n)) {
      return root;
    }
    Set<Node> reads = readSets.get(n);
    for (Node read : reads) {
      root = addReadWriteFact(root, n.getNode(0).getString(0), 
        read.getNode(0).getString(0), "R");
    }
    return root;
  }

  /**
   * Modifies the root node of an Overlog AST by adding fact tuples 
   * for the write set of a single fixpoint.
   * 
   * @param root The root node of the AST
   * @param n The tuple that initiates the fixpoint
   * @param The modified root of the AST
   *
   */
  private Node addWriteSet(Node root, final Node n) {
    if (!writeSets.containsKey(n)) {
      return root;
    }
    Set<Node> writes = writeSets.get(n);
    for (Node write : writes) {
      root = addReadWriteFact(root, n.getNode(0).getString(0), 
        write.getNode(0).getString(0), "W");
    }
    return root;
  }

 /**
   * Appends a nodes to the AST which contain information about the read write
   * write sets of the fixpoints in this tree.
   *
   * @param root The root node of the AST
   * @param fixpointName The name of the node that initiates the fixpoint
   * @param tupleName The name of the node in the read or write set
   * @param The type of fact being added, which is either "R" for read
   *        or "W" for write.
   */
  private Node addReadWriteFact(Node root, final String fixpointName, 
    final String tupleName, final String type) {
    final Node e = GNode.create("StringConstant", fixpointName);
    final Node a = GNode.create("StringConstant", tupleName);
    final Node t = GNode.create("StringConstant", type);
    Pair<Node> childList;
    childList = new Pair<Node>(e);
    childList.add(a);
    childList.add(t);
    final Node identifier = 
      GNode.create("RuleIdentifier", new String("concurrent"));
    final Node tuple = GNode.create("Tuple", identifier, childList);
    final Node fact = GNode.create("GenericFact", tuple);
    root = GNode.ensureVariable(GNode.cast(root));
    root = root.add(fact);
    return root;
  }

  /**
   * Performs a depth first search to discover all nodes reachable by
   * a fixpoint. This is a recursive function which adds to its visited
   * set parameter. 
   * 
   * @param n The node currently being explored in the search.
   * @param fixpointStart The node that initiates the fixpoint
   * @param visited The set of nodes already visited in this search. 
   * When calling this function, the set should be empty.
   *
   */
  private void computeFixpoint(Node n, 
    final Node fixpointStart, Set<Node> visited) {
    if (visited.contains(n)) {
      return;
    }
    visited.add(n);
    if (!closureMap.containsKey(n)) {
      return;
    } 
    List<Node> rules = closureMap.get(n);
    for (Node rule : rules) {
      Pair<Node> actions = rule.getList(3);
      for (Node action : actions) {
        if ("Tuple".equals(action.getName())) {
          if (action != fixpointStart) {
            if (readSets.containsKey(fixpointStart)) {
              readSets.get(fixpointStart).add(action);
            } else {
              Set<Node> reads = new HashSet<Node>();
              reads.add(action);
              readSets.put(fixpointStart, reads);
            }
          } 
        }
      }
      Node tmp = getExternal(rule);
      if (tmp != null) {
        if (writeSets.containsKey(fixpointStart)) {
          writeSets.get(fixpointStart).add(rule.getNode(1));
        } else {
          Set<Node> writes = new HashSet<Node>();
          writes.add(rule.getNode(2));
          writeSets.put(fixpointStart, writes);
          return;
        }
      }
      tmp = getMaterialized(rule); 
      if (tmp != null) {
        if (writeSets.containsKey(fixpointStart)) {
          writeSets.get(fixpointStart).add(rule.getNode(2));
        } else {
          Set<Node> writes = new HashSet<Node>();
          writes.add(rule.getNode(2));
          writeSets.put(fixpointStart, writes);
          return;
        }
      }
      computeFixpoint(rule.getNode(2), fixpointStart, visited);
    }    
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

  public void visitMaterialization(final GNode n) {
    final String name = n.getNode(0).getString(0);
    materialized.add(name); 
  }

  public void visitRule(final GNode n) {
    // dispatch to the "actions"
    for (Node child : n.<Node>getList(3)) {
      dispatch(child); 
      if ("Tuple".equals(child.getName())) {
        if (closureMap.containsKey(child)) {
          closureMap.get(child).add(n);
        } else {
          List<Node> rules = new ArrayList<Node>();
          rules.add(n);
          closureMap.put(child, rules);
        }
      }
    }
  }
}
