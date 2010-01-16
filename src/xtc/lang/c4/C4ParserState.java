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

import xtc.lang.CParserState;
import xtc.tree.Node;

import java.util.HashMap;

/**
 * State for parsing C4.
 *
 * @author Marco Yuen
 * @version $Revision: 1.5 $
 */
public class C4ParserState extends CParserState { 
  
  /** The name of the current aspect in context. */
  private String curAspectName = null;
  
  /** The binding of aspect and context. */
  private HashMap<String, HashMap<String, Boolean>> aspectContextBindings = null;
  
  /** The flag for indicating the current context is inside an advice. */
  public static final int FLAG_ADVICE = 0x40;
  
  public C4ParserState() {
    super();
    this.aspectContextBindings = new HashMap<String, HashMap<String, Boolean>>();
  }
  
  /**
   * Set the FLAG_ADVICE flag and current aspectName in the current context.
   *
   * @param aspectName The name of the current aspect
   */
  public void setAdvice(String aspectName) {
    top.set(FLAG_ADVICE);
    curAspectName = aspectName;
    if(DEBUG) System.out.println("Setting curAspectName to " + curAspectName);
  }

  /**
   * Sets the FLAG_ADVICE flag and current aspectName in the current context
   * 
   * @param aNode
   *          A concrete syntax tree formatting node.
   */
  public void setAdvice(Node aNode) {
    top.set(FLAG_ADVICE);
    curAspectName = aNode.getNode(0).getString(0);
  }
  
  /**
   * Bind the specified identifier. Depending on the current parsing context, the identifier is
   * either bound as a type or as an object/function/constant.
   * 
   * @param id
   *          The identifier.
   */
  public void bind(String id) {
    // Ignore the binding if a function parameter list has already
    // been parsed or the binding appears inside a structure
    // declaration list.
    if (top.next.isSet(FLAG_PARAMS) || top.next.isSet(FLAG_STRUCTURE)) {
      if (DEBUG) {
        System.out.println("ignoring bind(" + id + ", " + top.isSet(FLAG_TYPEDEF) + ")");
      }
      return;
    } else if (DEBUG) {
      System.out.println("bind(" + id + ", " + top.isSet(FLAG_TYPEDEF) + ")");
    }

    // Get the top-most scope.
    Context c = top;
    while (!c.isSet(FLAG_SCOPE)) {
      c = c.next;
    }

    if (c.isSet(FLAG_ADVICE)) {
      if (DEBUG)
        System.out.println("BIND: binding " + id + " in aspect context: " + curAspectName + " <--------");

      if (!aspectContextBindings.containsKey(curAspectName))
        aspectContextBindings.put(curAspectName, new HashMap<String, Boolean>());

      aspectContextBindings.get(curAspectName).put(id, top.isSet(FLAG_TYPEDEF) ? Boolean.TRUE : Boolean.FALSE);

      if (DEBUG)
        System.out.println("------- Aspect Bindings -------\n" + aspectContextBindings
                           + "\n------- Aspect Bindings -------");
    }

    // Record the name.
    c.bindings.put(id, top.isSet(FLAG_TYPEDEF) ? Boolean.TRUE : Boolean.FALSE);
    c.set(FLAG_MODIFIED);
  }
  
  /**
   * Determine whether the specified identifier names a type.
   * 
   * @param id
   *          The identifier.
   * @return <code>true</code> if the specified identifier names a type.
   */
  public boolean isType(String id) {
    // If we have already parsed a type specifier, the identifier does
    // not name a type.
    if (top.isSet(FLAG_TYPE_SPEC)) {
      if (DEBUG)
        System.out.println("isType(" + id + ") -> false");
      return false;
    }

    // Otherwise, we consult the symbol table.
    Context c = top;

    do {
      while (!c.isSet(FLAG_SCOPE)) {
        c = c.next;
      }

      Object value = c.bindings.get(id);
      if (null != value) {
        boolean type = ((Boolean) value).booleanValue();
        if (DEBUG)
          System.out.println("isType(" + id + ") -> " + type);
        return type;
      }

      if (c.isSet(FLAG_ADVICE) && aspectContextBindings.containsKey(curAspectName)) {
        if (DEBUG)
          System.out.println("Consulting the aspect bindings table for aspect '" + curAspectName + "' and id '" + id
                             + "' <-------");
        value = aspectContextBindings.get(curAspectName).get(id);
        if (null != value) {
          boolean type = ((Boolean) value).booleanValue();
          if (DEBUG)
            System.out.println("aspect bindings:isType(" + id + ") -> " + type);
          return type;
        }
      }

      c = c.next;
    } while (null != c);

    if (DEBUG)
      System.out.println("isType(" + id + ") -> false");
    return false;
  }
}

// vim: set sts=2 sw=2 et :
