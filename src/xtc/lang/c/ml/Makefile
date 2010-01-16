# Makefile
#*******************************************************************************
# xtc - The eXTensible Compiler
# Copyright (C) 2007 New York University
#
# This program is free software; you can redistribute it and/or
# modify it under the terms of the GNU General Public License
# version 2 as published by the Free Software Foundation.

# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.

# You should have received a copy of the GNU General Public License
# along with this program; if not, write to the Free Software
# Foundation, 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301,
# USA.
#*******************************************************************************

#*******************************************************************************
# 
# This section describe the current package
#
# o SOURCE - List of source files (in Ocaml)
# o OBJECT - List of object files 
#
#*******************************************************************************

SOURCE = \
	pretty.mli \
	pretty.ml \
	inthash.mli \
	inthash.ml \
	errormsg.mli \
	errormsg.ml \
	trace.mli \
	trace.ml \
	growArray.mli \
	escape.mli \
	escape.ml \
	longarray.mli \
	longarray.ml \
	growArray.ml \
	cabs.ml \
	cabshelper.ml \
	whitetrack.mli \
	whitetrack.ml \
	cprint.ml \
	lexerhack.ml \
	machdep.ml \
	cparser.mli \
	cparser.ml \
	clexer.mli \
	clexer.ml \
	cily.mli \
	cily.ml \
	frontc.mli \
	frontc.ml \
	cchecker.mli \
	cchecker.ml \
	main.ml

OBJECT = \
	pretty.cmx \
	inthash.cmx \
	errormsg.cmx \
	trace.cmx \
	escape.cmx \
	longarray.cmx \
	growArray.cmx \
	cabs.cmx \
	cabshelper.cmx \
	whitetrack.cmx \
	cprint.cmx \
	lexerhack.cmx \
	machdep.cmx \
	cparser.cmx \
	clexer.cmx \
	cily.cmx \
	frontc.cmx \
	cchecker.cmx \
	main.cmx		

#***************************************************************************
#
# Include common part of makefile
#
#***************************************************************************

ifdef JAVA_DEV_ROOT

.PHONY : cleanall

checker :

include $(JAVA_DEV_ROOT)/Makerules

checker: $(SOURCE) clexer.mll
	ocamllex  clexer.mll
	ocamlopt -c $(SOURCE)
	mkdir -p $(CLASS_DIR)/xtc/lang/c/ml
	mv -f *.cmi *.cmx *.o $(CLASS_DIR)/xtc/lang/c/ml	
	ocamlopt str.cmxa unix.cmxa -o $(JAVA_DEV_ROOT)/bin/MLCAnalyzer \
		-I $(CLASS_DIR)/xtc/lang/c/ml $(OBJECT)

cleanall:
	rm -f $(SOURCE_DIR)/xtc/lang/c/ml/*.cmi
	rm -f $(SOURCE_DIR)/xtc/lang/c/ml/*.cmx
	rm -f $(SOURCE_DIR)/xtc/lang/c/ml/*.o
	rm -f $(CLASS_DIR)/xtc/lang/c/ml/*.cmi
	rm -f $(CLASS_DIR)/xtc/lang/c/ml/*.cmx
	rm -f $(CLASS_DIR)/xtc/lang/c/ml/*.o
	rm -f $(JAVA_DEV_ROOT)/bin/MLCAnalyzer

configure:
	$(JAVAC) $(JAVAC_OPTIONS) Machdep.java
	$(JAVA) $(JAVA_OPTIONS) xtc.lang.c.ml.Machdep > machdep.ml
endif
