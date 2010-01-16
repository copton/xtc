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

/**
 * SubStringFunction external method class.
 *
 * @version $Revision: 1.8 $
 */
class SubStringFunction implements Function {
  /**
   * Get the name of the function.
   * @return the function name
   */
  public String getName() {
    return "substring";
  }
  /**
   * Return the sub substring specified by args1 and 2
   * @param args The target string and the start and end index of the
   *             substring
   * @return The specified substring
   * 
   */
  public Object apply(List<Object> args) {
    Engine.Sequence<?> arg = (Engine.Sequence)args.get(0);
    Item item = (Item)arg.get( 0 );
    String val = (String)item.object;
    
    Item resItem = null;
    Integer s = (Integer)args.get(1);
    int start = s.intValue();
    
    if (2 == args.size()) {
      resItem = new Item(val.substring(start), null, 0);
    } else if (3 == args.size()) {
      Integer l = (Integer)args.get(2);
      int len = l.intValue();
      resItem = 
        new Item(val.substring(start, start + len ), null, 0 );
    }
    Engine.Sequence<Item> result = new Engine.Sequence<Item>();
    result.add(resItem);
    return result;
  }
}
