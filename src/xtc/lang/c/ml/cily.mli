(*
 *
 * Copyright (c) 2001-2002, 
 *  George C. Necula    <necula@cs.berkeley.edu>
 *  Scott McPeak        <smcpeak@cs.berkeley.edu>
 *  Wes Weimer          <weimer@cs.berkeley.edu>
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 * 1. Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 * 3. The names of the contributors may not be used to endorse or promote
 * products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER
 * OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 *)

(*
 * CIL: An intermediate language for analyzing C programs.
 *
 * George Necula
 *
 *)

(** This module defines the abstract syntax of CIL. It also provides utility 
 * functions for traversing the CIL data structures, and pretty-printing 
 * them. The parser for both the GCC and MSVC front-ends can be invoked as 
 * [Frontc.parse: string -> unit ->] {!Cil.file}. This function must be given 
 * the name of a preprocessed C file and will return the top-level data 
 * structure that describes a whole source file. By default the parsing and 
 * elaboration into CIL is done as for GCC source. If you want to use MSVC 
 * source you must set the {!Cil.msvcMode} to [true] and must also invoke the 
 * function [Frontc.setMSVCMode: unit -> unit]. *)


(** {b The Abstract Syntax of CIL} *)


(** The top-level representation of a CIL source file (and the result of the 
 * parsing and elaboration). Its main contents is the list of global 
 * declarations and definitions. You can iterate over the globals in a 
 * {!Cil.file} using the following iterators: {!Cil.mapGlobals}, 
 * {!Cil.iterGlobals} and {!Cil.foldGlobals}. You can also use the 
 * {!Cil.dummyFile} when you need a {!Cil.file} as a placeholder. For each 
 * global item CIL stores the source location where it appears (using the 
 * type {!Cil.location}) *)

type file = 
    { mutable fileName: string;   (** The complete file name *)
      mutable globals: global list; (** List of globals as they will appear 
                                        in the printed file *)
      mutable globinit: fundec option;  
      (** An optional global initializer function. This is a function where 
       * you can put stuff that must be executed before the program is 
       * started. This function, is conceptually at the end of the file, 
       * although it is not part of the globals list. Use {!Cil.getGlobInit} 
       * to create/get one. *)
      mutable globinitcalled: bool;     
      (** Whether the global initialization function is called in main. This 
       * should always be false if there is no global initializer. When you 
       * create a global initialization CIL will try to insert code in main 
       * to call it. This will not happen if your file does not contain a 
       * function called "main" *)
    } 
(** Top-level representation of a C source file *)

and comment = location * string

(** {b Globals}. The main type for representing global declarations and 
 * definitions. A list of these form a CIL file. The order of globals in the 
 * file is generally important. *)

(** A global declaration or definition *)
and global =
  | GType of typeinfo * location    
    (** A typedef. All uses of type names (through the [TNamed] constructor) 
        must be preceded in the file by a definition of the name. The string 
        is the defined name and always not-empty. *)

  | GCompTag of compinfo * location     
    (** Defines a struct/union tag with some fields. There must be one of 
        these for each struct/union tag that you use (through the [TComp] 
        constructor) since this is the only context in which the fields are 
        printed. Consequently nested structure tag definitions must be 
        broken into individual definitions with the innermost structure 
        defined first. *)

  | GCompTagDecl of compinfo * location
    (** Declares a struct/union tag. Use as a forward declaration. This is 
      * printed without the fields.  *)

  | GEnumTag of enuminfo * location
   (** Declares an enumeration tag with some fields. There must be one of 
      these for each enumeration tag that you use (through the [TEnum] 
      constructor) since this is the only context in which the items are 
      printed. *)

  | GEnumTagDecl of enuminfo * location
    (** Declares an enumeration tag. Use as a forward declaration. This is 
      * printed without the items.  *)

  | GVarDecl of varinfo * location
   (** A variable declaration (not a definition). If the variable has a 
       function type then this is a prototype. There can be several 
       declarations and at most one definition for a given variable. If both 
       forms appear then they must share the same varinfo structure. A 
       prototype shares the varinfo with the fundec of the definition. Either 
       has storage Extern or there must be a definition in this file *)

  | GVar  of varinfo * initinfo * location
     (** A variable definition. Can have an initializer. The initializer is 
      * updateable so that you can change it without requiring to recreate 
      * the list of globals. There can be at most one definition for a 
      * variable in an entire program. Cannot have storage Extern or function 
      * type. *)

  | GFun of fundec * location           
     (** A function definition. *)

  | GAsm of string * location           (** Global asm statement. These ones 
                                            can contain only a template *)
  | GPragma of attribute * location     (** Pragmas at top level. Use the same 
                                            syntax as attributes *)
  | GText of string                     (** Some text (printed verbatim) at 
                                            top level. E.g., this way you can 
                                            put comments in the output.  *)

(** {b Types}. A C type is represented in CIL using the type {!Cil.typ}. 
 * Among types we differentiate the integral types (with different kinds 
 * denoting the sign and precision), floating point types, enumeration types, 
 * array and pointer types, and function types. Every type is associated with 
 * a list of attributes, which are always kept in sorted order. Use 
 * {!Cil.addAttribute} and {!Cil.addAttributes} to construct list of 
 * attributes. If you want to inspect a type, you should use 
 * {!Cil.unrollType} or {!Cil.unrollTypeDeep} to see through the uses of 
 * named types. *)
(** CIL is configured at build-time with the sizes and alignments of the 
 * underlying compiler (GCC or MSVC). CIL contains functions that can compute 
 * the size of a type (in bits) {!Cil.bitsSizeOf}, the alignment of a type 
 * (in bytes) {!Cil.alignOf_int}, and can convert an offset into a start and 
 * width (both in bits) using the function {!Cil.bitsOffset}. At the moment 
 * these functions do not take into account the [packed] attributes and 
 * pragmas. *)

and typ =
    TVoid of attributes   (** Void type. Also predefined as {!Cil.voidType} *)
  | TInt of ikind * attributes 
     (** An integer type. The kind specifies the sign and width. Several 
      * useful variants are predefined as {!Cil.intType}, {!Cil.uintType}, 
      * {!Cil.longType}, {!Cil.charType}. *)


  | TFloat of fkind * attributes 
     (** A floating-point type. The kind specifies the precision. You can 
      * also use the predefined constant {!Cil.doubleType}. *)

  | TPtr of typ * attributes  
           (** Pointer type. Several useful variants are predefined as 
            * {!Cil.charPtrType}, {!Cil.charConstPtrType} (pointer to a 
            * constant character), {!Cil.voidPtrType}, 
            * {!Cil.intPtrType}  *)

  | TArray of typ * exp option * attributes
           (** Array type. It indicates the base type and the array length. *)

  | TFun of typ * (string * typ * attributes) list option * bool * attributes
          (** Function type. Indicates the type of the result, the name, type 
           * and name attributes of the formal arguments ([None] if no 
           * arguments were specified, as in a function whose definition or 
           * prototype we have not seen; [Some \[\]] means void). Use 
           * {!Cil.argsToList} to obtain a list of arguments. The boolean 
           * indicates if it is a variable-argument function. If this is the 
           * type of a varinfo for which we have a function declaration then 
           * the information for the formals must match that in the 
           * function's sformals. Use {!Cil.setFormals}, or 
           * {!Cil.setFunctionType}, or {!Cil.makeFormalVar} for this 
           * purpose. *)

  | TNamed of typeinfo * attributes 
          (** The use of a named type. Each such type name must be preceded 
           * in the file by a [GType] global. This is printed as just the 
           * type name. The actual referred type is not printed here and is 
           * carried only to simplify processing. To see through a sequence 
           * of named type references, use {!Cil.unrollType} or 
           * {!Cil.unrollTypeDeep}. The attributes are in addition to those 
           * given when the type name was defined. *)

  | TComp of compinfo * attributes
(** The most delicate issue for C types is that recursion that is possible by 
 * using structures and pointers. To address this issue we have a more 
 * complex representation for structured types (struct and union). Each such 
 * type is represented using the {!Cil.compinfo} type. For each composite 
 * type the {!Cil.compinfo} structure must be declared at top level using 
 * [GCompTag] and all references to it must share the same copy of the 
 * structure. The attributes given are those pertaining to this use of the 
 * type and are in addition to the attributes that were given at the 
 * definition of the type and which are stored in the {!Cil.compinfo}. *)

  | TEnum of enuminfo * attributes
           (** A reference to an enumeration type. All such references must
               share the enuminfo among them and with a [GEnumTag] global that 
               precedes all uses. The attributes refer to this use of the 
               enumeration and are in addition to the attributes of the 
               enumeration itself, which are stored inside the enuminfo  *)

  
  | TBuiltin_va_list of attributes
            (** This is the same as the gcc's type with the same name *)

(**
 There are a number of functions for querying the kind of a type. These are
 {!Cil.isIntegralType}, 
 {!Cil.isArithmeticType}, 
 {!Cil.isPointerType}, 
 {!Cil.isFunctionType}, 
 {!Cil.isArrayType}. 

 There are two easy ways to scan a type. First, you can use the
{!Cil.existsType} to return a boolean answer about a type. This function
is controlled by a user-provided function that is queried for each type that is
used to construct the current type. The function can specify whether to
terminate the scan with a boolean result or to continue the scan for the
nested types. 

 The other method for scanning types is provided by the visitor interface (see
 {!Cil.cilVisitor}).

 If you want to compare types (or to use them as hash-values) then you should
use instead type signatures (represented as {!Cil.typsig}). These
contain the same information as types but canonicalized such that simple Ocaml
structural equality will tell whether two types are equal. Use
{!Cil.typeSig} to compute the signature of a type. If you want to ignore
certain type attributes then use {!Cil.typeSigWithAttrs}. 

*)


(** Various kinds of integers *)
and ikind = 
    IChar       (** [char] *)
  | ISChar      (** [signed char] *)
  | IUChar      (** [unsigned char] *)
  | IInt        (** [int] *)
  | IUInt       (** [unsigned int] *)
  | IShort      (** [short] *)
  | IUShort     (** [unsigned short] *)
  | ILong       (** [long] *)
  | IULong      (** [unsigned long] *)
  | ILongLong   (** [long long] (or [_int64] on Microsoft Visual C) *)
  | IULongLong  (** [unsigned long long] (or [unsigned _int64] on Microsoft 
                    Visual C) *)

(** Various kinds of floating-point numbers*)
and fkind = 
    FFloat      (** [float] *)
  | FDouble     (** [double] *)
  | FLongDouble (** [long double] *)


(** {b Attributes.} *)

and attribute = Attr of string * attrparam list
(** An attribute has a name and some optional parameters. The name should not 
 * start or end with underscore. When CIL parses attribute names it will 
 * strip leading and ending underscores (to ensure that the multitude of GCC 
 * attributes such as const, __const and __const__ all mean the same thing.) *)

(** Attributes are lists sorted by the attribute name. Use the functions 
 * {!Cil.addAttribute} and {!Cil.addAttributes} to insert attributes in an 
 * attribute list and maintain the sortedness. *)
and attributes = attribute list
 
(** The type of parameters of attributes *)
and attrparam = 
  | AInt of int                          (** An integer constant *)
  | AStr of string                       (** A string constant *)
  | ACons of string * attrparam list       (** Constructed attributes. These 
                                             are printed [foo(a1,a2,...,an)]. 
                                             The list of parameters can be 
                                             empty and in that case the 
                                             parentheses are not printed. *)
  | ASizeOf of typ                       (** A way to talk about types *)
  | ASizeOfE of attrparam
  | ASizeOfS of typsig                   (** Replacement for ASizeOf in type
                                             signatures.  Only used for
                                             attributes inside typsigs.*)
  | AAlignOf of typ
  | AAlignOfE of attrparam
  | AAlignOfS of typsig
  | AUnOp of unop * attrparam
  | ABinOp of binop * attrparam * attrparam
  | ADot of attrparam * string           (** a.foo **)
  | AStar of attrparam                   (** * a *)
  | AAddrOf of attrparam                 (** & a **)
  | AIndex of attrparam * attrparam      (** a1[a2] *)
  | AQuestion of attrparam * attrparam * attrparam (** a1 ? a2 : a3 **)

(** {b Structures.} The {!Cil.compinfo} describes the definition of a 
 * structure or union type. Each such {!Cil.compinfo} must be defined at the 
 * top-level using the [GCompTag] constructor and must be shared by all 
 * references to this type (using either the [TComp] type constructor or from 
 * the definition of the fields. 

   If all you need is to scan the definition of each 
 * composite type once, you can do that by scanning all top-level [GCompTag]. 

 * Constructing a {!Cil.compinfo} can be tricky since it must contain fields 
 * that might refer to the host {!Cil.compinfo} and furthermore the type of 
 * the field might need to refer to the {!Cil.compinfo} for recursive types. 
 * Use the {!Cil.mkCompInfo} function to create a {!Cil.compinfo}. You can 
 * easily fetch the {!Cil.fieldinfo} for a given field in a structure with 
 * {!Cil.getCompField}. *)

(** The definition of a structure or union type. Use {!Cil.mkCompInfo} to 
 * make one and use {!Cil.copyCompInfo} to copy one (this ensures that a new 
 * key is assigned and that the fields have the right pointers to parents.). *)
and compinfo = {
    mutable cstruct: bool;              
   (** True if struct, False if union *)
    mutable cname: string;              
   (** The name. Always non-empty. Use {!Cil.compFullName} to get the full 
    * name of a comp (along with the struct or union) *)
    mutable ckey: int;                  
    (** A unique integer. This is assigned by {!Cil.mkCompInfo} using a 
     * global variable in the Cil module. Thus two identical structs in two 
     * different files might have different keys. Use {!Cil.copyCompInfo} to 
     * copy structures so that a new key is assigned. *)
    mutable cfields: fieldinfo list;    
    (** Information about the fields. Notice that each fieldinfo has a 
      * pointer back to the host compinfo. This means that you should not 
      * share fieldinfo's between two compinfo's *) 
    mutable cattr:   attributes;        
    (** The attributes that are defined at the same time as the composite 
     * type. These attributes can be supplemented individually at each 
     * reference to this [compinfo] using the [TComp] type constructor. *)
    mutable cdefined: bool;
    (** This boolean flag can be used to distinguish between structures
     that have not been defined and those that have been defined but have
     no fields (such things are allowed in gcc). *)
    mutable creferenced: bool;          
    (** True if used. Initially set to false. *)
  }

(** {b Structure fields.} The {!Cil.fieldinfo} structure is used to describe 
 * a structure or union field. Fields, just like variables, can have 
 * attributes associated with the field itself or associated with the type of 
 * the field (stored along with the type of the field). *)

(** Information about a struct/union field *)
and fieldinfo = { 
    mutable fcomp: compinfo;            
     (** The host structure that contains this field. There can be only one 
      * [compinfo] that contains the field. *)
    mutable fname: string;              
    (** The name of the field. Might be the value of {!Cil.missingFieldName} 
     * in which case it must be a bitfield and is not printed and it does not 
     * participate in initialization *)
    mutable ftype: typ;     
    (** The type *)
    mutable fbitfield: int option;      
    (** If a bitfield then ftype should be an integer type and the width of 
     * the bitfield must be 0 or a positive integer smaller or equal to the 
     * width of the integer type. A field of width 0 is used in C to control 
     * the alignment of fields. *)
    mutable fattr: attributes;          
    (** The attributes for this field (not for its type) *)
    mutable floc: location;
    (** The location where this field is defined *)
}



(** {b Enumerations.} Information about an enumeration. This is shared by all 
 * references to an enumeration. Make sure you have a [GEnumTag] for each of 
 * of these. *)

(** Information about an enumeration *)
and enuminfo = {
    mutable ename: string;              
    (** The name. Always non-empty. *)
    mutable eitems: (string * exp * location) list;
    (** Items with names and values. This list should be non-empty. The item 
     * values must be compile-time constants. *)
    mutable eattr: attributes;         
    (** The attributes that are defined at the same time as the enumeration 
     * type. These attributes can be supplemented individually at each 
     * reference to this [enuminfo] using the [TEnum] type constructor. *)
    mutable ereferenced: bool;         
    (** True if used. Initially set to false*)
}

(** {b Enumerations.} Information about an enumeration. This is shared by all 
 * references to an enumeration. Make sure you have a [GEnumTag] for each of 
 * of these. *)

(** Information about a defined type *)
and typeinfo = {
    mutable tname: string;              
    (** The name. Can be empty only in a [GType] when introducing a composite 
     * or enumeration tag. If empty cannot be referred to from the file *)
    mutable ttype: typ;
    (** The actual type. This includes the attributes that were present in 
     * the typedef *)
    mutable treferenced: bool;         
    (** True if used. Initially set to false*)
}

(** {b Variables.} 
 Each local or global variable is represented by a unique {!Cil.varinfo}
structure. A global {!Cil.varinfo} can be introduced with the [GVarDecl] or
[GVar] or [GFun] globals. A local varinfo can be introduced as part of a
function definition {!Cil.fundec}. 

 All references to a given global or local variable must refer to the same
copy of the [varinfo]. Each [varinfo] has a globally unique identifier that 
can be used to index maps and hashtables (the name can also be used for this 
purpose, except for locals from different functions). This identifier is 
constructor using a global counter.

 It is very important that you construct [varinfo] structures using only one
 of the following functions:
- {!Cil.makeGlobalVar} : to make a global variable
- {!Cil.makeTempVar} : to make a temporary local variable whose name
will be generated so that to avoid conflict with other locals. 
- {!Cil.makeLocalVar} : like {!Cil.makeTempVar} but you can specify the
exact name to be used. 
- {!Cil.copyVarinfo}: make a shallow copy of a varinfo assigning a new name 
and a new unique identifier

 A [varinfo] is also used in a function type to denote the list of formals. 

*)

(** Information about a variable. *)
and varinfo = { 
    mutable vname: string;		
    (** The name of the variable. Cannot be empty. It is primarily your 
     * responsibility to ensure the uniqueness of a variable name. For local 
     * variables {!Cil.makeTempVar} helps you ensure that the name is unique. 
     *)

    mutable vtype: typ;                 
    (** The declared type of the variable. *)

    mutable vattr: attributes;          
    (** A list of attributes associated with the variable.*)
    mutable vstorage: storage;          
    (** The storage-class *)

    mutable vglob: bool;	        
    (** True if this is a global variable*)

    mutable vinline: bool;
    (** Whether this varinfo is for an inline function. *)

    mutable vdecl: location;            
    (** Location of variable declaration. *)

    mutable vid: int;  
    (** A unique integer identifier. This field will be 
     * set for you if you use one of the {!Cil.makeFormalVar}, 
     * {!Cil.makeLocalVar}, {!Cil.makeTempVar}, {!Cil.makeGlobalVar}, or 
     * {!Cil.copyVarinfo}. *)

    mutable vaddrof: bool;              
    (** True if the address of this variable is taken. CIL will set these 
     * flags when it parses C, but you should make sure to set the flag 
     * whenever your transformation create [AddrOf] expression. *)

    mutable vreferenced: bool;          
    (** True if this variable is ever referenced. This is computed by 
     * [removeUnusedVars]. It is safe to just initialize this to False *)

    mutable vdescr: Pretty.doc;
    (** For most temporary variables, a description of what the var holds.
     *  (e.g. for temporaries used for function call results, this string
     *   is a representation of the function call.) *)

    mutable vdescrpure: bool;
    (** Indicates whether the vdescr above is a pure expression or call.
     *  Printing a non-pure vdescr more than once may yield incorrect
     *  results. *)
}

(** Storage-class information *)
and storage = 
    NoStorage     (** The default storage. Nothing is printed  *)
  | Static
  | Register
  | Extern                              


(** {b Expressions.} The CIL expression language contains only the side-effect free expressions of
C. They are represented as the type {!Cil.exp}. There are several
interesting aspects of CIL expressions: 

 Integer and floating point constants can carry their textual representation.
This way the integer 15 can be printed as 0xF if that is how it occurred in the
source. 

 CIL uses 64 bits to represent the integer constants and also stores the width
of the integer type. Care must be taken to ensure that the constant is
representable with the given width. Use the functions {!Cil.kinteger},
{!Cil.kinteger64} and {!Cil.integer} to construct constant
expressions. CIL predefines the constants {!Cil.zero},
{!Cil.one} and {!Cil.mone} (for -1). 

 Use the functions {!Cil.isConstant} and {!Cil.isInteger} to test if
an expression is a constant and a constant integer respectively.

 CIL keeps the type of all unary and binary expressions. You can think of that
type qualifying the operator. Furthermore there are different operators for
arithmetic and comparisons on arithmetic types and on pointers. 

 Another unusual aspect of CIL is that the implicit conversion between an
expression of array type and one of pointer type is made explicit, using the
[StartOf] expression constructor (which is not printed). If you apply the
[AddrOf}]constructor to an lvalue of type [T] then you will be getting an
expression of type [TPtr(T)].

 You can find the type of an expression with {!Cil.typeOf}. 

 You can perform constant folding on expressions using the function
{!Cil.constFold}. 
*)

(** Expressions (Side-effect free)*)
and exp =
    Const      of constant              (** Constant *)
  | Lval       of lval                  (** Lvalue *)
  | SizeOf     of typ                   
    (** sizeof(<type>). Has [unsigned int] type (ISO 6.5.3.4). This is not 
     * turned into a constant because some transformations might want to 
     * change types *)

  | SizeOfE    of exp                   
    (** sizeof(<expression>) *)

  | SizeOfStr  of string
    (** sizeof(string_literal). We separate this case out because this is the 
      * only instance in which a string literal should not be treated as 
      * having type pointer to character. *)

  | AlignOf    of typ                   
    (** This corresponds to the GCC __alignof_. Has [unsigned int] type *)
  | AlignOfE   of exp  

                                        
  | UnOp       of unop * exp * typ     
    (** Unary operation. Includes the type of the result. *)

  | BinOp      of binop * exp * exp * typ
    (** Binary operation. Includes the type of the result. The arithmetic 
     * conversions are made explicit for the arguments. *)

  | CastE      of typ * exp            
    (** Use {!Cil.mkCast} to make casts.  *)

  | AddrOf     of lval                 
    (** Always use {!Cil.mkAddrOf} to construct one of these. Apply to an 
     * lvalue of type [T] yields an expression of type [TPtr(T)]. Use 
     * {!Cil.mkAddrOrStartOf} to make one of these if you are not sure which 
     * one to use. *)

  | StartOf    of lval   
    (** Conversion from an array to a pointer to the beginning of the array. 
     * Given an lval of type [TArray(T)] produces an expression of type 
     * [TPtr(T)]. Use {!Cil.mkAddrOrStartOf} to make one of these if you are 
     * not sure which one to use. In C this operation is implicit, the 
     * [StartOf] operator is not printed. We have it in CIL because it makes 
     * the typing rules simpler. *)

(** {b Constants.} *)

(** Literal constants *)
and constant =
  | CInt64 of int64 * ikind * string option 
    (** Integer constant. Give the ikind (see ISO9899 6.1.3.2) and the 
     * textual representation, if available. (This allows us to print a 
     * constant as, for example, 0xF instead of 15.) Use {!Cil.integer} or 
     * {!Cil.kinteger} to create these. Watch out for integers that cannot be 
     * represented on 64 bits. OCAML does not give Overflow exceptions. *)
  | CStr of string 
    (** String constant. The escape characters inside the string have been 
     * already interpreted. This constant has pointer to character type! The 
     * only case when you would like a string literal to have an array type 
     * is when it is an argument to sizeof. In that case you should use 
     * SizeOfStr. *)
  | CWStr of int64 list  
    (** Wide character string constant. Note that the local interpretation
     * of such a literal depends on {!Cil.wcharType} and {!Cil.wcharKind}.
     * Such a constant has type pointer to {!Cil.wcharType}. The
     * escape characters in the string have not been "interpreted" in 
     * the sense that L"A\xabcd" remains "A\xabcd" rather than being
     * represented as the wide character list with two elements: 65 and
     * 43981. That "interpretation" depends on the underlying wide
     * character type. *)
  | CChr of char   
    (** Character constant.  This has type int, so use charConstToInt
     * to read the value in case sign-extension is needed. *)
  | CReal of float * fkind * string option 
     (** Floating point constant. Give the fkind (see ISO 6.4.4.2) and also 
      * the textual representation, if available. *)
  | CEnum of exp * string * enuminfo
     (** An enumeration constant with the given value, name, from the given 
      * enuminfo. This is used only if {!Cil.lowerConstants} is true 
      * (default). Use {!Cil.constFoldVisitor} to replace these with integer 
      * constants. *)

(** Unary operators *)
and unop =
    Neg                                 (** Unary minus *)
  | BNot                                (** Bitwise complement (~) *)
  | LNot                                (** Logical Not (!) *)

(** Binary operations *)
and binop =
    PlusA                               (** arithmetic + *)
  | PlusPI                              (** pointer + integer *)
  | IndexPI                             (** pointer + integer but only when 
                                         * it arises from an expression 
                                         * [e\[i\]] when [e] is a pointer and 
                                         * not an array. This is semantically 
                                         * the same as PlusPI but CCured uses 
                                         * this as a hint that the integer is 
                                         * probably positive. *)
  | MinusA                              (** arithmetic - *)
  | MinusPI                             (** pointer - integer *)
  | MinusPP                             (** pointer - pointer *)
  | Mult                                (** * *)
  | Div                                 (** / *)
  | Mod                                 (** % *)
  | Shiftlt                             (** shift left *)
  | Shiftrt                             (** shift right *)

  | Lt                                  (** <  (arithmetic comparison) *)
  | Gt                                  (** >  (arithmetic comparison) *)  
  | Le                                  (** <= (arithmetic comparison) *)
  | Ge                                  (** >  (arithmetic comparison) *)
  | Eq                                  (** == (arithmetic comparison) *)
  | Ne                                  (** != (arithmetic comparison) *)            
  | BAnd                                (** bitwise and *)
  | BXor                                (** exclusive-or *)
  | BOr                                 (** inclusive-or *)

  | LAnd                                (** logical and. Unlike other 
                                         * expressions this one does not 
                                         * always evaluate both operands. If 
                                         * you want to use these, you must 
                                         * set {!Cil.useLogicalOperators}. *)
  | LOr                                 (** logical or. Unlike other 
                                         * expressions this one does not 
                                         * always evaluate both operands.  If 
                                         * you want to use these, you must 
                                         * set {!Cil.useLogicalOperators}. *)

(** {b Lvalues.} Lvalues are the sublanguage of expressions that can appear at the left of an assignment or as operand to the address-of operator. 
In C the syntax for lvalues is not always a good indication of the meaning 
of the lvalue. For example the C value
{v  
a[0][1][2]
 v}
 might involve 1, 2 or 3 memory reads when used in an expression context,
depending on the declared type of the variable [a]. If [a] has type [int
\[4\]\[4\]\[4\]] then we have one memory read from somewhere inside the area 
that stores the array [a]. On the other hand if [a] has type [int ***] then
the expression really means [* ( * ( * (a + 0) + 1) + 2)], in which case it is
clear that it involves three separate memory operations. 

An lvalue denotes the contents of a range of memory addresses. This range 
is denoted as a host object along with an offset within the object. The 
host object can be of two kinds: a local or global variable, or an object 
whose address is in a pointer expression. We distinguish the two cases so 
that we can tell quickly whether we are accessing some component of a 
variable directly or we are accessing a memory location through a pointer.
To make it easy to 
tell what an lvalue means CIL represents lvalues as a host object and an
offset (see {!Cil.lval}). The host object (represented as
{!Cil.lhost}) can be a local or global variable or can be the object
pointed-to by a pointer expression. The offset (represented as
{!Cil.offset}) is a sequence of field or array index designators.

 Both the typing rules and the meaning of an lvalue is very precisely
specified in CIL. 

 The following are a few useful function for operating on lvalues:
- {!Cil.mkMem} - makes an lvalue of [Mem] kind. Use this to ensure
that certain equivalent forms of lvalues are canonized. 
For example, [*&x = x]. 
- {!Cil.typeOfLval} - the type of an lvalue
- {!Cil.typeOffset} - the type of an offset, given the type of the
host. 
- {!Cil.addOffset} and {!Cil.addOffsetLval} - extend sequences
of offsets.
- {!Cil.removeOffset} and {!Cil.removeOffsetLval} - shrink sequences
of offsets.

The following equivalences hold {v 
Mem(AddrOf(Mem a, aoff)), off   = Mem a, aoff + off 
Mem(AddrOf(Var v, aoff)), off   = Var v, aoff + off 
AddrOf (Mem a, NoOffset)        = a                 
 v}

*)
(** An lvalue *)
and lval =
    lhost * offset

(** The host part of an {!Cil.lval}. *)
and lhost = 
  | Var        of varinfo    
    (** The host is a variable. *)

  | Mem        of exp        
    (** The host is an object of type [T] when the expression has pointer 
     * [TPtr(T)]. *)


(** The offset part of an {!Cil.lval}. Each offset can be applied to certain 
  * kinds of lvalues and its effect is that it advances the starting address 
  * of the lvalue and changes the denoted type, essentially focusing to some 
  * smaller lvalue that is contained in the original one. *)
and offset = 
  | NoOffset          (** No offset. Can be applied to any lvalue and does 
                        * not change either the starting address or the type. 
                        * This is used when the lval consists of just a host 
                        * or as a terminator in a list of other kinds of 
                        * offsets. *)

  | Field      of fieldinfo * offset    
                      (** A field offset. Can be applied only to an lvalue 
                       * that denotes a structure or a union that contains 
                       * the mentioned field. This advances the offset to the 
                       * beginning of the mentioned field and changes the 
                       * type to the type of the mentioned field. *)

  | Index    of exp * offset
                     (** An array index offset. Can be applied only to an 
                       * lvalue that denotes an array. This advances the 
                       * starting address of the lval to the beginning of the 
                       * mentioned array element and changes the denoted type 
                       * to be the type of the array element *)


(** {b Initializers.} A special kind of expressions are those that can appear 
 * as initializers for global variables (initialization of local variables is 
 * turned into assignments). The initializers are represented as type 
 * {!Cil.init}. You can create initializers with {!Cil.makeZeroInit} and you 
 * can conveniently scan compound initializers them with 
 * {!Cil.foldLeftCompound}. *)
(** Initializers for global variables. *)
and init = 
  | SingleInit   of exp   (** A single initializer *)
  | CompoundInit   of typ * (offset * init) list
    (** Used only for initializers of structures, unions and arrays. The 
     * offsets are all of the form [Field(f, NoOffset)] or [Index(i, 
     * NoOffset)] and specify the field or the index being initialized. For 
     * structures all fields must have an initializer (except the unnamed 
     * bitfields), in the proper order. This is necessary since the offsets 
     * are not printed. For unions there must be exactly one initializer. If 
     * the initializer is not for the first field then a field designator is 
     * printed, so you better be on GCC since MSVC does not understand this. 
     * For arrays, however, we allow you to give only a prefix of the 
     * initializers. You can scan an initializer list with 
     * {!Cil.foldLeftCompound}. *)


(** We want to be able to update an initializer in a global variable, so we 
 * define it as a mutable field *)
and initinfo = {
    mutable init : init option;
  } 

(** {b Function definitions.} 
A function definition is always introduced with a [GFun] constructor at the
top level. All the information about the function is stored into a
{!Cil.fundec}. Some of the information (e.g. its name, type,
storage, attributes) is stored as a {!Cil.varinfo} that is a field of the
[fundec]. To refer to the function from the expression language you must use
the [varinfo]. 

 The function definition contains, in addition to the body, a list of all the
local variables and separately a list of the formals. Both kind of variables
can be referred to in the body of the function. The formals must also be shared
with the formals that appear in the function type. For that reason, to
manipulate formals you should use the provided functions
{!Cil.makeFormalVar} and {!Cil.setFormals} and {!Cil.makeFormalVar}. 
*)
(** Function definitions. *)
and fundec =
    { mutable svar:     varinfo;        
         (** Holds the name and type as a variable, so we can refer to it 
          * easily from the program. All references to this function either 
          * in a function call or in a prototype must point to the same 
          * [varinfo]. *)
      mutable sformals: varinfo list;   
        (** Formals. These must be in the same order and with the same 
         * information as the formal information in the type of the function. 
         * Use {!Cil.setFormals} or 
         * {!Cil.setFunctionType} or {!Cil.makeFormalVar} 
         * to set these formals and ensure that they 
         * are reflected in the function type. Do not make copies of these 
         * because the body refers to them. *)
      mutable slocals: varinfo list;    
        (** Locals. Does NOT include the sformals. Do not make copies of 
         * these because the body refers to them. *)
      mutable smaxid: int;           (** Max local id. Starts at 0. Used for 
                                      * creating the names of new temporary 
                                      * variables. Updated by 
                                      * {!Cil.makeLocalVar} and 
                                      * {!Cil.makeTempVar}. You can also use 
                                      * {!Cil.setMaxId} to set it after you 
                                      * have added the formals and locals. *)
      mutable sbody: block;          (** The function body. *)
      mutable smaxstmtid: int option;  (** max id of a (reachable) statement 
                                        * in this function, if we have 
                                        * computed it. range = 0 ... 
                                        * (smaxstmtid-1). This is computed by 
                                        * {!Cil.computeCFGInfo}. *)
      mutable sallstmts: stmt list;  (** After you call {!Cil.computeCFGInfo} 
                                      * this field is set to contain all 
                                      * statements in the function *)
    }


(** A block is a sequence of statements with the control falling through from 
    one element to the next *)
and block = 
   { mutable battrs: attributes;      (** Attributes for the block *)
     mutable bstmts: stmt list;       (** The statements comprising the block*)
   } 


(** {b Statements}. 
CIL statements are the structural elements that make the CFG. They are 
represented using the type {!Cil.stmt}. Every
statement has a (possibly empty) list of labels. The
{!Cil.stmtkind} field of a statement indicates what kind of statement it 
is.

 Use {!Cil.mkStmt} to make a statement and the fill-in the fields. 

CIL also comes with support for control-flow graphs. The [sid] field in
[stmt] can be used to give unique numbers to statements, and the [succs]
and [preds] fields can be used to maintain a list of successors and
predecessors for every statement. The CFG information is not computed by
default. Instead you must explicitly use the functions
{!Cil.prepareCFG} and {!Cil.computeCFGInfo} to do it.

*)
(** Statements. *)
and stmt = {
    mutable labels: label list;        
    (** Whether the statement starts with some labels, case statements or 
     * default statements. *)

    mutable skind: stmtkind;           
    (** The kind of statement *)

    mutable sid: int;                  
    (** A number (>= 0) that is unique in a function. Filled in only after 
     * the CFG is computed. *)
    mutable succs: stmt list;          
    (** The successor statements. They can always be computed from the skind 
     * and the context in which this statement appears. Filled in only after 
     * the CFG is computed. *)
    mutable preds: stmt list;          
    (** The inverse of the succs function. *)
  } 

(** Labels *)
and label = 
    Label of string * location * bool   
          (** A real label. If the bool is "true", the label is from the 
           * input source program. If the bool is "false", the label was 
           * created by CIL or some other transformation *)
  | Case of exp * location              (** A case statement. This expression 
                                         * is lowered into a constant if 
                                         * {!Cil.lowerConstants} is set to 
                                         * true. *)
  | Default of location                 (** A default statement *)



(** The various kinds of control-flow statements statements *)
and stmtkind = 
  | Instr  of instr list               
  (** A group of instructions that do not contain control flow. Control 
   * implicitly falls through. *)

  | Return of exp option * location     
   (** The return statement. This is a leaf in the CFG. *)

  | Goto of stmt ref * location         
   (** A goto statement. Appears from actual goto's in the code or from 
    * goto's that have been inserted during elaboration. The reference 
    * points to the statement that is the target of the Goto. This means that 
    * you have to update the reference whenever you replace the target 
    * statement. The target statement MUST have at least a label. *)

  | Break of location                   
   (** A break to the end of the nearest enclosing Loop or Switch *)

  | Continue of location                
   (** A continue to the start of the nearest enclosing [Loop] *)
  | If of exp * block * block * location 
   (** A conditional. Two successors, the "then" and the "else" branches. 
    * Both branches fall-through to the successor of the If statement. *)

  | Switch of exp * block * (stmt list) * location  
   (** A switch statement. The statements that implement the cases can be 
    * reached through the provided list. For each such target you can find 
    * among its labels what cases it implements. The statements that 
    * implement the cases are somewhere within the provided [block]. *)

  | Loop of block * location * (stmt option) * (stmt option)
    (** A [while(1)] loop. The termination test is implemented in the body of 
     * a loop using a [Break] statement. If prepareCFG has been called,
     * the first stmt option will point to the stmt containing the continue
     * label for this loop and the second will point to the stmt containing
     * the break label for this loop. *)

  | Block of block                      
    (** Just a block of statements. Use it as a way to keep some block 
     * attributes local *)

    (** On MSVC we support structured exception handling. This is what you 
     * might expect. Control can get into the finally block either from the 
     * end of the body block, or if an exception is thrown. *)
  | TryFinally of block * block * location

    (** On MSVC we support structured exception handling. The try/except 
     * statement is a bit tricky: 
         [__try { blk } 
         __except (e) {
            handler
         }]

         The argument to __except  must be an expression. However, we keep a 
         list of instructions AND an expression in case you need to make 
         function calls. We'll print those as a comma expression. The control 
         can get to the __except expression only if an exception is thrown. 
         After that, depending on the value of the expression the control 
         goes to the handler, propagates the exception, or retries the 
         exception !!!
     *)      
  | TryExcept of block * (instr list * exp) * block * location
  

(** {b Instructions}. 
 An instruction {!Cil.instr} is a statement that has no local
(intraprocedural) control flow. It can be either an assignment,
function call, or an inline assembly instruction. *)

(** Instructions. *)
and instr =
    Set        of lval * exp * location  
   (** An assignment. The type of the expression is guaranteed to be the same 
    * with that of the lvalue *)
  | Call       of lval option * exp * exp list * location
   (** A function call with the (optional) result placed in an lval. It is 
    * possible that the returned type of the function is not identical to 
    * that of the lvalue. In that case a cast is printed. The type of the 
    * actual arguments are identical to those of the declared formals. The 
    * number of arguments is the same as that of the declared formals, except 
    * for vararg functions. This construct is also used to encode a call to 
    * "__builtin_va_arg". In this case the second argument (which should be a 
    * type T) is encoded SizeOf(T) *)

  | Asm        of attributes * (* Really only const and volatile can appear 
                               * here *)
                  string list *         (* templates (CR-separated) *)
                  (string option * string * lval) list * 
                                          (* outputs must be lvals with 
                                           * optional names and constraints. 
                                           * I would like these 
                                           * to be actually variables, but I 
                                           * run into some trouble with ASMs 
                                           * in the Linux sources  *)
                  (string option * string * exp) list * 
                                        (* inputs with optional names and constraints *)
                  string list *         (* register clobbers *)
                  location
    (** There are for storing inline assembly. They follow the GCC 
      * specification: 
{v 
  asm [volatile] ("...template..." "..template.."
                  : "c1" (o1), "c2" (o2), ..., "cN" (oN)
                  : "d1" (i1), "d2" (i2), ..., "dM" (iM)
                  : "r1", "r2", ..., "nL" );
 v}

where the parts are

  - [volatile] (optional): when present, the assembler instruction
    cannot be removed, moved, or otherwise optimized
  - template: a sequence of strings, with %0, %1, %2, etc. in the string to 
    refer to the input and output expressions. I think they're numbered
    consecutively, but the docs don't specify. Each string is printed on 
    a separate line. This is the only part that is present for MSVC inline
    assembly.
  - "ci" (oi): pairs of constraint-string and output-lval; the 
    constraint specifies that the register used must have some
    property, like being a floating-point register; the constraint
    string for outputs also has "=" to indicate it is written, or
    "+" to indicate it is both read and written; 'oi' is the
    name of a C lvalue (probably a variable name) to be used as
    the output destination
  - "dj" (ij): pairs of constraint and input expression; the constraint
    is similar to the "ci"s.  the 'ij' is an arbitrary C expression
    to be loaded into the corresponding register
  - "rk": registers to be regarded as "clobbered" by the instruction;
    "memory" may be specified for arbitrary memory effects

an example (from gcc manual):
{v 
  asm volatile ("movc3 %0,%1,%2"
                : /* no outputs */
                : "g" (from), "g" (to), "g" (count)
                : "r0", "r1", "r2", "r3", "r4", "r5");
 v}

 Starting with gcc 3.1, the operands may have names:

{v 
  asm volatile ("movc3 %[in0],%1,%2"
                : /* no outputs */
                : [in0] "g" (from), "g" (to), "g" (count)
                : "r0", "r1", "r2", "r3", "r4", "r5");
 v}

*)

(** Describes a location in a source file. *)
and location = { 
    line: int;		   (** The line number. -1 means "do not know" *)
    file: string;          (** The name of the source file*)
    byte: int;             (** The byte position in the source file *)
}


(** Type signatures. Two types are identical iff they have identical 
 * signatures. These contain the same information as types but canonicalized. 
 * For example, two function types that are identical except for the name of 
 * the formal arguments are given the same signature. Also, [TNamed] 
 * constructors are unrolled. *)
and typsig = 
    TSArray of typsig * int64 option * attribute list
  | TSPtr of typsig * attribute list
  | TSComp of bool * string * attribute list
  | TSFun of typsig * typsig list * bool * attribute list
  | TSEnum of string * attribute list
  | TSBase of typ


(** Convert a string representing a C integer literal to an expression. 
 * Handles the prefixes 0x and 0 and the suffixes L, U, UL, LL, ULL *)
val parseInt: string -> exp
