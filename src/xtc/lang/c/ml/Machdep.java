package xtc.lang.c.ml;

import xtc.Limits;

import java.math.BigInteger;

class Machdep {
  public static void main(String args[]) {
    StringBuffer str = new StringBuffer();
    // copyright information
    str.append("(*\n");
    str.append(" * xtc - The eXTensible Compiler\n");
    str.append(" * Copyright (C) 2005-2007 Robert Grimm\n");
    str.append(" *\n");
    str.append(" * This program is free software;");
    str.append("you can redistribute it and/or\n");
    str.append(" * modify it under the terms of the");
    str.append("GNU General Public License\n");
    str.append(" * version 2 as published by the Free Software Foundation.\n");
    str.append(" *\n");
    str.append(" * This program is distributed in the hope that");
    str.append("it will be useful,\n");
    str.append(" * but WITHOUT ANY WARRANTY;");
    str.append("without even the implied warranty of\n");
    str.append(" * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.");
    str.append("  See the\n");
    str.append(" * GNU General Public License for more details.\n");
    str.append(" *\n");
    str.append(" * You should have received a copy of the");
    str.append(" GNU General Public License\n");
    str.append(" * along with this program;");
    str.append(" if not, write to the Free Software\n");
    str.append(" * Foundation, 51 Franklin Street, Fifth Floor, Boston,");
    str.append("MA 02110-1301,\n");
    str.append(" * USA.\n");
    str.append(" *)\n");
    str.append("\n");
    
    // module, author and version
    str.append("(**\n");
    str.append(" * This domule are generated from Machdep.java,\n");
    str.append(" *   using values from xtc/Limits.java\n");
    str.append(" *\n");
    str.append(" * @author Anh Le\n");
    str.append(" * @version $Revision: 1.4 $\n");
    str.append(" *)\n");
    
    // define constant
    str.append("let os = \"");
    str.append(Limits.OS);
    str.append("\"\n\n");
    
    str.append("let arch = \"");
    str.append(Limits.ARCH);
    str.append("\"\n\n");
    
    str.append("let compiler = \"");
    str.append(Limits.COMPILER_NAME);
    str.append("\"\n\n");
    
    str.append("let compier_version = \"");
    str.append(Limits.COMPILER_VERSION);
    str.append("\"\n\n");
    
    str.append("let compiler_major = ");
    str.append(Integer.toString(Limits.COMPILER_VERSION_MAJOR));
    str.append("\n\n");
    
    str.append("let compiler_minor = ");
    str.append(Integer.toString(Limits.COMPILER_VERSION_MINOR));
    str.append("\n\n");
    
    str.append("let compiler_revision = 0\n\n");
    
    str.append("(*******************************************************)\n\n");
    
    str.append("let is_big_endian = ");
    str.append(Boolean.toString(Limits.IS_BIG_ENDIAN));
    str.append("\n\n");
    
    str.append("let void_size = ");
    str.append(Integer.toString(Limits.VOID_SIZE));
    str.append("\n\n");
    
    str.append("let void_align = ");
    str.append(Integer.toString(Limits.VOID_ALIGN));
    str.append("\n\n");
    
    str.append("let function_size = ");
    str.append(Integer.toString(Limits.FUNCTION_SIZE));
    str.append("\n\n");
    
    str.append("let function_align = ");
    str.append(Integer.toString(Limits.FUNCTION_ALIGN));
    str.append("\n\n");
    
    str.append("let pointer_size = ");
    str.append(Integer.toString(Limits.POINTER_SIZE));
    str.append("\n\n");
    
    str.append("let pointer_align = ");
    str.append(Integer.toString(Limits.POINTER_ALIGN));
    str.append("\n\n");
    
    str.append("let pointer_nat_align = ");
    str.append(Integer.toString(Limits.POINTER_NAT_ALIGN));
    str.append("\n\n");
    
    str.append("let ptrdiff_size = ");
    str.append(Integer.toString(Limits.PTRDIFF_SIZE));
    str.append("\n\n");
    
    str.append("let ptrdiff_rank = ");
    str.append(Integer.toString(Limits.PTRDIFF_RANK));
    str.append("\n\n");
    
    str.append("let sizeof_size = ");
    str.append(Integer.toString(Limits.SIZEOF_SIZE));
    str.append("\n\n");
    
    str.append("let sizeof_rank = ");
    str.append(Integer.toString(Limits.SIZEOF_RANK));
    str.append("\n\n");
    
    str.append("let array_max = ");
    str.append(Limits.ARRAY_MAX.toString(10));
    str.append("\n\n");
    
    str.append("(*******************************************************)\n\n");
    
    str.append("let bool_size = ");
    str.append(Integer.toString(Limits.BOOL_SIZE));
    str.append("\n\n");
    
    str.append("let bool_align = ");
    str.append(Integer.toString(Limits.BOOL_ALIGN));
    str.append("\n\n");
    
    str.append("let bool_nat_align = ");
    str.append(Integer.toString(Limits.BOOL_NAT_ALIGN));
    str.append("\n\n");
    
    str.append("(*******************************************************)\n\n");
    
    str.append("let is_char_signed = ");
    str.append(Boolean.toString(Limits.IS_CHAR_SIGNED));
    str.append("\n\n");
    
    str.append("let char_bits = ");
    str.append(Integer.toString(Limits.CHAR_BITS));
    str.append("\n\n");
    
    str.append("let char_min = ");
    str.append(Limits.CHAR_MIN.toString(10));
    str.append("\n\n");
    
    str.append("let char_max = ");
    str.append(Limits.CHAR_MAX.toString(10));
    str.append("\n\n");
    
    str.append("let uchar_max = ");
    str.append(Limits.UCHAR_MAX.toString(10));
    str.append("\n\n");
    
    str.append("(*******************************************************)\n\n");
    
    str.append("let is_wchar_signed = ");
    str.append(Boolean.toString(Limits.IS_WCHAR_SIGNED));
    str.append("\n\n");
    
    str.append("let wchar_size = ");
    str.append(Integer.toString(Limits.WCHAR_SIZE));
    str.append("\n\n");
    
    str.append("let wchar_rank = ");
    str.append(Integer.toString(Limits.WCHAR_RANK));
    str.append("\n\n");
    
    str.append("(*******************************************************)\n\n");
    
    str.append("let is_string_const = ");
    str.append(Boolean.toString(Limits.IS_STRING_CONST));
    str.append("\n\n");
    
    str.append("(*******************************************************)\n\n");
    
    str.append("let short_size = ");
    str.append(Integer.toString(Limits.SHORT_SIZE));
    str.append("\n\n");
    
    str.append("let short_align = ");
    str.append(Integer.toString(Limits.SHORT_ALIGN));
    str.append("\n\n");
    
    str.append("let short_nat_align = ");
    str.append(Integer.toString(Limits.SHORT_NAT_ALIGN));
    str.append("\n\n");
    
    str.append("let short_min = ");
    str.append(Limits.SHORT_MIN.toString(10));
    str.append("\n\n");
    
    str.append("let short_max = ");
    str.append(Limits.SHORT_MAX.toString(10));
    str.append("\n\n");
    
    str.append("let ushort_max = ");
    str.append(Limits.USHORT_MAX.toString(10));
    str.append("\n\n");
    
    str.append("(*******************************************************)\n\n");
    
    str.append("let is_int_signed = ");
    str.append(Boolean.toString(Limits.IS_INT_SIGNED));
    str.append("\n\n");
    
    str.append("let int_size = ");
    str.append(Integer.toString(Limits.INT_SIZE));
    str.append("\n\n");
    
    str.append("let int_align = ");
    str.append(Integer.toString(Limits.INT_ALIGN));
    str.append("\n\n");
    
    str.append("let int_nat_align = ");
    str.append(Integer.toString(Limits.INT_NAT_ALIGN));
    str.append("\n\n");
    
    //str.append("let int_min = Int64.of_int ");
    //str.append(Limits.INT_MIN.toString(10));
    //str.append("\n\n");
    
    //str.append("let int_max = Int64.of_int ");
    //str.append(Limits.INT_MAX.toString(10));
    //str.append("\n\n");
    
    //str.append("let uint_max = Int64.of_int ");
    //str.append(Limits.UINT_MAX.toString(10));
    //str.append("\n\n");
    
    str.append("(*******************************************************)\n\n");
    
    str.append("let long_size = ");
    str.append(Integer.toString(Limits.LONG_SIZE));
    str.append("\n\n");
    
    str.append("let long_align = ");
    str.append(Integer.toString(Limits.LONG_ALIGN));
    str.append("\n\n");
    
    str.append("let long_nat_align = ");
    str.append(Integer.toString(Limits.LONG_NAT_ALIGN));
    str.append("\n\n");
    
    //str.append("let long_min = ");
    //str.append(Limits.LONG_MIN.toString(10));
    //str.append("\n\n");
    
    //str.append("let long_max = ");
    //str.append(Limits.LONG_MAX.toString(10));
    //str.append("\n\n");
    
    //str.append("let ulong_max: int64 = ");
    //str.append(Limits.ULONG_MAX.toString(10));
    //str.append("\n\n");
    
    str.append("(*******************************************************)\n\n");
    
    str.append("let long_long_size = ");
    str.append(Integer.toString(Limits.LONG_LONG_SIZE));
    str.append("\n\n");
    
    str.append("let long_long_align = ");
    str.append(Integer.toString(Limits.LONG_LONG_ALIGN));
    str.append("\n\n");
    
    str.append("let long_long_nat_align = ");
    str.append(Integer.toString(Limits.LONG_LONG_NAT_ALIGN));
    str.append("\n\n");
    
    //str.append("let long_long_min: int64 = ");
    //str.append(Limits.LONG_LONG_MIN.toString(10));
    //str.append("\n\n");
    
    //str.append("let long_long_max: int64 = ");
    //str.append(Limits.LONG_LONG_MAX.toString(10));
    //str.append("\n\n");
    
    //str.append("let ulong_long_max: int64 = ");
    //str.append(Limits.ULONG_LONG_MAX.toString(10));
    //str.append("\n\n");
    
    str.append("(*******************************************************)\n\n");
    
    str.append("let float_size = ");
    str.append(Integer.toString(Limits.FLOAT_SIZE));
    str.append("\n\n");
    
    str.append("let float_align = ");
    str.append(Integer.toString(Limits.FLOAT_ALIGN));
    str.append("\n\n");
    
    str.append("let float_nat_align = ");
    str.append(Integer.toString(Limits.FLOAT_NAT_ALIGN));
    str.append("\n\n");
    
    str.append("let double_size = ");
    str.append(Integer.toString(Limits.DOUBLE_SIZE));
    str.append("\n\n");
    
    str.append("let double_align = ");
    str.append(Integer.toString(Limits.DOUBLE_ALIGN));
    str.append("\n\n");
    
    str.append("let double_nat_align = ");
    str.append(Integer.toString(Limits.DOUBLE_NAT_ALIGN));
    str.append("\n\n");
    
    str.append("let long_double_size = ");
    str.append(Integer.toString(Limits.LONG_DOUBLE_SIZE));
    str.append("\n\n");
    
    str.append("let long_double_align = ");
    str.append(Integer.toString(Limits.LONG_DOUBLE_ALIGN));
    str.append("\n\n");
    
    str.append("let long_double_nat_align = ");
    str.append(Integer.toString(Limits.LONG_DOUBLE_NAT_ALIGN));
    str.append("\n\n");
    
    str.append("let gccHas__builtin_va_list = true\n\n");
    
    str.append("let __thread_is_keyword = true\n\n");
    
    
    System.out.println(str.toString());
    System.out.flush();
  }
}
