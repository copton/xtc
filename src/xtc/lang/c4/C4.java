package xtc.lang.c4;

import java.io.File;
import java.io.IOException;
import java.io.Reader;

import xtc.lang.C;
import xtc.lang.c4.transformer.C4AspectTransformer;
import xtc.parser.ParseException;
import xtc.parser.Result;
import xtc.tree.Formatting;
import xtc.tree.GNode;
import xtc.tree.Location;
import xtc.tree.Node;
import xtc.tree.ParseTreePrinter;

/**
 * 
 * @author Marco Yuen
 */
public class C4 extends C {
  public String getName() {
    return "C4 Tool";
  }

  public void init() {
    super.init();
    runtime.bool("transform", "optionTransformC4", false, "Transform the C4 advice into pure C.").bool(
        "debugTransform", "optionDebugTransformer", false, "Turn on debugging flag for C4 Transformers.");
  }

  public void process(Node node) {
    if (runtime.test("optionTransformC4")) {
      System.err.println("Invoking C4AspectTransformer ...");
      new C4AspectTransformer(node, runtime.test("optionDebugTransformer")).transform();
    }

    // Print AST.
    if (runtime.test("printAST")) {
      runtime.console().format(node, runtime.test("optionLocateAST")).pln().flush();
    }

    // Print source.
    if (runtime.test("printSource")) {
      if (runtime.test("optionParseTree") && (!runtime.test("optionStrip"))) {
        new ParseTreePrinter(runtime.console()).dispatch(node);
      } else {
        new C4Printer(runtime.console(), runtime.test("preserveLines"), runtime.test("formatGNU")).dispatch(node);
      }
      runtime.console().flush();
    }
  }
  
  public void prepare() {
    super.prepare();
    /*
    if (runtime.test("preserveLines") && runtime.test("optionTransformC4")) {
      System.err.println("Preseve Lines cannot be turned on with -transform. Disabling -preserveLines ...");
      runtime.setValue("preserveLines", false);
    }
    */
  }

  /**
   * Print memoization information.
   * 
   * @param parser
   *          The parser.
   * @param file
   *          The file.
   */
  private void printMemoInfo(Object parser, File file) {
    if (runtime.test("printMemoProfile")) {
      try {
        profile.invoke(parser, new Object[] { runtime.console() });
      } catch (Exception x) {
        runtime.error(file + ": " + x.getMessage());
      }
      runtime.console().pln().flush();
    }

    if (runtime.test("printMemoTable")) {
      try {
        dump.invoke(parser, new Object[] { runtime.console() });
      } catch (Exception x) {
        runtime.error(file + ": " + x.getMessage());
      }
      runtime.console().flush();
    }
  }

  @Override
  public Node parse(Reader in, File file) throws IOException, ParseException {
    if (runtime.test("optionParseTree")) { // ======================== Reader
      if (runtime.test("optionNoIncr")) {
        C4Reader parser = new C4Reader(in, file.toString(), (int) file.length());
        Result result = parser.pTranslationUnit(0);
        printMemoInfo(parser, file);
        return (Node) parser.value(result);

      } else {
        C4Reader parser = new C4Reader(in, file.getName());
        GNode unit = GNode.create("TranslationUnit");
        unit.setLocation(new Location(file.toString(), 1, 0));
        Node root = unit;
        boolean first = true;

        while (!parser.isEOF(0)) {
          Result result = first ? parser.pPrelude(0) : parser.pExternalDeclaration(0);
          printMemoInfo(parser, file);
          if (!result.hasValue())
            parser.signal(result.parseError());

          if (first) {
            root = Formatting.before1(result.semanticValue(), unit);
            first = false;
          } else {
            unit.add(result.semanticValue());
          }
          parser.resetTo(result.index);
        }

        // Grab any trailing annotations.
        Result result = parser.pAnnotations(0);
        if (!result.hasValue())
          parser.signal(result.parseError());
        unit.add(result.semanticValue());

        return root;
      }

    } else if (runtime.test("optionNoIncr")) { // ==================== Parser
      C4Parser parser = new C4Parser(in, file.toString(), (int) file.length());
      Result result = parser.pTranslationUnit(0);
      printMemoInfo(parser, file);
      return (Node) parser.value(result);

    } else {
      C4Parser parser = new C4Parser(in, file.getName());
      GNode root = GNode.create("TranslationUnit");
      boolean first = true;

      while (!parser.isEOF(0)) {
        Result result = first ? parser.pPrelude(0) : parser.pExternalDeclaration(0);
        printMemoInfo(parser, file);
        if (!result.hasValue())
          parser.signal(result.parseError());

        if (first) {
          first = false;
        } else {
          root.add(result.semanticValue());
        }
        parser.resetTo(result.index);
      }

      // Grab any trailing annotations.
      Result result = parser.pAnnotations(0);
      if (!result.hasValue())
        parser.signal(result.parseError());
      root.add(result.semanticValue());

      return root;
    }
  }

  /**
   * Run the tool with the specified command line arguments.
   * 
   * @param args
   *          The command line arguments.
   */
  public static void main(String[] args) {
    new C4().run(args);
  }
}
