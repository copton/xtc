/*
 * xtc - The eXTensible Compiler
 * Copyright (C) 2006 New York University
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

import java.util.List;
import java.util.Iterator;

import xtc.tree.Node;

/**
 * LinesFunction external method class.
 *
 * @version $Revision: 1.7 $
 */
class LinesFunction implements Function {
  /**
   * Get the name of the function.
   * @return The functions name
   */
  public String getName() {
    return "lines";
  }
  
  /**
   * prints line information for each item in the sequence
   * @param args The sequence of items
   * @return The sequence of items passed in args
   */
  public Object apply(List<Object> args) {
    Engine.Sequence<?> arg = (Engine.Sequence<?>)args.get(0);
    
    for (Iterator<?> argIter =arg.flatIterator(); argIter.hasNext(); ) {
      Item item = (Item)argIter.next();
      if (null != item.object) {
        System.out.println( ((Node)item.object).getLocation() );
        System.out.flush();
      }
    }
    return arg;
  }
}
