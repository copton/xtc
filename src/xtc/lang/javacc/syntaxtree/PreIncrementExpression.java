//
// Generated by JTB 1.2.2
//

package xtc.lang.javacc.syntaxtree;

/**
 * Grammar production.
 * <pre>
 * f0 -> "++"
 * f1 -> PrimaryExpression()
 * </pre>
 */
public class PreIncrementExpression implements Node {
   public NodeToken f0;
   public PrimaryExpression f1;

   public PreIncrementExpression(NodeToken n0, PrimaryExpression n1) {
      f0 = n0;
      f1 = n1;
   }

   public PreIncrementExpression(PrimaryExpression n0) {
      f0 = new NodeToken("++");
      f1 = n0;
   }

   public void accept(xtc.lang.javacc.visitor.Visitor v) {
      v.visit(this);
   }
   public Object accept(xtc.lang.javacc.visitor.ObjectVisitor v, Object argu) {
      return v.visit(this,argu);
   }
}

