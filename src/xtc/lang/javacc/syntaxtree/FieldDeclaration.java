//
// Generated by JTB 1.2.2
//

package xtc.lang.javacc.syntaxtree;

/**
 * Grammar production.
 * <pre>
 * f0 -> ( "public" | "protected" | "private" | "static" | "final" | "transient" | "volatile" )*
 * f1 -> Type()
 * f2 -> VariableDeclarator()
 * f3 -> ( "," VariableDeclarator() )*
 * f4 -> ";"
 * </pre>
 */
public class FieldDeclaration implements Node {
   public NodeListOptional f0;
   public Type f1;
   public VariableDeclarator f2;
   public NodeListOptional f3;
   public NodeToken f4;

   public FieldDeclaration(NodeListOptional n0, Type n1, VariableDeclarator n2, NodeListOptional n3, NodeToken n4) {
      f0 = n0;
      f1 = n1;
      f2 = n2;
      f3 = n3;
      f4 = n4;
   }

   public FieldDeclaration(NodeListOptional n0, Type n1, VariableDeclarator n2, NodeListOptional n3) {
      f0 = n0;
      f1 = n1;
      f2 = n2;
      f3 = n3;
      f4 = new NodeToken(";");
   }

   public void accept(xtc.lang.javacc.visitor.Visitor v) {
      v.visit(this);
   }
   public Object accept(xtc.lang.javacc.visitor.ObjectVisitor v, Object argu) {
      return v.visit(this,argu);
   }
}

