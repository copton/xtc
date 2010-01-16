 /*
 * xtc - The eXTensible Compiler
 * Copyright (C) 2005-2007 New York University, Thomas Moschny
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;

import xtc.tree.GNode;
import xtc.tree.Visitor;

/**
 * The query engine.  This class' {@link #run} method performs queries
 * on abstract syntax trees.
 *
 * @author Joe Pamer
 * @author Laune Harris
 * @version $Revision: 1.56 $
 */
public class Engine {

  /**
   * The inner sequence class.
   *
   */
  static class Sequence<T> extends LinkedList<T> {

    /**
     * The flattening iterator class.
     *
     * A lot of assumptions can be made here because an empty list will never
     * be inserted into a sequence.
     */
    static class FlatIterator<K> implements Iterator<K> {

      /** The stack of iterators to be used. */
      private LinkedList<Iterator<K>> iterator_stack;
      
      /**
       * Create a new flat iterator.
       *
       */
      public FlatIterator(Iterator<K> iterator) {
        iterator_stack = new LinkedList<Iterator<K>>();
        iterator_stack.add(iterator);
      }
      
      /**
       * Query to see if there is another object in the collection.
       * @return True is there are more objects in collection, false otherwise
       */
      public boolean hasNext() {
        boolean has_next = iterator_stack.getLast().hasNext();
        while (true) {
          if (has_next) {
            break;
          } else {
            iterator_stack.removeLast();
            if (0 == iterator_stack.size()) {
              break;
            } else {
              has_next = iterator_stack.getLast().hasNext();
            }
          } 
        }
        
        return has_next;
      }
      
      /**
       * Return the next object in the collection.
       * @return The next object in the collection
       */
      @SuppressWarnings("unchecked")
      public K next() {
        // Will throw an exception on its own 
        Iterator<K> iterator = iterator_stack.getLast();
        K o = iterator.next();
        if (o instanceof List) {
          //supress warning on this line       	
          iterator = ((List<K>)o).iterator();
          o = iterator.next();
          iterator_stack.add(iterator); 
          return o;
        } else {
          return o;
        }
      }
      
      /**
       * Remove the last item in the collection.
       * 
       */
      public void remove() {
        // needs more testing
        iterator_stack.getLast().remove();
      }
    } // end class FlatIterator 
    
    /**
     * Add a new item to the sequence, but only if it's not a null list.
     * @param o to add to the sequence
     * @return True if object was added, false otherwise
     */
    public boolean add(T o) {
      if ((!(o instanceof List)) || (0 != ((List)o).size())) {
        return super.add(o);
      } else {
        return false;
      }
    }
    
    /**
     * See if the sequence contains a specified object.
     * @param oiq The search key
     * @return True if the object is in the sequence, false otherwise
     */
    public boolean contains(Object oiq) {
      for (Iterator<T> i = this.iterator(); i.hasNext();) {
        Object o = i.next();
        if (oiq.equals(o)) {
          return true;
        }
      }
      return false;
    }
    
    /**
     * Get a new flat iterator over the sequence.
     * @return The iterator for the current sequence
     */
    @SuppressWarnings("unchecked")
    public Iterator<Iterator<?>> flatIterator() {
      return new FlatIterator(this.iterator());
    }
  } // end class Sequence
  
  
  /**
   * The run-time environment of a query.
   *
   */
  static class Environment{
    
    /** 
     *  Stack frames.
     *
     */
    static class Frame {
      
      /** The map of variable names to values. */
      private HashMap<String, Sequence<Variable>> symbols;
      
      /**
       * Create a new stack frame.
       *
       */
      public Frame() {
        symbols = new HashMap<String, Sequence<Variable>>();
      }
      
      /**
       * Enter a variable into the stack frame.
       * @param name The name of the variable to insert into the stack frame
       * @param value The value of the variable to insert into the stack frame
       */
      @SuppressWarnings("unchecked")
      public void setVariable(String name, Sequence<?> value) {
        Sequence<Variable> v = (Sequence<Variable>)value;  
        symbols.put(name, v);
      }
      
      /**
       * Get a variable.
       * @param name The name of the variable to return
       * @return The Sequence of variables that match the name specified
       */
      public Sequence<Variable> getVariable(String name) {
        return symbols.get(name);
      }
      
    } // end class Frame
    
    /** A query's stack as a list of stack frames. */
    private LinkedList<Frame> stack_frames;
    
    /** The focus of a query's path expressions. */
    private LinkedList<Sequence<?>> focus_stack;
    
    /**
     * Create a new environment.
     *
     */
    public Environment() {
      stack_frames = new LinkedList<Frame>();
      focus_stack = new LinkedList<Sequence<?>>();
    }
    
    /**
     * Push a new stack frame.
     *
     */
    public void pushScope() {
      stack_frames.add(new Frame());
    }
    
    /**
     * Pop the current stack frame.
     *
     */
    public void popScope() {
      if (! (0 == stack_frames.size())) {
        stack_frames.removeLast();
      }
    }
    
    /**
     * Fetch a variable from the environment's stack.
     * @param name The name of the stack variable to return
     * @return The list of variables that match the name specified
     */
    public Sequence<Variable> getVariable(String name) {
      int nframe = stack_frames.size()-1;
      Sequence<Variable> value = null;
      
      while (nframe >= 0) {
        value = (stack_frames.get(nframe)).getVariable(name);
        if (null != value) {
          break;
        } else {
          nframe--;
        }
      }
      return value;
    }
    
    /**
     * Store a variable in the current stack frame.
     * @param name The name of the variable to add to the current stack frame
     * @param value The value of the variable to add to the current stack frame
     */ 
    @SuppressWarnings("unchecked")
    public void setVariable(String name, Sequence<?> value) {
      Sequence<Variable> v = (Sequence<Variable>) value;
      if (0 == stack_frames.size())
        pushScope();
      stack_frames.getLast().setVariable(name, v);
    }
 
    /**
     * Push a focus.
     * @param focus The focus to add to the focus stack
     */
    public void pushFocus(Sequence<?> focus) {
      focus_stack.add(focus);
    }
      
    /**
     * Pop a focus.
     * @return The focus on top of the focus stack
     */
    public Sequence<?> popFocus() throws NoSuchElementException {
      Sequence<?> l = focus_stack.getLast();
      focus_stack.removeLast();
      return l;
    }
    
    /**
     * Peek at the current focus.
     * @return the focus on top of the focus stack
     */
    public Sequence<?> peekFocus() throws NoSuchElementException {
      return focus_stack.getLast();
    } 
    
  } // end class Environment
  
  /**
   * Models a query variable as a name/value pair.
   *
   */
  static class Variable {
    
    /** The name of the variable. */
    String name;
    
    /** The value of the variable. */
    Sequence<?> value;
    
    /**
     * Create a new variable.
     * @param name The name of the variable to be create
     * @param value The value of the variable to be created
     */
    public Variable(String name, Sequence<?> value) {
      this.name = name;
      this.value = value;
    }

    /**
     * Print the variable name and value.
     *
     */
    public String toString() {
      return name + " : " + value;
    }
    
  } // end class Variable
  
  /** Flag for root-relative expression evaluation. */
  final int FOCUS_ROOT = 0;
  
  /** Flag for all-nodes-relative expression evaluation. */
  final int FOCUS_ALL = 1;
  
  /** Flag for expression evaluation with an implicit focus. */
  final int FOCUS_IMPLICIT = 3;

  /** Flag for continuing focus. */
  final int FOCUS_LAST = 4;

  /** Flag for inside-out tree traversal. */
  final int FOCUS_INSIDE_OUT = 5;
  
  /** The focus flag. */
  int focus_flag;

  /** The tree modification flag. */
  boolean modified_flag = false;

  /** The flag to see if we have to generate another BFS traversal. */
  boolean bad_breadth_flag = true;

  /** The BFS data. */
  Sequence<?> bfs_sequence;
  
  /** The query's run-time environmnt. */
  Environment environment;
  
  /** The AST to be transformed or queried. */
  GNode source_ast;

  /** The intermediate representation of the source tree. */
  Item item_tree;
  
  /** The query AST's visitor. */
  QueryVisitor visitor;
  
  /** The query's function library. */
  HashMap<String, Function> function_table;

  /** The built in library functions */
  String[] lib_funcs = 
  { "CountFunction", "LinesFunction", "LastFunction", "EmptyFunction",
    "TestFunction",  "IsNullFunction", "SubsequenceFunction", 
    "ConcatFunction", "UpperCaseFunction", "LowerCaseFunction",
    "SubStringFunction" };
  
  /**
   * Create a new engine. 
   *
   */
  public Engine() {
    environment = null;
    visitor = new QueryVisitor();
    function_table = new HashMap<String, Function>();
    //add the built in library functions to the function table
    String class_name = "";
    for (int i = 0; i < lib_funcs.length; i++) {
      class_name = lib_funcs[i];
      try {
        Class<?> class_definition = Class.forName("xtc.xform." + class_name);
        Object function_object = class_definition.newInstance();
        addFunction((Function)function_object);
      } catch (Exception e) {
        String msg = "Error: Unable to load class \"" + class_name + "\".";
        throw new RuntimeException(msg);
      }       
    }    
  }
 
  /**
   * Add a function to the engine's function library.
   * @param function The external function to add to function library
   */
  protected void addFunction(Function function) {
    function_table.put(function.getName(), function);
  }
  
  /**
   * Call the specified function in the engine's function library.
   *
   * @param name The name of the function.
   * @param args The function's arguments.
   * @throws IllegalArgumentException Signals that the specified function
   *   cannot found in the function library.
   */
  protected Object callFunction(String name, ArrayList<Object> args) 
    throws IllegalArgumentException {
    
    Function function = function_table.get(name);
    
    if (null == function) {
      throw new IllegalArgumentException();
    } else {
      return function.apply(args);
    }
  }
  
  /** 
   * Perform a query on an AST.
   *
   * @param query The query.
   * @param ast The AST to be queried.
   * @return The result of the query.
   */
  public List<Object> run(Query query, GNode ast) {
    // initialize the environment
    environment = new Environment();
    environment.pushScope();
    source_ast = ast;
    item_tree = genItemTree(source_ast, null, 0);
    
    // return the result of the query
    return createObjectList(castToSequenceOfItem(visitor.dispatch(query.ast)));
  } 

  /**
   * Turn the list of items into a list of tree items.
   * @param item_list The list of items 
   * @return List of tree items
   */
  private List<Object> createObjectList(List<Item> item_list) {
    ArrayList<Object> object_list = new ArrayList<Object>(item_list.size());
    Iterator<Item> val_it = item_list.iterator();
    
    while (val_it.hasNext()) {
      Object o = val_it.next();
      
      if (o instanceof List) {
        List<Item> temp = new ArrayList<Item>();
        temp.addAll(castToSequenceOfItem(o));
    	  
        object_list.add(createObjectList(temp));
      } else {
        object_list.add(((Item)o).object);
      }
    }
    
    return object_list;
  }
  
  /**
   * Get the root of the source AST.
   *
   * @return The (possibly transformed) source AST.
   */
  public GNode getASTRoot() {
    return modified_flag ? (GNode)genFinalTree(item_tree) : source_ast;
  }

  /**
   * Build a new tree out of item objects.
   * @param root The root 
   * @param parent
   * @param index
   * @return The tree of item objects
   */
  private Item genItemTree(Object root, Item parent, int index) {
    Item root_item = new Item(root, parent, index);

    if (root instanceof GNode) {
      int child_index = 0;
      for (Iterator<Object> child_iterator = ((GNode)root).iterator(); 
           child_iterator.hasNext(); child_index++) {
 
        root_item.addChild(genItemTree(child_iterator.next(), 
                                       root_item, child_index));
      }
    }

    return root_item;
  }
 
  /**
   * Build the final tree.
   *
   */   
  private Object genFinalTree(Item root) {
    Object object = root.object;

    if (object instanceof GNode) {
      object = GNode.create(((GNode)object).getName());
      if (null != root.children) {
        for (Iterator<Item> child_iterator = root.children.iterator();
             child_iterator.hasNext();) {

          ((GNode)object).add(genFinalTree(child_iterator.next()));
        }
      }
    }

    return object;
  }
  
  /** Visistor to evaluate a query over the source AST. */
  class QueryVisitor extends Visitor {
    
    /**
     * Visit the specified XForm node.
     *
     * @param xform  The XForm node.
     * @return The value of the XForm expression.
     */
    public List<?> visitXForm(GNode xform) {
        
      if (0 == xform.size()) {
        return new ArrayList<Object>();
      }

      // Load external functions
      GNode import_node = (GNode)xform.get(0);
      if (null != import_node) {
        dispatch(import_node);
      }

      // return value of the query
      return (List<?>)dispatch((GNode)xform.get(1));
    }
    
    /**
     * Visit the specified import statement.
     *
     * @param statement The import statement node.
     */
    public void visitImportStatement(GNode statement) {
      String class_name = "";
      
      for (Iterator<Object> class_iterator = statement.iterator();
           class_iterator.hasNext();) {
        
        try {

          GNode class_node = (GNode)class_iterator.next();
          class_name = (String)((Item)dispatch(class_node)).object;
          
          Class<?> class_definition = Class.forName(class_name);
          Object function_object = class_definition.newInstance();
          
          addFunction((Function)function_object);
        } catch (Exception e) {
          String msg = "Error: Unable to load class \"" + class_name + "\".";
          throw new RuntimeException(msg);
        }
      }
    }
    
    
    /**
     * Visit the specified compound expression node.
     *
     * @param  expression The compound expression node.
     * @return The value of the compound expression.
     */
    public Sequence<?> visitCompoundExpression(GNode expression) {
      Sequence<Sequence<?>> value = new Sequence<Sequence<?>>();
      int pushes = 0;

      //evaluate each of the compound expression's children
      for (Iterator<Object> exp_iterator = expression.iterator();
           exp_iterator.hasNext();) {
   
        Sequence<?> cur_val = (Sequence<?>)dispatch((GNode)exp_iterator.next());
        value.add(cur_val);
        environment.pushFocus(cur_val);
        pushes++;
      }

      // pop off all of the new focii
      while (0 != pushes) {
        environment.popFocus();
        pushes--;
      }

      // return the value of the compound expression
      return value;
    }
    
    /**
     * Visit the specified let expression.
     *
     * @param expression The let expression node.
     * @return The value of the expression.
     */
    public Sequence<?> visitLetExpression(GNode expression) {
      // enter a new scope
      environment.pushScope();
      
      // set the let bindings
      dispatch((GNode)expression.get(0));
      
      // get the result of its return value using the bindings above
      Sequence<?> return_value = (Sequence<?>)dispatch((GNode)expression.get(1));
      
      // reenter the previous scope
      environment.popScope();
      
      // return the resultant sequence
      return return_value;
    }
    
    /**
     * Visit the specified let-binding list, and enter each of its bound
     * variables into the symbol table.
     *
     * @param binding_list The let binding list node.
     */
    public void visitLetBindingList(GNode binding_list) {
      
      // bind each sequence to a variable name
      for (Iterator<Object> binding_iterator = binding_list.iterator(); 
          binding_iterator.hasNext();) {
        
        dispatch((GNode)binding_iterator.next());
      }
    }
    
    /**
     * Visit the specified let-binding and enter its variable 
     * into the symbol table.
     *
     * @param let_binding The let binding node.
     */
    public void visitLetBinding(GNode let_binding) {
      
      // extract the name of the variable
      GNode varref_node = (GNode)let_binding.get(0);
      String name = (String)varref_node.get(0); 
      
      // determine the variable's value
      Sequence<Variable> value = new Sequence<Variable>();
      
      List<?> temp = (List<?>)dispatch((GNode)let_binding.get(1));
      
      for (Iterator<?> iter = temp.iterator(); iter.hasNext();){
        value.add((Variable)iter.next());
      }
      
      // bind the value to the name in the symbol table
      environment.setVariable(name, value);
    }
    
    /**
     * Visit the specified for-expression.
     *
     * @param expression The for expression node.
     * @return The semantic value of the for expression.
     */
    public Sequence<?> visitForExpression(GNode expression) {
           
      // enter a new scope
      environment.pushScope();
      
      // get the list of bound variables
      ArrayList<Object> variable_list = new ArrayList<Object>();
      
       variable_list.addAll((List<?>)(dispatch((GNode)expression.get(0))));
      
      // create a list of iterators - one for each variable
      int numVars = variable_list.size();
      ArrayList<Iterator<?>> iterators = new ArrayList<Iterator<?>>(numVars);
       
      // initialize the return sequence
      Sequence<Sequence<?>> value = new Sequence<Sequence<?>>();
      
      // store a value-iterator and its first value for each variable
      for (int i = 0; i < numVars; i++) {
        Iterator<?> value_iterator = ((Variable)variable_list.get(i))
          .value.flatIterator();
        iterators.add(value_iterator);
        if (value_iterator.hasNext()) {
          setVariable((Variable)variable_list.get(i), value_iterator.next());
        } else {
          // no value sequence for this var, thus empty result
          environment.popScope();
          return value;
        }
      }
     
      // generate the return value, and iterate the variable values
      while (true) {        
        value.addAll(castToListOfSequence(dispatch((GNode)expression.get(1))));
          
        for (int i = numVars - 1; i >= 0; --i) {
          Iterator<?> value_iterator = iterators.get(i);
          if (value_iterator.hasNext()) {
            setVariable((Variable)variable_list.get(i), value_iterator.next());
            break;
          }
          if (i == 0) {
            environment.popScope();
            return value;
          }
          // reset and store iterator for var #i and its first value
          value_iterator = ((Variable)variable_list.get(i)).value.
            flatIterator();
          iterators.set(i, value_iterator);
          setVariable((Variable)variable_list.get(i), value_iterator.next());
        }
      }
    }
    
    
    private <T> void setVariable(Variable v, T value) {
      Sequence<Object> current_value = new Sequence<Object>();
      current_value.add(value);
      environment.setVariable(v.name, current_value);
    }
        
    /**
     * Visit the specified cfor expression node.
     *
     * @param expression The cfor expression node.
     * @return The semantic value of the expression.
     */
    public Sequence<?> visitCForExpression(GNode expression) {
      // enter a new scope
      environment.pushScope();
      
      // get the list of for variables
      ArrayList<?> variable_list = 
        (ArrayList<?>)dispatch((GNode)expression.get(0));
      
      // create a list of iterators - one for each variable
      ArrayList<Iterator<?>> iterators = 
        new ArrayList<Iterator<?>>(variable_list.size());
      
      // initialize the return sequence
      Sequence<Sequence<?>> value = new Sequence<Sequence<?>>();
      
      // set the initial values for the bound variables
      for (int i = 0; i < variable_list.size(); i++) {
    	Variable v = (Variable)variable_list.get(i); 
    	iterators.add(v.value.flatIterator());
      }
      
      // generate the return value, and update the bound variables
      while (true) {
        //update variable values
        for ( int i = 0; i < iterators.size(); i++) {
          Iterator<?> value_iterator = iterators.get(i);
          
          if (value_iterator.hasNext()) {
            Sequence<Object> current_value = new Sequence<Object>();
            current_value.add(value_iterator.next());
            Variable v = (Variable)variable_list.get(i);
            environment.setVariable(v.name,current_value);
            
          } else { // one's empty, so we're done
            environment.popScope();
            return value;
          }
        }
        //concatenate the resulting sequences
        value.addAll(castToListOfSequence(dispatch((GNode)expression.get(1))));
      }
    }
    
    /**
     * Visit the specified iterative binding list node.
     *
     * @param binding_list The iterative binding list node.
     * @return The variables bound.
     */
    public ArrayList<Object> visitIterativeBindingList(GNode binding_list) {
      
      // initialize the variable list
      ArrayList<Object> variables = new ArrayList<Object>(); 
      
      // create a list of variable names coupled with sequences
      for (Iterator<Object> bindings = binding_list.iterator(); 
           bindings.hasNext();) {
        variables.add(dispatch((GNode)bindings.next()));
      }
      
      return variables;
    }
    
    /**
     * Visit the specified iterative binding.
     *
     * @param it_binding The iterative binding list node.
     * @return The variable bound.
     */
    public Variable visitIterativeBinding(GNode it_binding) {
      // couple a variable name with a sequence
      return new Variable((String)((GNode)it_binding.get(0)).get(0), 
                          (Sequence)dispatch((GNode)it_binding.get(1)));
    }

    /**
     * Visit the specified removal
     * @param expression The reference nodes for insertions
     * @return A sequence constaining the inserted nodes
     */
    public Sequence<?> visitRemoveExpression(GNode expression) {
      modified_flag = true;
      bad_breadth_flag = true;
      Sequence<?> targets = (Sequence<?>) dispatch((GNode)(expression.get(0)));
          
      for (Iterator<?> target_iterator = targets.flatIterator();
           target_iterator.hasNext();) {
        
        Item target = (Item)target_iterator.next();
        Item parent = target.parent;
        
        if (null == parent) {
           throw new RuntimeException("Error: can't remove tree root");
        } else {
          int index = target.index;
          parent.removeChild(index);
        }
      }
      return targets;
    }
    
    
    /**
     * Visit the specified addition
     * @param expression The reference nodes for additions
     * @return A sequence constaining the modified nodes
     */
    public Sequence<?> visitAddExpression(GNode expression) {
      modified_flag = true;
      bad_breadth_flag = true;
      
      Sequence<?> targets = (Sequence<?>) dispatch((GNode)(expression.get(1)));
      
      if (targets.isEmpty()) { 
        return targets;
      }      
      Sequence<Item> added = new Sequence<Item>();
      Sequence<?> additions = (Sequence<?>)dispatch((GNode)(expression.get(0)));
      
      for (Iterator<?> targetIter = targets.flatIterator(); 
           targetIter.hasNext(); ) {
        Item target = (Item)targetIter.next();
        for (Iterator<?> addIter = additions.flatIterator(); 
             addIter.hasNext();){
          Item addition = (Item)addIter.next();
          target.addChild(addition);
          added.add(target);
        }
      }
      return added;
    }
    
    

    
    /**
     * Visit the specified replacement.
     *
     * @param expression The replacement node.
     * @return A sequence containing only the root of the source AST.
     */
    public Sequence<Item> visitReplacementExpression(GNode expression) {
      // search out the items to replace
      Sequence<Item> targets = 
        castToSequenceOfItem(dispatch((GNode)(expression.get(0))));
     
      if (targets.isEmpty()) { 
        return targets;
      } else {
        // get the replacement sequence
        Sequence<Item> replacements = 
          castToSequenceOfItem(dispatch((GNode)(expression.get(1))));
        return replace(targets,replacements);
      }
    }

    /**
     * Visit the specified insertion
     * @param expression The reference nodes for insertions
     * @return A sequence constaining the inserted nodes
     */
    public Sequence<?> visitInsertBeforeExpression( GNode expression ) {
      return insert(expression, true);
    }
    
    /**
     * Visit the specified insertion
     * @param expression The reference nodes for insertions
     * @return A sequence constaining the inserted nodes
     */
    public Sequence<?> visitInsertAfterExpression( GNode expression ) {
      return insert(expression, false);
    }
    
    /**
     * Replace each item in a list of targets with one or more replacement
     * items.  A singleton containing the root node of the source AST is
     * returned.
     * @param targets The sequence of items to replace
     * @param replacements The sequence of replacement items
     * @return The sequence of replacement items
     */
    private Sequence<Item> replace(Sequence<Item> targets, 
                                   Sequence<Item> replacements) {
      modified_flag = true;
      bad_breadth_flag = true;

      Sequence<Item> replaced = new Sequence<Item>();
      
      for (Iterator<?> target_iterator = targets.flatIterator();
           target_iterator.hasNext();) {
        
        Item target = (Item)target_iterator.next();
        Item parent = target.parent;    
        
        if (null == parent) {
          // only for replacing the root
          if (1 < replacements.size()) {
            throw new RuntimeException("Error: Tree root can only be replaced"
                                       + " by a single item.");
          } else {
            item_tree = replacements.get(0);
            replaced.add(item_tree);
          }
        } else {
          int index = target.index;
          
          // if there's just one item to pop in, we can do the replacement
          // a lot faster
          if (1 == replacements.size()) {
            Item replacement_item = replacements.get(0);
            parent.replaceChild(index, replacement_item);
            replaced.add(replacement_item);
          } else {
            // some shifting is in order
            parent.replaceChild(index, replacements);
            replaced.addAll(replacements);
          }
        }
      } 
             
      return replacements;
    }

    /*
     * Insert
     * @param position True indicate before, False indicates after
     * @param expression The insertion node
     * @return The list of inserted nodes
     *
     */
    private Sequence<?> insert(GNode expression, boolean position) {
      modified_flag = true;
      bad_breadth_flag = true;
      
      Sequence<?> targets = (Sequence<?>) dispatch((GNode)(expression.get(1)));
      
      if (targets.isEmpty()) { 
        return targets;
      }
      Sequence<Item> insertions = new Sequence<Item>();
      
      for (Iterator<?> targetIter = targets.flatIterator();
           targetIter.hasNext(); ) {
        Item target = (Item)targetIter.next();
        Item parent = target.parent;
        
        if (null == parent) {
          throw new RuntimeException("Error: Can't insert before tree root");
        } else {
          int index = target.index;
          if (position) {
            insertions = new Sequence<Item>();
            insertions.addAll(castToSequenceOfItem(dispatch((GNode)(expression.get(0)))));
            insertions.add( target );
            parent.replaceChild( index, insertions );   
          } else {
            insertions = new Sequence<Item>();
            insertions.add( target );
            insertions.addAll(castToSequenceOfItem(dispatch((GNode)(expression.get(0)))));
            parent.replaceChild( index, insertions );
          }
        }
      }
      return insertions;
    }
    
    /**
     * Visit an if expression.
     *
     * @param expression The if expression node.
     * @return The value of the expression.
     */
    public Sequence<?> visitIfExpression(GNode expression) {
      // resolve the conditional
      Sequence<?> conditional = (Sequence<?>)dispatch((GNode)expression.get(0));
      
      // if it's not null, resolve child 0, otherwise 1
      if (!conditional.isEmpty()) {
        return (Sequence<?>)dispatch((GNode)expression.get(1));
      } else {
        return (Sequence<?>)dispatch((GNode)expression.get(2));
      }
    }
    
    /** 
     * Visit the specified new-item expression.
     *
     * @param expression The new-item expression.
     * @return The new item as a singleton.
     */
    public Sequence<Item> visitNewItemExpression(GNode expression) {
      // wrap the item in a list
      Sequence<Item> item_wrapper = new Sequence<Item>();
      item_wrapper.add((Item)dispatch((GNode)expression.get(0)));
      
      // return the wrapped item
      return item_wrapper;
    }
    
    /**
     * Visit the specified new-node expression.
     *
     * @param expression The new-node expression node.
     * @return The new node as a singleton.
     */
    public Item visitNewNodeExpression(GNode expression) {
      // grab the name for the node
      GNode new_node = GNode.create((String)dispatch((GNode)expression.get(0)));
      Item item = new Item(new_node,null,0);
      
      //add the children nodes to the template
      Sequence<?> children = (Sequence<?>)dispatch((GNode)expression.get(1));
      Iterator<?> child_iterator = children.flatIterator();
      for ( ; child_iterator.hasNext() ; ) {
        Item it = new Item((Item)child_iterator.next());
        item.addChild(it);
        new_node.add(it.getObject());
      }
      return item;
    }
    
    /**
     * Visit the specified children.
     *
     * @param children_node The children node.
     * @return A list of child nodes.
     */
    public Sequence<Object> visitChildren(GNode children_node) {
      // initialize child list
      Sequence<Object> child_list = new Sequence<Object>();
      
      // gather the children
      for (Iterator<Object> child_iterator = children_node.iterator(); 
           child_iterator.hasNext();) {
        
        GNode child_node = (GNode)child_iterator.next();
        
        if (! child_node.isEmpty()) {
          Object child_object = dispatch((GNode)child_node.get(0));
          if (child_object instanceof List) {
            child_list.addAll((List<?>)child_object);
          } else {
            child_list.add(child_object);
          }
        }
      }
      
      return child_list;
    }
    
    /**
     * Visit a null expression.
     *
     * @param null_node The null node.
     * @return An item with a null value.
     */
    public Item visitNull(GNode null_node) {
      // simply return a null object
      return new Item(null, null, 0);
    }
    
    /**
     * Visit the specified string literal.
     *
     * @param s The string literal node.
     * @return The the node.
     */
    public Item visitStringLiteral(GNode s) {
      // return its string child - whether it be a quoted string or a decimal
      // note that we're stripping the quotation-marks from the string
      String string =  s.getString(0).substring(1, s.getString(0).length()-1);
      return new Item(string, null, 0);
    }
    
    /**
     * Visit the specified path expression.
     * 
     * @param path_expression The path expression node.
     * @return The value of the path expression.
     */
    public Sequence<?> visitPathExpression(GNode path_expression) { 
      if (1 == path_expression.size()) {
        focus_flag = FOCUS_IMPLICIT;
        // return the result of its relative path expression
        return (Sequence<?>)dispatch((GNode)path_expression.get(0));
      } else {
        // grab the path-modifier
        String focus_modifier = (String)path_expression.get(0);
        if ((null != focus_modifier) && ("/".equals(focus_modifier))) {
          focus_flag = FOCUS_ROOT;
          return (Sequence<?>)dispatch((GNode)path_expression.get(1));
        } else {
          if (null == focus_modifier) {
            focus_flag = FOCUS_ALL;
          } else { // inside_out
            focus_flag = FOCUS_INSIDE_OUT;
          }
          return (Sequence<?>)dispatch((GNode)path_expression.get(2));
        }
      }
    }
    
    /**
     * Visit the specified relative path expression.
     *
     * @param rp_expression The relative path expression node.
     * @return The value of the relative path expression.
     */  
    public Sequence<?> visitRelativePathExpression(GNode rp_expression) {
      // it's just a step expression
      if (1 == rp_expression.size()) {
        return (Sequence<?>)dispatch((GNode)rp_expression.get(0));
      } else {
        // evaluate the outer focus
        Sequence<?> outer_focus = (Sequence<?>)dispatch((GNode)rp_expression.get(0));
        environment.pushFocus(outer_focus); 
        
        // set the focus modifier
        String focus_modifier = (String)rp_expression.get(1);
        if ("/".equals(focus_modifier)) {
          focus_flag = FOCUS_LAST;
        } else { // it's "//"
          focus_flag = FOCUS_ALL;
        }
        Sequence<?> inner_focus = (Sequence<?>)dispatch((GNode)rp_expression.get(2));
        // pop the outer focus
        environment.popFocus();
        return inner_focus;
      }
    }
    
    /**
     * Visit the specified step expression.
     *
     * @param step_expression The step expression node.
     * @return The value of the step expression.
     */
    public Sequence<?> visitStepExpression(GNode step_expression) { 
      // get the step expression's item test
      GNode test = step_expression.getGeneric(0);
      // set aside space for predicates
      GNode predicates = null;
      
      // if there are predicates, capture them
      if (2 == step_expression.size()) {
        predicates = step_expression.getGeneric(1);
      }
      
      // collect the tree items that satisfy the test
      Sequence<?> result = collect(test);
      
      // set the result
      if (null == predicates) {
        return result;
      } else {
        // store the intermediate result as the implicit focus
        environment.pushFocus(result);
        // dispatch the predicate list
        result = (Sequence)dispatch(predicates);
        // remove the intermediate result from the focus stack
        environment.popFocus();
        return result;
      }
    }
    
    /**
     * Visit the specified predicate list.
     *
     * @param predicates The list of predicates.
     * @return The current outer focus filtered by the predicate list.
     */
    public Sequence<Item> visitPredicateList(GNode predicates) {
      // this list stores the filtered tree items
      Sequence<Item> filtered = castToSequenceOfItem(environment.peekFocus());
      
      // filter the items on each predicate
      for (Iterator<Object> predicate_iterator = predicates.iterator(); 
           predicate_iterator.hasNext();) {
        
        // evaluate the predicate for the current focus
        filtered = 
          intersection(filtered,
                       castToSequenceOfItem(dispatch((GNode)predicate_iterator.next())));
        if (filtered.isEmpty()) {
          // No items satisfied the filter
          break;
        } else {
          // replace the current focus with the filtered result
          environment.popFocus();
          environment.pushFocus(filtered);
        }
      }
      return filtered;
    }
    
    /**
     * Visit the specified predicate.
     *
     * @param predicate The predicate.
     * @return The current outer focus filtered by the predicate.
     */
    public Sequence<?> visitPredicate(GNode predicate) {
      Object value_object = dispatch((GNode)predicate.get(0));
     
      //crude, fix me
      if (value_object instanceof Sequence && 
          !((Sequence<?>)value_object).isEmpty() &&
          ((Sequence<?>)value_object).get(0) instanceof Integer)
        value_object = ((Sequence)value_object).get(0);
      
      // if it's an integer, we'll want to grab the focus item at that index
      if (value_object instanceof Integer) {
        int index = ((Integer)value_object).intValue();
        Sequence<Item> outer_focus = castToSequenceOfItem(environment.peekFocus());
      
        if ((1 <= index) && (index <= outer_focus.size())) {
          // wrap the item in its own list
          Sequence<Item> item_wrapper = new Sequence<Item>();
          item_wrapper.add(outer_focus.get(index-1));
          return item_wrapper;
        } else {
          return new Sequence<Item>();
        }
      } else { // otherwise, return the filtered outer focus
        return (Sequence<?>)value_object;
      }
    }       
    
    /**
     * Collect items in the target AST that satisfy the criteria of
     * an item test.
     * @param test_node The test node
     * @return The items that satisfy an item test
     */
    private Sequence<?> collect(GNode test_node) {
      // the type of item test
      String test_name = test_node.getName();
      
      // gather the outer focus nodes     
      Sequence<Item> outer_focus = null;


      // Here, we need to set up the focus
      // notice how variable references, parenthesized expressions and 
      // function calls can be used as a focus, but Identifiers and string
      // literals can't (they NEED some kind of parent).     
      if ((focus_flag == FOCUS_ROOT) || (focus_flag == FOCUS_ALL)) {
        // set the initial focus to be the root node
        outer_focus = new Sequence<Item>();
        outer_focus.add(item_tree);
      } else if (focus_flag == FOCUS_INSIDE_OUT) {
        // we can just use the previously compiled one
        if (!bad_breadth_flag) { 
          outer_focus = castToSequenceOfItem(bfs_sequence);
        } else {
          bfs_sequence = reverse_bft(item_tree);
          outer_focus = castToSequenceOfItem(bfs_sequence);
          bad_breadth_flag = false;
        }
      } else if ((FOCUS_IMPLICIT == focus_flag) &&
                 (!"ContextItem".equals(test_name))) {

        Sequence<Object> t_val = null;
        if ("VariableReference".equals(test_name)) {
          t_val = castToSequenceOfObject(environment.getVariable((String)test_node.get(0)));
          if (null == t_val) {
            String msg = "Error, Line " + test_node.getLocation().line
              + ": Variable " + (String)test_node.get(0)
              + " not initialized.";
            throw new RuntimeException(msg);
          } else {
            return t_val;
          }
        } else if ("ParenthesizedExpression".equals(test_name)) {
          return (Sequence)dispatch(test_node);
        } else if ("FunctionCall".equals(test_name)) {
          t_val = new Sequence<Object>();
          Object o = dispatch(test_node);
          if (o instanceof Integer) {
            t_val.add( o ); 
          } else {          
            t_val.addAll((List<?>)o);
          }
          return t_val;
        } else if ("Identifier".equals(test_name)) {
          
          String item_name = (String)test_node.get(0);
          Sequence<?> foo = environment.peekFocus();
          Sequence<?> maybeParents = environment.popFocus();
          environment.pushFocus( foo );
          t_val = new Sequence<Object>();

          for (Iterator<?> parent_iterator = maybeParents.flatIterator(); 
              parent_iterator.hasNext(); ) {
            Item parent = (Item)parent_iterator.next();
            List<Item> l = parent.getChildren();
            
            for ( int i = 0; i < l.size(); i++  ){
              Item item = l.get(i);
              Object o = item.object;
              if (o instanceof GNode) {
                String tmp = ((GNode)o).getName();
                if (tmp.equals(item_name)) {
                  t_val.add(parent);
                }
              }     
            }                       
          }
          return t_val;
        }
            
      } else { // continuing focus
        try {
          outer_focus = castToSequenceOfItem(environment.peekFocus());
        } catch (NoSuchElementException nse) {
          String msg = "Error, Line " + test_node.getLocation().line +
            ": Attempted to evaluate a path expression without focus.";
          throw new RuntimeException(msg);
        }
      }
      Sequence<Item> value = new Sequence<Item>();
      
      // we want to consider every element of the focus - 
      // first we take care of primary expressions that work on the outer
      // focus, then we try to match up its children
      if (FOCUS_ALL == focus_flag) {
        return test(test_node, outer_focus);
      } else if ("ContextItem".equals(test_name)) {
        return outer_focus;
      } else if ("FunctionCall".equals(test_name)) {
        return (Sequence)dispatch(test_node);
      } else if ("ReverseStep" == test_name) {
        Sequence<Item> parents = new Sequence<Item>();
        for (Iterator<?> parent_iterator = outer_focus.flatIterator(); 
             parent_iterator.hasNext();) {
          
          Item parent = ((Item)parent_iterator.next()).parent;
          
          if (null == parent) {
            String msg = "Error, Line " + test_node.getLocation().line +
              ": Item has no parent.";
            throw new RuntimeException(msg);
          } else {
            parents.add(parent);
          }
        }
        value.addAll(parents);
      } else {
        // collect all of the children 
        Sequence<Item> child_items = new Sequence<Item>();
        
        for (Iterator<?> parent_iterator = outer_focus.flatIterator(); 
             parent_iterator.hasNext(); ) {
          
          Item parent_item = (Item)parent_iterator.next();
          Item child_item;
          
          if ((null != parent_item.object) && 
              (parent_item.object instanceof GNode) &&
              (null != parent_item.children)) {
            
            for (Iterator<?> child_iterator = parent_item.children.iterator();
                 child_iterator.hasNext();) {
              
              child_item = (Item)child_iterator.next();
              child_items = castToSequenceOfItem(child_item.addToList(castToListOfObject(child_items)));
            }
          }
        }
        value = union(value, test(test_node, child_items)); 
      }
      return value;
    }
    
    /**
     * Determine if items pass the step-expression test.
     * @param test_node The item to test
     * @param items  
     * @return The value of the step-expression
     */
    private Sequence<Item> test(GNode test_node, Sequence<Item> items) {
      Sequence<Item> value = new Sequence<Item>();
      
      String test_name = test_node.getName();
      Iterator<?> item_iterator = null;
      
      // we'll consider each type of primary expression in turn
      if ("ReverseStep".equals(test_name)) {
        // If the primary Expression is a "..", we'll want to add the
        // parent of item to the result sequence
        for (item_iterator = items.flatIterator(); item_iterator.hasNext();) {
          Item parent = ((Item)item_iterator.next()).parent;
          if (null == parent) {
            String msg = "Error, Line " + test_node.getLocation().line 
              + ": Item has no parent.";
            throw new RuntimeException(msg);
          } else {
            value = castToSequenceOfItem(parent.addToList(castToListOfObject(value)));
          }
        } 
      } else if ("Wildcard".equals(test_name)) {
        // If the primary expression is a wildcard, we'll simply add
        // item to the result sequence
        // I also realize that I'm discarding the memory I allocated above
        value = items;
      } else if ("ContextItem".equals(test_name)) {
        // was addAll
        value = items;
      } else if ("Identifier".equals(test_name)) {

        // if item is a GNode, see if its name matches the identifier
        for (item_iterator = items.flatIterator(); item_iterator.hasNext();) {
          Item item = (Item)item_iterator.next();
          if ((null != item.object) && (item.object instanceof GNode)) {
            String item_name = ((GNode)item.object).getName();
          
            if (item_name.equals(test_node.get(0))) {
              value = castToSequenceOfItem(item.addToList(castToListOfObject(value)));
            }
          } 
        }
      } else if ("StringLiteral".equals(test_name)) {
        String s = (String)test_node.get(0);
        s = s.substring(1, s.length()-1);
        
        for (item_iterator = items.flatIterator(); item_iterator.hasNext();) {
          Item item = (Item)item_iterator.next();
          // if the item is a string, see if it matches the string literal
          if ((null != item.object) && (item.object instanceof String)) {
            if (s.equals(item.object)) {
              value = castToSequenceOfItem(item.addToList(castToListOfObject(value)));
            }
          }
        }  
        
      } else if ("FunctionCall".equals(test_name)) {
        environment.pushFocus(items);
        value = castToSequenceOfItem(dispatch(test_node));
        environment.popFocus();
      } else {
        // it's either a VariableReference or a parenthesized single
        // expression
        Sequence<Item> child_value = null;
        if ("VariableReference".equals(test_name)) {
          // get the variable's value
          Sequence<Item> var_val = 
        	  castToSequenceOfItem(environment.getVariable((String)test_node.get(0)));
          if (null == var_val) {
            String msg = "Error, Line " + test_node.getLocation().line
              + ": Variable " + (String)test_node.get(0)
              + " not initialized.";
            throw new RuntimeException(msg);
          } else {
            child_value = var_val;
          }
        } else { // evaluate the parenthesized single expression
          child_value = castToSequenceOfItem(dispatch(test_node));
        }
        // only add sequence elements that are elements of children
        value = intersection(items, child_value);
      }
      
      // if we're searching every node, we'll want to recurse into the
      // children of element
      if (FOCUS_ALL == focus_flag ) {
   
        Sequence<Item> child_items = new Sequence<Item>();
        for (item_iterator = items.flatIterator(); item_iterator.hasNext();) {
          Item parent_item = (Item)item_iterator.next();
          
          if ((null != parent_item.object) && 
              (parent_item.object instanceof GNode) &&
              (null != parent_item.children)) {
            
            for (Iterator<Item> child_iterator = parent_item.children.iterator();
                 child_iterator.hasNext();) {
              
              child_items.add(child_iterator.next());
            }
          }   
        }
        if (!child_items.isEmpty()) {
          value = union(value,test(test_node, child_items));
        }

      }

      return value;
    }
    
    /**
     * Visit the specified parenthesized-expression.
     *
     * @param pe The parenthesized expression node.
     * @return The value of the expression.
     */
    public Sequence<Item> visitParenthesizedExpression(GNode pe) {
      int old_focus_flag = focus_flag;
      Sequence<Item> value = castToSequenceOfItem(dispatch((GNode)pe.get(0)));
      focus_flag = old_focus_flag;
      
      // want to account for empty sequence creation
      return value == null ? new Sequence<Item>() : value;
    }
    
    /**
     * Visit the specified integer-literal node.
     *
     * @param integer_literal The integer-literal node.
     * @return The decimal value of the integer literal.
     */
    public Integer visitIntegerLiteral(GNode integer_literal) {
      return new Integer(Integer.parseInt((String)integer_literal.get(0),10));
    }
    
    /**
     * Visit the specified identifier node.
     *
     * @param id The identifier node.
     * @return The value of the identifier node.
     */
    public String visitIdentifier(GNode id) {
      return (String)id.get(0);
    }
       
    /**
     * Visit the specified function call call node.
     *
     * @param function_call The function call node.
     * @return The value of the function call.
     */
    public Object visitFunctionCall(GNode function_call) {
      String function_name = (String)dispatch((GNode)function_call.get(0));
      GNode arg_node = (GNode)function_call.get(1);
      ArrayList<Object> arg_list = null;
      
      if (null != arg_node) {
    	ArrayList<Object> temp = new ArrayList<Object>();
    	temp.addAll((List<?>)dispatch((GNode)function_call.get(1)));
    	arg_list = temp;
      }
      
      try {
        return callFunction(function_name, arg_list);
      } catch (IllegalArgumentException iae) {
        String msg = "Error, Line " + function_call.getLocation().line
          + ": External function " + function_name + " not found.";
        throw new IllegalArgumentException(msg);
      }
      
    }
    
    /**
     * Visit the specified argument list.
     *
     * @param args The argument list node.
     * @return The arguments.
     */
    public ArrayList<Object> visitArgumentList(GNode args) {
      int nchildren = args.size();
      ArrayList<Object> arg_list = new ArrayList<Object>(nchildren);
      
      for (Iterator<Object> arg_iterator = args.iterator(); 
          arg_iterator.hasNext();) {
        
        arg_list.add(dispatch((GNode)arg_iterator.next()));
      }
      
      return arg_list;
    }
      
    /**
     * Visit the specified intersection expression.
     *
     * @param expression The expression.
     * @return The intersection of two sequences.
     */
    public Sequence<Item> visitIntersectionExpression(GNode expression) {
      Sequence<Item> a = castToSequenceOfItem(dispatch((GNode)expression.get(0)));
      Sequence<Item> b = castToSequenceOfItem(dispatch((GNode)expression.get(1)));
      
      return intersection(a,b);
    }
     
    /**
     * Visit the specified union expression.
     *
     * @param expression The expression.
     * @return The union of two sequences.
     */
    public Sequence<Item> visitUnionExpression(GNode expression) {
      Sequence<Item> a = castToSequenceOfItem(dispatch((GNode)expression.get(0)));
      Sequence<Item> b = castToSequenceOfItem(dispatch((GNode)expression.get(1)));
      return union(a,b);
    }

    /**
     * Visit the specified differ expression.
     *
     * @param expression The differ expression node.
     * @return The difference  of two sequences.
     */
    public Sequence<Item> visitDifferExpression( GNode expression ) {
      Sequence<Item> a = castToSequenceOfItem(dispatch((GNode)expression.get(0)));
      Sequence<Item> b = castToSequenceOfItem(dispatch((GNode)expression.get(1)));
      return difference(a,b); 
    }
    
    /**
     * Visit the specified "or" expression.
     *
     * @param expression The expression.
     * @return The value of the "or" expression.
     */
    public Sequence<Item> visitOrExpression(GNode expression) {
      Sequence<Item> value = null;
      
      for (Iterator<Object> exp_iterator = expression.iterator();
           exp_iterator.hasNext();) {
        
        value = castToSequenceOfItem(dispatch((GNode)exp_iterator.next()));
        if (!value.isEmpty()) {
          return value;
        } else {
          continue;
        }
      }
      
      // should only return null if the expression doesn't have any children,
      // which shouldn't conceivably happen.
      return value;
    }
      
    /**
     * Visit the specified "and" expression.
     * 
     * @param expression The expression.
     * @return The value of the "and" expression.
     */
    public Sequence<Object> visitAndExpression(GNode expression) {
      Sequence<Object> value = null, result = new Sequence<Object>();

      for (Iterator<Object> exp_iterator = expression.iterator();
           exp_iterator.hasNext();) {

        value = castToSequenceOfObject(dispatch((GNode)exp_iterator.next()));

        if (value.isEmpty()) {
          return value;
        } else {
          result.addAll(value);
        }
      }
      return result;
    }
    
    /** 
     * Determine  the logical union of two sequences.
     * @param a The first sequence
     * @param b The second sequence
     * @return the value of the union expression
     */
    private Sequence<Item> union(Sequence<Item> a, Sequence<Item> b) {
      Sequence<Item> union_list = new Sequence<Item>();
      if (null != a) {
        union_list.addAll(a);
      }
      
      if (null != b) {
        for (Iterator<?> b_iterator = b.flatIterator(); b_iterator.hasNext();) {
          Item b_item = (Item)b_iterator.next();
          union_list = castToSequenceOfItem(b_item.addToList(castToListOfObject(union_list)));
        }
      }      
      return union_list;
    }
    
    /**
     * Determine the logical intersection of two sequences.
     * @param a The first sequence
     * @param b The second sequence
     * @return The value of the intersection expression
     */
    private Sequence<Item> intersection(Sequence<Item> a, Sequence<Item> b) {
      Sequence<Item> intersection_list = new Sequence<Item>();
      
      if ((null != a) && (null != b)) {
        for (Iterator<?> a_iterator = a.flatIterator(); a_iterator.hasNext();) {
          Object a_object = a_iterator.next();
          if (b.contains(a_object)) {
            intersection_list.add((Item)a_object);
          }
        }
      }
      
      return intersection_list;
    }

    /**
     * Determine the logical difference between the specified
     * sequences.
     *
     * @param a The first sequence.
     * @param b The second sequence.
     * @return The logical difference.
     */
    private Sequence<Item> difference(Sequence<Item> a, Sequence<Item> b) {
      Sequence<Item> diffList = a;
            
      if ((null != a) && (null != b ) ) {
        for (Iterator<?> bIter = b.flatIterator(); bIter.hasNext(); ) {
          Object b_object = bIter.next(); 
          if (b.contains(b_object)) {
            diffList.remove(b_object);
          }          
        }
      }            
      return diffList;
    } 
    
    /**
     * Flip the AST upside-down, and then flatten into a sequence using a 
     * breadth-first traversal.
     * @param parent_item
     * @return The sequence representing the upside down ast
     */
    private Sequence<Item> reverse_bft(Item parent_item) {
      Sequence<Item> parents = new Sequence<Item>();
      Sequence<Item> rev_tree = new Sequence<Item>();
      
      Item pi = parent_item;
       
      parents.add(pi);

      while (!parents.isEmpty()) {
        pi = parents.removeFirst();

        if (parent_item.object instanceof GNode) {
          if (null != pi.children) {
            for (Iterator<Item> i = pi.children.iterator(); i.hasNext();) {
              Item child_item = i.next();
              parents.addFirst(child_item);
            }
          } 
        }
        rev_tree.addFirst(pi);
      }
      return rev_tree;
    }
  } // end class QueryVisitor

  /** MagiCast (TM).  Cast to list of sequence*/
  @SuppressWarnings("unchecked")
  <T> List<Sequence<?>> castToListOfSequence(T o) {
    return (List<Sequence<?>>)o;
  }
  
  /** MagiCast (TM).  Cast to list of objects*/
  @SuppressWarnings("unchecked")
  <T> List<Object> castToListOfObject(T o) {
    return (List<Object>)o;
  }
  
  /** MagiCast (TM).  Cast to sequence of item*/
  @SuppressWarnings("unchecked")
  <T> Sequence<Item> castToSequenceOfItem(T o) {
    return (Sequence<Item>)o;
  } 
  
  /** MagiCast (TM).  Cast to sequence of object*/
  @SuppressWarnings("unchecked")
  <T> Sequence<Object> castToSequenceOfObject(T o) {
    return (Sequence<Object>)o;
  }
  
} // end class Engine
