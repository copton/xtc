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
 * LowerCaseFunction external method class.
 *
 * @version $Revision: 1.8 $
 */
class LowerCaseFunction implements Function {

  /**
   * Get the name of the function.
   * @return The function's name
   */
  public String getName() {
    return "lowercase";
  }

  /**
   * Takes a string and converts its characters to upper case
   * @param args The string to convert to lower case
   * @return The lowercased string
   */
  public Object apply(List<Object> args) {
    Engine.Sequence<?> arg = (Engine.Sequence<?>)args.get(0);
    Item item = (Item)arg.get( 0 );
    String val = (String)item.object;
    Item resItem = new Item( val.toLowerCase(), null, 0 );
    Engine.Sequence<Item> result = new Engine.Sequence<Item>();
    result.add( resItem );
    return result;
  }
}
