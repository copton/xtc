/*
 * xtc - The eXTensible Compiler
 * Copyright (C) 2007 New York University
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
package xtc.lang.overlog;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.PrintWriter;
import java.io.FileWriter;

import java.util.Map;

import xtc.Constants;

import xtc.lang.JavaPrinter;

import xtc.parser.ParseException;

import xtc.tree.GNode;
import xtc.tree.Node;
import xtc.tree.Printer;
import xtc.tree.Visitor;

import xtc.type.TypePrinter;

import xtc.util.SymbolTable;
import xtc.util.Tool;


/**
 * The driver for processesing the Overlog language.
 *
 * @author Robert Soule
 * @version $Revision: 1.32 $
 */
public class Overlog extends Tool {

  /** Create a new driver for Overlog. */
  public Overlog() {
    // Nothing to do.
  }

  public String getName() {
    return "xtc Overlog Driver";
  }

  public String getCopy() {
    return Constants.FULL_COPY;
  }

  public void init() {
    super.init();
    runtime.
      bool("optionTypeAnalyze", "optionTypeAnalyze", false,
           "Analyze the program's AST.").
      bool("optionConcurrency", "optionConcurrency", false,
           "Augment the program's AST with concurrency meta data.").
      word("o", "optionOutput", false,
           "Specify the base name of the generated java source code.").
      word("package", "package", false,
           "Specify the package of the generated code.").
      word("olgName", "olgName", false,
           "Specify the name of the main OLG class in the generated code.").
      bool("generateJava", "generateJava", false,
           "Output the result of the Overlog to Java source transformation.").
      bool("printAST", "printAST", false,
           "Print the program's AST in generic form.").
      bool("printSource", "printSource", false,
           "Print the program's AST in source form.").
      bool("printSymbolTable", "printSymbolTable", false,
           "Print the program's symbol table.");

  }

  public Node parse(Reader in, File file) throws IOException, ParseException {
    Parser parser =
      new Parser(in, file.toString(), (int)file.length());
    return (Node)parser.value(parser.pProgram(0));
  }

  public void process(Node node) {
    String basedir = (String)runtime.getValue("optionOutput");
    if (null == basedir) {
      basedir = "./generated/";
    } 
    String thePackage = (String)runtime.getValue("package");
    if (null == thePackage) {
      thePackage = "overlogRuntime";
    } 
    String olgName = (String)runtime.getValue("olgName");
    if (null == olgName) {
      olgName = "TestOLG";
    } 

     // Analyze the AST.
    if (runtime.test("optionTypeAnalyze")) {
      // Create symbol table.
      SymbolTable table = new SymbolTable();
      // Perform type checking.
      node = new TypeAnalyzer(runtime).analyze(node, table);
      // Print the symbol table.
      if (runtime.test("printSymbolTable")) {
        // Save the registered visitor.
        Visitor visitor = runtime.console().visitor();
        // Note that the type printer's constructor registers the just
        // created printer with the console.
        new TypePrinter(runtime.console());
        try {
          table.root().dump(runtime.console());
        } finally {
          // Restore the previously registered visitor.
          runtime.console().register(visitor);
        }
        runtime.console().flush();
      }
    }
    // Generate Java Code.
    if (runtime.test("generateJava")) {
      Transformer transformer = null;
      SymbolTable table = new SymbolTable();
      transformer = 
        new Transformer((GNode)node, table,runtime, thePackage, olgName);
      transformer.run();
      Map<String, GNode> transformed = transformer.getTransformedAST();
      for (String className : transformed.keySet()) {
        GNode myclass = transformed.get(className);
        File    dir  = new File(basedir);
        dir.mkdirs();
        File    file = new File(dir, className + ".java" );
        Printer out = null;
        try {
          out = new Printer(new PrintWriter(
              new BufferedWriter(new FileWriter(file))));
        } catch (IOException x) {
          if (null == x.getMessage()) {
            runtime.error(file.toString() + ": I/O error");
          } else {
            runtime.error(file.toString() + ": " + x.getMessage());
          }
          return;
        }
        new JavaPrinter(out).dispatch(myclass);
        out.flush();
        out.close();
      }
    }
    if (runtime.test("optionConcurrency")) {
      // Perform concurrency checking.
      node = new ConcurrencyAnalyzer(runtime).analyze(node);
    }

    // Print AST.
    if (runtime.test("printAST")) {
      runtime.console().format(node).pln().flush();
    }

    // Print source.
    if (runtime.test("printSource")) {
      new OverlogPrinter(runtime.console()).dispatch(node);
      runtime.console().pln().flush();
    }
  }

  /**
   * Run the driver with the specified command line arguments.
   *
   * @param args The command line arguments.
   */
  public static void main(String[] args) {
    new Overlog().run(args);
  }
}
