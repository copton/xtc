/* Generated By:JavaCC: Do not edit this line. JavaParserConstants.java */
package xtc.lang.javacc;

public interface JavaParserConstants {

  int EOF = 0;
  int SINGLE_LINE_COMMENT = 9;
  int FORMAL_COMMENT = 10;
  int MULTI_LINE_COMMENT = 11;
  int ABSTRACT = 13;
  int BOOLEAN = 14;
  int BREAK = 15;
  int BYTE = 16;
  int CASE = 17;
  int CATCH = 18;
  int CHAR = 19;
  int CLASS = 20;
  int CONST = 21;
  int CONTINUE = 22;
  int _DEFAULT = 23;
  int DO = 24;
  int DOUBLE = 25;
  int ELSE = 26;
  int EXTENDS = 27;
  int FALSE = 28;
  int FINAL = 29;
  int FINALLY = 30;
  int FLOAT = 31;
  int FOR = 32;
  int GOTO = 33;
  int IF = 34;
  int IMPLEMENTS = 35;
  int IMPORT = 36;
  int INSTANCEOF = 37;
  int INT = 38;
  int INTERFACE = 39;
  int LONG = 40;
  int NATIVE = 41;
  int NEW = 42;
  int NULL = 43;
  int PACKAGE = 44;
  int PRIVATE = 45;
  int PROTECTED = 46;
  int PUBLIC = 47;
  int RETURN = 48;
  int SHORT = 49;
  int STATIC = 50;
  int SUPER = 51;
  int SWITCH = 52;
  int SYNCHRONIZED = 53;
  int THIS = 54;
  int THROW = 55;
  int THROWS = 56;
  int TRANSIENT = 57;
  int TRUE = 58;
  int TRY = 59;
  int VOID = 60;
  int VOLATILE = 61;
  int WHILE = 62;
  int STRICTFP = 63;
  int ASSERT = 64;
  int INTEGER_LITERAL = 65;
  int DECIMAL_LITERAL = 66;
  int HEX_LITERAL = 67;
  int OCTAL_LITERAL = 68;
  int FLOATING_POINT_LITERAL = 69;
  int EXPONENT = 70;
  int CHARACTER_LITERAL = 71;
  int STRING_LITERAL = 72;
  int IDENTIFIER = 73;
  int LETTER = 74;
  int DIGIT = 75;
  int LPAREN = 76;
  int RPAREN = 77;
  int LBRACE = 78;
  int RBRACE = 79;
  int LBRACKET = 80;
  int RBRACKET = 81;
  int SEMICOLON = 82;
  int COMMA = 83;
  int DOT = 84;
  int ASSIGN = 85;
  int GT = 86;
  int LT = 87;
  int BANG = 88;
  int TILDE = 89;
  int HOOK = 90;
  int COLON = 91;
  int EQ = 92;
  int LE = 93;
  int GE = 94;
  int NE = 95;
  int SC_OR = 96;
  int SC_AND = 97;
  int INCR = 98;
  int DECR = 99;
  int PLUS = 100;
  int MINUS = 101;
  int STAR = 102;
  int SLASH = 103;
  int BIT_AND = 104;
  int BIT_OR = 105;
  int XOR = 106;
  int REM = 107;
  int LSHIFT = 108;
  int RSIGNEDSHIFT = 109;
  int RUNSIGNEDSHIFT = 110;
  int PLUSASSIGN = 111;
  int MINUSASSIGN = 112;
  int STARASSIGN = 113;
  int SLASHASSIGN = 114;
  int ANDASSIGN = 115;
  int ORASSIGN = 116;
  int XORASSIGN = 117;
  int REMASSIGN = 118;
  int LSHIFTASSIGN = 119;
  int RSIGNEDSHIFTASSIGN = 120;
  int RUNSIGNEDSHIFTASSIGN = 121;

  int DEFAULT = 0;
  int IN_SINGLE_LINE_COMMENT = 1;
  int IN_FORMAL_COMMENT = 2;
  int IN_MULTI_LINE_COMMENT = 3;

  String[] tokenImage = {
    "<EOF>",
    "\" \"",
    "\"\\t\"",
    "\"\\n\"",
    "\"\\r\"",
    "\"\\f\"",
    "\"//\"",
    "<token of kind 7>",
    "\"/*\"",
    "<SINGLE_LINE_COMMENT>",
    "\"*/\"",
    "\"*/\"",
    "<token of kind 12>",
    "\"abstract\"",
    "\"boolean\"",
    "\"break\"",
    "\"byte\"",
    "\"case\"",
    "\"catch\"",
    "\"char\"",
    "\"class\"",
    "\"const\"",
    "\"continue\"",
    "\"default\"",
    "\"do\"",
    "\"double\"",
    "\"else\"",
    "\"extends\"",
    "\"false\"",
    "\"final\"",
    "\"finally\"",
    "\"float\"",
    "\"for\"",
    "\"goto\"",
    "\"if\"",
    "\"implements\"",
    "\"import\"",
    "\"instanceof\"",
    "\"int\"",
    "\"interface\"",
    "\"long\"",
    "\"native\"",
    "\"new\"",
    "\"null\"",
    "\"package\"",
    "\"private\"",
    "\"protected\"",
    "\"public\"",
    "\"return\"",
    "\"short\"",
    "\"static\"",
    "\"super\"",
    "\"switch\"",
    "\"synchronized\"",
    "\"this\"",
    "\"throw\"",
    "\"throws\"",
    "\"transient\"",
    "\"true\"",
    "\"try\"",
    "\"void\"",
    "\"volatile\"",
    "\"while\"",
    "\"strictfp\"",
    "\"assert\"",
    "<INTEGER_LITERAL>",
    "<DECIMAL_LITERAL>",
    "<HEX_LITERAL>",
    "<OCTAL_LITERAL>",
    "<FLOATING_POINT_LITERAL>",
    "<EXPONENT>",
    "<CHARACTER_LITERAL>",
    "<STRING_LITERAL>",
    "<IDENTIFIER>",
    "<LETTER>",
    "<DIGIT>",
    "\"(\"",
    "\")\"",
    "\"{\"",
    "\"}\"",
    "\"[\"",
    "\"]\"",
    "\";\"",
    "\",\"",
    "\".\"",
    "\"=\"",
    "\">\"",
    "\"<\"",
    "\"!\"",
    "\"~\"",
    "\"?\"",
    "\":\"",
    "\"==\"",
    "\"<=\"",
    "\">=\"",
    "\"!=\"",
    "\"||\"",
    "\"&&\"",
    "\"++\"",
    "\"--\"",
    "\"+\"",
    "\"-\"",
    "\"*\"",
    "\"/\"",
    "\"&\"",
    "\"|\"",
    "\"^\"",
    "\"%\"",
    "\"<<\"",
    "\">>\"",
    "\">>>\"",
    "\"+=\"",
    "\"-=\"",
    "\"*=\"",
    "\"/=\"",
    "\"&=\"",
    "\"|=\"",
    "\"^=\"",
    "\"%=\"",
    "\"<<=\"",
    "\">>=\"",
    "\">>>=\"",
  };

}
