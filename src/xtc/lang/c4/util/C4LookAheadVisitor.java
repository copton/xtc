package xtc.lang.c4.util;

import xtc.tree.Node;
import xtc.tree.Visitor;

/**
 * An utility class that can determine if a specified child exists.
 * 
 * @author Marco Yuen
 */
public class C4LookAheadVisitor extends Visitor {
  /** The child's name that the visitor is looking for. */
  String targetName = "";

  /** The result of the search. */
  boolean searchResult = false;

  /**
   * Determines if the specified node name exists in the given node.
   * 
   * @param n
   *          The node.
   * @param childName
   *          The name of the target node.
   * @return True, if there childName exists.
   */
  public boolean hasChild(Node n, String childName) {
    targetName = childName;
    dispatch(n);
    return this.searchResult;
  }

  /**
   * General visit method.
   * 
   * @param n
   *          A node.
   */
  public void visit(Node n) {
    for (int i = 0; i < n.size(); ++i) {
      Object o = n.get(i);

      if (o instanceof Node) {
        Node theNode = (Node) o;
        if (theNode.hasName(targetName)) {
          searchResult = true;
          return;
        }
        dispatch(theNode);
      }
    } // for
  } // visit

}
