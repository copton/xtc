package xtc.lang.c4.util;

/**
 * An enum that holds most of the queries use in C4.
 * 
 * @author Marco Yuen
 */
public enum C4XFormQuery {
  /** Replaces any return statements with assignment statements. This query contains format string. */
  ReplaceReturnWithAssignment("for $r in //ReturnStatement replace $r with ExpressionStatement< "
                              + "AssignmentExpression< PrimaryIdentifier<\"%s\", \"=\", $r/* [1] > > >"),

  /** Replaces any return statements with goto statements. This query contains format string. */
  ReplaceReturnWithGotoQuery(
      "for $r in //ReturnStatement replace $r with GotoStatement<null, PrimaryIdentifier<\"%s\"> >"),

  /** Prefixes the simple declarator with a specified string. */
  PrefixSimpleDeclarator(
      "for $r in //SimpleDeclarator return replace $r with SimpleDeclarator< concat( \"%s\", $r/*[1] ) >"),

  /** Suffixes the simple declarator with a specified string. */
  SuffixSimpleDeclarator(
      "for $r in //SimpleDeclarator return replace $r with SimpleDeclarator< concat( $r/*[1] ,\"%s\" ) >"),

  /** Replaces a simple declarator with a different one. */
  ReplaceSimpleDeclarator("replace //SimpleDeclarator with SimpleDeclarator< \"%s\" >"),

  /** Looks for a function call to proceed. This query is used in around advice. */
  CheckForProceed("//FunctionCall/PrimaryIdentifier/\"proceed\""),

  /** Looks for the simple declarator that contains the function name. */
  GetFunctionName("//FunctionDeclarator/SimpleDeclarator"),

  /** Looks for all aspect structure type definitions. */
  GetAllAspectStructureTypeDefinitions("//AspectStructureTypeDefinition"),

  /** Looks for all structure type definitions. */
  GetAllStructureTypeDefinition("//StructureTypeDefinition"),

  /** Returns a list of simple declarator. */
  GetSimpleDeclarator("//SimpleDeclarator");

  /** The actual query string for each enum type. */
  private final String queryString;

  /**
   * The constructor.
   * 
   * @param q
   *          The query string for the enum type.
   */
  private C4XFormQuery(String q) {
    queryString = q;
  }

  @Override
  public String toString() {
    return queryString;
  }

  /**
   * Format a query.
   * 
   * @param vars
   *          The list of argument strings.
   * @return A formatted string.
   */
  public String format(Object... vars) {
    return String.format(queryString, vars);
  }
}
