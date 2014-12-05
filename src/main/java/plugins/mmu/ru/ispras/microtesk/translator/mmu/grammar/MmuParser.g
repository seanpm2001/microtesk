/*
 * Copyright 2012-2014 ISP RAS (http://www.ispras.ru)
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

parser grammar MmuParser;

//==================================================================================================
// Options
//==================================================================================================

options {
  language=Java;
  tokenVocab=MmuLexer;
  output=AST;
  superClass=ParserBase;
  backtrack=true;
}

import CommonParser;

//==================================================================================================
// Header for the Generated Java File
//==================================================================================================

@header {
/*
 * Copyright 2012-2014 ISP RAS (http://www.ispras.ru)
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

import ru.ispras.microtesk.translator.antlrex.SemanticException;
import ru.ispras.microtesk.translator.antlrex.ParserBase;
import ru.ispras.microtesk.translator.simnml.ESymbolKind;
}

//==================================================================================================
// MMU Specification
//==================================================================================================

startRule 
    : bufferOrAddress* EOF!
    ;

bufferOrAddress
    : address
    | buffer
    | memory
    ;

//==================================================================================================
// Address
//==================================================================================================

address
    : MMU_ADDRESS^ ID
        (addressParameter)*
    ;

addressParameter
    : width
    | segment
    | format
    ;

//--------------------------------------------------------------------------------------------------

width
    : MMU_WIDTH! ASSIGN! expr
    ;

//--------------------------------------------------------------------------------------------------

segment
    : MMU_SEGMENT! (segmentID=ID)? ASSIGN! LEFT_PARENTH! expr COMA! expr RIGHT_PARENTH!
    ;

//==================================================================================================
// Buffer
//==================================================================================================

buffer
    : MMU_BUFFER^ ID LEFT_PARENTH! addressType=ID addressArg=ID RIGHT_PARENTH!
        (bufferParameter)*
    ;

bufferParameter
    : ways
    | sets
    | format
    | index
    | match
    | policy
    ;

//--------------------------------------------------------------------------------------------------

ways
    : MMU_WAYS^ ASSIGN! expr
    ;

//--------------------------------------------------------------------------------------------------

sets
    : MMU_SETS^ ASSIGN! expr
    ;

//--------------------------------------------------------------------------------------------------

format
    : MMU_FORMAT^ (formatID=ID)? ASSIGN! LEFT_PARENTH!
        field (COMA! field)*
      RIGHT_PARENTH!
    ;

field
    : ID COLON! expr (ASSIGN! expr)?
    ;

//--------------------------------------------------------------------------------------------------

index
    : MMU_INDEX^ ASSIGN! expr
    ;

//--------------------------------------------------------------------------------------------------

match
    : MMU_MATCH^ ASSIGN! expr
    ;

//--------------------------------------------------------------------------------------------------

policy
    : MMU_POLICY^ ASSIGN! ID
    ;

//==================================================================================================
// Memory
//==================================================================================================

memory
    : MMU_MEMORY^ ID LEFT_PARENTH! addressType=ID addressArg=ID RIGHT_PARENTH!
        (memoryParameter)*
    ;

memoryParameter
    : read
    | write
    ;

//--------------------------------------------------------------------------------------------------

read
    : MMU_READ^ ASSIGN! LEFT_BRACE! sequence RIGHT_BRACE!
    ;

//--------------------------------------------------------------------------------------------------

write
    : MMU_WRITE^ ASSIGN! LEFT_BRACE! sequence RIGHT_BRACE!
    ;

//==================================================================================================
// The End
//==================================================================================================
