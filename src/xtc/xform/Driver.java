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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;

import java.lang.reflect.Method;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import java.util.List;
import java.util.Iterator;

import xtc.lang.JavaParser;
import xtc.lang.JavaPrinter;
import xtc.lang.CParser;
import xtc.lang.CPrinter;

import xtc.parser.Result;
import xtc.parser.ParseException;

import xtc.tree.GNode;
import xtc.tree.Node;
import xtc.tree.Printer;

import xtc.util.Tool;
import xtc.util.Statistics;

/**
 * The driver for parsing and printing XForm.
 * 
 * @author Joe Pamer
 * @author Laune Harris
 * @author Robert Grimm
 * @version $Revision: 1.33 $
 */
public class Driver extends Tool {

  /**
   * Initialize this driver.  This method declares this drivers's
   * command line options. The following options are available:<ul>
   *
   * <li>a boolean option <code>optionC</code> for parsing 
   * with XTC's CParser,</li>
   * <li>a boolean option <code>optionJava</code> for parsing 
   *  with XTC's JavaParser,</li>
   * <li>a word option <code>parser</code> specifying an 
   * arbitrary parser,</li>
   * <li>a word option <code>unit</code> specifiying the 
   * program unit,</li>
   * <li>a boolean option <code>printC</code> for printing post-query 
   * source with XTC's CPrinter,</li>
   * <li>a boolean option <code>printJava</code> for printing post-query 
   * source with XTC's JavaPrinter,</li>
   * <li>a boolean option <code>printer</code> for specifying the printer for 
   * post-query source,</li>     
   * <li>a boolean option <code>preAST</code> for printing the 
   * pre-query AST,</li>     
   * <li>a boolean option <code>postAST</code> for printing the 
   * post-query AST,</li> 
   * <li>a boolean option <code>queryAST</code> for printing the XForm 
   * query's AST,</li>
   * <li>a boolean option <code>queryVal</code> for printing 
   * query's result,</li>
   * <li>a boolean option <code>debug</code> for printing stack traces on 
   * runtime errors</li>
   * </ul>
   */  
  public void init(){
    super.init();
    
    runtime.
      bool("c", "optionC", false, 
           "Parse source file using XTC's CParser").
      bool("java", "optionJava", false,
           "Parse source file using XTC's JavaParser").
      word("parser", "optionParser", false, 
           "Parse source file with parser. Eg. \"xtc.lang.NewJavaParser\"" ).
      word("unit", "optionUnit", false,
           "Specify source file's program unit. Eg. \"compilationUnit\"").
      bool("printC", "optionCPrinter", false,
           "Print post-query source with XTC's CPrinter" ).
      bool("printJava", "optionJavaPrinter", false,
           "Print post-query source with XTC's JavaPrinter" ).
      word("printer", "optionPrinter", false, 
           "Print post-query source with printer.").
      bool("preAST", "optionPreAST", false, 
           "Print the AST of the pre-query source").
      bool("postAST", "optionPostAST", false, 
           "Print the AST of the post-query source").
      bool("queryAST", "optionQueryAST", false, 
           "Print the query's AST" ).
      bool("queryVal", "optionQueryValue", false, 
           "Print the value of the query.").
      bool("debug", "optionDebug", false, 
           "Print a stack trace if a runtime error occurs"); 
  }
  
  /**
   * Run this Driver with the command line arguments
   * @param args The command line arguments.
   */
  public void run(String[] args) {
    init();
    runtime.console().p(getName()).p(", v. ").p(getVersion()).p(", ").pln().
      pln(getCopy()).flush();
    
    //Print the tool description and exit if there are no arguments.
    if (0 == args.length) {
      runtime.console().pln().
        pln("Usage: <option>* <xform-file-name> <source-file-name>").pln().
        pln("Options are:");
      runtime.printOptions();
      
      String explanation = getExplanation();
      if (null != explanation) {
        runtime.console().pln().wrap(0, explanation).pln();
      }
      runtime.console().pln().flush();
      runtime.exit();
    }
    
    // Process the command line arguments.
    int index = runtime.process(args);
    if (index >= args.length) {
      runtime.error("no file names specified");
    }
    
    prepare();
    
    //Stop if there have been errors.
    if (runtime.seenError()) {
      runtime.exit();
    }
    
    String xformName = args[index++];
    Query query  = parseXform(xformName);
    if (runtime.test("optionQueryAST")) {
      runtime.console().pln().format(query.ast).pln().flush();
    }
    
    boolean    measure   = runtime.test("optionPerformance");
    boolean    doGC      = runtime.test("optionGC");
    int        warmUp    = measure? runtime.getInt("runsWarmUp") : 0;
    int        total     = measure? runtime.getInt("runsTotal")  : 1;
    Statistics time      = measure? new Statistics() : null;
    Statistics fileSizes = measure? new Statistics() : null;
    Statistics latencies = measure? new Statistics() : null;
    
    if (measure) {
      runtime.console().p("Legend: file, size, time (ave, med, stdev), ").
        pln().flush(); 
    }
    
    while (index < args.length) {
      String sourceName = args[index++];
      if (runtime.test( "optionVerbose")) {
        runtime.console().p("Parsing " + sourceName).pln();
      }
      
      //open and parse the sourcefile
      Node ast = null;
      Reader in = null;
      File sourcefile = null;
      try {
        sourcefile = locate( sourceName );
        in = new BufferedReader(new FileReader(sourcefile) );
      } catch (IllegalArgumentException x) {
        runtime.error(x.getMessage());
      } catch (FileNotFoundException x) {
        runtime.error(x.getMessage());
        runtime.exit();
      } catch (IOException x) {
        if (null == x.getMessage()) {
          runtime.error(": I/O error");
        } else {
          runtime.error(": " + x.getMessage());
        }
      } catch (Throwable x) {
        x.printStackTrace();
        runtime.error();
      } 
      
      try {
        ast = parse(in, sourcefile);
      } catch (ParseException x) {
        runtime.error(x.getMessage());
      } catch (Throwable x) {
        runtime.error();
        x.printStackTrace();
      } 
      
      if (runtime.test("optionPreAST")) {
        runtime.console().pln().format(ast).pln().flush();
      }
      
      //run the query
      GNode result_ast = null;
      List<Object> query_value = null;
      Engine engine = new Engine();
      
      if (runtime.test("optionVerbose")) {
        runtime.console().p("Performing query.").pln().flush();
      }
      
      if (measure) {
        time.reset();
      }
      for (int i = 0; i < total; i++) {
        if (doGC) {
          System.gc();
        }
        
        //begin timing
        long startTime = 0;
        
        if (measure) {   
          startTime = System.currentTimeMillis();
        }
        
        try {
          query_value = engine.run(query, (GNode)ast.strip());
          result_ast = engine.getASTRoot();
        } catch (Throwable x) {
          while (null != x.getCause()) {
            x = x.getCause();
          }
          
          if (runtime.test( "optionDebug")) {
            x.printStackTrace();
          }
          return;
        }
        
        if (measure) {
          //timing ends
          long endTime = System.currentTimeMillis();
          if (i >= warmUp) {
            time.add(endTime - startTime);
          }
        } 
      }
      //Collect performance data for this file's runs
      if (measure) {
        long   fileSize = sourcefile.length();
        double latency  = time.mean();
                
        fileSizes.add(fileSize / 1024.0);
        latencies.add(latency);
        
        runtime.console().p(sourceName).
          p(' ').p(Statistics.round(fileSize / 1024.0)).p(' ').
          p( Statistics.round( latency ) ).p(' ').
          pln().flush();
      }
      
      if (runtime.test("optionQueryValue")) {
        printSequence(query_value);
        runtime.console().pln().flush();
      }        
      process(result_ast);
    }
    
    // Print overall statistics, if requested.
    if (measure) {
      double throughput = 1000.0 / Statistics.fitSlope(fileSizes, latencies);
      runtime.console().pln().
        p("Throughput      : ").p(Statistics.round(throughput)).pln().
        flush();    
    }
  }
 
  /*
   * Open and parse the XForm file and create query
   * @param filename The name of the xform query file
   * @return The created query
   */  
  public Query parseXform( String filename ) {
    if (runtime.test("optionVerbose")) {
      runtime.console().p("Parsing " + filename).pln().flush();
    }
    
    BufferedReader xform_in = null;
    File xformfile = null;
    try {
      xformfile = locate(filename);
      xform_in = new BufferedReader(new FileReader(xformfile));
    
    } catch (IllegalArgumentException x) {
      runtime.error(x.getMessage());
      runtime.exit();
    } catch (FileNotFoundException x) {
      runtime.error(x.getMessage());
      runtime.exit();
    } catch (IOException x) {
      if (null == x.getMessage()) {
        runtime.error(": I/O error");
      } else {
        runtime.error(": " + x.getMessage());
      }
      runtime.exit();
    } catch (Throwable x) {
      runtime.error();
      if (runtime.test("optionDebug")) {
        x.printStackTrace();
      }
      runtime.exit();
    }
    
    Query query = null;
    try {
      query = new Query(xform_in);
    } catch (IllegalArgumentException iae) {
      runtime.error("Error: XForm query is malformed.");
    }
    return query;
  }

  /*
   * Parse the source file using the parser specified by the command line
   * options (-parser, -c, -java)
   * @param in The reader for the source file
   * @param file The source file's file object
   * @return The source file's AST root
   */
  public Node parse(Reader in, File file) throws IOException, ParseException {
    if (runtime.test("optionJava")) {
      JavaParser parser  =
        new JavaParser(in, file.toString(), (int)file.length());
      return (GNode)parser.value(parser.pCompilationUnit(0));

    } else if (runtime.test("optionC")) {
      CParser parser = new CParser(in, file.toString(), (int)file.length());
      return (GNode)parser.value(parser.pTranslationUnit(0));

    } else if (null != runtime.getValue("optionParser")) {
      String name = (String)runtime.getValue( "optionParser" );
      String unit = (String)runtime.getValue("optionUnit");
      
      if (null == unit) {
        runtime.error( "-parser option requires -unit option" );
      }
      
      unit = "p" + unit;
      try {
        Class<?> klass = Class.forName(name);
        Constructor<?> klassConst = 
          klass.getConstructor(new Class[]{Reader.class, 
                                           String.class, int.class});
        Long l = new Long(file.length());
        Object parser = 
          klassConst.newInstance(new Object[]{in, file.toString(), 
                                              new Integer(l.intValue())});
        Method meth = klass.getMethod(unit,new Class[]{int.class});
        
        if (null == meth) {
          runtime.error("unit does not match any in " + name);
        }
        Result result = 
          (Result)(meth.invoke(parser, new Object[] { new Integer(0)}));
        if (result.hasValue()) {
          return (GNode)result.semanticValue();
        }      
      } catch (ClassNotFoundException e) {
        runtime.error("Unable to find class " + name);
      } catch (ExceptionInInitializerError e ) {
        runtime.error("Unable to initialise " + name);
      } catch (NoSuchMethodException e ) {
        runtime.error("Method " + unit + " not found");
      } catch (InstantiationException e) {
        runtime.error("Unable to instantiate " + name);
      } catch (IllegalAccessException e) {
        runtime.error("Unable to access method" + unit );
      } catch (InvocationTargetException e) {
        runtime.error("Invocation error on method " + unit);
      }         
    }    
    return null;
  }

  /*
   * Handle post query command line requests
   * @param node The AST's root node
   */ 
  public void process( Node node ){
    GNode root = (GNode)node;
    if (runtime.test("optionPostAST")) {
      runtime.console().pln().format(root).pln().flush();
    }
    if (runtime.test("optionJavaPrinter")) {
      new JavaPrinter(runtime.console()).dispatch(root);
      runtime.console().flush();
    } else if (runtime.test("optionCPrinter")) {
      new CPrinter( runtime.console() ).dispatch( root );
      runtime.console().flush();
    } else if (null != runtime.getValue("optionPrinter")) {
      String pName = (String)runtime.getValue("optionPrinter");
      try {
        Class<?> klass = Class.forName(pName);
        Constructor<?> klassConst = 
          klass.getConstructor(new Class[]{Printer.class} );
        Object printer = 
          klassConst.newInstance(new Object[]{runtime.console()} );
        Method meth = klass.getMethod("dispatch", new Class[]{GNode.class});
        
        meth.invoke(printer, new Object[]{root});
        runtime.console().flush();
      } catch (ClassNotFoundException e) {
        runtime.error("Unable to find " + pName );
      } catch (ExceptionInInitializerError e ) {
        runtime.error("Unable to initialise " + pName );
      } catch (NoSuchMethodException e ) {
        runtime.error("Unable to locate method 'dispatch' in " + pName );
      } catch (InstantiationException e) {
        runtime.error("Unable to instantiate " + pName );
      } catch (IllegalAccessException e) {
        runtime.error("Unable to access method 'dispatch' in" + pName);
      } catch (InvocationTargetException e) {
        runtime.error("Invocation failure on method 'dispatch");
      }       
    }    
  }
  
  /*
   * Returns the name of this driver
   * @return The driver name
   */
  public String getName() {
    return "Xform AST Query and Transformation Language";
  }
  
  /** 
   * Print the results of a query.
   * @param l The List/Sequence of query results
   */
  private void printSequence(List<?> l) {
    runtime.console().p("(");
    for (Iterator<?> i = l.iterator(); i.hasNext();) {
      Object item = i.next();
      if (item instanceof GNode) {
        runtime.console().p(((GNode)item).getName());
      } else if (item instanceof String) {
        runtime.console().p((String)item);
      } else if (item instanceof List) {
        printSequence((List<?>)item);
      } else if (null == item) {
        runtime.console().p("null");
      } else {
        String msg = "Error: Unidentified object in sequence.";
        throw new RuntimeException(msg);
      }
      if (i.hasNext()) {
        runtime.console().p(",");
      }
    }
    runtime.console().p(")");
  }
  
  /* 
   * Main: create a new Xform driver and execute with args
   * @param args The command line arguments
   */
  public static void main(String[] args) {
    new Driver().run(args);
  }
}

