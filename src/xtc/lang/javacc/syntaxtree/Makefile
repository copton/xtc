# Makefile 2 
#***************************************************************************
# 
# Java development root directory.
# 
# (C) 1999 Jacob Dreyer - Geotechnical Software Services
# jacob.dreyer@geosoft.no - http://geosoft.no
#
# Modifications Copyright (C) 2001, 2004 Robert Grimm 
# rgrimm@alum.mit.edu
#
# This program is free software; you can redistribute it and/or
# modify it under the terms of the GNU General Public License
# as published by the Free Software Foundation; either version 2
# of the License, or (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program; if not, write to the Free Software
# Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
#
#***************************************************************************




#***************************************************************************
#
# This section describes the current package.
#
# o PACKAGE     - The complete package name. 
# o PACKAGE_LOC - Same as PACKAGE but with "/"s instead of "."s.
# o SOURCE      - List of the source files. Remember extension.
# o JNI_SOURCE  - Files from SOURCE that are to be built with the JAVAH 
#                 compiler.
# o JAR_EXTRAS  - None-class files and directories that are to be bundled
#                 into the jar archive.
#
#***************************************************************************

PACKAGE     = xtc.lang.javacc.syntaxtree
PACKAGE_LOC = xtc/lang/javacc/syntaxtree

SOURCE    = \
	AdditiveExpression.java \
	AllocationExpression.java \
	AndExpression.java \
	ArgumentList.java \
	Arguments.java \
	ArrayDimsAndInits.java \
	ArrayInitializer.java \
	AssertStatement.java \
	AssignmentOperator.java \
	Block.java \
	BlockStatement.java \
	BooleanLiteral.java \
	BreakStatement.java \
	CastExpression.java \
	CastLookahead.java \
	ClassBody.java \
	ClassBodyDeclaration.java \
	ClassDeclaration.java \
	CompilationUnit.java \
	ConditionalAndExpression.java \
	ConditionalExpression.java \
	ConditionalOrExpression.java \
	ConstructorDeclaration.java \
	ContinueStatement.java \
	DoStatement.java \
	EmptyStatement.java \
	EqualityExpression.java \
	ExclusiveOrExpression.java \
	ExplicitConstructorInvocation.java \
	Expression.java \
	FieldDeclaration.java \
	ForInit.java \
	ForStatement.java \
	ForUpdate.java \
	FormalParameter.java \
	FormalParameters.java \
	IfStatement.java \
	ImportDeclaration.java \
	InclusiveOrExpression.java \
	Initializer.java \
	InstanceOfExpression.java \
	InterfaceDeclaration.java \
	InterfaceMemberDeclaration.java \
	LabeledStatement.java \
	Literal.java \
	LocalVariableDeclaration.java \
	MethodDeclaration.java \
	MethodDeclarationLookahead.java \
	MethodDeclarator.java \
	MultiplicativeExpression.java \
	Name.java \
	NameList.java \
	NestedClassDeclaration.java \
	NestedInterfaceDeclaration.java \
	Node.java \
	NodeChoice.java \
	NodeList.java \
	NodeListInterface.java \
	NodeListOptional.java \
	NodeOptional.java \
	NodeSequence.java \
	NodeToken.java \
	NullLiteral.java \
	PackageDeclaration.java \
	PostfixExpression.java \
	PreDecrementExpression.java \
	PreIncrementExpression.java \
	PrimaryExpression.java \
	PrimaryPrefix.java \
	PrimarySuffix.java \
	PrimitiveType.java \
	RelationalExpression.java \
	ResultType.java \
	ReturnStatement.java \
	ShiftExpression.java \
	Statement.java \
	StatementExpression.java \
	StatementExpressionList.java \
	SwitchLabel.java \
	SwitchStatement.java \
	SynchronizedStatement.java \
	ThrowStatement.java \
	TryStatement.java \
	Type.java \
	TypeDeclaration.java \
	UnaryExpression.java \
	UnaryExpressionNotPlusMinus.java \
	UnmodifiedClassDeclaration.java \
	UnmodifiedInterfaceDeclaration.java \
	VariableDeclarator.java \
	VariableDeclaratorId.java \
	VariableInitializer.java \
	WhileStatement.java


JNI_SOURCE =

JAR_EXTRAS = 


#***************************************************************************
#
# Include common part of makefile
#
#***************************************************************************

ifdef JAVA_DEV_ROOT
include $(JAVA_DEV_ROOT)/Makerules
endif
