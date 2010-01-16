//
// Generated by JTB 1.2.2
//

package xtc.lang.javacc.syntaxtree;

/**
 * Grammar production.
 * <pre>
 * f0 -> ( "static" | "abstract" | "final" | "public" | "protected" | "private" | "strictfp" )*
 * f1 -> UnmodifiedClassDeclaration()
 * </pre>
 */
public class NestedClassDeclaration implements Node {
   public NodeListOptional f0;
   public UnmodifiedClassDeclaration f1;

   public NestedClassDeclaration(NodeListOptional n0, UnmodifiedClassDeclaration n1) {
      f0 = n0;
      f1 = n1;
   }

   public void accept(xtc.lang.javacc.visitor.Visitor v) {
      v.visit(this);
   }
   public Object accept(xtc.lang.javacc.visitor.ObjectVisitor v, Object argu) {
      return v.visit(this,argu);
   }
}
