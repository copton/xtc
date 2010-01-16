/*
 * xtc - The eXTensible Compiler
 * Copyright (C) 2005-2007 Robert Grimm, Princeton University
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
package xtc.lang.c4;

import java.lang.reflect.Method;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Reader;

import xtc.Constants;

import xtc.tree.GNode;
import xtc.tree.Printer;

import xtc.util.Statistics;

import xtc.parser.Result;
import xtc.parser.SemanticValue;
import xtc.parser.ParseError;

import xtc.lang.CCounter;
import xtc.lang.c4.transformer.C4AspectTransformer;

/**
 * The driver for C4.
 *
 * @author Robert Grimm
 * @author Marco Yuen
 * @version $Revision: 1.8 $
 */
public final class C4Driver {

  /** The number of warm-up runs for collecting statistics. */
  private static final int WARM_UP_RUNS = 2;

  /** The total number of runs for collecting statistics. */
  private static final int TOTAL_RUNS   = 22;

  /** The flag for an erroneous execution. */
  private static boolean error = false;

  /** The option for incremental parsing. */
  private static boolean optionIncremental = true;

  /** The option for performing GC. */
  private static boolean optionGC = false;

  /** The option for printing AST statistics. */
  private static boolean optionASTStats = false;

  /** The option for printing the AST in generic form. */
  private static boolean optionPrintAST = false;

  /** The option for printing the AST in C source form. */
  private static boolean optionPrint = false;

  /** The option for collecting parsing statistics. */
  private static boolean optionStats = false;

  /** The option for printing parsing statistics in table form. */
  private static boolean optionTable = false;

  /** The option for printing the memorization table. */
  private static boolean optionPrintTable = false;

  /** The option for invoking the Aspect Transformer. */
  private static boolean optionAspectTransformer = false;
  
  /** The option to turn on or off transformater debug flag. */
  private static boolean optionTransformerDebug = false;

  /** The file sizes (in KB). */
  private static Statistics fileSizes = null;

  /** The average latencies (in ms). */
  private static Statistics latencies = null;

  /** The average heap utilization (in KB). */
  private static Statistics heapSizes = null;

  /** The method for printing the memorization table. */
  private static Method dump = null;

  /** Hide the constructor. */
  private C4Driver() {
  }

  /**
   * Report a parse error.
   *
   * @param err The parse error.
   * @param parser The C parser.
   * @throws IOException Signals an exceptional condition while
   *   accessing the parser.
   */
  private static void error(ParseError err, C4Parser parser) 
    throws IOException {

    System.err.println();
    System.err.print(parser.format(err));
    error = true;
  }

  /**
   * Process the specified C file.
   *
   * @param file The file name.
   * @throws Exception Signals an exceptional condition.
   */
  private static void process(String file) throws Exception {
    final long fileSize = new File(file).length();
    Reader     in       = null;

    if (Integer.MAX_VALUE < fileSize) {
      throw new IllegalArgumentException("File too large");
    }

    try {
      // Set up for collecting statistics.
      Statistics time       = null;
      Statistics memory     = null;
      int        iterations = 1;

      if (optionStats) {
        time       = new Statistics();
        memory     = new Statistics();
        iterations = TOTAL_RUNS;
        fileSizes.add((double)fileSize / 1024.0);
      }

      for (int i=0; i<iterations; i++) {
        // Open file.
        in = new BufferedReader(new FileReader(file));

        // Perform GC if requested.
        if (optionGC) {
          System.gc();
        }

        // Set up the statistics for this run.
        long startTime   = 0;
        long startMemory = 0;
        
        if (optionStats) {
          startMemory = Runtime.getRuntime().freeMemory();
          startTime   = System.currentTimeMillis();
        }

        // Do the actual parsing.
        C4Parser parser    = new C4Parser(in, file, (int)fileSize);
        GNode   root      = null;

        if (optionIncremental) {
          root            = GNode.create("TranslationUnit");
          boolean first   = true;

          while (! parser.isEOF(0)) {
            Result result = null;

            if (first) {
              result      = parser.pPrelude(0);
            } else {
              result      = parser.pExternalDeclaration(0);
            }

            if (! result.hasValue()) {
              error((ParseError)result, parser);
              return;
            }
            
            if (first) {
              first       = false;
            } else {
              root.add(((SemanticValue)result).value);
            }
            parser.resetTo(result.index);
          }

          // Grab any trailing annotations.
          Result result = parser.pAnnotations(0);
          if (! result.hasValue()) {
            error((ParseError)result, parser);
            return;
          }
          root.add(((SemanticValue)result).value);

        } else {
          Result result   = parser.pTranslationUnit(0);
          if (! result.hasValue()) {
            error((ParseError)result, parser);
            return;
          }
          root            = (GNode)((SemanticValue)result).value;
        }

        // Transform Aspect constructs to C.
        if (optionAspectTransformer) {
          System.err.println("Invoking C4AspectTransformer ...");
          new C4AspectTransformer(root, optionTransformerDebug).transform();
        }

        // Print program statistics.
        if (optionASTStats) {
          CCounter counter = new CCounter();
          counter.dispatch(root);
          Printer  printer = new
            Printer(new BufferedWriter(new OutputStreamWriter(System.out)));
          counter.print(printer);
          printer.flush();
        }

        // Print the result.
        if (optionPrint || optionPrintAST || optionPrintTable) {
          Printer printer = new
            Printer(new BufferedWriter(new OutputStreamWriter(System.out)));

          if (optionPrintAST) {
            printer.format(root).pln();
          }
          if (optionPrint) {
            new C4Printer(printer).dispatch(root);
          }
          if (optionPrintTable) {
            dump.invoke(parser, new Object[] { printer });
          }

          printer.flush();
        }

        // Collect the statistics for this run.
        if (optionStats) {
          long endTime   = System.currentTimeMillis();
          long endMemory = Runtime.getRuntime().freeMemory();

          if (i >= WARM_UP_RUNS) {
            time.add(endTime - startTime);
            memory.add(startMemory - endMemory);
          }
        }

        // Close the input stream.
        in.close();
      }

      // Print the statistics.
      if (optionStats) {
        double latency  = time.mean();
        double heapSize = memory.mean();

        latencies.add(latency);
        heapSizes.add(heapSize / 1024.0);

        if (optionTable) {
          System.out.println(file + " " + fileSize + " " +
                             Statistics.round(latency) + " " +
                             time.median() + " " +
                             Statistics.round(time.stdev()) + " " +
                             Statistics.round(heapSize) + " " +
                             memory.median() + " " +
                             Statistics.round(memory.stdev()));
        } else {
          System.out.println("  file size     : " + fileSize);
          System.out.println("  time   mean   : " + 
                             Statistics.round(latency));
          System.out.println("  time   median : " + time.median());
          System.out.println("  time   stdev  : " +
                             Statistics.round(time.stdev()));
          System.out.println("  memory mean   : " +
                             Statistics.round(heapSize));
          System.out.println("  memory median : " + memory.median());
          System.out.println("  memory stdev  : " +
                             Statistics.round(memory.stdev()));
        }
      }

    } finally {
      // Clean up.
      if (null != in) {
        try {
          in.close();
        } catch (Exception x) {
        }
      }
    }
  }

  /**
   * Run the driver with the specified arguments.
   *
   * @param args The command line arguments.
   */
  public static void main(String[] args) {
    System.err.print("C4 Parser Driver Version ");
    System.err.print(Constants.VERSION);
    System.err.print("   ");
    System.err.println(Constants.COPY);

    if ((null == args) || (0 == args.length)) {
      System.err.println();
      System.err.println("Usage: <option>* <file-name>+");
      System.err.println();
      System.err.println("Options are:");
      System.err.println("  -noincr    Do not parse incrementally.");
      System.err.println("  -gc        Perform GC before parsing a file.");
      System.err.println("  -aststats  Collect and print AST statistics.");
      System.err.println("  -ast       Print the AST in generic form.");
      System.err.println("  -source    Print the AST in C source form.");
      System.err.println("  -at        Transform Aspect to C.");
      System.err.println("  -tdebug    Turn on debugging for Aspect Transformer.");
      System.err.println("  -stats     Collect and print performance " +
                         "statistics.");
      System.err.println("  -table     Print performance statistics as a " +
                         "table.");
      System.err.println("  -memo      Print the memoization table.");
      System.err.println();
      System.exit(1);
    }

    int start = -1;
    error     = false;

    // Process options.
    for (int i=0; i<args.length; i++) {
      if (args[i].startsWith("-")) {
        if ("-noincr".equals(args[i])) {
          optionIncremental = false;

        } else if ("-gc".equals(args[i])) {
          optionGC = true;

        } else if ("-aststats".equals(args[i])) {
          optionASTStats = true;

        } else if ("-ast".equals(args[i])) {
          optionPrintAST = true;

        } else if ("-source".equals(args[i])) {
          optionPrint = true;

        } else if ("-tdebug".equals(args[i])) {
          optionTransformerDebug = true;

        } else if ("-at".equals(args[i])) {
          optionAspectTransformer = true;

        } else if ("-stats".equals(args[i])) {
          optionStats = true;

        } else if ("-table".equals(args[i])) {
          optionTable = true;

        } else if ("-memo".equals(args[i])) {
          // Find method for printing the memorization table.
          try {
            dump = C4Parser.class.getMethod("dump", new Class[] {Printer.class});
          } catch (Exception x) {
            System.err.println("Parser cannot print memoization table." +
                               " Rebuild with dumpTable option.");
            error = true;
          }

          optionPrintTable = true;

        } else {
          System.err.println("Unrecognized option " + args[i]);
          error = true;
        }

      } else {
        start = i;
        break;
      }
    }

    if (-1 == start) {
      System.err.println("No file names specified");
      error = true;
    }

    if (error) {
      System.exit(1);
    }

    if (optionStats) {
      fileSizes = new Statistics();
      latencies = new Statistics();
      heapSizes = new Statistics();

      if (optionTable) {
        System.out.println("Legend: File, size, time (ave, med, stdev), " +
                           "memory (ave, med, stdev)");
        System.out.println();
      }
    }

    // Process files.
    error = false;

    for (int i=start; i<args.length; i++) {
      if (! (optionStats && optionTable)) {
        System.err.println("Processing " + args[i] + " ...");
      }
      try {
        process(args[i]);
      } catch (Throwable x) {
        error = true;

        while (null != x.getCause()) {
          x = x.getCause();
        }
        if (x instanceof FileNotFoundException) {
          System.err.println(x.getMessage());
        } else {
          x.printStackTrace();
        }
      }
    }

    if (optionStats) {
      double time   = 1000.0 / Statistics.fitSlope(fileSizes, latencies);
      double memory = Statistics.fitSlope(fileSizes, heapSizes);

      System.out.println();
      System.out.println("Overall performance      : " +
                         Statistics.round(time) + " KB/s");
      System.out.println("Overall heap utilization : " +
                         Statistics.round(memory) + ":1");
    }

    // Return the right status code.
    if (error) {
      System.exit(1);
    } else {
      System.exit(0);
    }
  }
  
}
