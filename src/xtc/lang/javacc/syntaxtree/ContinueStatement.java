//
// Generated by JTB 1.2.2
//

package xtc.lang.javacc.syntaxtree;

/**
 * Grammar production.
 * <pre>
 * f0 -> "continue"
 * f1 -> [ &lt;IDENTIFIER&gt; ]
 * f2 -> ";"
 * </pre>
 */
public class ContinueStatement implements Node {
   public NodeToken f0;
   public NodeOptional f1;
   public NodeToken f2;

   public ContinueStatement(NodeToken n0, NodeOptional n1, NodeToken n2) {
      f0 = n0;
      f1 = n1;
      f2 = n2;
   }

   public ContinueStatement(NodeOptional n0) {
      f0 = new NodeToken("continue");
      f1 = n0;
      f2 = new NodeToken(";");
   }

   public void accept(xtc.lang.javacc.visitor.Visitor v) {
      v.visit(this);
   }
   public Object accept(xtc.lang.javacc.visitor.ObjectVisitor v, Object argu) {
      return v.visit(this,argu);
   }
}

