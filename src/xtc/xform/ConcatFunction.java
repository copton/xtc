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
 * ConcatFunction external method class.
 *
 * @version $Revision: 1.8 $
 */
class ConcatFunction implements Function {

  /**
   * Get the name of the function.
   * @return The function name
   */
  public String getName() {
    return "concat";
  }

  /**
   * Return a singleton Sequence containing the
   *  concatenation of all the strings passed in args.
   * @param args The list of strings to concatenate
   * @return The concatenated string
   */
  public Object apply(List<Object> args) {
    String res = "";
    
    for( int i = 0; i < args.size(); i++ )      {
      Engine.Sequence<?> arg = (Engine.Sequence<?>)args.get(i);
      Item item = (Item)arg.get( 0 );
      
      String val = (String)item.object;
      res += val;
    }
    
    Item resItem = new Item( res, null, 0 );
    
    Engine.Sequence<Item> result = new Engine.Sequence<Item>();
    result.add( resItem );
    return result;
  }
}
