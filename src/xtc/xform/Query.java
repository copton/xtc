/*
 * xtc - The eXTensible Compiler
 * Copyright (C) 2005-2006 New York University
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
package xtc.xform;

import java.io.Reader;
import java.io.StringReader;
import java.io.IOException;

import xtc.tree.GNode;
import xtc.tree.Node;

import xtc.parser.Result;
import xtc.parser.SemanticValue;

/**
 * This class represents XForm AST queries.
 *
 * @author Joe Pamer
 * @version $Revision: 1.11 $
 */
public class Query {

  /** The query's AST */
  GNode ast = null;

  /**
   * Create a new Query.
   *
   * @param in A reader containing the query's source code.
   * @throws IllegalArgumentException
   *  Signals a malformed query.
   */
  public Query(Reader in) throws IllegalArgumentException {
    Result r;

    try {
      XFormParser xform_parser = new XFormParser(in,"dummy");
      r = xform_parser.pXForm(0);
    } catch (IOException x) {
      throw new AssertionError("Unexpected I/O condition");
    }

    if (r.hasValue()) {
      ast = (GNode)((Node)((SemanticValue)r).value).strip();
    } else {
      throw new IllegalArgumentException("Malformed query.");
    }
  }
  
  /**
   * Create a new query.
   *
   * @param query The query as a string.
   * @throws IllegalArgumentException
   *   Signals a malformed query.
   */
  public Query(String query) throws IllegalArgumentException {
    this(new StringReader(query));
  }

} // end class Query
