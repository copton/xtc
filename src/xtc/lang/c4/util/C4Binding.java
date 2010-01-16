package xtc.lang.c4.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * C4 binding class.
 * 
 * @author Marco Yuen
 */
public class C4Binding {
  /** The binding for symbols. */
  private Map<String, String> symbols = null;

  /** The binding or struct type. */
  private Map<String, List<String>> structType = null;

  /** The binding for struct instance. */
  private Map<String, List<String>> structInst = null;

  /** The name of aspect that this binding belongs to. */
  private String aspectName = null;

  public C4Binding(String aspName) {
    this.aspectName = aspName;
    this.symbols = new HashMap<String, String>();
    this.structInst = new HashMap<String, List<String>>();
    this.structType = new HashMap<String, List<String>>();
  }

  /**
   * Returns the name of the aspect that owns this binding.
   * 
   * @return The name of the aspect.
   */
  public String getAspectName() {
    return this.aspectName;
  }

  /**
   * Adds a structure instance and its fields to the binding.
   * 
   * @param declarator
   *          The structure instance's name.
   * @param fields
   *          The fields being introduced.
   */
  public void addStructInstBinding(String declarator, List<String> fields) {
    this.structInst.put(declarator, fields);
  }

  /**
   * Adds a structure type and its fields to the binding.
   * 
   * @param declarator
   *          The structure type's name.
   * @param fields
   *          The fields being introduced.
   */
  public void addStructTypeBinding(String declarator, List<String> fields) {
    this.structType.put(declarator, fields);
  }

  /**
   * Adds a symbol and its mangled version to the binding.
   * 
   * @param origName
   *          The original symbol name.
   * @param mangledName
   *          The mangled symbol name.
   */
  public void addSymbol(String origName, String mangledName) {
    this.symbols.put(origName, mangledName);
  }

  /**
   * Checks if a declarator is a structure type or not.
   * 
   * @param declarator
   *          The declarator of interest.
   * @return True, if the given declarator is a structure type. False, otherwise.
   */
  public boolean isStructType(String declarator) {
    return structType.containsKey(declarator);
  }

  /**
   * Checks if the given declarator defined.
   * 
   * @param declarator
   *          The declarator of interest.
   * @return True, if the given declaration is defined in the binding. False, otherwise.
   */
  public boolean isDefined(String declarator) {
    return symbols.containsKey(declarator);
  }

  /**
   * Returns the mangled version of a given declarator.
   * 
   * @param declarator
   *          The original un-mangle version of the declarator.
   * @return The mangled declarator.
   */
  public String getMangledDeclarator(String declarator) {
    String mangledDeclarator = null;

    if (symbols.containsKey(declarator))
      mangledDeclarator = symbols.get(declarator);

    return mangledDeclarator;
  }

  /**
   * Returns the introduced fields associated with a structure instance.
   * 
   * @param structInstName
   *          The structure instance's name.
   * @return A list of introduced fields. Or, null.
   */
  public List<String> getStructInstFields(String structInstName) {
    return structInst.get(structInstName);
  }

  /**
   * Returns the introduced fields associated with a structure type.
   * 
   * @param structTypeName
   *          The structure type's name.
   * @return A list of introduced fields. Or, null.
   */
  public List<String> getStructTypeFields(String structTypeName) {
    return structType.get(structTypeName);
  }

}
