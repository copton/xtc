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
 * This module is the program entry point for the Ocmal-based C type checker
 * program, which reads a C program file, parses it,and then do typechecking 
 * on the resulted AST 
 **)
 
module F = Frontc
open Frontc
open Cchecker

let theMain () = 
  let file_name = Sys.argv.(1) in
  let fname,cabs = F.parse_to_cabs_inner file_name in
    print_endline "xtc Ml C typechecker, (C) 2007 NYU, uses part of CIL";
    print_endline ("Processing " ^ file_name);
    analyze (fname,cabs) ;;
                    
begin 
  try 
    theMain (); 
  with F.CabsOnly -> (* this is OK *) ()
end;
