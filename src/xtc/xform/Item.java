/*
 * xtc - The eXTensible Compiler
 * Copyright (C) 2005-2007 New York University
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

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * A tree item contained in a sequence.
 *
 * @author Joe Pamer
 * @version $Revision: 1.18 $
 */
public class Item {
  /** The tree item object. */
  Object object;

  /** The tree item's parent. */
  Item parent;

  /** The item's child index in the parent item. */
  int index;

  /** The children of this item */
  List<Item> children;

  /**
   * Create a new item.
   *
   */
  public Item() {
    object = null;
    parent = null;
    children = null;
    index = 0;
  } 

  /**
   * Create a new item.
   *
   * @param object The item object.
   * @param parent The item's parent.
   * @param index The item's child index.
   */
  public Item(Object object, Item parent, int index) {
    this.object = object;
    this.parent = parent;
    this.index = index;
    this.children = null;
  }

  /**
   * Create a new item.
   *
   * @param object The item object.
   * @param parent The item's parent.
   * @param index The item's child index.
   * @param children The item's child items.
   */
  public Item(Object object, Item parent, int index, List<Item> children) {
    this.object = object;
    this.parent = parent;
    this.index = index;
    addChildren(children);    
  }
  
  /**
   * Create a new that is a copy of an existing item.
   *
   * @param i The item object.
   */
  public Item(Item i) {
    this.object = i.object;
    this.parent = i.parent;
    this.index = i.index;
    addChildren(i.children);    
  }

  /**
   * Get the item's object.
   *
   * @return The item's object.
   */
  public Object getObject() {
    return this.object;
  }

  /**
   * Get the item's parent.
   *
   * @return The item's parent item.
   */
  public Item getParent() {
    return this.parent;
  }

  /**
   * Get the item's index.
   *
   * @return The item's index.
   */
  public int getIndex() {
    return this.index;
  }

  /**
   * Get the item's children.
   *
   * @return The item's children.
   */
  public List<Item> getChildren() {
    return this.children;
  }

  /**
   * Test for equality.
   *
   * @param o The object to test for equality.
   * @return True if o is equal, false otherwise.
   */
  public boolean equals(Object o) {
    if (o instanceof Item) {
      Item other = (Item)o;
      if ((this.object == other.object) && 
          (this.parent == other.parent) &&
          (this.index == other.index)) {
        return true;
      } else {
        return false;
      }
    } else {
      return false;
    }            
  }

  /**
   * Add a new child to the item.
   *
   * @param child The new child item.
   */
  public void addChild(Item child) {
    if (null == this.children) {
      this.children = new LinkedList<Item>();
    }

    child.parent = this;
    child.index = this.children.size();

    this.children.add(child);
  }

  /**
   * Add a new child at the specified index.
   * 
   * @param child The new child item.
   * @param index The index.
   */
  public void addChild(Item child, int index) {
    if (null == this.children) {
      if (0 == index) {
        this.children = new LinkedList<Item>();
      } else {
        throw new RuntimeException("Error: Attempted to add first child at "
                                   + "index greater than 0.");
      }
    }
    child.parent = this;
    child.index = index;

    this.children.add(index, child);

    // adjust the other children's indices
    for (int newidx = index+1; newidx < this.children.size(); newidx++) {
      (this.children.get(newidx)).index = newidx;
    }
  }

  /**
   * Add a collection of new children to an item.
   *
   * @param children The new children.
   */
  public void addChildren(List<Item> children) {
    if (null == children) {
      return;
    } else if (null == this.children) {
      this.children = new LinkedList<Item>();
    }

    int index = this.children.size();

    for (Iterator<Item> child_iterator = children.iterator();
         child_iterator.hasNext(); index++) {
      Item child_item = child_iterator.next();
      child_item.index = index;
      child_item.parent = this;
      this.children.add(child_item);
    }
  }

  /** 
   * Insert a collection of new children at the specified index.
   *
   * @param children The children to insert.
   * @param index The index to insert at.
   */
  public void addChildren(List<Item> children, int index) {
    if (null == this.children) {
      if (0 == index) {
        this.children = new LinkedList<Item>();
      } else {
        throw new RuntimeException("Error: Attempted to add first children at"
                                   + " an index greater than 0.");
      }
    }
    
    int insert_index = index;

    for (Iterator<Item> child_iterator = children.iterator();
         child_iterator.hasNext(); index++) {
      
      Item child_item = child_iterator.next();
      child_item.parent = this;
      child_item.index = index;
    }

    this.children.addAll(insert_index, children);

    // adjust old children's indices
    while (index < this.children.size()) {
      (this.children.get(index)).index = index;
      index++;
    }
  }    

  /**
   * Remove the child at the specified index.
   *
   * @param index The index of the child to eliminate.
   */
  public void removeChild(int index) {
    this.children.remove(index);
    
    // adjust the indices of the remaining children
    while(index < this.children.size()) {
      (this.children.get(index)).index = index;
      index++;
    }
  }

  /**
   * Replace the item at the specified index with the given item.
   *
   * @param index The index of the child to be replaced.
   * @param item The item to make the replacement with.
   */
  public void replaceChild(int index, Item item) {
    item.parent = this;
    item.index = index;

    this.children.set(index, item);
  }

  /**
   * Replace the item at the specified index with the given list of items.
   *
   * @param index The index of the child to be replaced.
   * @param items The items to make the replacement with.
   */
  public void replaceChild(int index, List<Item> items) {
    removeChild(index);
    addChildren(items, index);
  }  

  /**
   * Add to a list (only if it's not already a member of the list).
   *
   * @param list The list to be added to.
   * @return The potentially modified list.
   */
  public List<Object> addToList(List<Object> list) {
    if(!list.contains(this)) {
      list.add(this);
    }
    return list;
  }

  /**
   * Return the item as a string value.
   *
   * @return The item as a string.
   */
  public String toString() {
    if (null == this.object) {
      return "null";
    } else if (this.object instanceof String){
      return (String)this.object;
    } else {
      return this.object.toString();
    }
  }

} // end class Item
