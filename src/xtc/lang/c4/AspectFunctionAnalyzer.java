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

import java.util.ArrayList;
import java.util.List;

import xtc.tree.GNode;
import xtc.xform.Query;
import xtc.xform.Engine;

/**
 * The class is used during transformation from aspect-ized C to pure C.
 * 
 * @author Marco Yuen
 * @version $Revision: 1.5 $
 */
public class AspectFunctionAnalyzer {

  /** The XForm engine */
  private Engine engine;

  /** Flag to determine whether there is a before advice. */
  private boolean hasBefore = false;

  /** Flag to determine whether there is a after advice. */
  private boolean hasAfter = false;

  /** Flag to determine whether there is a around advice. */
  private boolean hasAround = false;

  /** The GNode to analyzed. */
  private GNode functionNode = null;

  /** A list of before advice. */
  private ArrayList<String> beforeAdvice = null;

  /** The GNode of AspectStatementList (Before). */
  private GNode beforeAdviceNode = null;

  /** A list of after advice. */
  private ArrayList<String> afterAdvice = null;

  /** The GNode of AspectStatementList (After).*/
  private GNode afterAdviceNode = null;

  /** THe list of around advice. */
  private ArrayList<String> aroundAdvice = null;

  /** The Statement in the function body. */
  private ArrayList<GNode> functionBody = null;

  /**
   * The constructor.
   *
   * @param n a reference to the function being analyzed.
   */
  public AspectFunctionAnalyzer(GNode n) {
    engine = new Engine();
    this.functionNode = n;
    this.beforeAdvice = new ArrayList<String>();
    this.afterAdvice = new ArrayList<String>();
    this.aroundAdvice = new ArrayList<String>();
    this.functionBody = new ArrayList<GNode>();
  }

  /**
   * Start the analysis on the aspect function.
   * 
   */
  public void analyze() {
    List<Object> results;

    // System.out.println("Starting to Analyze ...");
    if (null == functionNode)
      throw new AssertionError("functionNode is NULL.");

    // Get the AspectStatementList's.
    Query aspectStatmList = new Query("//AspectStatementList");
    results = engine.run(aspectStatmList, functionNode);

    // Analyze before advice
    beforeAdviceNode = GNode.cast(results.get(0));
    if (beforeAdviceNode.size() > 0)
      hasBefore = true;
    for (Object beforeAdviceChild : beforeAdviceNode) {
      GNode n = GNode.cast(beforeAdviceChild);
      beforeAdvice.add(n.getString(1));
    }

    // Analyze after advice
    afterAdviceNode = (GNode) results.get(1);
    if (afterAdviceNode.size() > 0)
      hasAfter = true;
    for (Object afterAdviceChild : afterAdviceNode) {
      GNode n = GNode.cast(afterAdviceChild);
      afterAdvice.add(n.getString(1));

      // Recording around advice.
      if (beforeAdvice.contains(n.getString(1))) {
        hasAround = true;
        aroundAdvice.add(n.getString(1));
      }
    }

    // Capturing the body.
    GNode aspectCompound = (GNode) functionNode.get(4); // I may use XForm here.
    for (Object aspectCompChild : aspectCompound) {
      GNode n = GNode.cast(aspectCompChild);
      if (null != n) {
        if (n.hasName("AspectStatementList"))
          continue;
        functionBody.add(n);
      }
    }
  }

  /**
   * Determine if there is before advice
   *
   * @return True, there is before advice.
   */
  public boolean hasBefore() {

    return this.hasBefore;
  }

  /**
   * Get all of the before advice.
   *
   * @return A reference to all of the before advice of the current function.
   */
  public GNode getBefore() {

    return this.beforeAdviceNode;
  }

  /**
   * Extract the function body from a aspect function.
   *
   * @return A list of statements in the function body.
   */
  public ArrayList<GNode> getBody() {
    return this.functionBody;
  }

  /**
   * Determine if there is after advice.
   * 
   * @return True if there is at least one after advice. False, otherwise.
   */
  public boolean hasAfter() {
    return this.hasAfter;
  }

  /**
   * Extract all the after advice for transformation.
   *
   * @return The after AspectStatementList
   */
  public GNode getAfter() {

    return this.afterAdviceNode;
  }

  /**
   * Check if there is around advice in the current function 
   * 
   * @return True if there is an around advice detected.
   */
  public boolean hasAround() {

    return this.hasAround;
  }

  /**
   * Check if the specified advice name is an around advice.
   *
   * @param name The aspect name.
   * @return True, if the given aspect name is an around advice.
   */
  public boolean isAround(String name) {

    return aroundAdvice.contains(name);
  }
}
