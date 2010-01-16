(*
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
 *)  
 
(**
 * This is the implementation of the Ocaml-based C type checker
 *
 * @author Anh Le
 * @version $Revision: 1.7 $
 **)
 
(******************************************************************************
 *                       Including modules                                    *
 ******************************************************************************) 
module E = Errormsg
module CH = Cabshelper
module MD = Machdep 
module H = Hashtbl

open Cabs
open Machdep
open Pretty

(******************************************************************************
 *                       Type definition                                      *
 ******************************************************************************)
(** The type of compile time values. *)
type valueType = IValue of int | FValue of float | SValue of string 
                 | LValue of valueType list;;
                   
(** Type of variable array length *)
type vLength   = Unknown | Known;;

(** Array size type.*)
type arraySize = Incomplete | VarLength of vLength | Fixed of int;;

(** Qualifier definition.*)
type qualifier = ConstQ | VolatileQ | RestrictQ ;;

(** FunctionSpecifier definition.*)
type functionSpecifier = InlineF ;;

(** Storage class definition.*)
type storageClass = ExternS | RegisterS | StaticS | TypedefS | AutoS;;

type gcc_attribute = {att_name: string; att_value: valueType option };;

(* Definition of C types.*)
type typ = {
    ty: raw_type ;
    qualifiers: qualifier list;
    storage: storageClass option;
    fSpec: functionSpecifier option;
    value: valueType option;
    implicit: bool option;
    initialised: bool option;
    loc: cabsloc option;
    in_top: bool;
    attributes : gcc_attribute list
  }             
  
and raw_type = 
  ErrorT | VoidT | CharT | UCharT | SCharT | BoolT |
  ShortT | UShortT | UIntT | IntT | ULongT | LongT | ULongLongT | 
  DoubleT | FloatT | LongDoubleT | LongLongT | PointerT of typ | 
  FloatComplexT | DoubleComplexT | LongDoubleComplexT | ComplexT |
  ArrayT of typ * arraySize | BitfieldT of typ * int |  
  StructT of string * int * typ list option | 
  UnionT of string * int * typ list option| 
  LabelT of string | FunctionT of typ * typ list option | 
  MemberT of string * typ | ListT of typ list | VarArgT | WideCharT |
  EnumeratorT of string * typ * int | EnumT of string * int * typ list option;;
  
      
(******************************************************************************
 *                             Some ultilities                                *
 ******************************************************************************)
(* Check if a value of an optional type has None value.*)
let is_none va = match va with
    None -> true
  | _ -> false ;;  
 
let show_error s loca = let _ = (E.log "%s:%d:%d: error: %s\n"
                                loca.filename loca.lineno loca.ident s) in () ;;
         
let show_warning s loca = let _ = (E.log "%s:%d:%d: warning: %s\n"
                                loca.filename loca.lineno loca.ident s) in () ;;
                                
let trace s loca = let _ = (E.log "%s:%d:%d: type: %s\n"
                                loca.filename loca.lineno loca.ident s) in () ;;                                
                                
(* Check that s starts with the prefix p *)
let starts_with p s = 
  let lp = String.length p in
  let ls = String.length s in
  lp >= ls && String.sub p 0 ls = s ;;
  
let ends_with p s = 
  let lp = String.length p in
  let ls = String.length s in
  lp >= ls && String.uppercase (String.sub p (lp - ls) ls) = s ;;  
  
(* Create a type record with all fields. *)   
let make_type t qual store fspec valu imp init pos top att = 
  { ty = t; qualifiers = qual; storage = store; fSpec = fspec; value = valu; 
    implicit = imp; initialised = init; loc = pos; in_top = top;
    attributes = att } ;; 

(* Create a type record with just a raw_type. *)        
let make_type2 t = 
  { ty = t; qualifiers = []; storage = None; fSpec = None; value = None; 
    implicit = None; initialised = None; loc = None; in_top = false;
    attributes = []} ;;
    
(* Create a type record with rawtype and storage class.*)
let make_type3 t st = 
  { ty = t; qualifiers = []; storage = st; fSpec = None; value = None; 
    implicit = None; initialised = None; loc = None; in_top = false;
    attributes = []} ;;

(* Crate a type record with rawtype and value. *)        
let update_value t va =       
  { ty = t.ty; qualifiers = t.qualifiers; storage = t.storage;
    fSpec = t.fSpec; value = va; implicit = t.implicit; 
    initialised = t.initialised ; loc = t.loc; in_top = t.in_top;
    attributes = t.attributes} ;;
    
let update_qualifier t qual = 
  { ty = t.ty; qualifiers = qual; storage = t.storage;
    fSpec = t.fSpec; value = t.value; implicit = t.implicit; 
    initialised = t.initialised ; loc = t.loc; in_top = t.in_top;
    attributes = t.attributes} ;;
              
(* Update position field in a type.*)
let update_loc t pos =     
  { ty = t.ty; qualifiers = t.qualifiers; storage = t.storage;
    fSpec = t.fSpec; value = t.value; implicit = t.implicit; 
    initialised = t.initialised; loc = pos; in_top = t.in_top;
    attributes = t.attributes} ;;
    
(* Update in_top field in a type.*)
let update_top t top =     
  { ty = t.ty; qualifiers = t.qualifiers; storage = t.storage;
    fSpec = t.fSpec; value = t.value; implicit = t.implicit; 
    initialised = t.initialised; loc = t.loc; in_top = top;
    attributes = t.attributes} ;;   
    
(* Update in_top field in a type.*)
let update_attributes t att =     
  { ty = t.ty; qualifiers = t.qualifiers; storage = t.storage;
    fSpec = t.fSpec; value = t.value; implicit = t.implicit; 
    initialised = t.initialised; loc = t.loc; in_top = t.in_top;
    attributes = att} ;;        

(* Index number, used to create a fresh name.*)        
let index_num: int ref = ref 0 ;;

let fresh_name s = 
  index_num := 1 + !index_num;
  s ^ "(" ^ (string_of_int !index_num) ^ ")" ;;
  
let nonce () =   
  index_num := 1 + !index_num;
  !index_num ;;
  
let rec union l1 l2 = match l2 with
    [] -> l1
  | x::xs -> if (List.mem x l1) then union l1 xs
             else union (List.append l1 [x]) xs ;;  
             
let rec union_flat l ll = match ll with
    [] -> l
  | x::xs -> let nl = union l x in
                union_flat nl xs ;;
                
let rec substract l1 l2 res = match l2 with
    [] -> res
  | x::xs -> if (List.mem x l1) then substract l1 xs res
             else substract l1 xs (List.append res [x]) ;;
    
(******************************************************************************
 *                   To_String functions for debugging                        *
 ******************************************************************************)
 
let qualifier_to_string qual = match qual with
    ConstQ -> "ConstQ " 
  | VolatileQ -> "VolatileQ " 
  | RestrictQ -> "RestrictQ "
    
let qualifiers_to_string quals = match quals with
    [] -> "[] "
  | _ -> let sl = List.map qualifier_to_string quals in
           "[" ^ (String.concat ", " sl) ^ "]" ;;
    
let storage_to_string sto = match sto with
    None -> "NoneStorage"
  | Some stor -> 
      match stor with
          ExternS -> "ExternS " 
        | RegisterS -> "RegisterS " 
        | StaticS -> "StaticS "
        | TypedefS -> "TypedefS " 
        | AutoS -> "AutoS "       
    
let fSpec_to_string fs = match fs with
    None -> "NoneFSpec "
  | Some _ -> "Inline " ;;      
    
let rec valueType_to_string va = match va with
    IValue(i) -> (string_of_int i) ^ " "
  | FValue(f) -> (string_of_float f) ^ " " 
  | SValue(s) -> s ^ " "
  | LValue(vl) -> let sl = List.map valueType_to_string vl in
                    String.concat "," sl ;;   
     
let value_to_string va = match va with
    None -> "NoneValue "
  | Some v -> valueType_to_string v     
    
let impl_to_string imp = match imp with
    None -> "None_Impl "
  | Some _ -> "Implicit "     
    
let init_to_string init = match init with
    None -> " NoneInit"
  | Some ini -> if ini then "true, " else "false, " ;;    
    
let loc_to_string loc = match loc with
    None -> "NoneLocation " 
  | Some lo -> lo.filename ^ ":" ^ (string_of_int lo.lineno) ^ " " ;;
  
let top_to_string top = if top then "true" 
                        else "false" ;; 
                        
let attribute_to_string at = 
  "{" ^ at.att_name ^ ", " ^ (value_to_string at.att_value) ^ "}" ;;
  
let attributes_to_string att = match att with
    [] -> "[] "
  | _ -> let sl = List.map attribute_to_string att in
           "[" ^ (String.concat ", " sl) ^ "]" ;;                                                         
  
let arraySize_to_string si = match si with
    Incomplete -> "[] "
  | VarLength _ -> "[*] "
  | Fixed(i) -> (string_of_int i) ^ " " ;;

let rec typ_to_string (t: typ) = 
  "{" ^ (rawtype_to_string t.ty) ^ ", " ^ (qualifiers_to_string t.qualifiers) ^
  ", " ^ (storage_to_string t.storage) ^ ", " ^ (fSpec_to_string t.fSpec) ^
  ", " ^ (value_to_string t.value) ^ ", " ^ (impl_to_string t.implicit) ^
  ", " ^ (init_to_string t.initialised) ^ ", " ^ (loc_to_string t.loc) ^ 
  ", " ^ (top_to_string t.in_top) ^ ", " ^ (attributes_to_string t.attributes) ^
  "}" 
  
and rawtype_to_string rt = match rt with
    ErrorT -> "ErrorT " 
  | VoidT -> "VoidT " 
  | CharT -> "CharT "
  | UCharT -> "UCharT " 
  | SCharT -> "SCharT "
  | BoolT -> "BoolT "
  | ShortT -> " ShortT "
  | UShortT -> "UShortT " 
  | UIntT -> "UIntT " 
  | IntT -> "IntT "
  | ULongT -> "ULongT "
  | LongT -> "LongT "
  | ULongLongT -> "ULongLongT "
  | DoubleT -> "DoubleT "
  | FloatT -> "FloatT " 
  | LongDoubleT -> "LongdoubleT " 
  | LongLongT -> "LongLongT "
  | PointerT(t) -> "Pointer of " ^ (typ_to_string t) ^ ""
  | FloatComplexT -> "FloatComplexT "
  | DoubleComplexT -> "DoubleComplexT " 
  | LongDoubleComplexT -> "LongDoubleComplexT "
  | ComplexT -> "ComplexT " 
  | ArrayT(t,si) -> "ArrayT of " ^ (typ_to_string t) ^ " : " ^
                       (arraySize_to_string si)
  | BitfieldT(t,i) -> "Bitfield of " ^ (typ_to_string t) ^ " : " ^
                         (string_of_int i)
  | StructT(s,non,tl) -> 
      let sl = match tl with
                   None -> ["None "]
                 | Some(tll) -> List.map typ_to_string tll in
      let str = String.concat ", " sl in
        "Structure of {" ^ s ^ (string_of_int non) ^ ", [" ^ str ^ "]"           
  | UnionT(s,non,tl) ->
      let sl = match tl with
                   None -> ["None "]
                 |Some tll -> List.map typ_to_string tll in
      let str = String.concat ", " sl in
        "Union of {" ^ s ^ (string_of_int non) ^ ", [" ^ str ^ "]"            
  | LabelT(s) -> "Label " ^ s 
  | FunctionT(t,tl) ->
      let sl = match tl with
                   None -> ["None "]
                 |Some tll -> List.map typ_to_string tll in
      let str = String.concat ", " sl in
        "Function of " ^ (typ_to_string t) ^ " -> " ^ "[" ^ str ^ "]"
    
  | MemberT(s,t) -> "Member of " ^ s ^ " : " ^ (typ_to_string t) 
  | ListT(tl) -> let sl = List.map typ_to_string tl in
                      "[" ^ (String.concat "," sl) ^ "]"
  | EnumT(str,_,_) -> "Enum : " ^ str
                      
  | VarArgT -> "VaragrT "
  | WideCharT -> "WideCharT " 
  | EnumeratorT(str,_,_) -> "Enumerator: " ^ str ;;
  
let uop_to_string uop = match uop with
    MINUS -> "-"
  | PLUS -> "+" 
  | NOT -> "!" 
  | BNOT -> "BNOT" 
  | MEMOF -> "MEMOF" 
  | ADDROF -> "ADDROF"
  | PREINCR -> "++" 
  | PREDECR -> "--" 
  | POSINCR -> "P++"
  | POSDECR -> "P--" ;;
  
let bop_to_string bop = match bop with
    ADD -> "+"
  | SUB -> "-"
  | MUL -> "*"
  | DIV -> "/"
  | MOD -> "%"
  | AND -> "&&"
  | OR -> "||"
  | BAND -> "&"
  | BOR -> "|"
  | XOR -> "xor"
  | SHL -> "<<"
  | SHR -> ">>"
  | EQ -> "=="
  | NE -> "!="
  | LT -> "<"
  | GT -> ">"
  | LE -> "<="
  | GE -> ">="
  | ASSIGN -> "="
  | ADD_ASSIGN -> "+="
  | SUB_ASSIGN -> "-="
  | MUL_ASSIGN -> "*="
  | DIV_ASSIGN -> "/="
  | MOD_ASSIGN -> "%="
  | BAND_ASSIGN -> "&="
  | BOR_ASSIGN -> "|="
  | XOR_ASSIGN -> "xor="
  | SHL_ASSIGN -> "<<="
  | SHR_ASSIGN -> ">>=" ;;
  
let const_to_string cons = match cons with 
  | CONST_INT(str) -> str
  | CONST_FLOAT(str) -> str
  | CONST_CHAR _ -> "cchar"
  | CONST_WCHAR _ -> "wchar"
  | CONST_STRING(str) -> str
  | CONST_WSTRING _ -> "wstring" ;;
  
let rec expr_to_string expr = match expr with
    NOTHING -> "NOTHING"
  | UNARY(uop,e) -> (uop_to_string uop) ^ " " ^ (expr_to_string e)
  | LABELADDR(str) -> "&&" ^ str
  | BINARY(bop,e1,e2) -> (expr_to_string e1) ^ " " ^ (bop_to_string bop) ^ " " ^
                         (expr_to_string e2)
  | QUESTION(e1,e2,e3) -> (expr_to_string e1) ^ "? " ^ (expr_to_string e2) ^ 
                          ": " ^ (expr_to_string e3)

   
  | CAST(_,init) -> "(CAST() " ^ (init_to_string init) ^ ")" 
    
  | CALL(e,el) -> let sl = List.map expr_to_string el in                    
                   "CALL" ^ (expr_to_string e) ^ "(" ^ (String.concat "," sl) ^ ")" 
  | COMMA(el) -> let sl = List.map expr_to_string el in                    
                    "(" ^ (String.concat "," sl) ^ ")" 
  | CONSTANT(cons) -> const_to_string cons
  | PAREN(e) -> "(" ^ (expr_to_string e) ^ ")"
  | VARIABLE(str) -> "VAR(" ^ str ^ ")"
  | EXPR_SIZEOF(e) -> "sizeof(" ^ (expr_to_string e) ^ ")"
  | TYPE_SIZEOF _ -> "sizeof()"
  | EXPR_ALIGNOF(e) -> "alignof(" ^ (expr_to_string e) ^ ")"
  | TYPE_ALIGNOF _ -> "alignof()"
  | INDEX(e1,e2) -> (expr_to_string e1) ^ "[" ^ (expr_to_string e2) ^ "]"
  | MEMBEROF(e,str) -> "(" ^ (expr_to_string e) ^ "." ^ str ^ ")"
  | MEMBEROFPTR(e,str) -> "(" ^ (expr_to_string e) ^ "->" ^ str ^ ")"
  | GNU_BODY _ -> "Block"
  | EXPR_PATTERN _ -> "EXPR_PATTERN"

and init_to_string init = match init with
  | NO_INIT -> "NO_INIT"
  | SINGLE_INIT(e) -> expr_to_string e
  | COMPOUND_INIT _ -> "COMPOUND_INIT" ;;
        
        
(******************************************************************************
 *                            Useful values                                   *
 ******************************************************************************)
let curLoc: cabsloc ref = ref CH.cabslu

let voidt = make_type2 VoidT

let errort = make_type2 ErrorT          
        
(******************************************************************************
 *                       Symbol table data and functions                      *
 ******************************************************************************)

(*Scope information, containing a list of local id. *)
type scopeinfo = {scopename: string;
                  mutable id_list: string list} ;;
                  
(* The environment (symbol table). *)                                         
let env : (string, typ) H.t = H.create 307

(* We also keep a global environment. This is always a subset of the env *)
let genv : (string, typ) H.t = H.create 307

(* List of scopes.*)
let scopes :  scopeinfo list ref = ref []

(* Current scope. *)
let currentScope : scopeinfo ref = 
  ref {scopename = "global"; id_list = []}
(* Scope just exit. *)
let justExitScope : scopeinfo ref = 
  ref {scopename = "global"; id_list = []}
                  
(* Enter a new scope.*)
let enter_scope (name: string) =
  scopes := !currentScope :: !scopes ;
  if (name = !justExitScope.scopename) then
    currentScope := !justExitScope   
  else
    currentScope := {scopename = name; id_list = []}  

(* Exit the current scope, check if remove bindings.*)
let exit_scope rev = 
  let this, rest = 
    match !scopes with
      car :: cdr -> car, cdr
    | [] -> E.s (E.error "Not in any scope")
  in
  scopes := rest;
  justExitScope := !currentScope;
  let idlist = !currentScope.id_list in
  let rec remove_id = function
      [] -> ()
    | x::xs -> H.remove env x ; remove_id xs in
  if (rev) then remove_id idlist;
  currentScope := this
  
let is_at_global () = List.length !scopes = 0
  
(* Define in current scope. *)  
let define (name: string) (info: typ) = 
  !currentScope.id_list <- name :: !currentScope.id_list;
  H.add env name info;
  if (is_at_global ()) then H.add genv name info 
  
(* Redefine in current scope. *)  
let redefine (name: string) (info: typ) =
  if not(List.mem name !currentScope.id_list) then 
    !currentScope.id_list <- name :: !currentScope.id_list;
  H.remove env name;
  H.add env name info
  
(* Define in global scope. *)
let define_global (name: string) (info: typ) = 
  !currentScope.id_list <- name :: !currentScope.id_list;
  H.add env name info ;
  H.add genv name info
  
(* Redefine in global scope. *)
let redefine_global (name: string) (info: typ) = 
  H.remove genv name ;
  H.add genv name info
      
(* Check if a name is defined. *)
let is_defined (name: string) = H.mem env name

(* Check if a name is defined locally. *)
let is_defined_locally (name: string) = 
  (H.mem env name) && (List.mem name !currentScope.id_list)
  
(* Lookup.*)
let lookup name = 
  try 
    H.find env name 
  with Not_found -> errort

(**)
let lookup_locally name = 
  try
    if (List.mem name !currentScope.id_list) then
      H.find env name
    else errort
  with Not_found -> errort
  
(**)  
let lookup_global name = 
  try 
    H.find genv name 
  with Not_found -> errort  
  
  
        
(*******************************************************************************
 *                             Type helper functions                           *
 ******************************************************************************)
let rec type_equals t1 t2 = match t1.ty,t2.ty with
    StructT(s1,non1,_),StructT(s2,non2,_) -> (s1 = s2) && (non1 = non2)
  | UnionT(s1,non1,_),UnionT(s2,non2,_) -> (s1 = s2) && (non1 = non2)
  | PointerT(tt1),PointerT(tt2) -> type_equals tt1 tt2
  | FunctionT(r1,tl1),FunctionT(r2,tl2) -> type_equals r1 r2
  | _ -> t1.ty = t2.ty ;;
  
(** get an Ivalue *)
let get_int t = match t.value with
    Some (IValue(i)) -> Some i
  | _ -> None ;;

(** get a Fvalue *) 
let get_float t = match t.value with 
    Some (FValue(f)) -> Some f
  | _ -> None ;; 
  
(** Check if a type is packed. *)
let rec check_packed att_list = match att_list with
   [] -> false
  | x::xs -> ("packed" = x.att_name) || check_packed xs ;;
  
let is_packed t = check_packed t.attributes ;;

  
(** Get aligned attribute.*)
let rec check_aligned att_list = match att_list with
    [] -> 0
  | x::xs -> if ("aligned" = x.att_name) then
               if (is_none x.att_value) then 0
               else begin match x.att_value with
                        Some (IValue(i)) -> i
                      | _ -> 0  
                    end
             else check_aligned xs ;;            
             
let get_aligned t = check_aligned t.attributes ;;              

(* Get alignment. *)
(* Add checking for natural here.*)
let rec get_alignment t natural = match t.ty with
    StructT(_,_,tl) | UnionT(_,_,tl) | EnumT(_,_,tl) ->
      if (is_packed t) then 1
      else let ll = match tl with
                        Some res -> res
                      | _ -> [] in  
           let al = get_aligned t in 
           let max = if (al > 0) then al
                     else 1 in
             get_max_alignment max ll
  | _ ->
    let al = get_aligned t in
      if (al > 0) then al
      else match t.ty with
          VoidT -> MD.void_align
        | BoolT -> if natural then MD.bool_nat_align
                   else MD.bool_align
        | CharT| SCharT| UCharT -> 1
        | ShortT| UShortT -> if natural then MD.short_nat_align
                             else MD.short_align
        | IntT | UIntT -> if natural then MD.int_nat_align
                          else MD.int_align
        | LongT | ULongT -> if natural then MD.long_nat_align
                            else MD.long_align
        | LongLongT | ULongLongT -> if natural then MD.long_long_nat_align
                                    else MD.long_long_align
        | FloatT -> if natural then MD.float_nat_align
                    else MD.float_align
        | DoubleT -> if natural then MD.double_nat_align 
                     else MD.double_align
        | LongDoubleT -> if natural then MD.long_double_nat_align
                         else MD.long_double_align
        | FloatComplexT -> if natural then MD.float_nat_align
                           else MD.float_align
        | DoubleComplexT -> if natural then MD.double_nat_align
                            else MD.double_align
        | LongDoubleComplexT -> if natural then MD.long_double_nat_align
                                else MD.long_double_align
        | PointerT _ -> if natural then MD.pointer_nat_align
                        else MD.pointer_align
        | FunctionT _ -> MD.function_align
        | ArrayT(ty,_) -> get_alignment ty true
        | BitfieldT(ty,_) -> get_alignment ty false
        | MemberT(_,ty) -> get_alignment ty false
        | _ -> let _ = show_error "type without alignment" !curLoc in 1
        
(* Get max alignment.*)
and get_max_alignment max tl = match tl with
    [] -> max
  | x::xs -> match x.ty with
                 MemberT(_,ty) ->
                   begin match ty.ty with
                      BitfieldT(tty,si) -> 
                        if (si > 0) then
                          let al = get_alignment tty false in
                          let new_max = if (al > max) then al
                                        else max in
                            get_max_alignment new_max xs            
                        else get_max_alignment max xs
                     | _ -> let al = get_alignment ty false in
                          let new_max = if (al > max) then al
                                        else max in
                            get_max_alignment new_max xs        
                   end         
               | _ -> max ;; 
               
(* Check if a type is a variable-length array.*)
let is_var_array t = match t.ty with
    ArrayT(_,si) -> begin match si with
                        Fixed _ -> false
                      | _ -> true 
                    end
  | _ -> false;;
  
(* Check var array.*)
let rec check_var_array tl = match tl with
    [] -> false
  | x::xs -> match x.ty with
                 MemberT(_,ty) -> is_var_array ty || check_var_array xs
               | _ -> false ;;
               
(* Check if a struct or union has a trailing array.*)
let has_trailing_array t = match t.ty with
    UnionT(_,_,tl) -> 
      let ll = match tl with
                   Some res -> res
                 | _ -> [] in
        check_var_array ll
  | StructT(_,_,tl) ->
      let ll = match tl with
                   Some res -> res
                 | _ -> [] in                   
      if (List.length ll = 0) then false
      else let t = List.nth ll ((List.length ll) - 1) in
               is_var_array t
  | _ -> false ;;
  
               
(** Check if a type contains constant zero *)
let zero t = match t.value with
   Some (IValue _) -> if (get_int t) = Some 0 then true else false
 | Some (FValue _) -> if (get_float t = Some 0.0) then true else false
 | _ -> false ;;
 
(* Value equals.*)
let value_equals t1 t2 = match (t1.value, t2.value) with
      (Some IValue(i1), Some IValue(i2)) -> i1 = i2
    | (Some FValue(f1), Some FValue(f2)) -> f1 = f2
    | _ -> false ;;    
 
(* get the base type of a pointer or array type *)
let get_base t = match t.ty with 
   PointerT(t1) -> t1
 | ArrayT(t1,_) -> t1
 | _ -> t ;; 
 
(* *)
let pointer_equals t1 t2 = type_equals (get_base t1) (get_base t2)

let rec check_qualifiers quals1 quals2 = match quals2 with
    [] -> true
  | x::xs -> (List.mem x quals1) && (check_qualifiers quals1 xs)    

(* Check qualifiers.*)
let has_qualifiers t1 t2 =
  if (List.length t2.qualifiers = 0) then true
  else if (List.length t1.qualifiers = 0) then false
  else check_qualifiers t1.qualifiers t2.qualifiers 
  
(**  get the list of parameter types *)
let rec getParameterTypes t = match t.ty with
      FunctionT(_, l) -> l
    | PointerT(ty) -> getParameterTypes ty
    | _ -> None ;;
    
(* Check if a type is auto. *)
let is_auto t = match t.storage with
   Some ExternS | Some StaticS -> false
  | _ -> true ;;
    
(* Check if a type is declared with auto.*)
let has_auto t = match t.storage with
    Some AutoS -> true
  | _ -> false ;;    

(* Check if a type is declared with register. *)
let is_register t = match t.storage with
    Some RegisterS -> true
  | _ -> false ;;
  
(* Check if a type is declared with type def. *)
let is_typedef t = match t.storage with
    Some TypedefS -> true
  | _ -> false ;; 
  
(* Check if a type is declared with extern. *)
let is_extern t = match t.storage with
    Some ExternS -> true
  | _ -> false ;; 
  
(* Check if a type is declared with static.*)
let is_static t = match t.storage with
    Some StaticS -> true
  | _ -> false ;;
  
(* Check if a type is a errort.*)
let is_error t = match t.ty with
    ErrorT -> true
  | _ -> false ;;    
  
(** test if void type *)
let is_void t = match t.ty with
    VoidT -> true
  | _ -> false ;;  
  
(** Test if a type is integer type *)
let is_integer t = match t.ty with 
    CharT | UCharT | SCharT | BoolT |
    ShortT | UShortT | UIntT  | IntT | ULongT | LongT | ULongLongT | 
    LongLongT | BitfieldT _ | EnumT _ -> true
  | _ -> false ;;
  
(* Test if a type is a char type. *)
let is_char t = match t.ty with
    CharT | UCharT | SCharT -> true
  | _ -> false ;;
  
(* Test if a type a the type of a string literal.*)
let is_string t = match t.value with 
    Some (SValue _) -> true
  | _ -> false ;;        
                  
(** Test for bitfield *)
let rec is_bitfield t = match t.ty with
     BitfieldT _ -> true
   | MemberT(_,ty) -> is_bitfield ty
   | _ -> false ;;
   
(** Require an integer type. *)
let ensure_integer t op = 
  if (is_error t) || (is_integer t) then t
  else let _ = show_error ("integer required in " ^ op) !curLoc 
    in errort ;;  
        
(** is_qualified *)
let is_qualified t = if ((List.length t.qualifiers) > 0) then true else false ;;

(** Test if this is a float type *)
let is_float t = match t.ty with
    FloatT | DoubleT | LongDoubleT -> true
  | _ -> false ;;
  
(* Test if complex type. *)
let is_complex t = match t.ty with
    FloatComplexT | DoubleComplexT | LongDoubleComplexT | ComplexT -> true 
  | _ -> false ;;  
union
(** Test if artithmetic type.*)
let is_arithmetic t = is_integer t || is_float t || is_complex t ;;

(** Require an arithmetic type *)
let ensure_arithmetic t op = 
  if (is_error t) || (is_arithmetic t) then t
  else let _ = show_error ("arithmetic required in " ^ op) !curLoc 
          in errort ;;
          
(** Test if pointer. *)
let is_pointer t = match t.ty with 
    PointerT _ -> true 
  | _ -> false ;;

(** *)
let to_pointer t = match t.ty with
   PointerT _ -> t
 | ArrayT _ -> let tt = PointerT(get_base t) in
                    make_type2 tt
 | _ -> let _ = show_error "not pointer or array type" !curLoc in errort ;;          

(* test for array type *)
let is_array t = match t.ty with
    ArrayT _ -> true
  | _ -> false ;;  
        
(** Test if this type is scalar *) 
let is_scalar t = is_arithmetic t || is_pointer t || is_array t ;;

(** Require a scalar type *)
let ensure_scalar t op = 
  if (is_error t) || (is_scalar t) then t
  else let _ = show_error ("scalar required in " ^ op) !curLoc 
         in errort ;;
          
(*test for fixed sized array *)
let is_fixed t = match t.ty with
    ArrayT(_, si) -> begin match si with
                            Fixed _ -> true
                          | _ -> false
                        end    
  | _ -> false ;;

(** Test if function type *)
let is_function t = match t.ty with
    FunctionT _ -> true
  | _ -> false ;;

(** Require a function type *)
let ensure_function t op = 
  if (is_error t) || (is_function t) then t
  else let _ = show_error ("function required in " ^ op) !curLoc 
         in errort ;;
          
(* check for constant qualifier *)
let is_const t = let quals = t.qualifiers in 
  List.mem ConstQ quals ;;

(** Test if l value. *)
let is_lvalue t = not (is_void t) && not (is_function t) ;;

(** Test for modifiable lvalue *)
let is_modifiable_lvalue t = (is_lvalue t) && not (is_const t) ;;

(** ensure modifiable lvalue *)
let ensure_modifiable_lvalue t = 
  if (is_error t) || (is_modifiable_lvalue t) then ()
  else let _ = show_error "modifying read-only operand" !curLoc
          in () ;;      
  
(** Test for aggregate type *)
let is_aggregate t = match t.ty with
    ArrayT _ | StructT _ | UnionT _ -> true
  | _ -> false ;;

(* Test for struct or union types.*)
let is_struct_union t = match t.ty with
    StructT _ | UnionT _ -> true
  | _ -> false ;;
  
(* Test for enum type.*)
let is_enum t = match t.ty with
    EnumT _ -> true
  | _ -> false ;;    
                          
(* Test for anonymous struct or union. *)
let is_anonymous t = match t.ty with
    StructT(s,_,_) -> starts_with s "struct("
  | UnionT(s,_,_) -> starts_with s "union("
  | _ -> false ;;
  
(** Get the return type of a function *)
let rec get_return_type t = match t.ty with 
    FunctionT(r, _) -> r
   | PointerT(tt) -> get_return_type tt 
   | _ ->
     let _ = show_error "not function type" !curLoc in errort ;;  
   
(** Test if incomplete *)
let is_incomplete t = match t.ty with
   VoidT -> true
 | ArrayT(b,size) -> begin match size with
                            Incomplete -> not (is_typedef b)
                          | _ -> false
                        end    
 | StructT(s,_,ty) | UnionT(s,_,ty) | EnumT(s,_,ty)-> 
    if (None = ty) then
      let tt = lookup ("tag_" ^ s) in
        match tt.ty with
            StructT(_,_,ttt) | UnionT(_,_,ttt) -> None = ttt
          | _ -> true  
    else false  

 | _ -> false ;;          

(** Test if complete. *)
let is_complete t = not (is_incomplete t) ;;

(** *)
let ensure_complete t op = 
  if (is_error t) || (is_complete t) then t
  else let _ = show_error ("complete type required " ^ op) !curLoc
          in errort ;; 
          
(* get the type of the member names s form the structure *)
let rec get_member_type t s = match t.ty with 
  StructT(sn,_, ml) | UnionT(sn,_,ml) -> 
    begin match ml with
        None -> 
          let rt = lookup ("tag_" ^ sn) in
          begin match rt.ty with
              StructT(_,_, ml) | UnionT(_,_,ml) ->
                begin match ml with
                    None -> errort
                  | Some ll -> get_member_type_helper ll s   
                end 
            | _ -> errort  
          end    
      | Some l -> get_member_type_helper l s    
    end
| _ -> errort

and get_member_type_helper tl s = match tl with
    [] -> errort
  | x::xs -> 
      match x.ty with 
          MemberT(str, mt) -> 
            if (starts_with str "member(") && not(is_bitfield mt) then
              let me = get_member_type mt s in
                if not(is_error me) then me
                else get_member_type_helper xs s
            else if (str = s) then mt
             else get_member_type_helper xs s          
        | _ -> errort;;          
   
(* Check if 2 number (arithmetic) types are equals ignoring signedness.*)
let equal_ignore_signedness t1 t2 = match t1.ty with
    CharT | UCharT | SCharT -> is_char t2
  | ShortT | UShortT -> begin match t2.ty with
                            ShortT | UShortT -> true
                          | _ -> false
                        end  
  | UIntT | IntT -> begin match t2.ty with
                        UIntT | IntT -> true
                      | _ -> false
                    end        
  | ULongT | LongT -> begin match t2.ty with
                          LongT | ULongT -> true
                        | _ -> false
                      end 
  | ULongLongT | LongLongT -> begin match t2.ty with
                                  LongLongT | ULongLongT -> true
                                | _ -> false
                              end
  | _ -> t1.ty = t2.ty ;;           
  
(* Compose 2 types. *)
let rec compose t1 t2 = if (type_equals t1 t2) then t1 else
  match t1.ty,t2.ty with
      (ArrayT(b1,s1), ArrayT(b2,s2)) ->
         let ret = compose b1 b2 in
           if not (is_error ret) then
             begin match s1,s2 with
                 (VarLength _ , VarLength _)  -> t1
               | (Fixed(i1), Fixed(i2))  -> if (i1 = i2) then t1
                                            else errort
               | (Fixed _, _ )  -> t1
               | (_, Fixed _) -> t2
               | _ -> t1
             end
           else errort  
    | _ -> t1 ;;
    
(* Pointerize a type.*)
let rec pointerize t = match t.ty with
    ArrayT _ -> let tt = PointerT(get_base t) in make_type2 tt
  | FunctionT _ -> let tt = PointerT(t) in make_type2 tt 
  | MemberT(_,ty) -> pointerize ty
  | _ -> t ;;
           
(* Promote type.*)
let promote t = match t.ty with
    CharT | UCharT | SCharT | ShortT | UShortT | IntT -> 
      make_type2 UIntT
  | _ -> t ;;   
                                                                                                                                
(* promote integer types to a common type *)
let convert_int t1 t2 = match t1.ty, t2.ty with
   (ULongLongT,_) | (_,ULongLongT) -> make_type2 ULongLongT
 | (LongLongT,_)  | (_,LongLongT)  -> make_type2 LongLongT
 | (ULongT,_)     | (_,ULongT)     -> make_type2 LongT
 | (LongT,_)      | (_,LongT)      -> make_type2 ULongT
 | (UIntT,_)      | (_,UIntT)      -> make_type2 UIntT
 | (IntT,_)       | (_,IntT)       -> make_type2 IntT
 | (UShortT,_)    | (_,UShortT)    -> make_type2 UShortT
 | (ShortT,_)     | (_,ShortT)     -> make_type2 ShortT
 | (CharT,_)      | (_,CharT)      -> make_type2 CharT
 | _                               -> make_type2 IntT ;;
 
(* convert arithmetic operands to common type *) 
let arith_convert t1 t2 = 
  let r1 = ensure_arithmetic t1 "arithmetic convert" in 
  let r2 = ensure_arithmetic t2 "arithmetic convert" in
    if (is_error r1) || (is_error r2) then errort
    else if (LongDoubleT = t1.ty) || (LongDoubleT = t2.ty) then
      make_type2 LongDoubleT
    else if (DoubleT =  t1.ty) || (DoubleT = t2.ty) then
      make_type2 DoubleT
    else if (FloatT = t1.ty) || (FloatT = t2.ty) then
      make_type2 FloatT
    else if (is_integer t1 && is_integer t2) then (convert_int t1 t2) 
    else if (type_equals t1 t2) then t1
    else t1 ;;
    
(* Resolve a type. *)
let rec resolve t = match t.ty with
    MemberT(_,ty) -> resolve ty
  | _ -> t ;;
  
(* Get size of type. *)
let rec get_size t = match t.ty with
    ArrayT(ty,si) -> 
     let s1 = begin match si with
                  Fixed(ret) -> ret
                | Incomplete -> 0
                | _ -> 0  
              end in
     let s2 = get_size ty in
     if (0 = s1 || 0 = s2) then 0
     else s1 * s2                           
  | StructT(_,_,tl) -> begin match tl with
                               None -> 0
                             | Some ll -> List.length ll
                           end  
  | UnionT _ -> 1
  | _ -> 1 ;;

(** Kludge... surely there must be a better way to do this? *)
let rec flattenListTypes l = match l with
  [] -> []
| x::xs -> List.append x (flattenListTypes xs) ;;   

(**)
let show_binding name t =
  print_endline (name ^ ": ") ;
  print_endline "*****" ;;
  
(* Get offset.*)
let rec get_offset base name = match base.ty with
    StructT(_,_,tl) -> 
      let ll = match tl with
                   Some res -> res
                 | _ -> [] in                   
      let res = layout 0 (is_packed base) (has_trailing_array base)
                                       0 0 1 name ll
                         in res                
  | UnionT(_,_,tl) -> 
      let ll = match tl with
                   Some res -> res
                 | _ -> [] in
        get_offset_members name ll
                                             
  | _ -> -1   
  
(**)
and get_offset_members name tl = match tl with
    [] -> -1
  | x::xs -> match x.ty with
                 MemberT(me,ty) ->
                   if (me = name) then 0
                   else 
                     if not(is_bitfield ty) && 
                           (starts_with me "member(") then
                       let off = get_offset ty name in
                         if (off >= 0) then off
                         else get_offset_members name xs
                     else get_offset_members name xs      
               | _ -> -1 ;
               
  
(* Get size of a structure. *)
and layout res packed has_trailing bit_count bit_size bit_align name
               tl = match tl with
    [] -> if ("" = name) then 
             begin
             res
             end
          else -1
  | x::xs -> 
    if (has_trailing) && (1 = List.length tl) then res
    else
      let is_last = (has_trailing && (2 = List.length tl)) ||
                    (1 = List.length tl) and
          al = if (packed) then 1
               else get_alignment x false in
      match x.ty with
          MemberT(me,ty) -> 
            begin match ty.ty with
                BitfieldT(_,width) ->
                  if (packed) then
                    let new_bit_count = bit_count + width in
                      begin 
                        if (is_last) || not(is_bitfield (List.hd xs)) then
                        let moo = new_bit_count / MD.char_bits in
                        let new_res = if (0 != new_bit_count mod MD.char_bits)
                                        then res + moo + 1
                                      else res + moo in
                          layout new_res packed has_trailing 0 0 1 name xs
                        else layout res packed has_trailing new_bit_count
                                    bit_size bit_align name xs                 
                      end  
                  else 
                    begin
                    if (0 = width) then
                      begin
                        let new_res = if (0 != bit_count mod MD.char_bits) then
                                        res + (bit_count / MD.char_bits) + 1
                                      else res + (bit_count / MD.char_bits) 
                        in let moo = new_res mod al in
                           let new_new_res = if (0 != moo) then
                                               new_res + al - moo
                                             else new_res in
                        layout new_new_res packed has_trailing 0 0 1 name xs                                      
                      end
                    else if (0 = bit_size) then
                      begin
                        let new_bit_count = width and
                            new_bit_size = sizeof x and
                            new_bit_align = al in
                          if (is_last) || not(is_bitfield (List.hd xs)) then
                            let moo = new_bit_count / MD.char_bits in
                            let new_res = 
                                 if (0 != new_bit_count mod MD.char_bits) then
                                    res + moo + 1
                                 else res + moo in
                              layout new_res packed has_trailing 0 0 1 name xs      
                          else layout res packed has_trailing new_bit_count
                                      new_bit_size new_bit_align name xs
                      end
                    else if (bit_count + width <= bit_size * MD.char_bits) 
                      then
                      begin
                        let new_bit_count = bit_count + width in
                        if (is_last) || not(is_bitfield (List.hd xs)) then
                          let moo = new_bit_count / MD.char_bits in
                          let new_res = 
                               if (0 != new_bit_count mod MD.char_bits) then
                                 res + moo + 1
                               else res + moo in
                            layout new_res packed has_trailing 0 0 1 name xs      
                        else layout res packed has_trailing new_bit_count
                                    bit_size bit_align name xs
                      end
                    else 
                      begin
                        let res1 = res + bit_size in
                        let moo = res1 mod bit_align in
                        let new_res = if (0 != moo) then 
                                        res1 + bit_align - moo
                                      else res1 and
                            new_bit_count = width and
                            new_bit_size = sizeof x and
                            new_bit_align = al in
                          if (is_last) || not(is_bitfield (List.hd xs)) then
                            let moo = new_bit_count / MD.char_bits in
                            let new_new_res = 
                                 if (0 != new_bit_count mod MD.char_bits) then
                                   new_res + moo + 1
                                 else new_res + moo in
                              layout new_new_res packed has_trailing 
                                     0 0 1 name xs      
                          else layout new_res packed has_trailing 
                                new_bit_count new_bit_size new_bit_align name xs
                            
                      end
                    end
              | _ -> 
                let moo = res mod al in
                let new_res = if (0 != moo) then res + al - moo
                              else res in
                  if not("" = name) then
                    if (me = name) then new_res
                    else 
                      if (starts_with me "member(") then
                        let off = get_offset ty name in
                        if (off > (0 - 1)) then (new_res + off)
                        else layout (new_res + (sizeof x)) packed has_trailing 
                                    0 0 1 name xs 
                      else layout (new_res + (sizeof x)) packed has_trailing 
                                    0 0 1 name xs                 
                  else
                    begin
                      
                   layout (new_res  + (sizeof x)) packed has_trailing 
                              0 0 1 name xs 
                    end           
            end
        | _ -> -1
        
(* Get size of some arithmetic types, in bytes. *)
and sizeof t = match t.ty with
    VoidT -> MD.void_size 
  | BoolT -> MD.bool_size  
  | CharT | UCharT | SCharT -> 1
  | ShortT | UShortT -> MD.short_size
  | UIntT | IntT -> MD.int_size
  | ULongT | LongT -> MD.long_size
  | ULongLongT | LongLongT -> MD.long_long_size
  | LongDoubleT -> MD.long_double_size
  | PointerT _ -> MD.pointer_size
  | DoubleT -> MD.double_size
  | FloatT -> MD.float_size
  | FloatComplexT -> 2 * MD.float_size
  | DoubleComplexT -> 2 * MD.double_size
  | LongDoubleComplexT -> 2 * MD.long_double_size
  | ArrayT(ty,si) -> begin match si with
                         Fixed(i) -> i * (sizeof ty)
                       | _ -> 0
                     end   
  | UnionT(_,_,tl) -> 
      let ll = match tl with
                   Some res -> res
                 | _ -> [] in  
      let ret = get_max_size 0 ll in
        ret
  | StructT(_,_,tl) -> 
      let ll = match tl with
                   Some res -> res
                 | _ -> [] in
      let si = layout 0 (is_packed t) (has_trailing_array t)
                      0 0 1 "" ll and
          al = get_aligned t in
      let max = if (al > 0) then al
                else 1 in
      let max_al = if (is_packed t) then max
                   else get_max_alignment max ll in
      let moo = si mod max_al in
        if (0 != moo) then si + max_al - moo
        else si                                      
                         
  | FunctionT _ -> MD.function_size
  | BitfieldT(ty,_) -> sizeof ty
  | MemberT(_,ty) -> sizeof ty
  | EnumT _ -> MD.int_size  
  | _ -> let _ = show_error "unmatch type" !curLoc in 0
  
(* Get max size. *)
and get_max_size max tl = match tl with
    [] -> max
  | x::xs -> match x.ty with
                  MemberT(_,ty) -> 
                    let si = sizeof ty in
                    let new_max = if (si > max) then si
                                  else max in
                      get_max_size new_max xs  
               | _ -> max ;;            
  
(******************************************************************************
 *                      Analyzing helper functions and values                 *
 ******************************************************************************)
let in_function: bool ref = ref false ;;

let in_def: bool ref = ref false ;;

let is_top: bool ref = ref true ;;

let in_initializer: bool ref = ref false ;;

let in_addr: bool ref = ref false ;;

let in_structure: bool ref = ref false ;;

let in_loop: bool list ref = ref [] ;;

let is_in_loop () = (List.length !in_loop) > 0 ;;  

let in_switch: bool list ref = ref [] ;;

let is_in_switch () = (List.length !in_switch) > 0 ;;  

let in_para_decl: bool ref = ref false ;;

let currentReturnType: typ ref = ref errort ;;

let var_functions : (string, bool) H.t = H.create 307 ;;

let is_var_function name = H.mem var_functions name ;;

let mark_var_function name = H.add var_functions name true ;;

let old_functions : (string, bool) H.t = H.create 307 ;;

let is_old_function name = H.mem old_functions name ;;

let mark_old_function name = H.add old_functions name true ;;

let label_locations : (string, cabsloc) H.t = H.create 307 ;;

let add_location name loc = H.add label_locations name loc ;;

let get_location name = if H.mem label_locations name then
                           H.find label_locations name
                        else !curLoc ;;   

(* Prepare everything before start. *)
let start_file () = 
  H.clear env;
  H.clear genv;
  scopes := [];
  currentScope := {scopename = "global"; id_list = []};
  justExitScope := {scopename = "global"; id_list = []}; 
  in_function := false ;
  in_def := false ;
  is_top := true ;
  in_initializer := false ;
  in_addr := false ; 
  in_structure := false ;
  in_loop := [] ;
  in_switch := [] ;
  in_para_decl := false ;
  currentReturnType := errort ;
  H.clear var_functions ;
  H.clear old_functions ;
  label_locations = H.create 307 

     
(* Get id from the type name. *) 
let get_id (n: name) = let s,_,_,_ = n in s ;;

(** Define function **)
let c_define n t = 
  if not (is_defined n) then let _ = define n t in t
  else 
   (* let gva = lookup_global n in *)
    let va = lookup n in
   (* if (is_extern t) && (is_static gva) && (List.length !scopes != 1) then
      let pos = begin match gva.loc with
                    None -> !curLoc
                  | Some res -> res
                end in    
      let _ = show_error 
        "variable previously declared 'static' redeclared 'extern'" !curLoc in
      let _ = show_error ("previous definition of '" ^ n ^ "' was here") pos                
        in errort  
    else *)     
    match va.storage with
        Some ExternS | Some StaticS ->
          if (is_extern t) then 
            let ret = compose va t in              
              if not (is_error ret) then 
                let _ = redefine n ret in ret
              else errort                   
          else let _ = define n t in t
      | _ ->
        if (is_defined_locally n) then 
          let ty = lookup_locally n in
          let pos = match ty.loc with
                        None -> !curLoc 
                      | Some lo -> lo in
            if not (is_function ty) && (is_complete ty) && 
               not (is_at_global ()) then
             let _ = if (is_struct_union ty) then
                       let _ = show_error ("redefinition of '" ^ n) !curLoc
                         in errort
                     else 
                       let _ = show_error ("redeclaration of '" ^ n) !curLoc
                         in 
                       let _ = show_error ("previous declaration of '" ^ n ^
                               "' was here") pos in errort 
                in errort
            else let _ = define n t in t  
        else let _ = define n t in t ;;
        
(* Process assignments. *)
let processAssignment init t1 op t2 = 
  let ret = if (init) && (is_const t1) then
              {t1 with value = t2.value}
            else t1 in
  let r1 = pointerize t1 in      
  let r2 = pointerize t2 in  
  if (is_error t1) || (is_error t2) then errort
  else if (is_arithmetic r1 && is_arithmetic r2) then ret 
  else if (is_void r2) && not(starts_with op "return") then
    let _ = show_error ("void value not ignored as it ought to be in " ^ op)
                       !curLoc in t1   
  else if (is_integer r1 && is_pointer r2) then
    if (is_char r1) then t1 
    else 
      let _ = show_warning ("makes integer from pointer without a cast in " ^ 
                             op) !curLoc in ret 
  else if (is_pointer r1 && is_integer r2) then
    let _ = if not(zero r2) && not(is_array t1) then
       show_warning ("makes pointer from integer without a cast in " ^ op)
                    !curLoc in ret
  else if (init && is_array t1 && is_array t2) then ret            
  else if (is_pointer r1  && is_pointer r2) then
    (*Check signedness here*)
    let b1 = get_base r1 in
    let b2 = get_base r2 in
    if (is_error b1) || (is_error b2) then t1
   (* if (is_struct_union b1) && (is_incomplete b1) then
      let _ = show_warning ("incompatible pointer types in " ^ op) !curLoc in
         t1 *)
    else if (is_void b1) || (is_void b2) || (pointer_equals r1 r2) || 
            (is_arithmetic b1 && is_arithmetic t2) then 
      let _ = if not(is_string t2) && not(has_qualifiers b1 b2) then
          show_warning (op ^ " discards qualifiers from pointer target type")
                       !curLoc 
        in t1
    else if (is_arithmetic b1) && (is_arithmetic b2) &&
             (equal_ignore_signedness b1 b2) then
      let _ = show_warning ("pointer targets in " ^ op ^ " differ in signedness"
                            ) !curLoc in t1 
    else let _ = show_warning ("incompatible pointer types in " ^ op) !curLoc
           in t1              
  (*else if (is_struct_union r1)  then t1*)
  else if (type_equals t1 t2) then t1
  else 
   begin
   let _ = show_error ("incompatible types in " ^ op) !curLoc in t1 
   end ;;
  
(* Process indirection expression. *)
let process_indirection t = let ret = get_base t in  
  if (is_error t) then errort  
  else if not(is_pointer t) then
    let _ = show_error "operand to 'unary *' not a pointer type" !curLoc
      in errort
  else if (is_void ret) then
    let _ = show_warning "dereferencing 'void *' pointer" !curLoc in ret
  else if (is_incomplete ret) && not(is_void ret) then
    let _ = show_error "dereferencing pointer to incomplete type" !curLoc 
      in errort
  else ret ;;  
  
(* Process subscript expression.*)
let process_subscript t = let ret = get_base t in 
  if (is_error t) then errort  
  else if not(is_array t || is_pointer t) then 
    let _ = show_error "pointer or array required in subscript expression"
            !curLoc in errort
  else if (is_incomplete ret) then
    let _ = show_error "dereferencing pointer to incomplete type" !curLoc
      in errort
  else ret ;; 
  
(* Get a member type by index.*)
let get_index_type t index = match t.ty with
    ArrayT _ -> get_base t
  | StructT(_,_,tls) -> 
      begin match tls with
          Some tl -> if (index >= List.length tl) then voidt 
                     else let member = List.nth tl index in
                            resolve member
        | None -> errort
      end
  | UnionT(_,_,tls) -> 
      begin match tls with
          Some tl -> if (index >= List.length tl) then voidt 
                     else let member = List.nth tl index in
                            resolve member
        | None -> errort
      end                                        
  | _ -> t ;;  
  
(******************************************************************************
 *                           Analyzing functions                              *
 ******************************************************************************)
(* Collect specifiers. *)
let collect_specifier (specs: specifier) =
  let type_specs : typeSpecifier list ref = ref [] in
  let store_specs: storage list ref = ref [] in
  let qual_specs : cvspec list ref = ref [] in
  let func_specs : funspec list ref = ref [] in
  let typedef_specs : specifier ref = ref [] in
  let atts: attribute list ref = ref [] in
  let visit_spec sp = match sp with 
      SpecTypedef -> typedef_specs := sp :: !typedef_specs          
    | SpecCV(cv) -> qual_specs := cv :: !qual_specs            
    | SpecStorage(st) -> store_specs := st :: !store_specs
    | SpecInline -> func_specs := INLINE :: !func_specs
    | SpecType(ts) -> type_specs := ts :: !type_specs
    | SpecAttr(at) -> atts := at :: !atts
    | _ -> () in
  let _ = List.map visit_spec specs in
  !type_specs,!store_specs,!qual_specs,!func_specs,!typedef_specs,!atts

(* Sort the type specifiers. *)
let sort_specs tspecs = 
    let order = function (* Don't change this *)
      | Tvoid -> 0
      | Tsigned -> 1
      | Tunsigned -> 2
      | Tchar -> 3
      | Tshort -> 4
      | Tlong -> 5
      | Tint -> 6
      | Tfloat -> 8
      | Tdouble -> 9
      | Tbool -> 10
      | _ -> 11 (* There should be at most one of the others *)
    in
    List.stable_sort (fun ts1 ts2 -> compare (order ts1) (order ts2)) tspecs

(* Extract storage specifiers. *)
let extract_storage store_specs typedef_specs =
  if (List.length typedef_specs > 0) then Some TypedefS
  else match store_specs with
      [AUTO] -> Some AutoS
    | [STATIC] -> Some StaticS
    | [EXTERN] -> Some ExternS 
    | [REGISTER] -> Some RegisterS
    | _ -> None     
 
(* Extract qualifier specifiers. *)       
let extract_qualifier qual_specs = 
  let res : qualifier list ref = ref [] in
  let _ = if (List.mem CV_CONST qual_specs) then
            res := ConstQ :: !res in
  let _ = if (List.mem CV_VOLATILE qual_specs) then
            res := VolatileQ :: !res in          
  let _ = if (List.mem CV_RESTRICT qual_specs) then
            res := RestrictQ :: !res in
    !res

(* Extract function specifier. *)  
let extract_func_specifier specs = match specs with
    [INLINE] -> Some InlineF
  | _ -> None
  
  
(******************************************************************************
 *                        Process labels here                                 *
 ******************************************************************************)
(* Check is a label is used. *)  
let rec is_used s st = match st with
    BLOCK(bl,_) -> 
    let stl = bl.bstmts in
    let f = is_used s in
      List.exists f stl
  | RETURN(e,_) | COMPUTATION(e,_) | COMPGOTO(e,_) -> is_used_expr s e    
  | SEQUENCE(st1,st2,_) -> is_used s st1 || is_used s st2
  | IF(e,st1,st2,_) -> is_used_expr s e || is_used s st1 || is_used s st2
  | FOR(fc,e1,e2,st,_) -> 
      let b = begin match fc with
                  FC_EXP(e) -> is_used_expr s e
                | FC_DECL(def) -> is_used_def s def
              end in
       b || is_used_expr s e1 || is_used_expr s e2 || is_used s st
  | WHILE(e,st,_) | DOWHILE(e,st,_) | SWITCH(e,st,_) | CASE(e,st,_) -> 
     is_used_expr s e || is_used s st 
  | CASERANGE(e1,e2,st,_) -> 
     is_used_expr s e1 || is_used_expr s e2 || is_used s st
  | DEFAULT(st,_) | LABEL(_,st,_) -> is_used s st
  | GOTO(str,_) -> str = s
  | DEFINITION(def) -> is_used_def s def
  | _ -> false
  
  
(* Check used in definition (initializer).*)  
and is_used_def s d = match d with
   | DECDEF((_,initl),_) -> let f = is_used_init_name s in
                              List.exists f initl
   | _ -> false

(* Check used in a init name.*)
and is_used_init_name s initn = 
  let _,inite = initn in
    is_used_init_expr s inite

(* Check used in an expr*)    
and is_used_expr s e = match e with
    LABELADDR(str) -> str = s
    
  | UNARY(_,e) | PAREN(e) | EXPR_SIZEOF(e) | EXPR_ALIGNOF(e) | MEMBEROF(e,_) |
    MEMBEROFPTR(e,_) -> 
      is_used_expr s e
  
  | BINARY(_,e1,e2) | INDEX(e1,e2) -> is_used_expr s e1 || is_used_expr s e2
  
  | QUESTION(e1,e2,e3) -> 
      is_used_expr s e1 || is_used_expr s e2 || is_used_expr s e3
      
  | CALL(e,el) -> let f = is_used_expr s in
                  is_used_expr s e || List.exists f el
                  
  | COMMA(el) -> let f = is_used_expr s in
                   List.exists f el
                   
  | CAST(_,inite) -> is_used_init_expr s inite
  
  | GNU_BODY(bl) -> is_used s (BLOCK(bl,!curLoc))
  
  | _ -> false
  
(* Check if used in an init expression.*)
and is_used_init_expr s init = match init with
  | NO_INIT -> false
  | SINGLE_INIT(e) -> is_used_expr s e
  | COMPOUND_INIT(enl) -> let f = is_used_entry s in
                          List.exists f enl
  
(* Check if used in an init entry*)
and is_used_entry s en = 
  let _,inite = en in
    is_used_init_expr s inite    
                    
(* Check for defined labels. *)
let rec check_defined_labels sl st = match sl with
    [] -> voidt
  | x::xs -> let pos = get_location x in  
             if not(is_used x st) then
               show_warning ("label '" ^ x ^ "' defined but not used") pos;
             check_defined_labels xs st 
   
 
let rec define_local_labels sl bv = match sl with
    [] -> ()
  | x::xs -> let t = make_type (LabelT(x)) [] None None None None
                                (Some bv) None false [] in  
             let _ = define ("label_" ^ x) t in
               define_local_labels xs bv

                                             
let rec check_declared_labels loc st sl = match sl with
    [] -> ()
  | x::xs ->
      let t = lookup ("label_" ^ x) in
      let _ = if not(t.initialised = Some true) then
                show_warning ("'" ^ x ^ " declared but not defined") loc
              else if not(is_used x st) then 
                 let pos = get_location x in
                   show_warning ("'" ^ x ^ " defined but not used") pos                
       in check_declared_labels loc st xs                           
                  
 (* t qual store fspec valu imp init pos *)
let rec find_labels res st = match st with
    NOP _ -> res
  | BLOCK(bl,loc) ->
     curLoc := loc;
     (* Enter a new scope.*)
     enter_scope (fresh_name "block");
     let stl = bl.bstmts in
     let local_labels = bl.blabels in
     (* Define all local labels.*)
     define_local_labels local_labels false; 
     let f = find_labels [] in
     let labels_list = List.map f stl in
     let labels = if (List.length stl = 0) then []
                  else union_flat (List.hd labels_list) (List.tl labels_list) in
     let new_labels = substract local_labels labels [] in
       check_declared_labels loc st local_labels;
       exit_scope true;
       union res new_labels 
  
  | COMPUTATION(e,loc) -> 
    curLoc := loc;
    union res (find_labels_expr [] e)          
  
  | IF(_,st1,st2,loc) | SEQUENCE(st1,st2,loc) ->
     curLoc := loc;
     let sl1 = find_labels [] st1 in
     let sl2 = find_labels [] st2 in
       union res (union sl1 sl2)
      
  | WHILE(_,st,loc) | DOWHILE(_,st,loc) | FOR(_,_,_,st,loc) 
  | SWITCH(_,st,loc) | CASE(_,st,loc) ->
      curLoc := loc;
      union res (find_labels [] st)
      
  | LABEL(str,st,loc) ->
     curLoc := loc;
     let sl = find_labels [] st in
     let ret = union res sl in 
     if not (is_defined ("label_" ^ str)) then 
       let _ = define ("label_" ^ str)
                 (make_type (LabelT(str)) [] None None None None
                                (Some true) None false []) in    
         add_location str loc;                       
         union ret [str]
     else 
       let t = lookup ("label_" ^ str) in 
       if not(t.initialised = Some true) then
         let _ = redefine ("label_" ^ str) (make_type 
                    (LabelT(str)) [] None None None
                     None (Some true) (Some loc) false []) in
           add_location str loc;                                              
           union ret [str]                              
       else 
         let pos = get_location str in
         let _ = show_error ("duplicate label '" ^ str ^ "'") loc in
         let _ = show_error ("previous definition of '" ^ str ^ "' was here")
                             pos in ret
       
  | _ -> res   
     
(* Find labels in expression.*)  
and find_labels_expr res e = match e with   
    GNU_BODY(bl) ->
      let sl = find_labels [] (BLOCK(bl,!curLoc)) in
                      union res sl
  | QUESTION(_,e1,e2) ->
      let sl1 = find_labels_expr [] e1 in
      let sl2 = find_labels_expr [] e2 in
        union res (union sl1 sl2)  
  | _ -> res
  

(* Extract type specifiers. *)    
let rec extract_type specs = match specs with
    [Tvoid] -> voidt
  | [Tbool] -> make_type2 BoolT  
  | [Tchar] -> make_type2 CharT
  | [Tsigned; Tchar] -> make_type2 SCharT
  | [Tunsigned; Tchar] -> make_type2 UCharT

  | [Tshort] -> make_type2 ShortT
  | [Tsigned; Tshort] -> make_type2 ShortT
  | [Tshort; Tint] -> make_type2 ShortT
  | [Tsigned; Tshort; Tint] -> make_type2 ShortT

  | [Tunsigned; Tshort] -> make_type2 UShortT
  | [Tunsigned; Tshort; Tint] -> make_type2 UShortT

  | [] -> make_type2 IntT
  | [Tint] -> make_type2 IntT
  | [Tsigned] -> make_type2 IntT
  | [Tsigned; Tint] -> make_type2 IntT

  | [Tunsigned] -> make_type2 UIntT
  | [Tunsigned; Tint] -> make_type2 UIntT

  | [Tlong] -> make_type2 LongT
  | [Tsigned; Tlong] -> make_type2 LongT
  | [Tlong; Tint] -> make_type2 LongT
  | [Tsigned; Tlong; Tint] -> make_type2 LongT

  | [Tunsigned; Tlong] -> make_type2 ULongT
  | [Tunsigned; Tlong; Tint] -> make_type2 ULongT

  | [Tlong; Tlong] -> make_type2 LongLongT
  | [Tsigned; Tlong; Tlong] -> make_type2 LongLongT
  | [Tlong; Tlong; Tint] -> make_type2 LongLongT
  | [Tsigned; Tlong; Tlong; Tint] -> make_type2 LongLongT

  | [Tunsigned; Tlong; Tlong] -> make_type2 ULongLongT
  | [Tunsigned; Tlong; Tlong; Tint] -> make_type2 ULongLongT

  | [Tfloat] -> make_type2 FloatT
  | [Tdouble] -> make_type2 DoubleT

  | [Tlong; Tdouble] -> make_type2 LongDoubleT
  
  (* ADD HERE*)
  | [Tnamed(str)] ->
      if ("__builtin_va_list" = str) then
        (make_type2 VarArgT)
      else if not(is_defined str) then
        let _ = show_error ("undeclared typedef name " ^ str) !curLoc in errort
      else lookup str 
      
  | [TtypeofE(e)] -> analyze_expression e
  
  | [TtypeofT(specs,dec)] -> analyze_type_expr (specs,dec)
  
  | [Tstruct(str,fdls,atts)] -> 
    begin match fdls with
        None -> 
          if not(is_defined ("tag_" ^ str)) then
            let non = nonce () in
            let rt = make_type2 (StructT(str,non, None)) in 
            if (!in_para_decl) then
              show_warning "struct declared inside parameter list" !curLoc;
            define ("tag_" ^ str) rt;
            rt            
          else lookup ("tag_" ^ str)
      | Some fdl ->
         in_def := true;
         let name = if (String.length str = 0) then (fresh_name "struct")
                    else str in
         if (!in_structure) && (is_defined ("tag_" ^ name)) then
           let _ = show_error ("redefinition of " ^ name) !curLoc in errort
         else             
         let non = if not(is_defined ("tag_" ^ str)) then nonce ()
                   else let told = lookup ("tag_" ^ str) in
                     begin match told.ty with 
                          StructT(_,no,_) -> no
                       | _ -> -1   
                     end in           
           let _ = if (String.length str = 0) && (!in_para_decl) then
             show_warning "anonymous struct declared inside parameter list"
                          !curLoc in ();
           let attl = extract_attributes [] atts in               
           let _ = if not(is_defined ("tag_" ^ name)) then
             let rt = make_type2 (StructT(name, non, None)) in
             let rt2 = update_attributes rt attl in
               let _ = define ("tag_" ^ name) 
                              (update_loc rt2 (Some (!curLoc))) in ()
               in ();
           let tll = List.map analyze_field_group fdl in
           in_def := false;
           let tl = flattenListTypes tll in
           let res = make_type2 (StructT(name,non,Some tl)) in
           let res2 = update_attributes res attl in
           let prev = lookup ("tag_" ^ name) in
             if (is_incomplete prev) then
               begin
               let _ = redefine ("tag_" ^ name) res2 in res2
               end
             else 
               c_define ("tag_" ^ name) (update_loc res2 (Some(!curLoc)))            
    end
    
  | [Tunion(str,fdls,atts)] -> 
    begin match fdls with
        None -> 
          if not(is_defined ("tag_" ^ str)) then
            let non = nonce () in
            let rt = make_type2 (UnionT(str,non, None)) in 
            if (!in_para_decl) then
              show_warning "struct declared inside parameter list" !curLoc;
            define ("tag_" ^ str) rt;
            rt            
          else lookup ("tag_" ^ str)
      | Some fdl -> 
         in_def := true;
         let name = if ("" = str) then (fresh_name "union")
                    else str in
         if (!in_structure) && (is_defined ("tag_" ^ name)) then
           let _ = show_error ("redefinition of " ^ name) !curLoc in errort
         else                 
         let non = if not(is_defined ("tag_" ^ str)) then nonce ()
                   else let told = lookup ("tag_" ^ str) in
                     begin match told.ty with 
                          StructT(_,no,_) -> no
                       | _ -> -1   
                     end in                  
           let _ = if ("" = str) && (!in_para_decl) then
             show_warning "anonymous union declared inside parameter list"
                          !curLoc in ();
           let attl = extract_attributes [] atts in               
           let _ = if not(is_defined ("tag_" ^ name)) then
             let rt = make_type2 (UnionT(name, non,None)) in
             let rt2 = update_attributes rt attl in
               let _ = c_define ("tag_" ^ name) 
                              (update_loc rt2 (Some(!curLoc))) in ()
               in ();
           let tll = List.map analyze_field_group fdl in
           in_def := false;
           let tl = flattenListTypes tll in
           let res = make_type2 (UnionT(name,non,Some tl)) in
           let res2 = update_attributes res attl in
           let prev = lookup ("tag_" ^ name) in
             if (is_incomplete prev) then
               let _ = redefine ("tag_" ^ name) res2 in res2
             else 
               c_define ("tag_" ^ name) (update_loc res2 (Some(!curLoc)))            
    end 
    
  | [Tenum(str,enls,atts)] ->
    begin match enls with
        None -> 
          if not (is_defined ("tag_" ^ str)) then 
            let non = nonce () in
            let rt = make_type2 (EnumT(str,non,None)) in
            let _ = define ("tag_" ^ str) rt
              in rt
          else let t = lookup ("tag_" ^ str) in 
            if not(is_enum t) then
              let _ = show_error ("'" ^ str ^ "' defined as wrong kind of tag")
                                 !curLoc in errort
            else t                      
        
      | Some enl -> 
          let tl = analyze_enum_list enl 0 in
          let non = nonce () in
          let name = if ("" = str) then (fresh_name "enum")
                     else str in
          let attl = extract_attributes [] atts in
          let rt = update_attributes (make_type2 (EnumT(name,non,Some tl))) attl
          in let _ = define ("tag_" ^ str) rt in rt
    end
          
  | _ -> 
    let _ = show_error "Invalid combination of type specifiers" !curLoc in
      errort 
      
(* Extract attributes. *)
and extract_attributes res atts = match atts with
    [] -> res
  | x::xs -> 
    let _,el = x in
    if (List.length el > 0) then
      let e = List.hd el in
      let xa = begin match e with
            VARIABLE("packed") -> 
              {att_name = "packed"; att_value = None}  
          | CALL(VARIABLE("aligned"),tl) -> 
              if (List.length tl = 0) || (List.length tl > 1) then
                let _ = show_error ("wrong number of arguments specified for" ^
                                   " 'aligned' attribute") !curLoc
                  in {att_name = "aligned"; att_value = None}
              else
                let t = analyze_expression (List.hd tl) in
                let _ = if not(is_integer t) || not(is_const t) then
                   show_error "requested alignment is not an integer constant" 
                              !curLoc in
                   {att_name = "aligned"; att_value = t.value}  
                                      
          | _ -> {att_name = ""; att_value = None} end in
        extract_attributes (xa::res) xs
    else extract_attributes res xs          
      
(******************************************************************************
 *                        Process initializer here                            *
 *****************************************************************************)
 
(* Show excess warning in initializer list.*)
and show_excess_error nl = match nl with
    [] -> voidt
  | x::xs -> 
      let _ = begin match x with
                  (_,COMPOUND_INIT _) -> 
                    let _ = show_error "extra brace group at end of initializer"
                              !curLoc in voidt
                 | _ -> voidt 
              end in
        show_excess_error xs   
              
(* Recursively process initializer. *) 
and process_initializer t init = 
  match init with
    COMPOUND_INIT(el) -> 
      if (List.length el = 0) then t
      else 
      let _ = process_init_list t 1 el  (*and
          _ = if (is_scalar t) && (0 = List.length nl) then
                error "empty scalar initializer" at n *) 
        in t
  | _ -> t
  
(* Process a list of init entries. *)
and process_init_list t index el = match el with
    [] -> voidt
  | _::xs -> let rt = process_init_entry t index el in
               if (is_void rt) then voidt
               else process_init_list t (index + 1) xs
               
(* Process an intializer entry. *)
and process_init_entry t index el = 
  let ds,init = List.hd el in
  let element = process_init_what t index el ds in
      if (is_error element) then errort
      else if (is_void element) then voidt
      else
        begin match init with
            COMPOUND_INIT _ -> process_initializer element init                          
          | SINGLE_INIT(e) ->
              let rt = analyze_expression e in
              if not(is_struct_union element) && (is_struct_union rt) then
                let new_rt = resolve_element rt 0 in
                  processAssignment true element "initializer" new_rt
              else     
              let new_element = if not(is_aggregate rt) then
                                  begin                                    
                                    resolve_element element (index - 1) 
                                  end
                                else element in                                
              processAssignment true new_element "initializer" rt
                                
          | _ -> voidt                        
        end
        
and resolve_element element index = 
  if (is_struct_union element) then
    resolve_element (get_index_type element index) 0
  else if (is_array element) then
    resolve_element (get_base element) 0
  else element     
        
and process_init_what t index el ds = 
  let new_base = if (is_struct_union t) && (1 = get_size t) &&
                    (is_flat_init_list el) then
                   get_index_type t 0 
                 else t in
  if (NEXT_INIT = ds) then
    let si = get_size(new_base) in 
    if (si > 0) && (index > si) then
      let _ = show_warning "excess elements in initializer" !curLoc in
      let _ = show_excess_error el in voidt
    else get_index_type new_base (index - 1)
  else               
    match ds with
        INFIELD_INIT(str,next_init) ->
          if (NEXT_INIT = next_init) then process_field_des t str
          else if not(is_struct_union t) then
            begin
            let _ = show_error "field name not in struct or union initializer" 
                      !curLoc in errort
            end          
            else let member = get_member_type t str in
              if (is_error member) then
                 let _ = show_error ("unknown field " ^ str) !curLoc in errort
              else process_init_what (resolve member) 1 el next_init          
        
      | ATINDEX_INIT(e,NEXT_INIT) -> 
          let element = get_index_type new_base (index - 1) in 
          if (process_array_des element e NOTHING) then element
          else errort
          
      | ATINDEXRANGE_INIT(e1,e2) -> 
          let element = get_index_type new_base (index - 1) in 
          if (process_array_des element e1 e2) then element
          else errort
          
      | _ -> let _ = show_error "Unknown designation" !curLoc in errort
      
      
(* Process array designator. *)
and process_array_des t n1 n2 = 
  let t1 = analyze_expression n1 and
      t2 = if (NOTHING != n2) then analyze_expression n2
           else voidt in
    if not(is_integer t1) || (not(is_void t2) && not(is_integer t2)) then
      let _ = show_error "array index in initializer not of integer type" 
                !curLoc in false
    else if  not(is_const t1) && (None = get_int t1) ||
             (not(is_void t2) && (is_const t2) && (None = get_int t2)) then
      begin
      let _ = show_error "nonconstant array index in initializer" !curLoc
        in false
      end  
    else 
      let si = get_int t1 in
       begin match si with
           None -> let _ = show_error "can not compute size of array" !curLoc
                     in false
         | Some i -> 
             if (0 > i) then
               let _ = show_error "negative array index in initializer" !curLoc
                 in false
             else true
       end        
     
 (* Process field designator. *)
and process_field_des base str = 
  if not(is_struct_union base) then
    begin
    let _ = show_error "field name not in struct or union initializer" 
              !curLoc in errort
    end          
  else let member = get_member_type base str in
    if (is_error member) then
      begin
      let _ = show_error ("unknown field " ^ str) !curLoc in errort
      end
    else member
    
 (* Check is a list of initializers is flat.*)
and is_flat_init_list nl = match nl with
     [] -> true
   | x::xs -> match x with
                  (_, COMPOUND_INIT _) -> false
                | _ -> is_flat_init_list xs      
 
(* Analyze initializer at top level. *)
and analyze_initializer t init = match init with
  | COMPOUND_INIT(el) -> 
    if (List.length el = 0) then t
    else if (is_array t && is_char (get_base t)) then
      let size = get_size t in
         if (List.length el > 1) && (size > 0) && (List.length el > size) then
           let _ = show_error "excess elements in char array initializer" 
                     !curLoc in errort
         else t  
    else process_initializer t init    
  | _ -> t 
      
(******************************************************************************
 *                            Other analyze functions                         *
 *****************************************************************************)      
(* Analyze enumerator list. *)
and analyze_enum_list enl va = match enl with
   []     -> []
 | x::xs  -> 
   let new_val = analyze_enum_item x va in
   let str,_,_ = x in
   let it = make_type2 IntT in
   let et = make_type2 (EnumeratorT(str,it,new_val - 1)) in 
     (et) :: (analyze_enum_list xs new_val)  

(*Analyze enum item. *)   
(* t qual store fspec valu imp init pos *)                           
(* enum_item = string * expression * cabsloc*)                     
and analyze_enum_item en va = 
  let str,exp,loc = en in 
  if (NOTHING = exp) then
    let rt = make_type IntT [ConstQ] None None (Some (IValue(va)))
                       None None None false [] in
    let _ = define str rt in (va + 1)                   
  else 
    let t = analyze_expression exp in 
    let _ = ensure_integer t "enumerator" in
    let _ = define str t in
    if (is_error t) then (va + 1)
    else  
    begin match (get_int t) with
        None -> 
          va + 1  
      | Some i -> i + 1  
    end    

(* analyze field group.*)
and analyze_field_group (fr: field_group) = 
  (*specifier * (name * expression option) list*)
  in_structure := true;
  let specs,nl = fr in
  let t = processSpecifier specs in
  let f = analyze_field_decl t in
  let ret = List.map f nl in
  in_structure := false;
    ret

(* analyze a field declaration. *)
(*string * decl_type * attribute list * cabsloc*)
and analyze_field_decl t field_decl = 
  let (str,decl,att,loc),expr = field_decl in
  let name = if ("___missing_field_name" = str) then (fresh_name "member")
             else str in
  curLoc:= loc;
  let rt = analyze_decl_type t decl str false in
    if not(is_array rt) && (is_incomplete rt) then
      begin
      let _ = show_error ("field " ^ str ^ " has incomplete type") loc in errort
      end
    else   
    begin match expr with
        None -> (*Normal field*)
          make_type2 (MemberT(name,rt)) 
      | Some(e) -> (*Bitfield type*)
          let ti = analyze_expression e in
          let si = get_int ti in
            begin match si with
                None ->
                 (*               
                  let _ = show_error "can not compute size of bitfield" !curLoc
                          in *)
                    let bt = make_type2 (BitfieldT(rt,8)) in
                      make_type2 (MemberT(name,bt))
              | Some i -> 
                  if not("___missing_field_name" = str) && (0 = i) then
                    show_error ("zero width for bit-field '" ^ str ^ "'") 
                               !curLoc
                  else if (0 > i) then
                    show_error ("negative width for bit-field '" ^ str ^ "'")
                               !curLoc;
                  let bt = make_type2 (BitfieldT(rt,i)) in
                      make_type2 (MemberT(name,bt))
            end    
    end
      
        
(* Process specifier, return the base type. *)      
and processSpecifier specs = 
  let tspecs,sspecs,qspecs,fspecs,tdspecs,atts = collect_specifier specs in
  let tspecs' = sort_specs tspecs in
  let ts = extract_type tspecs' in
  let s = extract_storage sspecs tdspecs in
  let q = extract_qualifier qspecs in
  let f = extract_func_specifier fspecs in
  let attl = extract_attributes [] atts in
     make_type ts.ty (List.append ts.qualifiers q) s f None None None None 
                     false (List.append ts.attributes attl)
     
(* Process additive expression. *)
and process_additive op e1 e2 =      
  let t1 = analyze_expression e1 in
  let t2 = analyze_expression e2 in
    if (is_error t1) || (is_error t2) then errort  
    else if (is_arithmetic t1 && is_arithmetic t2) then
      let va = if (is_integer t1) then
                  let va1 = get_int t1 in
                  let va2 = get_int t2 in
                  begin match va1,va2 with
                      Some i1, Some i2 -> 
                        if ("+" = op) then 
                          Some (IValue(i1 + i2))
                        else Some (IValue(i1 - i2))
                    | _ -> None  
                  end                    
                else 
                  let va1 = get_float t1 in
                  let va2 = get_float t2 in
                  begin match va1,va2 with
                      Some f1, Some f2 -> 
                        if ("+" = op) then 
                          Some (FValue(f1 +. f2))
                        else Some (FValue(f1 -. f2))
                    | _ -> None  
                  end in
      let quals = if (is_const t1) && (is_const t2) then [ConstQ]
                  else [] in                               
      let res = arith_convert t1 t2 in
        update_qualifier (update_value res va) quals
    else if ((is_pointer t1 || is_array t1) && not(is_void (get_base t1)) && 
            (is_incomplete (get_base t1))) then                
      begin
     
      let _ = show_error "arithmetic on pointer to an incomplete type" !curLoc
        in errort
      end  
    else if ((is_pointer t2 ||is_array t2) && not(is_void (get_base t2)) &&
            (is_incomplete (get_base t2))) then
      begin
      let _ = show_error "arithmetic on pointer to an incomplete type" !curLoc
        in errort
     end                
    else 
      let r1 = pointerize t1 in
      let r2 = pointerize t2 in
      begin match op with
          "+" -> 
            if (is_pointer r1 && is_integer r2) then t1
            else if (is_integer r1 && is_pointer r2) then t2
            else if (is_pointer r1 && is_pointer r2) && 
                    (pointer_equals t1 t2) then to_pointer t1
            else if (is_array t1 && is_arithmetic (get_base t1) &&
                     is_arithmetic t2) then t1
            else if (is_arithmetic t1 && is_array t2 &&
                     is_arithmetic (get_base t2)) then t2                 
            else 
              let _ = show_error ("invalid operand to 'binary " ^ op) !curLoc
                in errort
                
        | _  ->
            if (is_pointer r1 && is_pointer r2 && 
                pointer_equals r1 r2) then make_type2 IntT         
            else if (is_pointer r1 && is_integer r2) then t1
            else if (is_array t1 && is_arithmetic (get_base t1) &&
                     is_arithmetic t2) then t1
            else let _ = show_error ("invalid operand to 'binary " ^ op) !curLoc
                   in errort
      end
      
(* Process multiplicative expression.*)
and process_multi op e1 e2 =         
  let t1 = ensure_scalar(analyze_expression e1) 
             "multiplicative expression" in
  let t2 = ensure_scalar(analyze_expression e2) 
             "multiplicative expression" in 
    if (is_error t1) || (is_error t2) then errort
    else          
    let va = if (is_integer t1) then
               let va1 = get_int t1 in
               let va2 = get_int t2 in
                  begin match va1,va2 with
                      Some i1, Some i2 -> 
                        if ("*" = op) then 
                          Some (IValue(i1 * i2))
                        else 
                          if (0 = i2) then None
                          else Some (IValue(i1 / i2))
                    | _ -> None  
                  end      
             else
               let va1 = get_float t1 in
               let va2 = get_float t2 in
                  begin match va1,va2 with
                      Some f1, Some f2 -> 
                        if ("+" = op) then 
                          Some (FValue(f1 *. f2))
                        else 
                          if (0.0 = f2) then None
                          else Some (FValue(f1 /. f2))
                    | _ -> None  
                  end in                         
    let quals = if (is_const t1) && (is_const t2) then [ConstQ] 
                else [] in
    let res = arith_convert t1 t2 in            
    begin match op with  
        "%"  ->
          let tmod1 = ensure_integer t1 "modulo expression" in
          let tmod2 = ensure_integer t2 "modulo expression" in
            if (is_error tmod1) || (is_error tmod2) then errort
            else update_value (update_qualifier t1 quals) va
      | "/" -> if (is_const t2) && (zero t2) then
                let _ = show_warning "division by zero" !curLoc in 
                let _ = if (!is_top) && (!in_initializer) then
                          show_error "initializer is not constant" !curLoc
                  in res
               else update_value (update_qualifier res quals) va
      | "*" -> update_value (update_qualifier res quals) va
      | _ -> errort
    end 
    
(* Analyze a type expression.*)
and analyze_type_expr (t: specifier * decl_type) = 
  let specs,dec = t in
  let tt = processSpecifier specs in
  if (is_error tt) then errort
  else analyze_decl_type tt dec "" false   
  
(* Process cast expression. *)
and processCast t1 t2 = 
  if (BoolT = t1.ty) && not(is_scalar t2) then
    let _ = show_error "scalar required to cast to boolean" !curLoc in errort
  else
    let rt = update_value t1 (t2.value) in
      rt
  
(* Process arguments.*)
and processArguments (tl: typ list) (elt: typ list) is_var = 
  if (List.length elt = 0) then voidt
  else 
  let _ = if not(is_var) && (List.length tl > List.length elt) then
            show_error "too few arguments to function" !curLoc
          else if not(is_var) && (List.length tl < List.length elt) then
            show_error "too many arguments to function" !curLoc in
  processArgumentsHelper tl elt 1
  
and processArgumentsHelper (tl: typ list) (elt: typ list) index = 
  match tl with
    [] -> voidt
  | x::xs -> let op = "passing argument " ^ (string_of_int index) in
    if (List.exists is_error elt) || (List.length elt = 0) then voidt
    else let _ = processAssignment false x op (List.hd elt) in
           processArgumentsHelper xs (List.tl elt) (index + 1)                
    
(* Analyze expression. *)
and analyze_expression n = match n with 
    NOTHING -> voidt
  | UNARY(uop,e) ->             
      begin match uop with
          MINUS ->
          let t = ensure_arithmetic (analyze_expression e) 
                    "unary minus expression" in 
          if (is_float t) then
            let va = begin match (get_float t) with
                         None -> None
                       | Some f -> Some (FValue(-. f))  
                     end in
             update_value t va
          else if (is_integer t) then 
            let va = begin match (get_int t) with
                         None -> None
                       | Some i -> Some (IValue(- i))  
                     end in
             update_value t va
          else 
            let _ = show_error "invalid type in unary minus" !curLoc in errort
          
        | PLUS ->
          let t = ensure_arithmetic (analyze_expression e) 
                    "unary plus expression" in 
          if (is_float t) then
            let va = begin match (get_float t) with
                         None -> None
                       | Some f -> Some (FValue(abs_float f))  
                     end in
             update_value t va
          else if (is_integer t) then
            let va = begin match (get_int t) with
                         None -> None
                       | Some i -> Some (IValue(abs i))  
                     end in
              update_value t va            
          else 
            let _ = show_error "invalid type in unary plus" !curLoc in errort 
          
        | NOT -> 
          let t = ensure_scalar (pointerize (analyze_expression e)) 
                "logical expression" in
          let va = if (is_const t) then
                      if (zero t) then
                        Some (IValue(1))
                      else Some (IValue(0))  
                    else None in
          let con = if (is_const t) then [ConstQ]
                    else [] in                
          let rt = update_qualifier (make_type2 IntT) con
            in update_value rt va 
        
        | BNOT ->
          let t = ensure_integer (analyze_expression e) "bitwise expression" in 
            let va = begin match (get_int t) with
                         None -> None
                       | Some i -> Some (IValue(lnot i))  
                     end in
              update_value t va            
        
        | MEMOF ->          
          let t = pointerize (analyze_expression e) in process_indirection t 
          
        | ADDROF ->
          begin match e with
              UNARY(MEMOF,e1) ->                
                let t = analyze_expression e1 in
                let _ = process_indirection (pointerize t) in t            
            | INDEX(e1,e2) ->                
                let t = analyze_expression e1 in
                let _ = ensure_integer (analyze_expression e2) 
                          "subscript expression" in                
                let _ = process_subscript t in t                   
            | _ ->
              in_addr := true;
              let t = analyze_expression e in
                if (is_error t) then 
                 begin
                   in_addr := false;
                   errort
                 end
                else 
                  let rt = make_type2 (PointerT(make_type2 t.ty)) in
                  let rt2 = update_qualifier rt t.qualifiers in
                    in_addr := false;
                    update_value rt2 t.value
          end
        
        | PREINCR | PREDECR ->
          let t = ensure_scalar (analyze_expression e) 
                    "increment/decrement expression" in
          let _ = ensure_modifiable_lvalue t in
           if (is_pointer t || is_array t) then
             if (is_complete (get_base t)) || (is_void (get_base t)) then t
             else
               let _ = show_error "arithmetic on pointer to an incomplete type 3"
                                  !curLoc in errort 
           else t 
            
        | POSINCR ->
          let t = ensure_scalar (analyze_expression e) 
                    "increment/decrement expression" in
          let _ = ensure_modifiable_lvalue t in
            if (is_error t) then errort 
            else if (is_float t) then
              let va = begin match (get_float t) with
                           None -> None
                         | Some f -> Some (FValue(1.0 +. f))  
                       end in
                update_value t va
            else if (is_integer t) then
              let va = begin match (get_int t) with
                           None -> None
                         | Some i -> Some (IValue(1 + i))  
                       end in
                update_value t va
            else if (is_pointer t || is_array t) then
              if (is_complete (get_base t)) || (is_void (get_base t)) then t
              else 
                begin
                let _ = show_error "arithmetic on pointer to an incomplete type"
                                  !curLoc in errort
                end                  
            else 
              begin
              let _ = show_error "invalid type in post incr" !curLoc in errort
              end
          
        | POSDECR ->
          let t = ensure_scalar (analyze_expression e) 
                    "increment/decrement expression" in
          let _ = ensure_modifiable_lvalue t in 
            if (is_error t) then errort
            else if (is_float t) then
              let va = begin match (get_float t) with
                           None -> None
                         | Some f -> Some (FValue(1.0 -. f))  
                       end in
                update_value t va
            else if (is_integer t) then
              let va = begin match (get_int t) with
                           None -> None
                         | Some i -> Some (IValue(1 - i))  
                       end in
                update_value t va      
            else if (is_pointer t || is_array t) then
              if (is_complete (get_base t)) || (is_void (get_base t)) then t
              else 
                let _ = show_error "arithmetic on pointer to an incomplete type 5"
                                  !curLoc in errort
            else 
              let _ = show_error "invalid type in pos decr" !curLoc in errort
      end
      
  | LABELADDR(str) -> 
    if not(is_defined ("label_" ^ str)) then
      show_error "undefined label" !curLoc;
    make_type2 (PointerT(make_type2 VoidT))
        
  | BINARY(bop,e1,e2) -> 
    begin match bop with
      ADD -> process_additive "+" e1 e2
    | SUB -> process_additive "-" e1 e2
    
    | MUL -> process_multi "*" e1 e2
    | DIV -> process_multi "/" e1 e2
    | MOD -> process_multi "%" e1 e2
    
    | AND -> 
      let _ = ensure_scalar (pointerize (analyze_expression e1)) 
                "logical expression" in
      let _ = ensure_scalar (pointerize (analyze_expression e2)) 
                "logical expression" in
        make_type2 IntT
          
    | OR ->
      let _ = ensure_scalar (pointerize (analyze_expression e1)) 
                "logical expression" in 
      let _ = ensure_scalar (pointerize (analyze_expression e2))
                "logical expression" in
        make_type2 IntT
    
    | BAND ->
      let t1 = ensure_integer (analyze_expression e1) "bitwise expression" in
      let t2 = ensure_integer (analyze_expression e2) "bitwise expression" in
      if (is_error t1) || (is_error t2) then errort
      else
        let va = 
               let va1 = get_int t1 in
               let va2 = get_int t2 in
                  begin match va1,va2 with
                      Some i1, Some i2 -> Some (IValue(i1 land i2))
                    | _ -> None  
                  end in
          update_value t1 va             
         
    | BOR ->
      let t1 = ensure_integer (analyze_expression e1) "bitwise expression" in
      let t2 = ensure_integer (analyze_expression e2) "bitwise expression" in
      if (is_error t1) || (is_error t2) then errort
      else
        let va = 
               let va1 = get_int t1 in
               let va2 = get_int t2 in
                  begin match va1,va2 with
                      Some i1, Some i2 -> Some (IValue(i1 lor i2))
                    | _ -> None  
                  end in
          update_value t1 va
          
    | XOR ->
      let t1 = ensure_integer (analyze_expression e1) "bitwise expression" in
      let t2 = ensure_integer (analyze_expression e2) "bitwise expression" in
      if (is_error t1) || (is_error t2) then errort
      else
        let va = 
               let va1 = get_int t1 in
               let va2 = get_int t2 in
                  begin match va1,va2 with
                      Some i1, Some i2 -> Some (IValue(i1 lxor i2))
                    | _ -> None  
                  end in
          update_value t1 va     
    
    | SHL ->
      let t1 = ensure_integer (analyze_expression e1) "shift expression" in
      let t2 = ensure_integer (analyze_expression e2) "shift expression" in
      let somei = get_int t2 in
      let va = begin match somei with
                   None -> 0
                 | Some i -> i           
               end in
      let res = promote t1 in
      if (None != somei && 0 > va) then
        show_warning "left shift count is negative" !curLoc;
      if (None != somei && (sizeof res)*8 <= va) then
        show_warning "left shift count >= width of type" !curLoc;
      let va = 
               let va1 = get_int t1 in
               let va2 = get_int t2 in
                  begin match va1,va2 with
                      Some i1, Some i2 -> Some (IValue(i1 lsl i2))
                    | _ -> None  
                  end in
          update_value t1 va        
    
    | SHR ->
      let t1 = ensure_integer (analyze_expression e1) "shift expression" in
      let t2 = ensure_integer (analyze_expression e2) "shift expression" in
      let somei = get_int t2 in
      let va = begin match somei with
                   None -> 0
                 | Some i -> i           
               end in
      let res = promote t1 in
      if (None != somei && 0 > va) then
        show_warning "right shift count is negative" !curLoc;
      if (None != somei && (sizeof res)*8 <= va) then
        show_warning "right shift count >= width of type" !curLoc;
      let va = 
               let va1 = get_int t1 in
               let va2 = get_int t2 in
                  begin match va1,va2 with
                      Some i1, Some i2 -> Some (IValue(i1 lsr i2))
                    | _ -> None  
                  end in
          update_value t1 va
          
    | EQ | NE ->
      let t1 = pointerize(analyze_expression e1) in
      let t2 = pointerize (analyze_expression e2) in
      let op = if (EQ = bop) then "=="
               else "!=" in
        if (is_error t1) || (is_error t2) then errort   
        else 
          let va = begin if (is_const t1) && (is_const t2) then
                      if (value_equals t1 t2) && (op = "==") then 
                        Some (IValue(1))
                      else if (value_equals t1 t2) then Some (IValue(0))
                      else if (op = "!=") then Some (IValue(1))
                      else Some (IValue(0))
                      else None
                    end and
              con = if (is_const t1) && (is_const t2) then [ConstQ]
                    else [] in
          let rt = update_qualifier (make_type2 IntT) con in
          let ret = update_value rt va in                     
        if (is_arithmetic t1 && is_arithmetic t2) then
          ret
        else 
          if (is_pointer t1 && is_pointer t2) then
            let b1 = get_base t1 in
            let b2 = get_base t2 in
            if (type_equals t1 t2 || is_void b1 ||
                is_void b2) || (is_arithmetic b1 && is_arithmetic b2) then 
              ret
            else 
              begin
              let _ = 
                show_error "comparison of distinct pointer types lacks a cast" 
                !curLoc in errort
              end  
          else if (is_pointer t1 && is_integer t2 && zero t2) then
            ret
          else 
            let _ = show_error ("invalid operands to binary " ^ op) !curLoc in
              errort
    
    | LT | GT | LE | GE ->
      let t1 = pointerize(analyze_expression e1) in
      let t2 = pointerize(analyze_expression e2) in
        if (is_error t1) || (is_error t2) then errort
        else if ((is_float t1) || (is_integer t1)) && 
             ((is_float t2) || (is_integer t2)) then
          make_type2 IntT
        else if (is_pointer t1 && is_pointer t2) then
           if (type_equals t1 t2) then make_type2 IntT
           else let _ = 
                  show_error "comparison of distinct pointer types lacks a cast"
                  !curLoc in errort
        else 
          begin
         
          let _ = show_error "invalid type in comparision" !curLoc in errort
          end
                   
    | ASSIGN | ADD_ASSIGN | SUB_ASSIGN | MUL_ASSIGN | DIV_ASSIGN | MOD_ASSIGN
    | BAND_ASSIGN | BOR_ASSIGN | XOR_ASSIGN | SHL_ASSIGN | SHR_ASSIGN ->
      let t1 = analyze_expression e1 in
      let t2 = analyze_expression e2 in            
      let _ = ensure_modifiable_lvalue t1 in 
      let _ = begin match bop with
                 ASSIGN -> processAssignment false t1 "assignment" t2
               | _ -> voidt
              end in t1
    end
  
  | QUESTION(e1,e2,e3) -> 
    let t1 = analyze_expression e1 and
        t3 = analyze_expression e3 in 
      let _ = ensure_scalar (pointerize t1) "conditional expression" in 
      let _ = analyze_expression e2 in 
        if (NOTHING = e2) then t1
        else update_value t3 None

  | CAST(ty,SINGLE_INIT(e)) -> 
    let t1 = analyze_type_expr ty in
    let t2 = analyze_expression e in      
     let rt = processCast t1 t2 in 
     let rt2 = update_qualifier rt t2.qualifiers in
       update_value rt2 t2.value
     
  | CAST(ty,init) ->                                        
      let t = analyze_type_expr ty in
      let _ = analyze_initializer t init in t   

  | CALL(e,el) ->
      begin match e with
          VARIABLE("__builtin_va_arg") ->
            if (List.length el != 2) then
              let _ = show_error "'__builtin_va_arg must have 2 arguments"
                        !curLoc in errort
            else             
              let t1 = analyze_expression (List.hd el) in
              if (t1.ty != VarArgT) then
                let _ = show_error 
                          "first argument to 'va_arg' not of type 'va_list'" 
                          !curLoc in errort
              else begin match (List.nth el 1) with
                  TYPE_SIZEOF(specs,dec) -> analyze_type_expr (specs,dec) 
                | _ -> let _ = show_error 
                     "invalid argument for '__builtin_va_arg'" !curLoc in errort  
                end 
        | VARIABLE("__xtc_trace") -> 
            if (List.length el > 0) then
              let fe = List.hd el in
              let tf = analyze_expression fe in
              let _ = trace (typ_to_string tf) !curLoc in
                voidt
            else voidt
              
        | VARIABLE(s)->
            let elt = List.map analyze_expression el in 
            if not(is_defined s) then 
              let _ = define s 
                        (make_type (FunctionT(make_type2 IntT, Some elt))
                         [] (Some ExternS) None None (Some true) None None 
                         false [])
                          in
                mark_old_function s; 
                make_type2 IntT
            else 
              let ft = lookup s in
              if (is_error ft) then errort
                
              else
              let ret  = get_return_type ft in
              let sometl = getParameterTypes ft in 
              let tl  = begin match sometl with
                            None -> []
                          | Some ll -> ll
                        end in  
              let _ = if not (is_old_function s) && (None != sometl) then 
                       let _ = processArguments tl elt (is_var_function s) in ()
                in ret              
        | _ ->
            let elt = List.map analyze_expression el in         
            let t = resolve (analyze_expression e) in 
              if (is_error t) then errort
              else
               let ft = if (is_pointer t && 
                           is_function (get_base t)) then get_base t
                        else if (is_function t) then t
                        else
                          let _ = show_error "called is not a function" !curLoc
                            in errort in
                 if not(is_error ft) then
                   let ret = get_return_type ft in
                   let sometl = getParameterTypes ft in 
                   let tl  = begin match sometl with
                                 None -> []
                               | Some ll -> ll
                             end in  
                   let _ = if (None != sometl) then
                             let _ = processArguments tl elt false in () 
                     in ret            
                 else errort  
              
      end
      
  | CONSTANT(co) -> analyze_constant co
  
  | PAREN(e) -> analyze_expression e
  
  | VARIABLE(str) -> 
      if not(is_defined str) then
        begin
        (*H.iter show_binding env;*)
        let _ = show_error ("undeclared identifier " ^ str) !curLoc in errort
        end
      else lookup str   
       
  | EXPR_SIZEOF(e) -> 
    let t = analyze_expression e in
      if (is_error t) then errort
      else if not(is_array t) && (is_incomplete t) then
        let _ = show_error "invalid application of 'sizeof' to incomplete type" 
                           !curLoc in errort
      else if (is_bitfield t) then
        let _ = show_error "'sizeof' applied to a bit-field" !curLoc in errort
      else 
        let si = sizeof t in
        make_type IntT [ConstQ] None None (Some (IValue(si)))
                  None None None false []                           
                        
  | TYPE_SIZEOF(specs,dec) -> 
    let t = analyze_type_expr (specs,dec) in
      if (is_error t) then errort
      else if not(is_array t) && (is_incomplete t) then
        let _ = show_error "invalid application of 'sizeof' to incomplete type" 
                           !curLoc in errort
      else if (is_bitfield t) then
        let _ = show_error "'sizeof' applied to a bit-field" !curLoc in errort
      else 
        let si = sizeof t in
        make_type IntT [ConstQ] None None (Some (IValue(si)))
                  None None None false []
                                              
  | EXPR_ALIGNOF(e) -> 
    let t = analyze_expression e in
      if (is_error t) then errort
      else if not(is_array t) && (is_incomplete t) then
        let _ = 
          show_error "invalid application of '__alignof' to incomplete type" 
                      !curLoc in errort
      else if (is_bitfield t) then
        let _ = show_error "__alignof' applied to a bit-field" !curLoc in errort
      else
        let al = get_alignment t true in
        make_type IntT [ConstQ] None None (Some (IValue(al)))
                  None None None false []
       
  | TYPE_ALIGNOF(specs,dec) -> 
    let t = analyze_type_expr (specs,dec) in
      if (is_error t) then errort
      else if (is_incomplete t) then
        let _ = 
          show_error "invalid application of '__alignof' to incomplete type" 
                      !curLoc in errort
      else if (is_bitfield t) then
        let _ = show_error "__alignof' applied to a bit-field" !curLoc in errort
      else 
        let al = get_alignment t true in
        make_type IntT [ConstQ] None None (Some (IValue(al)))
                  None None None false []
  
  | INDEX(e1,e2) -> 
    let _ = ensure_integer (analyze_expression e2) "subscript expression" in 
    let t = analyze_expression e1 in
       process_subscript t
  
  | MEMBEROF(e,str) -> 
    let t = analyze_expression e in
    if (is_error t) then errort 
    else 
      begin match t.ty with 
        StructT(sn,_,_) | UnionT(sn,_,_) -> 
          let st = lookup ("tag_" ^ sn) in
            if (is_complete st) then 
              get_member_type st str
            else let _ = show_error "request for member in incomplete type"
                                     !curLoc in errort
      | _ -> 
            let _ = show_error ("request for member '" ^ str ^ 
                                "' in something that is no struct or union")
                                !curLoc in errort  
      end 
  
  | MEMBEROFPTR(e,str) -> 
    let t = pointerize(analyze_expression e) in
    if (is_error t) then errort
    
    else if (is_pointer t) then 
      let base = get_base t in
        begin match base.ty with
            StructT(sn,_,_) | UnionT(sn,_,_) -> 
              let st = lookup ("tag_" ^ sn) in
                if (is_complete st) then
                  let me = get_member_type st str in
                    if (!in_addr) then
                      let si = get_int t in
                      let off = get_offset base str in
                      let va = match si with
                                   Some i -> Some (IValue(i + off))
                                 | _ -> None in
                      let rt = update_qualifier me t.qualifiers in
                        update_value rt va             
                    else me
                else 
                  let _ = show_error "dereferencing pointer to incomplete type"
                                    !curLoc in errort
          | _ -> let _ = show_error ("request for member '" ^ str ^ 
                                    "' in something that is no struct or union")
                                    !curLoc in errort                  
       end                             
    else 
      begin
     
      let _ = show_error "pointer type is required" !curLoc in errort
      end
      
  | GNU_BODY(bl) -> analyze_block bl false (*not body of a function*) true
                                            (*is expression*)
                                            
  | COMMA(el) -> let tl = List.map analyze_expression el in
                 if (List.length tl = 0) then voidt
                 else List.nth tl 1
                                                             
  | _ -> print_endline "unmatched expression"; voidt
  
(*t qual store fspec valu imp init pos  *)
(* Analyze constant.*)
and analyze_constant co = match co with 
    CONST_INT(str) -> 
      let va = begin match Cily.parseInt str with
                   Cily.Const (Cily.CInt64 (v64,_,_)) -> Int64.to_int v64
                 | _ -> E.s (E.error "Invalid attribute constant: %str") 
               end in
      if (ends_with str "ULL") then
        make_type ULongLongT [ConstQ] None None 
                  (Some (IValue(va))) None None None false []
      else if (ends_with str "LL") then
        make_type LongLongT [ConstQ] None None 
                  (Some (IValue(va))) None None None false []
      else if (ends_with str "UL") then
        make_type ULongT [ConstQ] None None (Some (IValue(va))) 
                  None None None false []  
      else if (ends_with str "U") then
        make_type UIntT [ConstQ] None None (Some (IValue(va))) 
                  None None None false []    
      else if (ends_with str "L") then
        make_type LongT [ConstQ] None None (Some (IValue(va))) 
                  None None None false []   
      else    
        make_type LongT [ConstQ] None None (Some (IValue(va))) 
                  None None None false []           
              
        
  | CONST_FLOAT(s) -> 
      let basestr = if (ends_with s "L") || (ends_with s "F") || 
                       (ends_with s "D") then
                      String.sub s 0 ((String.length s) - 1)
                    else s in    
    make_type LongDoubleT [ConstQ] None None 
              (Some (FValue(float_of_string basestr))) None None None false []
              
  | CONST_CHAR _ -> update_qualifier (make_type2 CharT) [ConstQ]
  
  | CONST_WCHAR _ -> update_qualifier (make_type2 CharT) [ConstQ]
  
  | CONST_STRING(str) ->
      make_type (ArrayT(update_qualifier (make_type2 CharT) [ConstQ],
                          Fixed(String.length str)))
               [ConstQ] None None (Some (SValue(str))) None None None false []
               
  | CONST_WSTRING(tl) ->  
      make_type (ArrayT(update_qualifier (make_type2 WideCharT) [ConstQ],
                          Fixed(List.length tl)))
               [ConstQ] None None (Some (SValue(""))) None None None false []      
    
     
(* Process array declarator. *)
and process_array t n = 
  if (NOTHING = n) then
    make_type3 (ArrayT(t,Incomplete)) (t.storage)    
  else 
    let st = analyze_expression n in
    if (is_error st) then errort
    else 
      let _ = ensure_integer st "array index" in
      let size = get_int st in
      let i = match size with
                  None -> 0
                | Some num -> 
                  let _ = if (is_const st) && (num < 0)  then
                            show_error "size of array is negative" !curLoc in
                             num in        
               make_type3 (ArrayT(t,Fixed(i))) (t.storage)      
     
(* Process declarator. *)
and analyze_decl_type (t: typ) (decl: decl_type) s of_fun = match decl with
    JUSTBASE -> t 
  | PARENTYPE(_,d,_) -> analyze_decl_type t d s of_fun
  | ARRAY(d,_,e) -> 
      let aty = process_array t e in
      let tu = update_loc aty t.loc in
        if not (is_error tu) then
          analyze_decl_type tu d s of_fun
        else errort        
                                          
  | PTR(_,dec) -> 
      let t' = make_type3 (PointerT(t)) (t.storage) in
      let tu = update_loc t' t.loc in
        analyze_decl_type tu dec s of_fun 
               
  | PROTO(dec,pl,b) -> 
      if b then mark_var_function s;
      (*Enter scope if not in function definition. *)
      if not(of_fun) then 
        enter_scope (fresh_name "temporary");
      let tl = List.map analyze_single_name pl in
      if (List.exists is_error tl) then
        let ft = make_type3 (FunctionT(t,None)) (t.storage) in
        let tu = update_loc ft t.loc in 
          analyze_decl_type tu dec s of_fun
      else
        let ft = if (List.length pl = 0) then
                   make_type3 (FunctionT(t, None)) (t.storage)    
                 else if (is_void_paralist pl tl)  then
                    make_type3 (FunctionT(t,Some [])) (t.storage)
                 else make_type3 (FunctionT(t,Some tl)) (t.storage) in
        let tu = update_loc ft t.loc in
          (* exit scope*)
          if not(of_fun) then exit_scope true;
          analyze_decl_type tu dec s of_fun
          
(* If function just has (void). *)
and is_void_paralist pl tl = 
  if (List.length pl = 0) then true
  else
  let t = List.hd tl in
  let p = List.hd pl in
  let _,(id,_,_,_) = p in
    ("" = id) && (is_void t)

(* Analyze a single name (a parameter declaration). *)
(* c_define here *)
and analyze_single_name sn = 
  in_para_decl := true;
  let specs,(id,dec,_,loc) = sn in
    (* loc error in parser here.*)
    (*curLoc := loc;*)
    let t = processSpecifier specs in
    let tu = update_loc t (Some !curLoc) in
    let t' = analyze_decl_type tu dec id false in
      in_para_decl := false;
      if (String.length id != 0) then
        if not(is_array t') && (is_incomplete t') then
          let _ = show_error "parameter has incomplete type" loc in errort
        else let _ = c_define id t' in t'        
      else
        if not(is_void t') then t'
        else
        let _ = if (None != t'.storage) then
          let _ = show_error 
            "'void' as only parameter may not have storage class" !curLoc in ()
          in     
        begin match t'.qualifiers with
            [] -> t'
          | _ ->
            let _ = show_error "'void' as only parameter may not be qualified"
                      !curLoc in errort 
        end      
                     
     
(* Analyze a name (declarator without init expression). *)
and analyze_name (t: typ) (isdef: bool) (of_fun: bool) (n: name) = 
(* string * decl_type * attribute list * cabsloc *)
  let id,decl,_,loc = n in
    curLoc := loc;
    let rt = analyze_decl_type t decl id of_fun in
    let tu = update_loc rt (Some loc) in
    if isdef then
      let _ = define id tu in tu
    else tu            
 
(* Analyze an init name (Declarator with init expresion).*)
(* c_define here.*)
and analyze_init_name (t: typ) (n: init_name) =
(* name * init_expression *)
  let (id,dec,_,loc),e = n in
    curLoc := loc;
    let ret = analyze_decl_type t dec id false in
    let _ = if (!is_top) then
                if (is_static t) then
                  redefine ("top__" ^ id) (update_loc ret (Some loc))
                else ()  
              else 
                if (in_inner id) && (is_extern t) && 
                   (is_defined ("top__" ^ id)) then
                  let told = lookup ("top__" ^ id) in
                  let pos = begin match told.loc with
                                Some lo -> lo
                              | _ -> !curLoc
                            end in
                  let _ = show_error ("variable previously declared 'static' " ^
                                      "redeclared 'extern'") loc in
                  let _ = show_error ("previous definition of '" ^ id ^
                                      "' was here") pos in () in 
    begin match e with
      NO_INIT -> 
        let _ = if (is_array ret) && (is_complete ret) &&
                     (is_incomplete (get_base ret)) then
                  show_error "array type has incomplete element type" loc in (); 
        let tu = update_loc ret (Some loc) in
        let tt = update_top tu (!is_top) in
          let _ = c_define id tt in tt
    | SINGLE_INIT(se) -> 
        let tu = update_loc ret (Some loc) in
        let tt = update_top tu (!is_top) in
        let _ = if (is_extern ret) then 
                 show_warning ("'" ^ id ^ "' initialized and declared 'extern'")
                   loc in ();
        let _ = c_define id tt in ();
        in_initializer := true;                       
        let tse = analyze_expression se in
        in_initializer := false;
       (* let _ = if (is_at_global ()) && not(is_const tse)  then
          show_error "initializer element is not constant" loc in (); *)
        let tr = if (None != tse.value) then
                   update_value tt (tse.value)
                 else tt in
        let _ = processAssignment true ret "=" tse in
        let _ = define id tr in tr                              
    | _ ->  
        let tu = update_loc ret (Some loc) in
        let tt = update_top tu (!is_top) in
        let _ = if (is_extern ret) then 
                  show_warning ("'" ^ id ^ "' initialized and declared 'extern'")
                               loc in ();
        let _ = c_define id tt in ();
        if (is_array ret) && (is_incomplete (get_base ret)) then
          let _ = show_error "array type has incomplete element type" loc 
            in errort   
        else analyze_initializer tt e
        
    end 
    
(* Check if an id is defined in a inner scope.*)
and in_inner id = if not(is_defined id) then false 
                  else let t = lookup id in
                    not(t.in_top)        
        
(* Analyze for clause.*)
and analyze_for_clause fc = match fc with
    FC_DECL(def) -> analyze_definition def
  | FC_EXP(e) -> analyze_expression e 

(* Analyze a statement. *)
and analyze_statement st = match st with
    NOP _ -> voidt
    
  | COMPUTATION(e,loc) ->
      curLoc := loc;
      analyze_expression e
  
  | BLOCK(bl,loc) ->
      curLoc := loc;
      analyze_block bl false (*not in function*) false (*not expression*)
  
  | SEQUENCE(st1,st2,loc) ->
      curLoc := loc;
      let _ = analyze_statement st1 in
      let _ = analyze_statement st2 in voidt
      
  | IF(e,st1,st2,loc) ->
      curLoc := loc;
      let _ = ensure_scalar (pointerize (analyze_expression e)) 
                "conditional statement" in 
      let _ = analyze_statement st1 in 
        analyze_statement st2
        
  | WHILE(e,st,loc) | DOWHILE(e,st,loc) -> 
      curLoc := loc;
      in_loop := true :: !in_loop;
      let _ = ensure_scalar (pointerize (analyze_expression e)) 
                "conditional statement" in
      let _ = analyze_statement st in 
      in_loop := List.tl !in_loop;
        voidt
        
  | FOR(fc,e1,e2,st,loc) ->
      curLoc := loc;
      in_loop := true :: !in_loop;
      
      (*H.iter show_binding env;*)
      let isdef = match fc with
                      FC_DECL _ -> true
                    | _ -> false in
      if isdef then enter_scope (fresh_name "for");
      let _ = analyze_for_clause in 
      if not(isdef) then enter_scope (fresh_name "for");      
      let _ = if (NOTHING != e1) then 
        let _ = ensure_scalar (pointerize (analyze_expression e1)) 
                         "ifor expression" in () in
      let _ = analyze_expression e2 in 
      let _ = begin match st with
                  BLOCK(bl,_) -> 
                    analyze_block bl true(*like in function body*)
                                                  false (*not expression*)
                | _ -> analyze_statement st
              end in 
        in_loop := List.tl !in_loop;
        exit_scope true;
        (*H.iter show_binding env;*)
         voidt 
         
  | BREAK(loc) -> 
      curLoc := loc;
      if not(is_in_loop ()) && not(is_in_switch ()) then
        let _ = show_error "not within a loop or switch" loc in voidt
      else voidt
        
  | CONTINUE(loc) ->
      curLoc := loc;
      if not(is_in_loop ()) then
        let _ = show_error "not within a loop" loc in voidt
      else voidt 
      
  | RETURN(e,loc) ->
      curLoc := loc; 
      let t = analyze_expression e in 
      let rt = !currentReturnType in
      let _ = if (is_void rt) && not(is_void t) then 
        show_warning "'return with a value in function returning void" loc 
          in ();
      (*let _ = if not(is_void rt) && (is_void t) then
        show_warning "return with no value in function returning a value" loc
          in ();*)
      let _ = if ((is_pointer t || is_array t) && is_integer rt) then
        show_warning "return makes integer from pointer without cast" loc in ();
      let _ = if (is_struct_union t) && (is_incomplete t) then
        show_error "return incomplete type" loc in ();
      if (NOTHING != e) then  
        let _ = processAssignment false rt "return statement" t in voidt
      else voidt
  
  | SWITCH(e,st,loc) ->
      curLoc := loc;
      in_switch := true :: !in_switch;
      let _ = ensure_integer (analyze_expression e) 
               "switch expression" in ();
      let _ = analyze_statement st in
      in_switch := List.tl !in_switch;
      voidt
               
  | CASE(e,st,loc) ->
      curLoc := loc;
      let _ = if not(is_in_switch ()) then
        show_error "not within a switch statement" loc in ();        
      let _ = ensure_integer (analyze_expression e) "case label" in
      let _ = analyze_statement st in voidt
      
  | DEFAULT(st,loc) -> 
      curLoc := loc;
      let _ = if not(is_in_switch ()) then
        show_error "not within a switch statement" loc in ();
      let id = "default" in  
      let t = make_type2 (LabelT(id)) in   
      if (is_defined_locally "label_default") then
        let _ = show_error "mutiple default labels" loc in errort
      else let _ = define "label_default" t in voidt 
  
  | LABEL(str,st,loc) ->
   (* t qual store fspec valu imp init pos *) 
      curLoc := loc;
      let t = make_type (LabelT(str)) [] None None None None (Some true) 
                         None false [] in
      define ("label_" ^ str) t; 
      let  _ = analyze_statement st in voidt
      
  | GOTO(str,loc) ->
     curLoc := loc;
     if not(is_defined ("label_" ^ str)) then
       let _ = show_error ("'" ^ str ^ " used but not defined") loc in errort
     else let t = lookup ("label_" ^ str) in
       if not(t.initialised = Some true) then
         let _ = show_error ("'" ^ str ^ " used but not defined") loc in errort  
     else voidt
   
  
  | COMPGOTO(e,loc) -> 
      curLoc := loc;
      let _ = analyze_expression e in voidt
  
  | DEFINITION(def) -> analyze_definition def
  
  | _ -> voidt 

(* Analyze a block. *)
and analyze_block (bl: block) in_func is_expr = 
  let lbs = bl.blabels in
  let sts = bl.bstmts in
  if not(in_func) then enter_scope (fresh_name "block"); 
  define_local_labels lbs false;  
  let tl = List.map analyze_statement sts in
    if not(in_func) then exit_scope true;
    if not(is_expr) then voidt
    else
      let lastt = get_last_type tl ((List.length tl) - 1)
        (*if (List.length tl > 1) then 
                        List.nth tl ((List.length tl) - 2) 
                      else List.nth tl ((List.length tl) - 1) *)  in
         lastt 
          
and get_last_type tl index = 
  if (index < 0 ) then voidt
  else
    let t = List.nth tl index in
      if not(is_void t) then t
      else get_last_type tl (index - 1)         
  
         
(* Analyze definition. *)        
and analyze_definition n = match n with
   FUNDEF((specs, decl),bl,loc1,loc2) ->
     curLoc := loc1;
     is_top := false;
     let id = get_id decl in
     let fnt = make_type2 (ArrayT(make_type2 CharT, 
                                    Fixed(String.length id))) in
     let st = BLOCK(bl,loc2) in
     let lbl = find_labels [] st in
     let _ = check_defined_labels lbl st in ();      
     let t = processSpecifier specs in
       if (is_error t) then 
         begin
           is_top := true;
           voidt
         end  
       else begin
         in_function := true;
         let _ = if (has_auto t) then 
           show_error "function definition declared 'auto'" loc1 in ();
         let _ = if (is_register t) then
           show_error "function definition declared 'register'" loc1 in ();
         let _ = if (is_typedef t) then 
           show_error "function definition declared 'typedef" loc1 in ();  
         let t' = update_loc t (Some loc1) in
           (* Enter scope to process parameter list *) 
           enter_scope ("function_" ^ id);
           (* Analyze the decl to get function type. *) 
           let ft = analyze_name t' false true decl in
           if (is_error ft) then 
             begin 
               is_top := true;
               voidt
             end  
           else
             (* Check return type of main. *)
             let rety = get_return_type ft in          
               let _ = if ("main" = id) && (IntT !=  rety.ty) then
                  show_warning "return type of 'main' is not 'int' " loc1 in ();
               currentReturnType := rety;    
               (* Exit scope (without removing bindings) to define in 
                  global scope.*)           
               exit_scope false;
               define id ft;
               (* Enter scope again*)
               enter_scope ("function_" ^ id);
               (* define _func_ before process the body*)
               let _ = c_define "__func__" fnt in ();
               curLoc := loc2;
               (*redefine labels at function scope*)
               let _ = define_local_labels lbl true in 
               let _ = analyze_block bl true false in
                 (* Exit scope, remove bindings.*)
                 exit_scope true;
                 in_function := false;
                 is_top := true;
                 currentReturnType := errort;
                 voidt end
            
 | DECDEF((specs, init_list),loc) -> 
     curLoc := loc ;
     let t = processSpecifier specs in
    (* if (is_error t) then 
     begin
     voidt
     end
     else *)     
       let t' = update_loc t (Some loc) in 
       let f = analyze_init_name t' in
       let _ = List.map f init_list in voidt
     
 | TYPEDEF((specs,decll),loc) -> 
     curLoc := loc;
     in_def := true;
     let t = processSpecifier specs in
     if (is_error t) then voidt
     else
       let t' = update_loc t (Some loc) in
       let f = analyze_name t' true false in
       let _ = List.map f decll in 
       in_def := false;
       voidt
 
 | ONLYTYPEDEF(specs,loc) ->
     curLoc := loc;
     let t = processSpecifier specs in
     if (is_error t) then voidt
     else
       let _ = if not (is_none t.fSpec) then 
         show_error "'iniline in empty declaration" loc in () ;
       let _ = if not (is_none t.storage ) then 
         show_warning "useless storage classes specifier in empty declaration"
                      loc in ();
       let _ = if (is_qualified t) then
         show_warning "useless type qualifier in empty declaration" loc in () ;
       let _ = if not (is_struct_union t) && not (is_enum t) then
         show_warning "declaration does not declare anything" loc in ();
       voidt    
 | _ -> voidt ;;  

let analyze (fl: Cabs.file) = 
  let _ = start_file () in
  let dl = snd fl in
  let _ = List.map analyze_definition dl in () ;;
