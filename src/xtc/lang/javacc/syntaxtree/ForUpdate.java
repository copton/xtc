//
// Generated by JTB 1.2.2
//

package xtc.lang.javacc.syntaxtree;

/**
 * Grammar production.
 * <pre>
 * f0 -> StatementExpressionList()
 * </pre>
 */
public class ForUpdate implements Node {
   public StatementExpressionList f0;

   public ForUpdate(StatementExpressionList n0) {
      f0 = n0;
   }

   public void accept(xtc.lang.javacc.visitor.Visitor v) {
      v.visit(this);
   }
   public Object accept(xtc.lang.javacc.visitor.ObjectVisitor v, Object argu) {
      return v.visit(this,argu);
   }
}

