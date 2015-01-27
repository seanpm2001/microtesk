/*
 * Copyright 2012-2015 ISP RAS (http://www.ispras.ru)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

tree grammar MmuTreeWalker;

//==================================================================================================
// Options
//==================================================================================================

options {
  language=Java;
  tokenVocab=MmuParser;
  ASTLabelType=CommonTree;
  superClass=MmuTreeWalkerBase;
}

@rulecatch {
catch (RecognitionException re) {
    reportError(re);
    recover(input,re);
}
}

//==================================================================================================
// Header for the Generated Java File
//==================================================================================================

@header {
/*
 * Copyright 2012-2015 ISP RAS (http://www.ispras.ru)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 *
 * WARNING: THIS FILE IS AUTOMATICALLY GENERATED. PLEASE DO NOT MODIFY IT. 
 */

package ru.ispras.microtesk.translator.mmu.grammar;

import static ru.ispras.microtesk.utils.PrintingUtils.trace;

import ru.ispras.fortress.expression.Node;
import ru.ispras.fortress.expression.NodeValue;

import ru.ispras.microtesk.translator.antlrex.Where;
import ru.ispras.microtesk.translator.antlrex.SemanticException;

import ru.ispras.microtesk.translator.mmu.MmuTreeWalkerBase;
import ru.ispras.microtesk.translator.mmu.MmuSymbolKind;
}

//==================================================================================================
// MMU Specification
//==================================================================================================

startRule 
    : declaration*
    ;

declaration
    : address
    | buffer
    | memory
    ;

//==================================================================================================
// Address
//==================================================================================================

address
    : ^(MMU_ADDRESS addressId=ID {declareAndPushSymbolScope($addressId, MmuSymbolKind.ADDRESS);}
        (
            ^(MMU_WIDTH expr[0])
          | ^(MMU_SEGMENT ID expr[0] expr[0])
          | ^(MMU_FORMAT (ID expr[0] expr[0]?)+)
        )*
      )
    ; finally {popSymbolScope();}

//==================================================================================================
// Buffer
//==================================================================================================

buffer
    : ^(MMU_BUFFER bufferId=ID {declareAndPushSymbolScope($bufferId, MmuSymbolKind.BUFFER);}
        ID ID
        (
            ^(MMU_WAYS expr[0])
          | ^(MMU_SETS expr[0])
          | ^(MMU_FORMAT (ID expr[0] expr[0]?)+)
          | ^(MMU_INDEX expr[0])
          | ^(MMU_MATCH expr[0])
          | ^(MMU_POLICY ID)
        )*
      )
    ; finally {popSymbolScope();}

//==================================================================================================
// Memory
//==================================================================================================

memory
    : ^(MMU_MEMORY memoryId=ID {declareAndPushSymbolScope($memoryId, MmuSymbolKind.MEMORY);}
        ID ID
        (^(MMU_VAR ID ID))*
        (
            ^(MMU_READ sequence)
          | ^(MMU_WRITE sequence)
        )*
      )
    ; finally {popSymbolScope();}

//==================================================================================================
// Statements
//==================================================================================================

sequence
    : ^(SEQUENCE statement*)
    ;

statement
    : attributeCallStatement
    | assignmentStatement
    | conditionalStatement
    | functionCallStatement
    ;

attributeCallStatement
    : ID
    | ^(DOT ID ID)
    | ^(INSTANCE_CALL ^(INSTANCE ID expr[0]*) ID?)
    ;

assignmentStatement
    : ^(ASSIGN location expr[0])
    ;

conditionalStatement
    : ifStmt
    ;

ifStmt
    : ^(IF expr[0] sequence elseIfStmt* elseStmt?)
    ;

elseIfStmt
    : ^(ELSEIF expr[0] sequence)
    ;

elseStmt
    : ^(ELSE sequence)
    ;

functionCallStatement
    :  ^(EXCEPTION STRING_CONST)
    ;

//==================================================================================================
// Expressions
//==================================================================================================

expr [int depth]
    : ifExpr[depth+1]  
    | binaryExpr[depth+1]
    | unaryExpr[depth+1]
    | atom
    ;

ifExpr [int depth]
    : ^(IF expr[depth] expr[depth] elseIfExpr[depth]* elseExpr[depth]?)
    ;

elseIfExpr [int depth]
    : ^(ELSEIF expr[depth] expr[depth])
    ;

elseExpr [int depth]
    : ^(ELSE expr[depth])
    ;

binaryExpr [int depth]
    : ^(OR            expr[depth+1] expr[depth+1])
    | ^(AND           expr[depth+1] expr[depth+1])
    | ^(VERT_BAR      expr[depth+1] expr[depth+1])
    | ^(UP_ARROW      expr[depth+1] expr[depth+1])
    | ^(AMPER         expr[depth+1] expr[depth+1])
    | ^(EQ            expr[depth+1] expr[depth+1])
    | ^(NEQ           expr[depth+1] expr[depth+1])
    | ^(LEQ           expr[depth+1] expr[depth+1])
    | ^(GEQ           expr[depth+1] expr[depth+1])
    | ^(LEFT_BROCKET  expr[depth+1] expr[depth+1])
    | ^(RIGHT_BROCKET expr[depth+1] expr[depth+1])
    | ^(LEFT_SHIFT    expr[depth+1] expr[depth+1])
    | ^(RIGHT_SHIFT   expr[depth+1] expr[depth+1])
    | ^(ROTATE_LEFT   expr[depth+1] expr[depth+1])
    | ^(ROTATE_RIGHT  expr[depth+1] expr[depth+1])
    | ^(PLUS          expr[depth+1] expr[depth+1])
    | ^(MINUS         expr[depth+1] expr[depth+1])
    | ^(MUL           expr[depth+1] expr[depth+1])
    | ^(DIV           expr[depth+1] expr[depth+1])
    | ^(REM           expr[depth+1] expr[depth+1])
    | ^(DOUBLE_STAR   expr[depth+1] expr[depth+1])
    ;

unaryExpr [int depth]
    : ^(UPLUS  expr[depth+1])
    | ^(UMINUS expr[depth+1])
    | ^(TILDE  expr[depth+1])
    | ^(NOT    expr[depth+1])
    ;

atom 
    : c = constant
    | location
    ;

//==================================================================================================
// Constant    
//==================================================================================================

constant returns [Node res]
@init {
int radix = 10;
}
@after {
$res = NodeValue.newInteger($t.text, radix);
}
    : t=CARD_CONST   { radix = 10; }
    | t=BINARY_CONST { radix = 2; }
    | t=HEX_CONST    { radix = 16; }
    ;

//==================================================================================================
// Location (variable)
//==================================================================================================

location 
    : ^(LOCATION locationExpr[0])
    ;

locationExpr [int depth]
    : ^(DOUBLE_COLON locationVal locationExpr[depth+1])
    | locationVal
    ;

locationVal
    : ^(LOCATION_BITFIELD locationAtom expr[0] expr[0]?)
    | locationAtom
    ;

locationAtom
    : ID
    | ^(DOT ID ID)
    | ^(INSTANCE_CALL ^(INSTANCE ID expr[0]*) ID?)
    ;

//==================================================================================================
// The End
//==================================================================================================
