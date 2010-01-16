/*
 * xtc - The eXTensible Compiler
 * Copyright (C) 2006-2007 New York University
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

import xtc.tree.GNode;

/**
 * EmptyFunction external method class.
 *
 * @author Laune Harris
 * @version $Revision: 1.8 $
 */
class EmptyFunction implements Function {
  /**
   * Get the name of the function.
   * 
   * @return The name of the function.
   */
  public String getName() {
    return "empty";
  }

  /**
   * returns an the sequence of items that have no children
   */
  public Object apply(List<Object> args) {
    Engine.Sequence<?> arg = (Engine.Sequence<?>)args.get(0);
    Engine.Sequence<Item> noChildren = new Engine.Sequence<Item>();
    for (Iterator<?> arg_iterator = arg.flatIterator(); arg_iterator.hasNext();) {
      Item item = (Item)arg_iterator.next();
      GNode n = (GNode)item.object;

      if (n.size() == 0) {
        noChildren.add( item );     
      } else {
        continue;
      }
    }    
    return noChildren;
  }
}
