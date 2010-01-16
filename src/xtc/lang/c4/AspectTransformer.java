/*
 * xtc - The eXTensible Compiler
 * Copyright (C) 2005-2006 Princeton University
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
package xtc.lang.c4;

import xtc.tree.GNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import xtc.xform.Query;
import xtc.xform.Engine;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Transforms Aspect constructs to pure C code.
 *
 * @author Marco Yuen
 * @version $Revision: 1.7 $
 */
public class AspectTransformer {
  /** The root of the AST to be transformed. */
  private GNode root = null;

  /** A list of names of the aspect nodes */
  private ArrayList<String> aspectList = null;

  /** A XForm engine */
  private Engine engine = null;

  /** A bindings of names and mangled names */
  private HashMap<String, HashMap<String, String>> bindings = null;

  /**
   * Global variable that indicates the name of the aspect that's being
   * transformed.
   */
  private String curAspectName = null;

  /** The counter for creating unique label statements. */
  private static int uniqueLabelCounter = 0;

  /**
   * Creates an AspectTransformer.
   * 
   * @param r
   *          The root node
   */
  public AspectTransformer(GNode r) {
    this.root = r;
    this.engine = new Engine();
    bindings = new HashMap<String, HashMap<String, String>>();

    // Create a list of node names that need to be transformed.
    this.aspectList = new ArrayList<String>();
    this.aspectList.add("AspectDefinition");
    this.aspectList.add("AspectStructureDeclaration");
    this.aspectList.add("AspectFunctionDefinition");
  };

  /**
   * @deprecated
   * A dispatcher.
   * 
   * @param name
   *          The name of the method.
   * @param args
   *          Arguments for the method.
   * @param argValues
   *          Values of the arguments.
   */
  public Object process(String name, Class<Object>[] args, Object[] argValues) {
    try {
      Method m = getClass().getMethod(name, args);
      return m.invoke(this, argValues);
    } catch (SecurityException e) {
      e.printStackTrace();
    } catch (NoSuchMethodException e) {
      e.printStackTrace();
    } catch (IllegalArgumentException e) {
      e.printStackTrace();
    } catch (IllegalAccessException e) {
      e.printStackTrace();
    } catch (InvocationTargetException e) {
      e.printStackTrace();
    }
    return null;
  }

  /**
   * Based on the current GNode name, and dispatch the correct method to perform
   * a transformation on the GNode.
   */
  public void transform() {
    List<Object> result = null;
    Query query = null;
    // Look the aspect nodes in the AST. Then transform the aspect nodes into C
    // nodes.
    for (String aspectNodeName : aspectList) {
      if (aspectNodeName.equals("AspectStructureDeclaration"))
        query = new Query("//" + aspectNodeName + "/../../../../..");
      else
        query = new Query("//" + aspectNodeName);

      result = engine.run(query, root);

      // Invoke the method with name "process"+aspectNodeName.
      if (!result.isEmpty()) {
        if (aspectNodeName.equals("AspectDefinition"))
          processAspectDefinition(result);
        else if (aspectNodeName.equals("AspectStructureDeclaration"))
          processAspectStructureDeclaration(result);
        else if (aspectNodeName.equals("AspectFunctionDefinition"))
          processAspectFunctionDefinition(result);
      }
    }
  }
  
// -------------------------------------------------------------------------
// Utility Functions
// -------------------------------------------------------------------------

  /**
   * Return the declarator of a node.
   * 
   * @param n
   *          The node.
   */
  private String getSimpleDeclarator(GNode n) {
    if (null == n)
      return null;
    Query simpleDeclarator = new Query("//SimpleDeclarator");
    List<Object> result = engine.run(simpleDeclarator, n);
    if (result.size() <= 0)
      return null;
    else
      return GNode.cast(result.get(0)).getString(0);
  }
  
  /**
   * Mangle the names in declarations.
   * 
   * @param n
   *          The node to be mangled.
   * @param prefix
   *          The string that prepend to the name.
   */
  private void mangleDeclarator(GNode n, String prefix) {
    boolean structUnion = false;
    Query simpleQuery = new Query("//SimpleDeclarator");
    Query structQuery = new Query("/DeclarationSpecifiers/StructureTypeDefinition/../..");
    Query unionQuery = new Query("/DeclarationSpecifiers/UnionTypeDefinition/../..");

    // Using XForm to determine what type of declaration n is.
    List<Object> result = engine.run(structQuery, n);
    if (result.size() > 0)
      structUnion = true;
    else {
      result = engine.run(unionQuery, n);
      if (result.size() > 0)
        structUnion = true;
    }

    // n is a structure or union declaration.
    if (structUnion) {
      GNode decl = (GNode) result.get(0);
      GNode structTypeDef = decl.getGeneric(1).getGeneric(0);
      String structTag = structTypeDef.getString(1);
      if (null != structTag) {
        structTypeDef.set(1, prefix + structTag);
        bindings.get(curAspectName).put("struct " + structTag, prefix + structTag);
      }

      GNode initDeclaratorList = decl.getGeneric(2);
      if (initDeclaratorList != null) {
        result = engine.run(simpleQuery, initDeclaratorList);
        GNode simpleDecl = (GNode) result.get(0);
        bindings.get(curAspectName).put(simpleDecl.getString(0), prefix + simpleDecl.getString(0));
        simpleDecl.set(0, prefix + simpleDecl.getString(0));
      }
      return;
    }

    // n is a function or variable declaration.
    result = engine.run(simpleQuery, n);
    GNode simpleDecl = (GNode) result.get(0);
    bindings.get(curAspectName).put(simpleDecl.getString(0), prefix + simpleDecl.getString(0));
    simpleDecl.set(0, prefix + simpleDecl.getString(0));
  }

  /**
   * Determine if a function has a void return type or not.
   *
   * @param declSpec The DeclarationSpecifier of FunctionDefinition.
   */
  private boolean isReturnVoid(GNode declSpec) {
    if(null != declSpec) {
      Query q = new Query("//VoidTypeSpecifier");
      List<Object> result = engine.run(q, declSpec);
      if(result.isEmpty())
        return false;
      else
        return true;
    }
    return false;
  }

  /**
   * This function manages the mangling of names for aspect introduction.
   * 
   * @param n
   *          The node that contains a primary identifier.
   */
  private void replacePriID(GNode n) {
    HashMap<String, String> symbols = bindings.get(curAspectName);
    List<Object> results = null;

    Query priIDQuery = new Query("//PrimaryIdentifier");
    Query structTypeRef = new Query("/DeclarationSpecifiers/StructureTypeReference/../..");
    Query typeDefName = new Query("//TypedefName");
    Query directCompSel = new Query("//DirectComponentSelection");
    Query indirectCompSel = new Query("//IndirectComponentSelection");

    results = engine.run(structTypeRef, n);
    if (results.size() > 0) {
      GNode declaration = (GNode) results.get(0);
      GNode structDecl = declaration.getGeneric(1).getGeneric(0);
      GNode initDeclList = declaration.getGeneric(2);
      /* System.out.println("-------> " + structDecl.getName()); */
      if (symbols.containsKey("struct " + structDecl.getString(1))) {
        structDecl.set(1, symbols.get("struct " + structDecl.getString(1)));
      }

      // if key is in symbol table -> this struct has an advice in it.
      if (symbols.containsKey("field_struct_tag " + structDecl.getString(1))) {
        if (null == initDeclList) {
          System.err.println("initDeclList is null!?");
          System.exit(1);
        }

        String decltor = getSimpleDeclarator(initDeclList);
        String val = symbols.get("field_struct_tag " + structDecl.getString(1));
        symbols.put("field_struct " + decltor, val);
      }

      // System.out.println("------- Symbol Table -------\n" + bindings +
      // "\n------- Symbol Table -------");
    }

    results = engine.run(priIDQuery, n);
    for (Object result : results) {
      GNode priID = GNode.cast(result);
      // System.out.println("priID: " + priID.getString(0));
      if (null != symbols && symbols.containsKey(priID.getString(0))) {
        // System.out.println("-------> Find a match in symbols table." +
        // priID.getString(0));
        priID.set(0, symbols.get(priID.getString(0)));
      }
    }

    results = engine.run(typeDefName, n);
    for (Object result : results) {
      GNode typedefName = GNode.cast(result);
      if (symbols.containsKey(typedefName.getString(0))) {
        typedefName.set(0, symbols.get(typedefName.getString(0)));
      }
    }

    results = engine.run(directCompSel, n);
    if (results.size() > 0) {
      GNode leftMost = GNode.cast(results.get(results.size() - 1));
      String structName = leftMost.getGeneric(0).getString(0);
      if (symbols.containsKey("field_struct " + structName)) {
        String fields = symbols.get("field_struct " + structName);
        System.out.println(structName + " fields: " + fields);
        System.out.println(symbols);
        if (fields.contains(leftMost.getString(1))) {
          leftMost.set(0, makeDirectComponentSel(leftMost.getGeneric(0), curAspectName));
        }
      }
    }

    results = engine.run(indirectCompSel, n);
    if (results.size() > 0) {
      GNode leftMost = (GNode) results.get(results.size() - 1);
      String structName = leftMost.getGeneric(0).getString(0);
      if (symbols.containsKey("field_struct " + structName)) {
        String fields = symbols.get("field_struct " + structName);
        if (fields.contains(leftMost.getString(1))) {
          leftMost.set(0, makeIndirectComponentSel(leftMost.getGeneric(0), curAspectName));
        }
      }
    }
  }

  
// -------------------------------------------------------------------------
// Nodes Creation Methods
// -------------------------------------------------------------------------

  /**
   * Create a structure field access statement. (eg. sturctname.field)
   *
   * @param PriID The struct name.
   * @param fieldName The field name.
   */
  public GNode makeDirectComponentSel(GNode PriID, String fieldName) {
    return GNode.create("DirectComponentSelection", PriID, fieldName);
  }
  
  /**
   * Create a structure field access with a pointer. (eg. structname->field)
   *
   * @param PriID The struct name.
   * @param fieldName The field name.
   */
  public GNode makeIndirectComponentSel(GNode PriID, String fieldName) {
    return GNode.create("IndirectComponentSelection", PriID, fieldName);
  }

  /**
   * Create a assignment.
   * 
   * @param id The left hand value.
   * @param val The right hand value.
   */
  public GNode makeAssignment(String id, GNode val) {
    return GNode.create("AssignmentExpression", 
                        GNode.create("PrimaryIdentifier", id), "=", val);
  }
  
  /**
   * Create a goto statement.
   * 
   * @param label
   *          The label to jump to.
   */
  public GNode makeGoto(String label) {
    GNode primaryId = GNode.create("PrimaryIdentifier", label);
    GNode gotoStmt = GNode.create("GotoStatement", null, primaryId);
    return gotoStmt;
  }
  
  /**
   * Create a goto statement. However, instead of using a label as the argument,
   * it uses a variable (eg. goto *var). This is a GCC extension.
   * 
   * @param label
   *          The name of the label variable.
   * @return A goto statement.
   */
  public GNode makeGotoWithAddress(String label) {
    return GNode.create("GotoStatement", GNode.create("PrimaryIdentifier", label));
  }

  /**
   * Create a label.
   * 
   * @param label
   *          The label name.
   * @param stmt
   *          Statements associate with that label.
   * @return A label in C.
   */
  public GNode makeLabel(String label, GNode stmt) {
    return GNode.create("LabeledStatement",
                        GNode.create("NamedLabel", label, null), stmt);
  }
  
  /**
   * Create a do-while loop.
   *
   * @param stmts The statemenst inside the do-while loop.
   * @param condition The invariant for the do-while loop.
   * @return A do-while loop construct.
   */
  public GNode makeDoStmt(GNode stmts, GNode condition) {
    if (null == stmts) stmts = GNode.create("CompoundStatement", null);
    return GNode.create("DoStatement", stmts, condition);
  }
  
  /**
   * Create a return statement.
   *
   * @param val The return value.
   * @return A return statement with the specified value.
   */
  public GNode makeReturn(GNode val) {
    return GNode.create("ReturnStatement", val);
  }

  /**
   * Create a structure declaration.
   *
   * @param type The Qualifier for the structure.
   * @param decl The name of the structure.
   * @return A sturct declaration with all the fields declared.
   */
  public GNode makeStructureDeclaration(GNode type, String decl) {
    GNode structDecl = GNode.create("StructureDeclaration");
    GNode specQualList = GNode.create("SpecifierQualifierList");
    GNode declarator = GNode.create("StructureDeclarationList");
    GNode attrDecl = GNode.create("AttributedDeclarator");
    
    attrDecl.add(null);
    attrDecl.add(GNode.create("SimpleDeclarator").add(decl));
    attrDecl.add(null);
    declarator.add(attrDecl);
    
    specQualList.add(type);
    
    structDecl.add(null);
    structDecl.add(specQualList);
    structDecl.add(declarator);
    
    return structDecl;
  }
  
  /**
   * Create a declaration.
   *
   * @param declSpecifier The type and qualifier for the declaration.
   * @param declarator The declarator for the declaration.
   * @return A declaration with the specificed type and name.
   */
  public GNode makeDeclaration(GNode declSpecifier, String declarator) {
    GNode initDeclList = GNode.create("InitializedDeclaratorList");
    GNode initDecl = GNode.create("InitializedDeclarator");
    GNode simpleDecl = GNode.create("SimpleDeclarator", declarator);
    
    // Create the InitializedDeclarator
    initDecl.add(null);
    initDecl.add(simpleDecl);
    initDecl.add(null);
    initDecl.add(null);
    initDecl.add(null);
    
    // Add it to InitializedDeclaratorList
    initDeclList.add(initDecl);
    
    GNode decl = GNode.create("Declaration");
    decl.add(null); // No __extension__
    decl.add(declSpecifier); // Type
    decl.add(initDeclList); // Declarator
    
    return decl;
  }
  
  /**
   * Create a declataion with initializer.
   *
   * @param declSpecifier The type.
   * @param initDeclarator The initialzier.
   * @return A declaration.
   */
  public GNode makeDeclaration(GNode declSpecifier, GNode initDeclarator) {
    GNode decl = GNode.create("Declaration");
    GNode initDeclList = GNode.create("InitializedDeclaratorList");
    
    initDeclList.add(initDeclarator);
    
    decl.add(null);
    decl.add(declSpecifier);
    decl.add(initDeclList);
    return decl;
  }

  /**
   * Create a structure definition.
   *
   * @param structTag The struct tag. Optional.
   * @param beforeAttrs The attributes. Optional.
   * @param declList The fields declaration.
   * @param afterAttrs The attributes. Optional.
   * @return A complete structure.
   */
  public GNode makeStructure(String structTag, GNode beforeAttrs, GNode declList, GNode afterAttrs) {
    GNode structure = GNode.create("StructureTypeDefinition");
    // Attributes
    if(null == beforeAttrs)
      structure.add(null);
    else
      structure.add(beforeAttrs);
    
    // Structure tag
    if(null == structTag)
      structure.add(null);
    else
      structure.add(structTag);
    
    // StructureDeclarationList
    structure.add(declList);
    
    // Attributes
    if(null == afterAttrs)
      structure.add(null);
    else
      structure.add(afterAttrs);
    
    return structure;
  }
  
  /**
   * Create a LabelAddressExpression.
   *
   * @param label The label.
   * @return A GNode LabelAddressExpression.
   */
  public GNode makeLabelAddressExpression(String label) {
    return GNode.create("LabelAddressExpression", label);
  }
  
  /**
   * Create a identifier with pointers.
   * 
   * @param numPointer
   * @param declarator
   * @return A GNode for pointer.
   */
  public GNode makePointerDeclarator(int numPointer, String declarator) {
    GNode pointerDecl = GNode.create("PointerDeclarator");
    GNode pointer = (GNode)GNode.create("Pointer").add(GNode.create("TypeQualifierList")).add(null);
    GNode tmpPointer = pointer, newPointer = null;
    
    for(int i=1; i<numPointer; ++i) {
      newPointer = (GNode)GNode.create("Pointer").add(GNode.create("TypeQualifierList")).add(null);
      tmpPointer.set(1,newPointer);
      tmpPointer = newPointer;
    }
    
    pointerDecl.add(pointer);
    pointerDecl.add(GNode.create("SimpleDeclarator").add(declarator));
    
    return pointerDecl;
  }
 
// -------------------------------------------------------------------------
// Aspect Nodes Transformation
// -------------------------------------------------------------------------

  /**
   * Transform a global advice to C code.
   *
   * @param nodes The list of AspectDefinition nodes.
   */
  public void processAspectDefinition(List<Object> nodes) {
    String prefix = null;
    int cnt = 0;
    ArrayList<GNode> declList = null;
    
    // Looping through the global declarations.
    for(int i=0; i<root.size(); ++i) {
      GNode n = root.getGeneric(i);
      declList = new ArrayList<GNode>();
      if(null != n && n.hasName("AspectDefinition")) {
        	curAspectName = n.getString(1);
        	// If this aspect has not been encountered before, 
        	// create a binding table for this aspect.
        	if(!bindings.containsKey(curAspectName))
        		bindings.put(curAspectName, new HashMap<String, String>());
        	
        	prefix = "__aspect__" + curAspectName + "__";
        	
        	// Process the declarations and function definitions.
        	// j starts at 2 because the first child would the string `aspect' and
        	// the second child would be the name of the aspect. The function defs and
        	// declarations begin at the third child.
        	for(int j=2; j<n.size(); ++j) {
        	  GNode decl = (GNode)n.get(j);
        	  mangleDeclarator(decl, prefix);
        	  declList.add(decl);
        	}
        	
        	root.remove(i);
        	root.addAll(i, declList);
        	++cnt;
      }
    }
    
    if(cnt != nodes.size()) {
      System.err.println("cnt != nodes.size()");
      System.exit(1);
    }
  }

  /**
   * Transform structure or union introduction into C code.
   * 
   * @param nodes
   *          The list of introduction nodes.
   */
  public void processAspectStructureDeclaration(List<Object> nodes) {
    // System.out.println("-------> Process AspectStructSpecifier");
    Query simpleDeclQuery = new Query("//SimpleDeclarator");
    List<Object> result = null;
    // A space-separated list of field names from the introduction aspect.
    String fieldsString = new String();

    for (Object childObject : nodes) {
      GNode declaration = GNode.cast(childObject);
      // System.out.println("-------> " + declaration.getName());
      GNode structTypeDef = declaration.getGeneric(1).getGeneric(0);
      String structTag = structTypeDef.getString(1);
      // System.out.println("-------> " + structTypeDef.getName());
      // System.out.println("\t\tStructure's Tag: " + structTag);
      GNode structDeclList = structTypeDef.getGeneric(2); // StructureDeclarationList
      GNode structDeclaration = null;
      GNode initDeclList = declaration.getGeneric(2);

      // Get the fields being introduced.
      for (int j = 0; j < structDeclList.size(); ++j) {
        GNode structDecl = GNode.cast(structDeclList.get(j));
        if (null != structDecl) {
          // System.out.println("\t\t" + structDecl.getName());
          GNode declSpec = structDecl.getGeneric(1);
          // System.out.println("\t\t" + declSpec.getName());
          if (declSpec.hasName("AspectStructureDeclaration")) {
            curAspectName = declSpec.getString(1);
            if (!bindings.containsKey(curAspectName))
              bindings.put(curAspectName, new HashMap<String, String>());

            GNode A_StructDeclList = declSpec.getGeneric(2); // StructureDeclarationList
            // System.out.println("-------> " + A_StructDeclList.getName());
            result = engine.run(simpleDeclQuery, A_StructDeclList);
            // for(Iterator simpleDecl = result.iterator();
            // simpleDecl.hasNext(); ) {
            for (Object simDeclNodeObject : result) {
              if (!GNode.test(simDeclNodeObject)) {
                System.err.println("Simple Decl is not a generic node.");
                System.exit(-1);
              }
              GNode simDeclNode = GNode.cast(simDeclNodeObject);
              fieldsString = fieldsString.concat(simDeclNode.getString(0) + ' ');
            }

            // Convert the structure aspect introduction into a nested
            // structure.
            structDeclaration = makeStructureDeclaration(makeStructure(null, null, A_StructDeclList, null),
                                                         declSpec.getString(1));
            structDeclList.set(j, structDeclaration);
          }
        }
      }

      bindings.get(curAspectName).put("field_struct_tag " + structTag, fieldsString);
      if (null != initDeclList) {
        for (Object childInitDecl : initDeclList) {
          GNode initDecl = GNode.cast(childInitDecl);
          bindings.get(curAspectName).put("field_struct " + getSimpleDeclarator(initDecl), fieldsString);
        }
      }
    }
  }

  /**
   * Transform AspectFunctionDefinition to C code.
   *
   * @param nodes
   */
  public void processAspectFunctionDefinition(List<Object> nodes) {
    AspectFunctionAnalyzer funcAnalyzer = new AspectFunctionAnalyzer(GNode.cast(nodes.get(0)));
    funcAnalyzer.analyze();

    boolean isAround = false, returnVoid = false, printReturn = false, hasAround = false, bodyBegin = false;
    ArrayList<String> aroundList = new ArrayList<String>();
    Query proceedCheck = new Query("//FunctionCall/PrimaryIdentifier/\"proceed\"");
    Query simpleDecl = new Query("//SimpleDeclarator");
    List<Object> result = null;
    hasAround = funcAnalyzer.hasAround();
    //System.out.println("AspectFunctionDefinition ------->");

    /*
     * XXX: To make this cleaner. I will probably need to change the grammar and use 
     * the inside-out operator from XForm.
     * Search for AspectFunctionDefinition in the root level.
     * If found, do the transformation. Then, replace AspectFunctionDefinition
     * with FunctionDefinition.
     */
    for(int nodeInx = 0; nodeInx < root.size(); ++nodeInx) {
      boolean gotReturn = false, gotProceed = false;
      
      GNode node = root.getGeneric(nodeInx);
      if(null == node || !node.hasName("AspectFunctionDefinition"))
        continue;
      
      GNode returnType = node.getGeneric(1);
      //System.out.println("Return Type:" + returnType.getName());
      returnVoid = isReturnVoid(returnType);
      
      GNode paramList = node.getGeneric(2).getGeneric(1);
      //System.out.println("Parameter List: " + paramList.getName());
      
      // GNode aspectCompound = (GNode)node.get(4);
      //System.out.println("Compound: " + aspectCompound.getName());
      
      // The node that will eventually replace AspectCompoundStatement.
      GNode replacementNode = GNode.create("CompoundStatement");
      
      GNode beforeAReturnVal = null;

      // >>>> ----------------------- Declaring __returned__
      // Declare __returned__ if the return type is non-void.
      if(!(returnVoid)) {
        // System.out.println("-------> Non-void return type.");
        // proxyNode = GNode.create("CompoundStatement");
        replacementNode.add(makeDeclaration(returnType, "__returned__"));
      }
      // <<<< ----------------------- Declaring __returned__ END
      
      // >>>> ----------------------- Declaring void *gotoDest
      if(hasAround) {
        GNode initDecl = GNode.create("InitializedDeclarator");
        for(int i=0; i<5; ++i) {
          initDecl.add(null);
        }
        initDecl.set(1, makePointerDeclarator(1, "gotoDest"));
        replacementNode.add(makeDeclaration(GNode.create("VoidTypeSpecifier", false), initDecl));
      }
      // <<<< ----------------------- Declaring void *gotoDest END

      // >>>> ----------------------- Transforming Before Advice
      if(funcAnalyzer.hasBefore()) {
        GNode aspectStmtList = funcAnalyzer.getBefore();

        // Convert AspectStatement in AspectStmtList to CompoundStatement's.
        for (Object aspectStmtChild : aspectStmtList) {
          GNode tmpCompound = GNode.create("CompoundStatement");
          GNode aspectStmt = GNode.cast(aspectStmtChild);
          curAspectName = aspectStmt.getString(1);
          //result = engine.run(proceedCheck, aspectStmt);
          if(funcAnalyzer.isAround(curAspectName)) {
            isAround = true;
            aroundList.add(curAspectName);
          }
          
          // System.out.println("-------> Processing (Before)" + aspectStmt.getString(1));
          
          // Skip the Keyword and aspect name, and copy all the declarations or 
          // expressions over to tmpCompound.
          
          for (int i=2; i<aspectStmt.size(); ++i) {
            GNode tmp = GNode.cast(aspectStmt.get(i));
            //System.out.println("-------> Adding " + tmp.getName());

            // Set a label. Preserve advice order.
            if(bodyBegin) {
              bodyBegin = false;
              tmpCompound.add(makeLabel("__body_begin", null));
            }
            
            // XXX I need to revamp this sometime in the future. Suggestions on improvements are VERY welcome :)
            result = engine.run(new Query("//FunctionCall/PrimaryIdentifier/\"proceed\"/../../.."), tmp);
            //System.out.println("Result: " + result.size());
            //if(tmp.hasName("ExpressionStatement") && tmp.getGeneric(0).hasName("FunctionCall")
            //                && tmp.getGeneric(0).getGeneric(0).getString(0).equals("proceed") && isAround) {
            if (isAround && result.size() > 0) {
              for (Object expStmtChild : result) {
                GNode expStmt = GNode.cast(expStmtChild);
                System.out.println("expStmt: " + expStmt.getName());
                // System.out.println("tmp: " + tmp.getName());

                gotProceed = true;
                if (tmp.hasName("ExpressionStatement")) {
                  GNode expressionList = tmp.getGeneric(0).getGeneric(1);
                  List<Object> simResult = engine.run(simpleDecl, paramList);
                  for (int paramInx = 0; paramInx < simResult.size(); ++paramInx) {
                    GNode simpleDeclarator = GNode.cast(simResult.get(paramInx));
                    tmpCompound.add(makeAssignment(simpleDeclarator.getString(0), expressionList.getGeneric(paramInx)));
                  }

                  tmpCompound.add(makeAssignment("gotoDest", makeLabelAddressExpression("__" + curAspectName
                                                                                        + "__return_point"
                                                                                        + uniqueLabelCounter)));

                  // Goto main body
                  tmpCompound.add(makeGoto("__body_begin"));

                  // Declare label
                  tmpCompound.add(makeLabel("__" + curAspectName + "__return_point" + uniqueLabelCounter++, null));
                } else {
                  List<Object> parResults = engine.run(new Query("//FunctionCall/PrimaryIdentifier/\"proceed\"/../../../.."),
                                                                 tmp);
                  // System.out.println("parResults size: " +
                  // parResults.size());
                  // for(Iterator pit = parResults.iterator(); pit.hasNext(); )
                  // {
                  for (Object stmtChild : parResults) {
                    GNode parent = GNode.cast(stmtChild);
                    ArrayList<GNode> statements = new ArrayList<GNode>();
                    // System.out.println("parent: " + parent.getName());
                    for (int j = 0; j < parent.size(); ++j) {
                      GNode child = parent.getGeneric(j);
                      // if(null != child && child.equals(expStmt)) {
                      if (null != child && result.contains(child)) {
                        GNode expressionList = child.getGeneric(0).getGeneric(1);
                        List<Object> simResult = engine.run(simpleDecl, paramList);
                        for (int paramInx = 0; paramInx < simResult.size(); ++paramInx) {
                          GNode simpleDeclarator = GNode.cast(simResult.get(paramInx));
                          statements.add(makeAssignment(simpleDeclarator.getString(0),
                                                        expressionList.getGeneric(paramInx)));
                        }

                        statements.add(makeAssignment("gotoDest", makeLabelAddressExpression("__" + curAspectName
                                                                                             + "__return_point"
                                                                                             + uniqueLabelCounter)));
                        statements.add(makeGoto("__body_begin"));
                        statements.add(makeLabel("__" + curAspectName + "__return_point" + uniqueLabelCounter++, null));
                        parent.remove(j);
                        parent.addAll(j, statements);
                      }
                    }
                  }
                }
              }
              if (!tmp.hasName("ExpressionStatement"))
                tmpCompound.add(tmp);
            } else if (tmp.hasName("ReturnStatement") && isAround) {
              gotReturn = true;
              if (!gotProceed) {
                tmpCompound.add(makeGoto("__after_" + curAspectName + "__start_point"));
                tmpCompound.add(makeLabel("__" + curAspectName + "__return_point", null));
              }
              replacePriID(tmp);
              tmpCompound.add(tmp);
            } else {
              replacePriID(tmp);
              tmpCompound.add(tmp);
            }
          }
          
          // Make sure there is a return statement for around advice that instrument on non-void 
          // return type function.
          if(isAround && !returnVoid && !gotReturn) {
            System.err.println("Missing return statement in before advice("+ curAspectName 
                            +") for non-void function.");
            System.exit(1);
          }
          
          // If the funciton has void return type, then jump to the end of the funciton.
          if(isAround && returnVoid) {
            if(!gotProceed) {
              tmpCompound.add(makeGoto("__after_" +curAspectName+"__start_point"));
              tmpCompound.add(makeLabel("__"+curAspectName+"__return_point", null));
            }
            tmpCompound.add(makeGoto("__return_point"));
          }
          if(isAround)
            bodyBegin = true;
          isAround = false;
          // No annotations.
          // tmpCompound.add(null);
          // Finally, add tmpCompound to replacementNode.
          replacementNode.add(tmpCompound);
        }
      }
      // <<<< ----------------------- Transforming Before Advice END

      // From here on now, proxyNode will either be an alias of replacementNode, or
      // a completely new node.
      GNode proxyNode = replacementNode;
      
      // body_begin label
      if(bodyBegin)
        proxyNode.add(makeLabel("__body_begin", null));

      // >>>> ----------------------- Transforming Body
      GNode returnVal = null;
      ArrayList<GNode> body = funcAnalyzer.getBody();
      for (Object bodyChild : body) {  
        GNode tmp = GNode.cast(bodyChild);

        // Replace ReturnStatement (return __val__) with an Assignment __returned__ = __val__
        if(tmp.hasName("ReturnStatement")) {
          returnVal = tmp.getGeneric(0);
          // assignment
          if(!returnVoid)
            proxyNode.add(makeAssignment("__returned__", returnVal));
          else
            printReturn = true;
          // goto
          proxyNode.add(makeGoto("__aspects_done"));
        } else {
          proxyNode.add(tmp);
        }
      }
      // <<<< ----------------------- Transforming Body END

      // >>>> ----------------------- ASPECTS_AFTER_PROLOGE
      proxyNode.add(makeLabel("__aspects_done", makeDoStmt(null, GNode.create("IntegerConstant", "0"))));
      // <<<< ----------------------- ASPECTS_AFTER_PROLOGE END

      // >>>> ----------------------- Transforming After Advice
      if(funcAnalyzer.hasAfter()) {
        // System.out.println("-------> After advice(s) detected. Start processing ..."); 
        GNode aspectStmtList = funcAnalyzer.getAfter();
        for (Object aspectStmtChild : aspectStmtList) {  
          GNode aspectStmt = GNode.cast(aspectStmtChild);
          GNode tmpCompound = GNode.create("CompoundStatement");
          curAspectName = aspectStmt.getString(1);
          if(aroundList.contains(curAspectName)) {
            aroundList.remove(curAspectName);
            isAround = true;
          }

          // Skip the Keyword and aspect name, and copy all the declarations or 
          // expressions over to tmpCompound.
          if(isAround) {
            tmpCompound.add(makeLabel("__after_" + curAspectName + "__start_point",null));
          }
          for(int i=2; i<aspectStmt.size(); ++i) {
            GNode tmp = (GNode)aspectStmt.get(i);
            result = engine.run(proceedCheck, tmp);
            if(result.size() > 0) {
              System.err.println("Calling proceed in after advice is not allowed.");
              System.exit(1);
            }
            // System.out.println("-------> Adding " + tmp.getName());
            replacePriID(tmp);
            if(tmp.hasName("ReturnStatement")) {
              GNode retVal = tmp.getGeneric(0);
              tmpCompound.add(makeAssignment("__returned__", retVal));
            } else
              tmpCompound.add(tmp);
          }
          tmpCompound.add(makeGoto("__"+aspectStmt.getString(1)+"____out__"));
          //ASPECT_AFTER_END
          tmpCompound.add(makeLabel("__"+aspectStmt.getString(1)+"____out__", 
                                    makeDoStmt(null, GNode.create("IntegerConstant", "0"))));

          if(isAround) {
            //tmpCompound.add(makeGoto("__" + curAspectName + "__return_point"));
            tmpCompound.add(makeGotoWithAddress("gotoDest"));
          }
          
          isAround = false;
          // No annotations.
          // tmpCompound.add(null);
          // Finally, add tmpCompound to replacementNode.
          proxyNode.add(tmpCompound);
        }
      }
      // <<<< ----------------------- Transforming After Advice END
      
      // ASPECTS_AFTER_EPILOGUE
      proxyNode.add(makeLabel("__return_point", null));
      if (!returnVoid) {
        if (null != beforeAReturnVal)
          proxyNode.add(makeReturn(beforeAReturnVal));
        else
          proxyNode.add(makeReturn(GNode.create("PrimaryIdentifier", "__returned__")));
      } else if (printReturn)
        proxyNode.add(makeReturn(null));

      if (!proxyNode.equals(replacementNode)) {
        // System.out.println("-------> Proxy node is different from replacement node.");
        // proxyNode.add(null);
        replacementNode.add(proxyNode);
      }
      
      // No annotations.
      // replacementNode.add(null);
      // Replacing AspectCompoundStatement with a CompoundStatement.
      node.set(4, replacementNode);
		
      // XXX
      // Replace node with a FunctionDefinition.
      GNode functionDef = GNode.create("FunctionDefinition");
      for (Object nodeChild : node) {
        functionDef.add(nodeChild);
      }

      root.set(nodeInx, functionDef);
      // XXX
    }

    if(aroundList.size() != 0) {
      for(int i=0; i<aroundList.size(); ++i)
        System.err.println("Missing after advice for: " + aroundList.get(i));
      System.exit(1);
    }
  }
}
