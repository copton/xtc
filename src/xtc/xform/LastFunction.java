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
 * LastFunction external method class.
 *
 * @version $Revision: 1.7 $
 */
class LastFunction implements Function {

  /**
   * Get the name of the function.
   * @return The function name
   */
  public String getName() {
    return "last";
  }

  /**
   * return the number of items in the Sequence
   * @param args The sequence
   * @return The number of items in sequence
   */
  public Object apply(List<Object> args) {
    Integer res = new Integer (((Engine.Sequence)args.get(0)).size()) ;
    return res;
  }
}
