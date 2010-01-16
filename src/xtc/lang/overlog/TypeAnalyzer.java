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

import xtc.type.ArrayT;
import xtc.type.BooleanT;
import xtc.type.ErrorT;
import xtc.type.FunctionT;
import xtc.type.InternalT;
import xtc.type.NumberT;
import xtc.type.NullReference;
import xtc.type.InternalParameter;
import xtc.type.TupleT;
import xtc.type.Type;
import xtc.type.VoidT;
import xtc.type.Wildcard;

import xtc.util.SymbolTable;
import xtc.util.Runtime;

/**
 * A visitor to type check Overlog programs.
 *
 * @author Robert Soule
 * @version $Revision: 1.26 $
 */
public final class TypeAnalyzer extends Visitor {

  /** The runtime. */
  private final Runtime runtime;

  /** The symbol table. */
  private SymbolTable table;

  /** A map to keep updated type information */
  private Map<Type, Type> updateMap;

  /** A set to ensure rule identifier names are unique */
  private Set<String> ruleIdentifiers; 

  /** the number of non materialized tuples in a rule */
  private int numNonMaterialized;

  /** The names of tuples declared materialized */
  private Set<String> materialized;
 
  /**
   * Create a new Overlog analyzer.
   *
   * @param runtime The runtime.
   */
  public TypeAnalyzer(Runtime runtime) {
    this.runtime = runtime;
    updateMap = new HashMap<Type, Type>();
    ruleIdentifiers = new HashSet<String>();
  }

  /**
   * Analyze the specified translation unit.
   *
   * @param unit The translation unit.
   * @return The corresponding symbol table.
   */
  public Node analyze(Node unit) {
    materialized = new HashSet<String>();
    Set<String>  tupleNames = new HashSet<String>();
    new MaterializationChecker().analyze(unit, tupleNames, materialized);
    return analyze(unit, new SymbolTable());
  }

  /**
   * Process the specified translation unit.
   *
   * @param unit The translation unit.
   * @param table The symbol table.
   * @return root of the AST
   */
  public Node analyze(Node unit, SymbolTable table) {
    this.table = table;
    materialized = new HashSet<String>();
    Set<String>  tupleNames = new HashSet<String>();
    new MaterializationChecker().analyze(unit, tupleNames, materialized);
    dispatch(unit);
    updateSymbolTable();
    return unit;
  }

 // =========================================================================

  /**
   * Create a new set whose only member is t
   *
   * @param t the sole member of the new set.
   */
  private void makeSet(Type t) {
    // In this representation, a set's representative member
    // may be espressed as a mapping from that type to null;
    updateMap.put(t, null);
  } 

  /**
   * Unite the dynamic sets that contain x and y into
   * a new set that is the union of these two sets.
   * If one of the types is a non-variable node, then 
   * then union makes the non variable node the representative
   * of the merged classes.
   *
   * @param x the first type
   * @param y the second type
   */
  private void union(final Type x, final Type y) {
    if ((x == null) || (y == null)) {
      return;
    } else if (isBasicType(x) && !isBasicType(y)) { 
      /* x represents a non-variable */ 
      updateMap.put(y, x);
    } else if (!isBasicType(x) && isBasicType(y)) { 
      /* y represents a non-variable */ 
      updateMap.put(x, y);
    } else  {
      // In the semantics of this fake disjoint sets algorithm, 
      // joining two sets means creating a new temp variable that
      // they both point to.
      //
      // @note:  3/20/08 I reversed this and code now works.
      // but its still strange. I wonder if this is the wrong
      // algorithm.
      //
      Type representative = new InternalParameter();
      // updateMap.put(x, representative);
      // updateMap.put(y, representative);
      updateMap.put(representative , x) ;
      updateMap.put(representative , y) ;
    }
  }

  /**
   * Return the representative element of the equivalence class
   * currently containing element x. 
   *
   * @param t the type who's representative you are looking for
   * @return the representative of that class. If the type is 
   * not currently found in the map, then t is returned. 
   */
  private Type find(final Type t) {
    final Type u = updateMap.get(t);
    if (u == null) {
      return t;
    } else {
      return find(u);
    }
  }

 // =========================================================================

  /**
   * Updates the nested scopes in the symbol table 
   * with the correct types after type unification.
   */ 
  private void updateSymbolTable() {
    updateScope(table.current());
    Iterator<String> iter = table.current().nested();
    for (; iter.hasNext();) {
      String symbol = iter.next();
      updateScope(table.current().getNested(symbol)); 
    }
  }

  /**
   * Updates all symbols defined in a single scopes 
   * in the symbol table with the correct types after 
   * type unification.
   *
   * @param scope A nested scope from the symbol table
   */ 
  private void updateScope(SymbolTable.Scope scope) {
    Iterator<String> iter = scope.symbols();
    for (; iter.hasNext();) {
      String symbol = iter.next();
      Type t = (Type)scope.lookup(symbol);
      Type u = find(t);
      if (u.isTuple()) {
        // dispatch to the terms
        List<Type> termList = new ArrayList<Type>();
        List<Type> tTypeList = t.toTuple().getTypes();
        for (Type s : tTypeList) {
          Type r = (find(s));
          if (r.isParameter()) r = new InternalT("opaque");
          termList.add(r);
        }
        scope.define(symbol, new TupleT(u.getName(), termList));
      } else if (u.isFunction()) {
        List<Type> updatedArgList= new ArrayList<Type>();
        List<Type> params = u.toFunction().getParameters();
        for (Type s : params) {
          Type r = (find(s));
          if (r.isParameter()) r = new InternalT("opaque");
          updatedArgList.add(r);
        }
        Type r = find(u.toFunction().getResult());
        if (r.isParameter()) r = new InternalT("opaque");
        Type updatedFunctionT = new FunctionT(
          r,
          updatedArgList, false); 
        scope.define(symbol, updatedFunctionT);
      } else if (u.isParameter()){
        scope.define(symbol, new InternalT("opaque"));
      } else {
        scope.define(symbol, u);
      }
    }
  }

  /**
   * Determines if a type is one of the basic types
   *
   * @param t the type to be checked
   * @return true if t is one of the basic types
   */
  private boolean isBasicType(final Type t) {
    switch (t.tag()) {
      case BOOLEAN:
      case INTEGER:
      case FLOAT:
      case VOID:
      case WILDCARD:
        return true;
      case INTERNAL:
        return ("location".equals(t.toInternal().getName()) ||
         "string constant".equals(t.toInternal().getName()));
      default:
        return false;
    }
  }

  /**
   * Determines if two types are the same basic type
   *
   * @param s the first type
   * @param t the second type
   * @return true if the two are the same basic type
   */
  public boolean areSameBasicType(final Type s, final Type t) {
    if (Type.Tag.WILDCARD == t.tag()) {
        return isBasicType(s);
    }
    switch (s.tag()) {
      case BOOLEAN:
        return t.isBoolean();
      case INTEGER:
        return t.isInteger();
      case FLOAT:
        return t.isFloat();
      case VOID:
        return t.isVoid();
      case WILDCARD:
        return isBasicType(t);
      case INTERNAL:
      {
        if (!t.isInternal()) {
          return false;
        } else if ("location".equals(s.toInternal().getName()) &&
          "location".equals(t.toInternal().getName())) {
          return true;
        } else if ("string constant".equals(s.toInternal().getName()) &&
          "string constant".equals(t.toInternal().getName())) {
          return true;
        } else {
          return false;
        }
      }
      default:
        return false;
    }
  }

  /**
   * Return true if a type is being used as a type
   * variable.
   *
   * @param s The type in question
   */
  private boolean isVariable(final Type s) {
    return s.isParameter();
  }

  /**
   * Determines if two expressions can be made identical
   *
   * @param m The first type
   * @param m The second type
   * @return true if m and n unify, false otherwise
   */
  private boolean unify(final Type m, final Type n) {
    final Type s = find(m);
    final Type t = find(n);
    if ((s == null) || (t==null)) {
      return false;
    } else if (s.equals(t)) {        
      /* s equals t */
      return true;
    } else if ( areSameBasicType(s, t) ) { 
      /* s and t are the same basic type */
      return true;
    } else if ( s.isTuple() && t.isTuple() ) {
      // make sure they have the same number of children
      if (s.toTuple().getTypes().size() != t.toTuple().getTypes().size()) {
        return false;
      }
      final List<Type> sTypeList = s.toTuple().getTypes();
      final List<Type> tTypeList = t.toTuple().getTypes();
      int i = 0; 
      for (Type lhs : sTypeList) {
        Type rhs = tTypeList.get(i);
        boolean unified = unify(lhs, rhs);
        if (!unified) {
          if (!((find(lhs).isFloat() && find(rhs).isInteger()) || 
           (find(lhs).isInteger() && find(rhs).isFloat()))) {
              return false;
          }
        }
        i++;
      }
      return true; 
    } else if (s.isFunction() && t.isFunction()) {
      if (((FunctionT)s).getParameters().size() != 
        ((FunctionT)t).getParameters().size() ) {
        return false;
      }
      final List<Type> sArgs = s.toFunction().getParameters();
      final List<Type> tArgs = t.toFunction().getParameters();
      int i = 0;
      for (Type lhs : sArgs) {
        Type rhs = tArgs.get(i);
        boolean unified = unify(lhs, rhs);
        if (!unified) {
          if (!((find(lhs).isFloat() && find(rhs).isInteger()) ||
           (find(lhs).isInteger() && find(rhs).isFloat()))) {
              return false;
          }
        }
        i++;
      }
      return unify(s.toFunction().getResult(), t.toFunction().getResult());

    } else if (isVariable(s) || isVariable(t)) { 
      /* s or t represents a variable */ 
      union(s,t);
      return true;
    } else {
      return false;
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

  public void visitRule(final GNode n) {
    // Rule clauses may start with an optional "RuleIdentifier".
    // Currently, RuleIdentifiers serve no purpose other than
    // to provide some clarity to the programmer. We therefore
    // want to check the first child, and see if it is a type
    // that we can ignore.
    String ruleName = "unknown";
    if ("RuleIdentifier".equals(n.getNode(0).getName())) {
      ruleName = n.getNode(0).getString(0); 
      if (ruleIdentifiers.contains(ruleName)) {
        runtime.error("Rule " + ruleName + " previously defined.", n);
        return;
      } else {
        ruleIdentifiers.add(ruleName);
      }
      table.enter(ruleName);
    } else {
      table.enter(table.freshName());
    }
    // dispath to the event
    dispatch(n.getNode(2));

    numNonMaterialized = 0;
    // dispatch to the "actions"
    for (Node child : n.<Node>getList(3)) {
      dispatch(child); 
    }
    if (numNonMaterialized > 1) {
      runtime.error("Rule " + ruleName + " has " +
        numNonMaterialized + " non-materialized tuples", n);
    }
    numNonMaterialized = 0;
    table.exit();
    updateSymbolTable();
  }

  public Type visitTuple(final GNode n) {
    // first we get the tuple's name
    final String name = n.getNode(0).getString(0);
    if (!materialized.contains(name)) {
      numNonMaterialized++;
    }
    // dispatch to the terms
    List<Type> termList = new ArrayList<Type>();
    for (Node term : n.<Node>getList(1)) {
      final Type t = (Type)dispatch(term);
      termList.add(t);
    }
    Type tuple = new TupleT(name, termList);
    final Type definedType = (Type)table.root().lookup(name);
    // Check to see if the tuple has been defined before
    if (null == definedType) {
      // If it hasn't, define it now
      table.root().define(name, tuple);
    } else {
      final boolean unified = unify(tuple, definedType);
      if (!unified) {
        if (name.equals("periodic")) {
          runtime.warning(
            "periodic tuple previously defined with different type.", n);
        } else {
          runtime.error("Tuple " + name + " previously defined " +
            "with different type", n);
          return ErrorT.TYPE;
        }
      }
    }
    return tuple;
  }

  public Type visitExpression(final GNode n) {
    final Type lhs = (Type)dispatch(n.getNode(0));
    final Type rhs = (Type)dispatch(n.getNode(2));
    // if these are variables, unify will make a union
    final boolean unified = unify(lhs, rhs);
    if (unified) {
      union(lhs, rhs);
      return find(lhs);
    } else {
      // Maybe we can coerce
      if ((find(lhs).isFloat() && find(rhs).isInteger()) || 
          (find(lhs).isInteger() && find(rhs).isFloat())) {
        return NumberT.FLOAT;
      } else {
        runtime.error("Assignment Expression error. Cannot assign "
          + find(rhs) + " to " + find(lhs), n);
        return ErrorT.TYPE;
      }
    } 
  }

  public Type visitLogicalOrExpression(final GNode n) {
    final Type lhs = (Type)dispatch(n.getNode(0));
    final Type rhs = (Type)dispatch(n.getNode(2));
    final boolean unified = unify(lhs, rhs);
    if (unified) {
      return new BooleanT();
    } else {
      runtime.error("Cannot compare "
        + find(lhs) + " and " + find(rhs) 
        + " in a logical or expression" , n);
      return ErrorT.TYPE;
    } 
  }

  public Type visitLogicalAndExpression(final GNode n) {
    final Type lhs = (Type)dispatch(n.getNode(0));
    final Type rhs = (Type)dispatch(n.getNode(2));
    final boolean unified = unify(lhs, rhs);
    if (unified) {
      return new BooleanT();
    } else {
      runtime.error("Cannot compare "
        + find(lhs) + " and " + find(rhs) 
        + " in a logical and expression" , n);
      return ErrorT.TYPE;
    } 
  }

  public Type visitEqualityExpression(final GNode n) {
    final Type lhs = (Type)dispatch(n.getNode(0));
    final Type rhs = (Type)dispatch(n.getNode(2));
    final boolean unified = unify(lhs, rhs);
    if (unified) {
      return new BooleanT();
    } else {
      // Maybe we can coerce
      if ((find(lhs).isFloat() && find(rhs).isInteger()) || 
          (find(lhs).isInteger() && find(rhs).isFloat())) {
        return new BooleanT();
      } else {
        runtime.error("Cannot compare "
           + find(lhs) + " and " + find(rhs) 
           + " in an equality expression" , n);
        return ErrorT.TYPE;
      }
    }
  }

  public Type visitShiftExpression(final GNode n) {
    final Type lhs = (Type)dispatch(n.getNode(0));
    final Type rhs = (Type)dispatch(n.getNode(2));
    if (lhs.isInteger() && rhs.isInteger()) {
      return NumberT.S_INT;
    } else if (lhs.isFloat() && rhs.isInteger()) {
      return NumberT.FLOAT;
    } else if (lhs.isInteger() && rhs.isFloat()) {
      return NumberT.FLOAT;
    } else if (lhs.isFloat() && rhs.isFloat()) {
      return NumberT.FLOAT;
    } else {
      if (unify(lhs, rhs)) {
        return find(lhs);
      } else {
        runtime.error("Cannot shift "
           + find(lhs) + " and " + find(rhs), n); 
        return ErrorT.TYPE;
     }
    }
  }

  public Type visitAdditiveExpression(final GNode n) {
    final Type lhs = (Type)dispatch(n.getNode(0));
    final Type rhs = (Type)dispatch(n.getNode(2));
    if (lhs.isInteger() && rhs.isInteger()) {
      return NumberT.S_INT;
    } else if (lhs.isFloat() && rhs.isInteger()) {
      return NumberT.FLOAT;
    } else if (lhs.isInteger() && rhs.isFloat()) {
      return NumberT.FLOAT;
    } else if (lhs.isFloat() && rhs.isFloat()) {
      return NumberT.FLOAT;
    } else {
      if (unify(lhs, rhs)) {
        return find(lhs);
      } else {
        runtime.error("Cannot add "
           + find(lhs) + " and " + find(rhs), n); 
        return ErrorT.TYPE;
     }
    }
  }

  public Type visitMultiplicativeExpression(final GNode n) {
    final Type lhs = (Type)dispatch(n.getNode(0));
    final Type rhs = (Type)dispatch(n.getNode(2));
    if (lhs.isInteger() && rhs.isInteger()) {
      return NumberT.S_INT;
    } else if (lhs.isFloat() && rhs.isInteger()) {
      return NumberT.FLOAT;
    } else if (lhs.isInteger() && rhs.isFloat()) {
      return NumberT.FLOAT;
    } else if (lhs.isFloat() && rhs.isFloat()) {
      return NumberT.FLOAT;
    } else {
      if (unify(lhs, rhs)) {
        return find(lhs);
      } else {
        runtime.error("Cannot multiply "
           + find(lhs) + " and " + find(rhs), n); 
        return ErrorT.TYPE;
     }
    }
  }

  public Type visitLogicalNegationExpression(final GNode n) {
    dispatch(n.getNode(0));
    // @fixme what are the rules here? is it like C and we allow ! int
    // or only booleans?
    return new BooleanT();
  }

  public Type visitInclusiveExpression(final GNode n) {
    final Type lhs = (Type)dispatch(n.getNode(0));
    final Type rhs = (Type)dispatch(n.getNode(2));
    final boolean unified = unify(lhs, rhs);
    if (unified) {
      Type type = new BooleanT();
      makeSet(type);
      return type;
    } else {
      // Maybe we can coerce
      if ((find(lhs).isFloat() && find(rhs).isInteger()) || 
          (find(lhs).isInteger() && find(rhs).isFloat())) {
        Type type = new BooleanT();
        makeSet(type);
        return type;
      } else {
        runtime.error("Cannot compare "
           + find(lhs) + " and " + find(rhs) 
           + " in an inclusion expression" , n);
        return ErrorT.TYPE;
      }
    } 
  }

  public Type visitRangeExpression(final GNode n) {
    final Type lhs = (Type)dispatch(n.getNode(1));
    final Type rhs = (Type)dispatch(n.getNode(2));
    final boolean unified = unify(lhs, rhs);
    if (unified) {
      return find(lhs);
    } else {
      // Maybe we can coerce
      if ((find(lhs).isFloat() && find(rhs).isInteger()) || 
          (find(lhs).isInteger() && find(rhs).isFloat())) {
        return NumberT.FLOAT;
      } else {
        runtime.error("Cannot compare "
           + find(lhs) + " and " + find(rhs) 
           + " in a range expression" , n);
        return ErrorT.TYPE;
      }
    } 
  }

  public Type visitPostfixExpression(final GNode n) {
    // first we get the tuple's name
    final String name = n.getNode(0).getString(0);
    // now create a temporary variable to represent the 
    // return value of this function.
    final Type retVar = new InternalParameter();
    makeSet(retVar);
    final ArrayList<Type> args = 
     (ArrayList<Type>)visitArguments(n.getGeneric(1));
    final FunctionT type = new FunctionT(retVar, args, false);
    // check to see if the function has been defined before. If it hasn't
    // define it, and we're done; If it has, see if we can unify.
    final Type definedType = (Type)table.root().lookup(name);
    if (null == definedType) {
      table.root().define(name, type);
    } else {
      final boolean unified = unify(definedType, type);
      if (!unified) {
        runtime.error("Function previously defined with a different type", n);
        return ErrorT.TYPE;
      }
    }
    return retVar;
  }

  public ArrayList<Type> visitArguments(final GNode n) {
    ArrayList<Type> argList= new ArrayList<Type>();
    if (n.size() != 0) {
      for (Node term : n.<Node>getList(0)) {
        Type t = (Type)dispatch(term);
        argList.add(t);
      }
    }
    return argList;
  }

  public Type visitVectorExpression(final GNode n) {
    ArrayList<Type> indexList= new ArrayList<Type>();
    final Type idx1 = (Type)dispatch((Node)indexList.get(0));
    final Type idx2 = (Type)dispatch((Node)indexList.get(1));
    final boolean unified = unify(idx1, idx2);
    if (!unified) {
      runtime.error("Vector mal-typed", n);
      return ErrorT.TYPE;
    } else {
      return new ArrayT(idx1, true);
    }
  }

  public Type visitMatrixExpression(final GNode n) {
    // @fixme should a matrix be a new type?
    return new ArrayT(NumberT.S_INT, true);
  }

  public Type visitMatrixEntry(final GNode n) {
    // @fixme should a matrix be a new type?
    return new ArrayT(NumberT.S_INT, true);
  }

  public Type visitParenthesizedExpression(final GNode n) {
    return (Type)dispatch(n.getNode(0));
  }

  public Type visitAggregate(final GNode n) {
    // visit the name
    dispatch(n.getNode(0));
    // visit what the aggregate is on
    return (Type)dispatch(n.getNode(1));
  }

  public Type visitMinAggregate(final GNode n) {
    // visit the name
    // dispatch(n.getNode(0));
    // visit what the aggregate is on
    return (Type)dispatch(n.getNode(0));
  }

  public Type visitMaxAggregate(final GNode n) {
    // visit the name
    // dispatch(n.getNode(0));
    // visit what the aggregate is on
    return (Type)dispatch(n.getNode(0));
  }

  public Type visitCountAggregate(final GNode n) {
    final Type type = NumberT.S_INT;
    makeSet(type);
    return type;
  }

  public Type visitLocationSpecifier(final GNode n) {
    final Type t = (Type)dispatch(n.getNode(0));
    final Type type = new InternalT("location");
    final boolean unified = unify(t, type);
    if (!unified) {
      runtime.error( "Location Specifier variable " +
        "previously defined as a different type", n);
      return ErrorT.TYPE;
    } else {
      return type;
    }
  }
  
  public Type visitAggregateIdentifier(final GNode n) {
    final String name = n.getString(0);
    Type type = (Type)table.current().lookup(name); 
    // if already defined, make sure its an aggregate
    // otherwise, create a new entry;
    if (type != null) {
      if ("aggregate".equals(type.getName())) {
        return type;
      } else {
        runtime.error("Aggregate Identifier " + 
          "previously defined as a different type", n);
        return ErrorT.TYPE;
      }
    } else {
      type = new InternalT("aggregate");
      makeSet(type);
      table.current().define(name, type);
      return type;
    }
  }

  public Type visitVariableIdentifier(final GNode n) {
    final String name = n.getString(0);
    Type type = (Type)table.current().lookup(name); 
    // if already defined, return that
    // otherwise, create a new entry;
    if (type != null) {
      return type;
    } else {
      type = new InternalParameter();
      makeSet(type);
      table.current().define(name, type);
      return type;
    }
  }

  public Type visitUnnamedIdentifier(final GNode n) {
    final Wildcard type = new Wildcard();
    makeSet(type);
    return type;
  }
  
  public Type visitFloatingPointConstant(final GNode n) {
    final Type type = NumberT.FLOAT;
    makeSet(type);
    return type;
  }

  public Type visitIntegerConstant(final GNode n) {
    final Type type = NumberT.S_INT;
    makeSet(type);
    return type;
  }

  public Type visitStringConstant(final GNode n) {
    final Type type = new InternalT("string constant");
    makeSet(type);
    return type;
  }

  public Type visitBooleanConstant(final GNode n) {
    final Type type = new BooleanT();
    makeSet(type);
    return type;
  }

  public Type visitInfinityConstant(final GNode n) {
    final Type type = NumberT.S_INT;
    makeSet(type);
    return type;
  }

  public Type visitTupleDeclaration(final GNode n) {
    // first we get the tuple's name
    final String name = n.getNode(0).getString(0);
    // dispatch to the terms
    List<Type> termList = new ArrayList<Type>();
    for (Node term : n.<Node>getList(1)) {
      final Type t = (Type)dispatch(term);
      termList.add(t);
    }
    Type tuple = new TupleT(name, termList);
    final Type definedType = (Type)table.root().lookup(name);
    // Check to see if the tuple has been defined before
    if (null == definedType) {
      // If it hasn't, define it now
      table.root().define(name, tuple);
    } else {
      final boolean unified = unify(tuple, definedType);
      if (!unified) {
        if (name.equals("periodic")) {
          runtime.warning(
            "periodic tuple previously defined with different type.", n);
        } else {
          runtime.error("Tuple " + name + " previously defined " +
            "with different type", n);
          return ErrorT.TYPE;
        }
      }
    }
    return tuple;
  }

  public Type visitFunctionDeclaration(final GNode n) {

    // get the return type
    Type retType = (Type)dispatch(n.getNode(0)); 
    makeSet(retType);

    // get the name of the function
    String name = n.getString(1);

    ArrayList<Type> args = new ArrayList<Type>();

    if (n.getList(2) != null) {
      for (Node term : n.<Node>getList(2)) {
        Type t = (Type)dispatch(term);
        args.add(t);
      }
    }

    final FunctionT type = new FunctionT(retType, args, false);

    // check to see if the function has been defined before. If it hasn't
    // define it, and we're done; If it has, see if we can unify.
    final Type definedType = (Type)table.root().lookup(name);
    if (null == definedType) {
      table.root().define(name, type);
    } else {
      final boolean unified = unify(definedType, type);
      if (!unified) {
        runtime.error("Function previously defined with a different type", n);
        return ErrorT.TYPE;
      }
    }
    return retType;
  }

  public Type visitNullConstant(final GNode n) {
    final Type type = new VoidT();
    type.annotate().constant(NullReference.NULL);
    makeSet(type);
    return type;
  }

  public Type visitLocationConstant(final GNode n) {
    final Type type = new InternalT("location");
    makeSet(type);
    return type;
  }


  public Type visitLocationType(final GNode n) {
    final Type type = new InternalT("location");
    makeSet(type);
    return type;
  }

  public Type visitIntType(final GNode n) {
    final Type type = NumberT.S_INT;
    makeSet(type);
    return type;
  }

  public Type visitFloatType(final GNode n) {
    final Type type = NumberT.FLOAT;
    makeSet(type);
    return type;
  }

  public Type visitStringType(final GNode n) {
    final Type type = new InternalT("string constant");
    makeSet(type);
    return type;
  }

  public Type visitBooleanType(final GNode n) {
    final Type type = new BooleanT();
    makeSet(type);
    return type;
  }

  public Type visitVoidType(final GNode n) {
    final Type type = new VoidT();
    makeSet(type);
    return type;
  }


}
