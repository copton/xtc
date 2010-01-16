package xtc.lang.c4;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import xtc.lang.c4.advice.C4AfterAdvice;
import xtc.lang.c4.advice.C4AroundAdvice;
import xtc.lang.c4.advice.C4BeforeAdvice;
import xtc.lang.c4.advice.C4GlobalIntroAdvice;
import xtc.lang.c4.advice.C4StructureIntroductionAdvice;
import xtc.lang.c4.util.C4Binding;

/**
 * A logical representation of an aspect.
 * 
 * @author Marco Yuen
 * @version $Revision: 1.2 $
 */
public class C4Aspect {

  /** The name of this aspect. */
  private String aspectName = null;

  /** The variable bindings for this aspect. */
  private C4Binding bindings = null;

  /** A mapping of function -> before advice. */
  private Map<String, List<C4BeforeAdvice>> beforeAdvice = null;

  /** A mapping of function -> around advice. */
  private Map<String, List<C4AroundAdvice>> aroundAdvice = null;

  /** A mapping of function -> after advice. */
  private Map<String, List<C4AfterAdvice>> afterAdvice = null;

  /** A list of global introduction advice. */
  private List<C4GlobalIntroAdvice> globalIntroAdvice = null;

  /** A list of structure introduction advice. */
  private List<C4StructureIntroductionAdvice> structIntroAdvice = null;

  /**
   * Constructor for a new aspect.
   * 
   * @param aspName
   *          The name of the aspect.
   */
  public C4Aspect(String aspName) {
    aspectName = aspName;
    bindings = new C4Binding(this.aspectName);
    beforeAdvice = new HashMap<String, List<C4BeforeAdvice>>();
    afterAdvice = new HashMap<String, List<C4AfterAdvice>>();
    aroundAdvice = new HashMap<String, List<C4AroundAdvice>>();
    globalIntroAdvice = new ArrayList<C4GlobalIntroAdvice>();
    structIntroAdvice = new ArrayList<C4StructureIntroductionAdvice>();
  }

  /**
   * Adds a new before advice to this aspect.
   * 
   * @param theBeforeAdvice
   *          The before advice.
   * @param functionName
   *          The function name that the before advice is instrumenting on.
   */
  public void addBeforeAdvice(C4BeforeAdvice theBeforeAdvice, String functionName) {
    List<C4BeforeAdvice> adviceList = null;

    if (beforeAdvice.containsKey(functionName))
      beforeAdvice.get(functionName).add(theBeforeAdvice);
    else {
      adviceList = new ArrayList<C4BeforeAdvice>();
      adviceList.add(theBeforeAdvice);
      beforeAdvice.put(functionName, adviceList);
    }
  }

  /**
   * Adds a new around advice to this aspect.
   * 
   * @param theAroundAdvice
   *          The around advice.
   * @param functionName
   *          The function name that the around advice is instrumenting on.
   */
  public void addAroundAdvice(C4AroundAdvice theAroundAdvice, String functionName) {
    List<C4AroundAdvice> adviceList = null;

    if (beforeAdvice.containsKey(functionName))
      aroundAdvice.get(functionName).add(theAroundAdvice);
    else {
      adviceList = new ArrayList<C4AroundAdvice>();
      adviceList.add(theAroundAdvice);
      aroundAdvice.put(functionName, adviceList);
    }
  }

  /**
   * Adds a new after advice to this aspect.
   * 
   * @param theAfterAdvice
   *          The after advice.
   * @param functionName
   *          The function name that the after advice is instrumenting on.
   */
  public void addAfterAdvice(C4AfterAdvice theAfterAdvice, String functionName) {
    List<C4AfterAdvice> adviceList = null;

    if (afterAdvice.containsKey(functionName))
      afterAdvice.get(functionName).add(theAfterAdvice);
    else {
      adviceList = new ArrayList<C4AfterAdvice>();
      adviceList.add(theAfterAdvice);
      afterAdvice.put(functionName, adviceList);
    }
  }

  /**
   * Adds a new global introduction advice.
   * 
   * @param theGlobalAdvice
   *          The global introduction advice.
   */
  public void addGlobalIntroAdvice(C4GlobalIntroAdvice theGlobalAdvice) {
    globalIntroAdvice.add(theGlobalAdvice);
  }

  /**
   * Adds a new structure introduction advice.
   * 
   * @param theStructAdvice
   *          The structure introduction advice.
   */
  public void addStructIntroAdvice(C4StructureIntroductionAdvice theStructAdvice) {
    structIntroAdvice.add(theStructAdvice);
  }

  /**
   * Adds a new mangled variable to the bindings.
   * 
   * @param originalName
   *          The original variable name.
   * @param mangledName
   *          The mangled variable name.
   */
  public void addToBindings(String originalName, String mangledName) {
    this.bindings.addSymbol(originalName, mangledName);
  }

  /**
   * Returns the bindings of the current aspect.
   * 
   * @return The bindings.
   */
  public C4Binding getBindings() {
    return this.bindings;
  }

  /**
   * Returns the name of the aspect.
   * 
   * @return The aspect's name.
   */
  public String getAspectName() {
    return this.aspectName;
  }

  /**
   * Stores the introduced fields.
   * 
   * @param fields
   *          The fields being introduced.
   * @param structureTagName
   *          The tag name, if any, of the structure.
   * @param declarators
   *          The declarators.
   * @param isType
   *          A flag used to indicate the delcarators are types or instances.
   */
  public void addStructureIntroFields(List<String> fields, String structureTagName, List<String> declarators,
                                      boolean isType) {
    // Sanity check.
    assert (structureTagName == null && declarators.isEmpty());
    if (structureTagName == null && declarators.isEmpty()) {
      System.err.println("No tag name nor instance name found. Abort.");
      System.exit(-1);
    }

    // System.out.println("\nC4 Aspect:\nFields introduced: " + fields);
    // System.out.println("Structure tag name: " + structureTagName);
    // System.out.println("Declarators: " + declarators);
    // System.out.println("isType: " + isType);

    // Associate the declarators (types or instance) with introduced fields.
    for (String declarator : declarators) {
      if (isType)
        this.bindings.addStructTypeBinding(declarator, fields);
      else
        this.bindings.addStructInstBinding(declarator, fields);
    }

    // If there is a tag name for the structure, associate it with the introduced fields.
    if (structureTagName != null)
      this.bindings.addStructTypeBinding(structureTagName, fields);

  }
}
