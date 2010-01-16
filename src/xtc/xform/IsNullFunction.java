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
import java.util.ArrayList;
import java.util.Iterator;


/**
 * isNull external method class.
 *
 * @author Joe Pamer
 * @version $Revision: 1.9 $
 */
        
class IsNullFunction implements Function {

  /**
   * Get the name of the function.
   * 
   * @return The name of the function.
   */
  public String getName() {
    return "isNull";
  }

  /**
   * Test to see if the list in args is composed soley of null items.
   *
   * @param args A list of one argument - a sequence of items.
   * @return A non-empty sequence if arg contains only null items, otherwise
   *  an empty sequence.
   */
  public Object apply(List<Object> args) {
    Engine.Sequence<?> arg = (Engine.Sequence<?>)args.get(0);

    for (Iterator<?> arg_iterator = arg.flatIterator(); 
         arg_iterator.hasNext();) {
        
      Item item = (Item)arg_iterator.next();
      if (null != item.object) {
        return new ArrayList<Item>();
      } else {
        continue;
      }
    }

    return arg;
  }
}
