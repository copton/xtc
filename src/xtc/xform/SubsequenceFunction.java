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
 * Subsequence external method class.
 *
 * @version $Revision: 1.8 $
 */
class SubsequenceFunction implements Function {

  /**
   * Get the name of the function.
   * @return The function name
   */
  public String getName() {
    return "subsequence";
  }

  /**
   * return items in the range specified by arg(1) and args(2)
   * @param args The sequence and the start and end index
   * @return The items in the index range specifed
   */
  public Object apply(List<Object> args) {
    Integer s = (Integer)args.get(1);
    Integer e = (Integer)args.get(2);
    int start =  s.intValue();
    int end   =  e.intValue();    
    Engine.Sequence<?> arg1 = ((Engine.Sequence<?>)args.get(0));
    Engine.Sequence<Item> result = new Engine.Sequence<Item>();

    for( int i = start - 1; i < end; i++ ){
      result.add((Item)arg1.get(i) );
    }
    return result;
  }
}
