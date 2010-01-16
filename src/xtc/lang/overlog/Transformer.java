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

import xtc.type.Type;
import xtc.type.TupleT;

import xtc.util.Runtime;
import xtc.util.SymbolTable;


/**
 * A visitor to translate Overlog ASTs into Java ASTs.
 *
 * @author Robert Soule
 * @version $Revision: 1.107 $
 */
public class Transformer extends Visitor {

  /** The root of the incoming Overlog abstract syntax tree. */
  private Node overlogAST;
 
  /** The generated factory. */
  private final OverlogJavaFactory factory;

  /** The names of tuples declared materialized */
  private Set<String> materialized;

  /** The names of all tuples */
  private Set<String> tupleNames;

  /** The body of the table initialization classe. */
  private Node tableInitializerBody;

  /** The symbol table for this typical file. */
  private SymbolTable table;

  /** Maps the names to tuple/index pairs. */
  private SymbolTable mapping;

  /** The type information of variables after normalization */
  private SymbolTable normalizedTypes;

  /** This Transformer's runtime. */
  private final Runtime runtime;

  /** A counter for key names */
  private static int keyTmp = 0;

  /** The current tuple that is being visited */
  private Node currentTuple;

  /** The current index of the term being visited */
  private int currentTupleTermNum;

  /** All of the compilation units */
  private Map<String, GNode> forest;

  /** The current java code block in the AST */
  private Node currentBlock; 

  /** The top level code block in the event handler class */
  private Node processTopLevel; 

  /** A counter for watch statement names */
  private int watchNum;

  /** A counter for periodic tuple names */
  private int periodicNum;

  /** The body of the current class */
  private Node classBody;

  /** The package name for the generated classes */
  private String thePackage;

  /** The name of the main class of an overlog file. This
    is the class that initializes tables, event queues, etc., 
    not the main line. */
  private String olgName;

  /** The index of the aggregate term in the output tuple **/
  private int aggIndex; 

  /**
   * Create a new Java to Overlog transformer.
   *
   * @param ast The Overlog AST to be transformed.
   * @param st The Symbol table of the type analyzed AST.
   * @param runtime The runtime.
   * @param thePackage The name of the package of the generated code.
   * @param olgName The name of the OLG implementation.
   */
  public Transformer(GNode ast, SymbolTable st, Runtime runtime, 
    String thePackage, String olgName) {
    factory = new OverlogJavaFactory();
    this.runtime = runtime;
    this.overlogAST = ast;
    this.table = st;
    this.thePackage = thePackage;
    this.olgName = olgName;
    tableInitializerBody = null;
    forest = new HashMap<String, GNode>();
    watchNum=0;
    periodicNum=0;
  }

  /**
   * Run this transformer.
   */
  public void run() {
    // Create symbol table.
    table = new SymbolTable();
    // Perform type checking.
    overlogAST = new TypeAnalyzer(runtime).analyze(overlogAST, table);
    // Normalize
    mapping = new SymbolTable();
    normalizedTypes = new SymbolTable();
    this.overlogAST = new Normalizer().analyze(overlogAST, 
      mapping, table, normalizedTypes);
    // printMySymbolTable(normalizedTypes);
    materialized = new HashSet<String>();
    tupleNames = new HashSet<String>();
    new MaterializationChecker().analyze(overlogAST, tupleNames, materialized);
    makeTupleClasses();
    createExecutable(); 
    createInitializer();
    registerMarshallers();
    addExitSubstriber(); 
    dispatch(overlogAST);
  }

  /**
   * Return the ast of the generated program.
   *
   * @return The the map of compilation units of 
   * the output java source code.
   */
  public Map<String, GNode> getTransformedAST() {
    return forest;
  }


// =========================================================================

  /**
   * Generic catch-all visit method
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

  public void visitClauses(final GNode n) {
    for (Node child : n.<Node>getList(0)) {
      dispatch(child);
    }
  }

  public void visitGenericFact(final GNode n) {
    Node tuple = n.getNode(0);
    Node tableAdd = factory.tableAddTuple(
      GNode.create("StringLiteral", "\"" + 
        tuple.getNode(0).getString(0) + "\""),
      GNode.create("PrimaryIdentifier", 
        "Tuple" + tuple.getNode(0).getString(0) ));
    GNode arguments = GNode.create("Arguments");
    arguments = GNode.ensureVariable(arguments);
    for (Node child : tuple.<Node>getList(1)) {
      arguments.add(dispatch(child));
    }
    tableAdd.getNode(0).getNode(3).getNode(0).set(3, arguments);
    tableInitializerBody.add(tableAdd);
  }

  public void visitRule(final GNode n) {
    String ruleName = "unknown";
    if ("RuleIdentifier".equals(n.getNode(0).getName())) {
      ruleName = n.getNode(0).getString(0);
      mapping.enter(ruleName);
      table.enter(ruleName);
      normalizedTypes.enter(ruleName);
    } 
    // Now we check to see if this is a rule generated by an
    // event or a table update.
    boolean eventGenerated = false;
    Node event = null;
    for (Node child : n.<Node>getList(3)) {
      if ("Tuple".equals(child.getName())) {
        if (!materialized.contains(child.getNode(0).getString(0))) {
          eventGenerated = true;  
          // event = factory.tupleAssign(
          //   child.getNode(0).getString(0));
          event = child;
        }
      }
    }
    if (eventGenerated) {
      createEventHandler(n, event, "");
    } else {
      // TODO : loop through every tuple, as if that were
      // the event.
      int id = 0;
      for (Node child : n.<Node>getList(3)) {
        if ("Tuple".equals(child.getName())) {
          // event = factory.tupleAssign(
          //   child.getNode(0).getString(0));
          createEventHandler(n, child, Integer.toString(id));
          id++;
        }
      }
    }
    // exit the scopes
    mapping.exit();
    table.exit();
    normalizedTypes.exit();
  }

  public void visitTuple(final GNode n) {
    String tupleName = n.getNode(0).getString(0);
    if (materialized.contains(tupleName)) {
      Node loop = factory.joinLoop(
        n.getNode(0).getString(0),
        GNode.create("StringLiteral", "\""+tupleName+"\""));
      Node innerBlock = loop.getNode(1);
      innerBlock = GNode.ensureVariable((GNode)innerBlock);
      loop.set(1, innerBlock);
      currentBlock.add(loop);
      currentBlock = innerBlock; 
    }
  }

  public void visitMaterialization(final GNode n) {
    Node keystmnt = factory.keys("keys" + keyTmp);
    tableInitializerBody.add(keystmnt);
    Node initializer = keystmnt.getNode(2).getNode(0).getNode(2).getNode(3);
    initializer = GNode.ensureVariable((GNode)initializer);
    keystmnt.getNode(2).getNode(0).getNode(2).set(3,initializer);
    for (Node k : n.getNode(3).<Node>getList(0)) {
      initializer.add(dispatch(k));
    }
    Node name = GNode.create("StringLiteral",
      "\""+n.getNode(0).getString(0)+"\"");
    Node lifetime = (Node)dispatch(n.getNode(1));
    Node size = (Node)dispatch(n.getNode(2));
    Node keys = GNode.create("PrimaryIdentifier", "keys" + keyTmp);
    tableInitializerBody.add(factory.tableInit(
      name, lifetime, size, keys));
    keyTmp++;
  }

  public void visitTupleObservation(final GNode n) {
    String name = n.getNode(0).getString(0);
    tableInitializerBody.add(
      factory.watchNew("watch"+watchNum));
    tableInitializerBody.add(
      factory.watchSub( 
        GNode.create("StringLiteral", "\""+name+"\""),
        GNode.create("PrimaryIdentifier", "watch"+watchNum)));
    watchNum++;
  }

  public Node visitExpression(final GNode n) {
    Type t = (Type)table.current().lookup(n.getNode(0).getString(0));
    Node typeNode = null;
    if (t != null) {
      if (t.isInteger()) {
        typeNode = GNode.create("PrimitiveType", "int");
      } else if (t.isBoolean()) {
        typeNode = GNode.create("PrimitiveType", "boolean");
      } else if (t.isFloat()) {
        typeNode = GNode.create("PrimitiveType", "float");
      } else if (t.isInternal()) {
        if ("string constant".equals(t.getName())) {
          typeNode = GNode.create("QualifiedIdentifier", "String");
        } else if ("location".equals(t.getName())) {
          typeNode = GNode.create("QualifiedIdentifier", "NetAddr");
        }
      }
    }
    currentBlock.add(
    factory.varDecl(typeNode, n.getNode(0).getString(0)));
    return GNode.create("ExpressionStatement", 
      GNode.create("Expression",
      dispatch(n.getNode(0)),   
      "=",
      dispatch(n.getNode(2))));
  }

  public Node visitLogicalOrExpression(final GNode n) {
    return GNode.create("LogicalOrExpression",
      dispatch(n.getNode(0)),   
      n.getString(1),
      dispatch(n.getNode(2)));   
  }

  public Node visitLogicalAndExpression(final GNode n) {
    return GNode.create("LogicalAndExpression",
      dispatch(n.getNode(0)),   
      n.getString(1),
      dispatch(n.getNode(2)));   
  }

  public Node visitEqualityExpression(final GNode n) {
    // @todo Is is safe to always use .equals not == ?
    if ("==".equals(n.getString(1))) {
      return factory.equals(
        (Node)dispatch(n.getNode(0)),
        (Node)dispatch(n.getNode(2)));   
    } else {
      return factory.notEquals(
        (Node)dispatch(n.getNode(0)),
        (Node)dispatch(n.getNode(2)));   
    }
  }

  public Node visitRelationalExpression(final GNode n) {
    return GNode.create("RelationalExpression",
      dispatch(n.getNode(0)),   
      n.getString(1),
      dispatch(n.getNode(2)));   
  }

  public Node visitShiftExpression(final GNode n) {
    return GNode.create("ShiftExpression",
      dispatch(n.getNode(0)),   
      n.getString(1),
      dispatch(n.getNode(2)));   
  }

  public Node visitAdditiveExpression(final GNode n) {
    return GNode.create("AdditiveExpression",
     dispatch(n.getNode(0)),   
     n.getString(1),
     dispatch(n.getNode(2)));   
  }

  public Node visitMultiplicativeExpression(final GNode n) {
    return  GNode.create("MultiplicativeExpression",
     dispatch(n.getNode(0)),   
     n.getString(1),
     dispatch(n.getNode(2)));   
  }

  public Node visitLogicalNegationExpression(final GNode n) {
    return  GNode.create("LogicalNegationExpression",
      dispatch(n.getNode(0)));
  }

  public Node visitInclusiveExpression(final GNode n) {
    return null;
  }

  public Node visitRangeExpression(final GNode n) {
    return null;
  }

  public Node visitPostfixExpression(final GNode n) {
    List<Node> arguments = new ArrayList<Node>();
    if (n.size() > 1) {
      Node tail = n.getNode(1);
      if (tail.size() > 1) {
        for (Node child : tail.<Node>getList(0)) {
          arguments.add((Node)dispatch(child));
        }
      }
    }
    String name = n.getNode(0).getString(0);
    if (name.startsWith("f_")) {
      name = name.replace("f_", "");
    }
    return factory.functionCall(name, arguments);
  }

  public Node visitPrimaryExpression(final GNode n) {
    return (Node)dispatch(n.getNode(0));
  }

  public Node visitMinAggregate(final GNode n) {
    Node lookup = (Node)dispatch(n.getNode(0));
    String tupleName = lookup.getNode(1).getNode(0).getString(0);
    Node termType = aggGetTermType(lookup);
    Node access = tupleAccess(termType, 
      GNode.create("PrimaryIdentifier", 
       lookup.getNode(1).getNode(0).getString(0)),
      GNode.create("IntegerLiteral", Integer.toString(aggIndex)));
    classBody.add( factory.aggMinFunction(
      tupleName,
      GNode.create("PrimaryIdentifier", tupleName),
      termType,
      access));
    return (Node)dispatch(n.getNode(0));
  }

  public Node visitMaxAggregate(final GNode n) {
    Node lookup = (Node)dispatch(n.getNode(0));
    String tupleName = lookup.getNode(1).getNode(0).getString(0);
    Node termType = aggGetTermType(lookup);
    Node access = tupleAccess(termType, 
      GNode.create("PrimaryIdentifier", 
       lookup.getNode(1).getNode(0).getString(0)),
      GNode.create("IntegerLiteral", Integer.toString(aggIndex)));
    classBody.add( factory.aggMaxFunction(
      tupleName,
      GNode.create("PrimaryIdentifier", tupleName),
      termType,
      access));
    return (Node)dispatch(n.getNode(0));
  }

  public Node visitCountAggregate(final GNode n) {
    return GNode.create("IntegerLiteral", "1");
  }

  private Node aggGetTermType(Node lookup) {
    Node termType = null;
    if (currentTuple != null) {
      Type t = (Type)table.root().lookup(currentTuple.getNode(0).getString(0));
      List<Type> l = ((TupleT)t).getTypes();
      Type s = l.get(currentTupleTermNum); 
      if (s.isInteger()) {
        termType = GNode.create("QualifiedIdentifier", "Integer");
      } else if (s.isBoolean()) {
        termType = GNode.create("QualifiedIdentifier", "Boolean");
      } else if (s.isFloat()) {
        termType = GNode.create("QualifiedIdentifier", "Float");
      } else if (s.isInternal()) {
        if ("string constant".equals(s.getName())) {
          termType = GNode.create("QualifiedIdentifier", "String");
        } else if ("location".equals(s.getName())) {
          termType = GNode.create("QualifiedIdentifier", "NetAddr");
        }
      }
    } 
    return termType;
  }

  public Node visitAggregateIdentifier(final GNode n) {
    return null;
  }

  public Node visitLocationSpecifier(final GNode n) {
    return (Node)dispatch(n.getNode(0));
  }

  public Node visitVariableIdentifier(final GNode n) {
    if (mapping.current().isDefined (n.getString(0))) {
      Node mapped = (Node)mapping.current().lookupLocally(n.getString(0));
      while (!"CastExpression".equals(mapped.getName())) {
        if (!mapping.current().isDefined(mapped.getString(0))) {
          break;
        }
        if (mapped.getString(0).equals(n.getString(0))) {
          break;
        }
        mapped = (Node)mapping.current().lookupLocally(mapped.getString(0));
      }
      if ("CastExpression".equals(mapped.getName())) {
        return mapped;
      }
    }
    return GNode.create("PrimaryIdentifier", n.getString(0));   
  }

  public Node visitFloatingPointConstant(final GNode n) {
    return GNode.create("FloatingPointLiteral", n.getString(0) + "f");
  }

  public Node visitIntegerConstant(final GNode n) {
    return GNode.create("IntegerLiteral", n.getString(0));
  }

  public Node visitStringConstant(final GNode n) {
    return GNode.create("StringLiteral", n.getString(0));
  }

  public Node visitBooleanConstant(final GNode n) {
    return GNode.create("BooleanLiteral", n.getString(0));
  }

  public Node visitInfinityConstant(final GNode n) {
    return GNode.create("IntegerLiteral", "Integer.MAX_VALUE");
  }

  public Node visitNullConstant(final GNode n) {
    return GNode.create("NullLiteral");
  }

  public Node visitUnnamedIdentifier(final GNode n) {
    if (currentTuple != null) {
      Type t = (Type)table.root().lookup(currentTuple.getNode(0).getString(0));
      List<Type> l = ((TupleT)t).getTypes();
      Type s = l.get(currentTupleTermNum); 
      if (s.isInteger()) {
        return GNode.create("IntegerLiteral", "0");
      } else if (s.isBoolean()) {
        return GNode.create("BooleanLiteral", "true");
      } else if (s.isFloat()) {
        return GNode.create("FloatLiteral", "0.0f");
      } else if (s.isInternal()) {
        if ("string constant".equals(s.getName())) {
         return GNode.create("NullLiteral");
        } else if ("location".equals(s.getName())) {
         return GNode.create("NullLiteral");
        }
      }
    } 
    return GNode.create("NullLiteral");
  }

  public Node visitLocationConstant(final GNode n) {
    return factory.netAddr(
      GNode.create("StringLiteral", "\""+n.getString(0)+"\""),
      GNode.create("IntegerLiteral", n.getString(1)));
  }

// =========================================================================

  /**
   * Create the Java AST skeleton.
   *
   * @return The root of the java ast.
   */
  private Node makeSkeleton() {
    Node compUnit = GNode.create("CompilationUnit", 8);
    compUnit.add(makePackage(thePackage));
    compUnit.add(
      GNode.create("ImportDeclaration", null,
         GNode.create("QualifiedIdentifier", "overlogRuntime", "*"),
         null));
    return compUnit;
  }

  /**
   * Iterate over the tuple in the symbol table
   * and create a java class.
   */
  private void makeTupleClasses() {
    SymbolTable.Scope scope = table.root();
    Iterator<String> iter = scope.symbols();
    for (; iter.hasNext();) {
      String symbol = iter.next();
      Type t = (Type)scope.lookup(symbol);
      if (t.isTuple()) {
        makeTupleClass(symbol, t);
      }
    }
  }

  /**
   * Create a java class for a single tuple.
   * 
   * @param symbol
   * @param t
   */
  private void makeTupleClass(String symbol, Type t) {
    List<Type> typeList = t.toTuple().getTypes();
    Node javaAST = GNode.cast(makeSkeleton());
    addImportsToTupleClass(javaAST);
    Node tupleClass = factory.tupleClass("Tuple" + symbol,
      GNode.create("IntegerLiteral", Integer.toString(typeList.size())),
      GNode.create("StringLiteral", "\""+symbol+"\"")); 
    javaAST.add(tupleClass);
    forest.put("Tuple" + symbol, (GNode)javaAST);
    Node tupleConstructor = tupleClass.getNode(5).getNode(3);
    Node tupleConstructorParams = tupleConstructor.getNode(3);
    tupleConstructorParams = 
     GNode.ensureVariable((GNode)tupleConstructorParams);
    tupleConstructor.set(3, tupleConstructorParams);
    Node tupleConstructorBlock = tupleConstructor.getNode(5);
    tupleConstructorBlock = GNode.ensureVariable((GNode)tupleConstructorBlock);
    tupleConstructor.set(5, tupleConstructorBlock);
    Node readMethod = factory.tupleReadExternal();
    Node readMethodBlock = readMethod.getNode(7);
    readMethodBlock = GNode.ensureVariable((GNode)readMethodBlock);
    readMethod.set(7, readMethodBlock);
    Node writeMethod = factory.tupleWriteExternal();
    Node writeMethodBlock = writeMethod.getNode(7);
    writeMethodBlock = GNode.ensureVariable((GNode)writeMethodBlock);
    writeMethod.set(7, writeMethodBlock);
    Node toStringMethod = factory.tupleToString();
    Node toStringMethodBlock = toStringMethod.getNode(7); 
    toStringMethodBlock = GNode.ensureVariable((GNode)toStringMethodBlock);
    toStringMethod.set(7, toStringMethodBlock);
    Node tupleClassBody = tupleClass.getNode(5);
    tupleClassBody = GNode.ensureVariable((GNode)tupleClassBody);
    tupleClass.set(5, tupleClassBody);
    tupleClassBody.add(readMethod);
    tupleClassBody.add(writeMethod);
    tupleClassBody.add(toStringMethod);
    toStringMethodBlock.add(
      factory.appendOpen());
    readMethodBlock.add(
      factory.newID());
    readMethodBlock.add(
      factory.readID());
    writeMethodBlock.add(
      factory.writeID());
    GNode args = GNode.create("Arguments");
    int index = 0;
    int size = typeList.size();
    for (Type term : typeList) {
      String name = new String("v" + index);
      if (term.isInteger()) {
        addIntegerTerm(name, index, size, args,
                       tupleConstructorParams, 
                       tupleConstructorBlock, 
                       writeMethodBlock, 
                       readMethodBlock, 
                       toStringMethodBlock); 
      } else if (term.isBoolean()) {
        addBooleanTerm(name, index, size, args,
                       tupleConstructorParams, 
                       tupleConstructorBlock, 
                       writeMethodBlock, 
                       readMethodBlock, 
                       toStringMethodBlock); 
      } else if (term.isFloat()) {
        addFloatTerm(name, index, size, args,
                       tupleConstructorParams, 
                       tupleConstructorBlock, 
                       writeMethodBlock, 
                       readMethodBlock, 
                       toStringMethodBlock); 
      } else if (term.isInternal()) {
        if ("string constant".equals(term.getName())) {
          addStringTerm(name, index, size, args,
                         tupleConstructorParams, 
                         tupleConstructorBlock, 
                         writeMethodBlock, 
                         readMethodBlock, 
                         toStringMethodBlock); 
        } else if ("location".equals(term.getName())) {
          addLocationTerm(name, index, size, args,
                         tupleConstructorParams, 
                         tupleConstructorBlock, 
                         writeMethodBlock, 
                         readMethodBlock, 
                         toStringMethodBlock); 
        }
      }
      index++;
    }
    readMethodBlock.add(
      GNode.create("ReturnStatement",
      GNode.create("NewClassExpression",
      null,
      null, 
      GNode.create("QualifiedIdentifier", "Tuple" + symbol), 
      args,
      null)));
    toStringMethodBlock.add(
      factory.appendClose());
    toStringMethodBlock.add(
      factory.returnString());
  }


  /**
   * A helper method to add imports.
   *
   * @param javaAST The computational unit
   */
  private void addImportsToTupleClass(Node javaAST) {
    javaAST.add(
      GNode.create("ImportDeclaration", null,
        GNode.create("QualifiedIdentifier", "overlogRuntime", "Tuple"),
        null));
    javaAST.add(
      GNode.create("ImportDeclaration", null,
        GNode.create("QualifiedIdentifier", "overlogRuntime", "Marshaller"),
        null));
    javaAST.add(
      GNode.create("ImportDeclaration", null,
        GNode.create("QualifiedIdentifier", "overlogRuntime", "NetAddr"),
        null));
    javaAST.add(
      GNode.create("ImportDeclaration", null,
        GNode.create("QualifiedIdentifier", "java", "io", "IOException"),
        null));
    javaAST.add(
      GNode.create("ImportDeclaration", null,
        GNode.create("QualifiedIdentifier", "java", "io", "DataInputStream"),
        null));
    javaAST.add(
      GNode.create("ImportDeclaration", null,
        GNode.create("QualifiedIdentifier", "java", "io", "DataOutputStream"),
        null));
    javaAST.add(
      GNode.create("ImportDeclaration", null,
        GNode.create("QualifiedIdentifier", "java", "lang", "StringBuffer"),
        null));
  }

  /**
   * Adds an interger term to a tuple class, along with
   * the related marshalling and string representation code.
   * 
   * @param name The name of the variable.
   * @param index The index of the term in the tuple.
   * @param size The number of terms in the tuple.
   * @param args The arguments to the new tuple created in the read.
   * @param tupleConstructorParams The parameters to the constructor.
   * @param tupleConstructorBlock  The block of the constructor.
   * @param writeMethodBlock The block of the write method.
   * @param readMethodBlock  The block of the read method.
   * @param toStringMethodBlock The block of the toString method.
   */
  private void addIntegerTerm(String name, 
                              int index, 
                              int size, 
                              GNode args,
                              Node tupleConstructorParams, 
                              Node tupleConstructorBlock, 
                              Node writeMethodBlock, 
                              Node readMethodBlock, 
                              Node toStringMethodBlock) {
    // add the parameters 
    tupleConstructorParams.add(
      makeParameter(GNode.create("PrimitiveType", "int"), name));
    // add the assignment statements in the constructor
    tupleConstructorBlock.add(
      factory.termAssignInteger(
        GNode.create("IntegerLiteral", Integer.toString(index)),
        GNode.create("PrimaryIdentifier", name))); 
    // add the marshall out
    writeMethodBlock.add(
      factory.writeInt(
        GNode.create("IntegerLiteral", Integer.toString(index))));
    // add the marshall in
    readMethodBlock.add(
      factory.readInt(name)); 
    // add the arguments
    args.add(GNode.create("PrimaryIdentifier", name));
    toStringMethodBlock.add(
      factory.appendInt(
        GNode.create("IntegerLiteral", Integer.toString(index))));
    if (index <= size - 2) {
      toStringMethodBlock.add(
        factory.appendComma());
    }
  }

 
  /**
   * Adds a boolean term to a tuple class, along with
   * the related marshalling and string representation code.
   * 
   * @param name The name of the variable.
   * @param index The index of the term in the tuple.
   * @param size The number of terms in the tuple.
   * @param args The arguments to the new tuple created in the read.
   * @param tupleConstructorParams The parameters to the constructor.
   * @param tupleConstructorBlock  The block of the constructor.
   * @param writeMethodBlock The block of the write method.
   * @param readMethodBlock  The block of the read method.
   * @param toStringMethodBlock The block of the toString method.
   */
  private void addBooleanTerm(String name, 
                              int index, 
                              int size, 
                              GNode args,
                              Node tupleConstructorParams, 
                              Node tupleConstructorBlock, 
                              Node writeMethodBlock, 
                              Node readMethodBlock, 
                              Node toStringMethodBlock) {
    // add the parameters 
    tupleConstructorParams.add(
      makeParameter(GNode.create("PrimitiveType", "boolean"), name));
    // add the assignment statements in the constructor
      tupleConstructorBlock.add(
        factory.termAssignBoolean(
        GNode.create("IntegerLiteral", Integer.toString(index)),
        GNode.create("PrimaryIdentifier", name))); 
    // add the marshall out
    writeMethodBlock.add(
      factory.writeBoolean(
        GNode.create("IntegerLiteral", Integer.toString(index))));
    // add the marshall in
    readMethodBlock.add(
      factory.readBoolean(name)); 
    args.add(GNode.create("PrimaryIdentifier", name));
    toStringMethodBlock.add(
      factory.appendBoolean(
        GNode.create("IntegerLiteral", Integer.toString(index))));
    if (index <= size - 2) {
      toStringMethodBlock.add(
      factory.appendComma());
    }
  }

  /**
   * Adds a location term to a tuple class, along with
   * the related marshalling and string representation code.
   * 
   * @param name The name of the variable.
   * @param index The index of the term in the tuple.
   * @param size The number of terms in the tuple.
   * @param args The arguments to the new tuple created in the read.
   * @param tupleConstructorParams The parameters to the constructor.
   * @param tupleConstructorBlock  The block of the constructor.
   * @param writeMethodBlock The block of the write method.
   * @param readMethodBlock  The block of the read method.
   * @param toStringMethodBlock The block of the toString method.
   */
  private void addLocationTerm(String name, 
                              int index, 
                              int size, 
                              GNode args,
                              Node tupleConstructorParams, 
                              Node tupleConstructorBlock, 
                              Node writeMethodBlock, 
                              Node readMethodBlock, 
                              Node toStringMethodBlock) {
    // add the parameters 
    tupleConstructorParams.add(
      makeParameter(
        GNode.create("QualifiedIdentifier", "NetAddr"), name));
    // add the assignment statements in the constructor
    tupleConstructorBlock.add(
      factory.termAssignAddress(
        GNode.create("IntegerLiteral", Integer.toString(index)),
        GNode.create("PrimaryIdentifier", name))); 
    // add the marshall out
    writeMethodBlock.add(
      factory.writeAddress(
        GNode.create("IntegerLiteral", Integer.toString(index))));
    readMethodBlock.add(
      factory.netAddrNew(
        new String("addr"+index)));
    args.add(GNode.create("PrimaryIdentifier", "addr"+index));
    // add the marshall in
    readMethodBlock.add(
      factory.readAddress(
        GNode.create("PrimaryIdentifier", "addr"+index)));
    toStringMethodBlock.add(
      factory.appendAddress(
        GNode.create("IntegerLiteral", Integer.toString(index))));
    if (index <= size - 2) {
      toStringMethodBlock.add(
        factory.appendComma());
    }
  }

  /**
   * Adds a string term to a tuple class, along with
   * the related marshalling and string representation code.
   * 
   * @param name The name of the variable.
   * @param index The index of the term in the tuple.
   * @param size The number of terms in the tuple.
   * @param args The arguments to the new tuple created in the read.
   * @param tupleConstructorParams The parameters to the constructor.
   * @param tupleConstructorBlock  The block of the constructor.
   * @param writeMethodBlock The block of the write method.
   * @param readMethodBlock  The block of the read method.
   * @param toStringMethodBlock The block of the toString method.
   */
  private void addStringTerm(String name, 
                              int index, 
                              int size, 
                              GNode args,
                              Node tupleConstructorParams, 
                              Node tupleConstructorBlock, 
                              Node writeMethodBlock, 
                              Node readMethodBlock, 
                              Node toStringMethodBlock) {
    // add the parameters 
    tupleConstructorParams.add(
      makeParameter(
        GNode.create("QualifiedIdentifier", "String"), name));
    // add the assignment statements in the constructor
    tupleConstructorBlock.add(
      factory.termAssignString(
        GNode.create("IntegerLiteral", Integer.toString(index)),
        GNode.create("PrimaryIdentifier", name))); 
    // add the marshall out
    writeMethodBlock.add(
      factory.writeString(
        GNode.create("IntegerLiteral", Integer.toString(index))));
    // add the marshall in
    readMethodBlock.add(
      factory.readString(name)); 
    args.add(GNode.create("PrimaryIdentifier", name));
    toStringMethodBlock.add(
      factory.appendString(
        GNode.create("IntegerLiteral", Integer.toString(index))));
    if (index <= size - 2) {
      toStringMethodBlock.add(
        factory.appendComma());
    }
  }

  /**
   * Adds a float term to a tuple class, along with
   * the related marshalling and string representation code.
   * 
   * @param name The name of the variable.
   * @param index The index of the term in the tuple.
   * @param size The number of terms in the tuple.
   * @param args The arguments to the new tuple created in the read.
   * @param tupleConstructorParams The parameters to the constructor.
   * @param tupleConstructorBlock  The block of the constructor.
   * @param writeMethodBlock The block of the write method.
   * @param readMethodBlock  The block of the read method.
   * @param toStringMethodBlock The block of the toString method.
   */
  private void addFloatTerm(String name, 
                              int index, 
                              int size, 
                              GNode args,
                              Node tupleConstructorParams, 
                              Node tupleConstructorBlock, 
                              Node writeMethodBlock, 
                              Node readMethodBlock, 
                              Node toStringMethodBlock) {
    // add the parameters 
    tupleConstructorParams.add(
      makeParameter(GNode.create("PrimitiveType", "float"), name));
    // add the assignment statements in the constructor
    tupleConstructorBlock.add(
      factory.termAssignFloat(
        GNode.create("IntegerLiteral", Integer.toString(index)),
        GNode.create("PrimaryIdentifier", name))); 
    // add the marshall out
    writeMethodBlock.add(
      factory.writeFloat(
        GNode.create("IntegerLiteral", Integer.toString(index))));
    // add the marshall in
    readMethodBlock.add(
      factory.readFloat(name)); 
    // add the arguments
    args.add(GNode.create("PrimaryIdentifier", name));
    toStringMethodBlock.add(
      factory.appendFloat(
        GNode.create("IntegerLiteral", Integer.toString(index))));
    if (index <= size - 2) {
      toStringMethodBlock.add(
        factory.appendComma());
    }
  }

  /**
   * Create a parameter node.
   *
   * @param type The type of the parameter.
   * @param name The name of the parameter.
   */
  private Node makeParameter(Node type, String name) {
    return GNode.create("FormalParameter", 
      GNode.create("Modifiers"),
      GNode.create("Type", 
        type, 
        null), 
      null, 
      name, 
      null);
  }

  /**
   * Add the package declaration to a class.
   * 
   * @param thePackage The string representation of the package name.
   */
  private Node makePackage(String thePackage) {
    String[] dirs = thePackage.split("\\.");
    Node qid = GNode.create("QualifiedIdentifier"); 
    for (String dir : dirs) {
      qid.add(dir);
    }
    return GNode.create("PackageDeclaration", null, qid); 
  }

  /**
   * Create the initializer function in the OLG class.
   */
  private void createInitializer() {
    Node tableInitializerClass = 
      factory.tableInitializerClass(olgName);
    tableInitializerBody = 
      tableInitializerClass.getNode(5).getNode(4).getNode(7);
    tableInitializerBody = GNode.ensureVariable((GNode)tableInitializerBody);
    tableInitializerClass.getNode(5).getNode(4).set(7, tableInitializerBody);
    Node javaAST = GNode.cast(makeSkeleton());
    forest.put(olgName, (GNode)javaAST);
    javaAST.add(tableInitializerClass);
  }

  /**
   * Register a marshaller for each tuple.
   */
  private void registerMarshallers() {
    for (String name : tupleNames) {
      tableInitializerBody.add(
        factory.registerMarshaller( 
          GNode.create("StringLiteral", "\""+name+"\""),
          GNode.create("PrimaryIdentifier", "Tuple"+name)));
    }
  }
  
  /**
   * Add a PRACTI subscriber for an exit tuple, 
   * to exit the runtime.
   */
  private void addExitSubstriber() {
    tableInitializerBody.add(
      factory.exitNew("exit"));
    tableInitializerBody.add(
      factory.exitSub( 
        GNode.create("StringLiteral", "\"exit\""),
        GNode.create("PrimaryIdentifier", "exit")));
  }
 
  /**
   * Create the class which contains the main line
   * for the Overlog transformation.
   */
  private void createExecutable() {
    Node javaAST = GNode.cast(makeSkeleton());
    forest.put("RunOLG", (GNode)javaAST);
    javaAST.add(factory.executableClass(
     GNode.create("PrimaryIdentifier", olgName)));
  }

  /**
   * Create nodes for accessing a term from a tuple.
   * 
   * @param type The type of the term.
   * @param index The index of the term accessed.
   * @return The cast expression node of the call expression..
   */
  private Node tupleAccess(Node type, Node name, Node index) {
    Node one = GNode.create("Type", type, null);
    Node two = GNode.create("Arguments", index);
    Node three = GNode.create("CallExpression", name, null, "getTerm", two);
    Node four = GNode.create("CastExpression", one, three);
    return four;
  }

  /**
   * Creates the tuple associated with action of a rule.
   *
   * @param n The rule node.
   * @return 
   */
  private Node createActionTuple(final GNode n) {
    Node qid = GNode.create("QualifiedIdentifier",
      "Tuple" + n.getNode(2).getNode(0).getString(0));
    List<Node> args = new ArrayList<Node>();
    currentTuple = n.getNode(2);
    currentTupleTermNum = 0;
    for (Node term : n.getNode(2).<Node>getList(1)) {
      if ("CountAggregate".equals(term.getName())) {
        args.add((Node)dispatch(term));
      } else {
        Node a = (Node)dispatch(term);
        args.add(a);
      }
      currentTupleTermNum++;
    }
    currentTuple = null;
    Node action = null;
    if (materialized.contains(n.getNode(2).getNode(0).getString(0))) {
      if (n.getString(1) == null) {
        action = factory.tableNew(qid, args);
      } else {
        action = factory.tableDelete(qid, args);
      }
    } else {
      action = factory.tupleNew(qid, args);
    }
    return action;
  }

  /**
   * Creates the tuple associated with action of a rule.
   *
   * @param n The rule node.
   * @return 
   */
  private Node createActionTupleForCount(final GNode n) {
    Node qid = GNode.create("QualifiedIdentifier",
      "Tuple" + n.getNode(2).getNode(0).getString(0));
    List<Node> args = new ArrayList<Node>();
    currentTuple = n.getNode(2);
    currentTupleTermNum = 0;
    for (Node term : n.getNode(2).<Node>getList(1)) {
      if ("CountAggregate".equals(term.getName())) {
        args.add(factory.countCall());
      } else {
        Node a = (Node)dispatch(term);
        if ("CastExpression".equals(a.getName())) {
          args.add(tupleAccess(
            a.getNode(0),
            GNode.create("PrimaryIdentifier", "t"),
            a.getNode(1).getNode(3).getNode(0)));
         } else {
           args.add(a);
         }
      }
      currentTupleTermNum++;
    }
    currentTuple = null;
    Node action = null;
    if (materialized.contains(n.getNode(2).getNode(0).getString(0))) {
      if (n.getString(1) == null) {
        action = factory.tableNew(qid, args);
      } else {
        action = factory.tableDelete(qid, args);
      }
    } else {
      action = factory.tupleNew(qid, args);
    }
    return action;
  }

  /**
   * Adds the imports to the event handler class.
   *
   * @param javaAST The compilation unit.
   */
  private void addImportsToEventHandler(Node javaAST) {
    javaAST.add(
      GNode.create("ImportDeclaration", null,
         GNode.create("QualifiedIdentifier", "overlogRuntime", "EventHandler"),
         null));
    javaAST.add(
      GNode.create("ImportDeclaration", null,
         GNode.create("QualifiedIdentifier", "overlogRuntime", "EventQueue"),
         null));
    javaAST.add(
      GNode.create("ImportDeclaration", null,
         GNode.create("QualifiedIdentifier", "java", "util", "Map"),
         null));
    javaAST.add(
      GNode.create("ImportDeclaration", null,
         GNode.create("QualifiedIdentifier", "java", "util", "List"),
         null));
    javaAST.add(
      GNode.create("ImportDeclaration", null,
         GNode.create("QualifiedIdentifier", "java", "util", "ArrayList"),
         null));
  }

  /**
   * TODO : This function is a work in progress, and currently
   * does not work.
   * Creates the event handler. 
   *
   * @param n The rule node.
   * @param event The event that fires off the handler.
   */
  protected void createEventHandlerReWrite(final GNode n, final GNode event) {
    // create the action generated by this handler   
    Node action = createActionTuple(n);
    Node currentBlock = action;
    // loop through the tuples for the conditionals
    for (Node child : n.<Node>getList(3)) {
      if (!"Tuple".equals(child.getName())) {
        if (!"Expression".equals(child.getName())) {
          currentBlock = factory.conditional(
            (Node)dispatch(child), 
            currentBlock); 
        }
      }
    }
    // make sure that there were some relationals
    if (currentBlock == action) {
      currentBlock = GNode.create("Block");
    }
    // loop through the tuples for the assignments
    for (Node child : n.<Node>getList(3)) {
      if (!"Tuple".equals(child.getName())) {
        if ("Expression".equals(child.getName())) {
          currentBlock.add(dispatch(child));
        }
      }
    }
  }

  /**
   * Creates the event handler.
   *
   * @param n The rule node.
   * @param event The event that fires off the handler.
   * @param id An extension to the name to act as an identifier
   *        in the case of multiple matches to this handler.
   * @return 
   */
  private void createEventHandler(final GNode n, final Node event, String id) {
    // Next check to see if there are any aggregates 
    // in the rule head.
    boolean isMinAggregate = false;
    boolean isMaxAggregate = false;
    boolean isCountAggregate = false;
    aggIndex = 0; 
    int counter = 0; 
    for (Node term : n.getNode(2).<Node>getList(1)) {
      String name = term.getName();
      if ("MinAggregate".equals(name)) {
        isMinAggregate = true;  
        aggIndex = counter;
      } else if ("MaxAggregate".equals(name)) {
        isMaxAggregate = true;  
        aggIndex = counter;
      } else if ("CountAggregate".equals(name)) {
        isCountAggregate = true;  
        aggIndex = counter;
      }
      counter++;
    }
    boolean isAggregate = 
      isMinAggregate || isMaxAggregate || isCountAggregate;
    // create the class
    String handlerName = "EventHandler" + n.getNode(0).getString(0) + id;
    Node eventHandlerClass = 
      factory.eventHandlerClass(handlerName);
    classBody = eventHandlerClass.getNode(5);
    classBody = GNode.ensureVariable((GNode)classBody);
    eventHandlerClass.set(5,classBody);
    Node javaAST = GNode.cast(makeSkeleton());
    forest.put(handlerName, (GNode)javaAST);
    addImportsToEventHandler(javaAST);
    javaAST.add(eventHandlerClass);
    // create the process function
    Node eventHandlerProcess = factory.eventHandlerProcess();
    eventHandlerProcess = GNode.ensureVariable((GNode)eventHandlerProcess);
    classBody.add(eventHandlerProcess);
    // get the body of the function
    currentBlock = 
      GNode.ensureVariable((GNode)eventHandlerProcess.getNode(7));
    eventHandlerProcess.set(7, currentBlock);
    processTopLevel = currentBlock;
    if (isAggregate) {
      currentBlock.add(factory.eventHandlerMatches());
    }
    // Add the "event" node.
    currentBlock.add(factory.tupleAssign(
      event.getNode(0).getString(0)));

    // register the event to the handler
    String eventName = event.getNode(0).getString(0);
    if ("periodic".equals(eventName)) {
      eventName = new String(eventName + periodicNum);
    }
    tableInitializerBody.add(
      factory.registerHandle(
      GNode.create("StringLiteral", 
      "\""+eventName+"\""),
      GNode.create("PrimaryIdentifier", 
      handlerName)));

    // loop through the tuples for the loops
    for (Node child : n.<Node>getList(3)) {
      if ("Tuple".equals(child.getName())) {
         currentTuple = child;
         if ("periodic".equals(child.getNode(0).getString(0))) {
           createPeriodicTuple(child);
           periodicNum++;
         }
         if (event != child) { 
           dispatch(child);
         }
         currentTuple = null;
      }
    }
    // loop through the tuples for the constraints
    for (Node child : n.<Node>getList(3)) {
      if (!"Tuple".equals(child.getName())) {
        if ("Expression".equals(child.getName())) {
          currentBlock.add(dispatch(child));
        } else {
          GNode block = GNode.create("Block");
          currentBlock.add(
            GNode.create("ConditionalStatement", 
            dispatch(child), 
            block, 
            null)  
          );
          currentBlock = block;
        }
      }
    }
    // create the action    
    Node action = createActionTuple(n);
    currentBlock.add(action);
    if (isMinAggregate || isMaxAggregate) {
      currentBlock.add(factory.saveEvent());
      processTopLevel.add(factory.aggFunctionCall());
    } else if (isCountAggregate) {
      Node action2 = createActionTupleForCount(n);
      currentBlock.add(factory.saveEvent());
      // This is commented out so that the t refers
      // to the input tuple, not the matches list.
      // processTopLevel.add(factory.matchesFirst());
      processTopLevel.add(action2);
      processTopLevel.add(factory.bufferEvent());
    } else {
      currentBlock.add(factory.bufferEvent());
    }
  }

  /**
   * Handles the special case of creating periodic tuples.
   *
   * @param n The periodic tuple
   * @return void
   */
  private void createPeriodicTuple(Node n) {
    String tupleName = new String(n.getNode(0).getString(0) + periodicNum);
    Type t = (Type)table.root().lookup("periodic");
    makeTupleClass(tupleName, t);
    String period = n.<Node>getList(1).get(1).getString(0);
    String interval = n.<Node>getList(1).get(2).getString(0);
    tableInitializerBody.add(
    factory.periodicNew( 
    "periodic" + periodicNum,
    GNode.create("StringLiteral", "Tupleperiodic" + periodicNum),
      GNode.create("IntegerLiteral", period),
      GNode.create("IntegerLiteral", interval)));
    tableInitializerBody.add(
      factory.registerPeg( 
      GNode.create("StringLiteral", "periodic" + periodicNum),
      GNode.create("IntegerLiteral", period),
      GNode.create("IntegerLiteral", interval)));
  }
}
