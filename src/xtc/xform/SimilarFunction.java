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
import java.util.LinkedList;

/**
 * similar external method class.
 *
 * @author Joe Pamer
 * @version $Revision: 1.7 $
 */

class SimilarFunction implements Function {

  /**
   * Get the name of the function.
   * 
   * @return The name of the function.
   */
  public String getName() {
    return "similar";
  }

  /**
   * Test to see if two items are "similar".  That is, their objects are 
   * equal, but their parents or indices may not be the same.
   *
   * This function takes two arguments.  The first is a list of items to
   * consider for similarity, and the second is a list of items representing
   * "values" to judge the first list by.  Any items in the first list who are
   * similar to one or more of the items in the second list will be returned.
   *
   * @param args The list of two arguments.
   * @return A non-empty sequence 
   */
  public Object apply(List<Object> args) {
    Engine.Sequence<?> arg_a = (Engine.Sequence)args.get(0);
    Engine.Sequence<?> arg_b = (Engine.Sequence)args.get(1);


    List<Item> similarList = new LinkedList<Item>();

    for (Iterator<?> a_iterator = arg_a.flatIterator(); a_iterator.hasNext();) {
      Item a_item = (Item)a_iterator.next();
      Object a_object = a_item.object;

      for (Iterator<?> b_iterator = arg_b.flatIterator(); 
           b_iterator.hasNext();) {

        if (a_object.equals(((Item)b_iterator.next()).object)) {
          similarList.add(a_item);
        } 
      }
    }

    return similarList;
  }
} // end class SimilarFunction
          
        
