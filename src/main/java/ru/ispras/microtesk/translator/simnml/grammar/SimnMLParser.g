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

/*======================================================================================*/
/* README SECTION                                                                       */
/*                                                                                      */
/* TODO:                                                                                */
/* - Brief description of the parser rules' structure and format                        */
/* - Instructions on how to debug and extend the rules                                  */
/* - "TODO" notes                                                                       */
/*======================================================================================*/

parser grammar SimnMLParser;

/*======================================================================================*/
/* Options                                                                              */
/*======================================================================================*/

options {
  language=Java;
  tokenVocab=SimnMLLexer;
  output=AST;
  superClass=ParserBase;
  backtrack=true;
}

/*======================================================================================*/
/* Additional tokens. Lists additional tokens to be inserted in the AST by the parser   */
/* to express some syntactic properties.                                                */
/*======================================================================================*/

tokens {
//RANGE; // root node for range type definitions ([a..b]). Not supported in this version.

  UPLUS;  // token for the unary plus operator (used in expressions)
  UMINUS; // token for the unary minus operator (used in expressions)

  SIZE_TYPE; // node for constructions that specify type and size of memory resources (reg, mem, var)

  LOCATION;          // node for locations (used as an expression atom)
  LOCATION_INDEX;    // node for constructions that specify access for a location by index (e.g. GPR[1])
  LOCATION_BITFIELD; // node for constructions that specify access to bitfields of a location (e.g. GPR[0]<0..8>)

  LABEL; // imaginary token for let expression that specify an alias for some specified location
  CONST; // imaginary token to distingush references to Let constants (used as an expression atom)

  ARGS;      // node for the list of args specified in AND-rules for mode and ops
  ARG_MODE;  // node to distinguish a mode argument 
  ARG_OP;    // node to distinguish an op argument 

  ALTERNATIVES; // node for the list of alternatives specified in OR-rules for modes and ops

  ATTRS;    // node for the list of attributes MODE and OP structures
  RETURN;   // node for the "return" attribute of MODE structure
  SEQUENCE; // node for the sequence of statements
}

/*======================================================================================*/
/* Default Exception Handler Code                                                       */
/*======================================================================================*/

@rulecatch {
catch (SemanticException re) { // Default behavior
    reportError(re);
    recover(input,re);
    // We don't insert error nodes in the IR (walker tree). 
    //retval.tree = (Object)adaptor.errorNode(input, retval.start, input.LT(-1), re);
}
catch (RecognitionException re) { // Default behavior
    reportError(re);
    recover(input,re);
    // We don't insert error nodes in the IR (walker tree). 
    //retval.tree = (Object)adaptor.errorNode(input, retval.start, input.LT(-1), re);
}
}

/*======================================================================================*/
/* Header for the generated parser Java class file (header comments, imports, etc).     */  
/*======================================================================================*/

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

package ru.ispras.microtesk.translator.simnml.grammar;

import ru.ispras.microtesk.translator.antlrex.SemanticException;
import ru.ispras.microtesk.translator.simnml.antlrex.ParserBase;
import ru.ispras.microtesk.translator.simnml.ESymbolKind;
}

@members {
/**
This is a global flag needed to indicate that the "bitFieldExpr" rule is being executed.
It is needed to avoid problems related to translation of bit field expressions.
The issue is that the parser incorrectly recognizes constructions like that: "GPR<1..2> + 1".
It recognizes the ">" token as the "greater than" operator instead of bit field expression
termination characher. So it recognizes "2> + 1" as a correct expression, but then fails
to continue parsing as it cannot find expected termination character. To overcome the problem,
comparison operators are excluded form expression rules when a bit field expression is being parsed.
This imposes a certain restriction. However, it is not important bacause bit field expresions
are expected to be evaluated to an integer value, but a boolean. Thus, comparison operators
should not be used in this case from a semantic point of view.
*/
private boolean inBitfield = false;
}

/*======================================================================================*/
/* Root rules of processor specifications                                               */
/*======================================================================================*/

// Start rule
startRule
    :  procSpec* EOF
    ;

procSpec
    :  letDef
    |  typeDef 
    |  memDef
    |  regRef
    |  varDef
    |  modeDef
    |  opDef
    ;
catch [SemanticException re] {
    reportError(re);
    recover(input,re);
}
catch [RecognitionException re] {
    reportError(re);
    recover(input,re);
}

/*======================================================================================*/
/* Let-rules (statically calculated constatns and aliases for memory locations)         */
/*======================================================================================*/

letDef
    :  LET^ id=ID ASSIGN! le=letExpr { declare($id, $le.res, false); }
    ;

letExpr returns [ESymbolKind res]
    :  expr    { $res = ESymbolKind.LET_CONST;  } // Statically calculated constant expression. E.g. let A = 2 ** 4
    |  STRING_CONST { $res = ESymbolKind.LET_STRING; } // Some string constant. E.g. let A = "some text"
//  |  IF^ constNumExpr THEN! letExpr (ELSE! letExpr)? ENDIF! // TODO: NOT SUPPORTED IN THE CURRENT VERSION
//  |  SWITCH Construction // TODO: NOT SUPPORTED IN THE CURRENT VERSION
    ;

/*======================================================================================*/
/*  Type rules                                                                          */
/*======================================================================================*/

typeDef
    :  TYPE^ id=ID ASSIGN! typeExpr { declare($id, ESymbolKind.TYPE, false); }
    ;

typeExpr
    :  id=ID  { checkDeclaration($id,ESymbolKind.TYPE); }
//  |  BOOL // TODO: NOT SUPPORTED IN THE CURRENT VERSION 
    |  INT^ LEFT_PARENTH! expr RIGHT_PARENTH!
    |  CARD^ LEFT_PARENTH! expr RIGHT_PARENTH!
    |  FIX^ LEFT_PARENTH! expr COMMA! expr RIGHT_PARENTH!
    |  FLOAT^ LEFT_PARENTH! expr COMMA! expr RIGHT_PARENTH!
//  |  LEFT_HOOK constExpr DOUBLE_DOT constExpr RIGHT_HOOK -> ^(RANGE constExpr constExpr) // TODO: NOT SUPPORTED IN THE CURRENT VERSION  
//  |  ENUM^ LEFT_PARENTH! identifierList RIGHT_PARENTH! // TODO: NOT SUPPORTED IN THE CURRENT VERSION  
    ;

// TODO: NOT SUPPORTED IN THE CURRENT VERSION 
// identifierList
//  :  ID^ (ASSIGN! CARD_CONST)? (COMMA! identifierList)?
//  ;

/*======================================================================================*/
/* Location rules (memory, registers, variables)                                        */
/*======================================================================================*/

memDef
    :  MEM^ id=ID LEFT_HOOK! st=sizeType {checkNotNull($st.start, $st.tree);} RIGHT_HOOK!
                                         {declare($id, ESymbolKind.MEMORY, false);} alias?
    ;

regRef
    :  REG^ id=ID LEFT_HOOK! st=sizeType {checkNotNull($st.start, $st.tree);} RIGHT_HOOK! 
                                         {declare($id, ESymbolKind.MEMORY, false);}
    ;

varDef
    :  VAR^ id=ID LEFT_HOOK! st=sizeType {checkNotNull($st.start, $st.tree);} RIGHT_HOOK!
                                         {declare($id, ESymbolKind.MEMORY, false);}
    ;

sizeType
    :  (expr COMMA)? te=typeExpr {checkNotNull($te.start, $te.tree);}
        -> ^(SIZE_TYPE expr? typeExpr)
    ;

alias
    :  ALIAS^ ASSIGN! locationAtom
    ;

/*======================================================================================*/
/*  Mode rules                                                                          */
/*======================================================================================*/

modeDef
    :  MODE^ id=ID {declareAndPushSymbolScope($id, ESymbolKind.MODE);} modeSpecPart
    ;  finally     {popSymbolScope();}

modeSpecPart
    :  andRule modeReturn? attrDefList
    |  orRule
    ;

modeReturn
    :  ASSIGN expr -> ^(RETURN expr)
    ;

/*======================================================================================*/
/*  Op rules                                                                            */
/*======================================================================================*/

opDef
    :  OP^ id=ID {declareAndPushSymbolScope($id, ESymbolKind.OP);} opSpecPart
    ;  finally   {popSymbolScope();}

opSpecPart
    :  andRule attrDefList
    |  orRule
    ;

/*======================================================================================*/
/* Or rules (for modes and ops)                                                         */
/*======================================================================================*/

orRule
    :  ASSIGN ID (VERT_BAR ID)* -> ^(ALTERNATIVES ID+)
    ;

/*======================================================================================*/
/* And rules (for modes and ops)                                                        */
/*======================================================================================*/

andRule
    :  LEFT_PARENTH (argDef (COMMA argDef)*)? RIGHT_PARENTH -> ^(ARGS argDef*)
    ;

argDef
    :  ID^ COLON! argType
    ;

argType
    :  {isDeclaredAs(input.LT(1), ESymbolKind.MODE)}? ID -> ^(ARG_MODE ID)
    |  {isDeclaredAs(input.LT(1), ESymbolKind.OP)}? ID -> ^(ARG_OP ID)
    |  typeExpr
    ;

/*======================================================================================*/
/* Attribute rules (for modes and ops)                                                  */
/*======================================================================================*/

attrDefList
    :  attrDef* -> ^(ATTRS attrDef*)
    ;

attrDef
    @after {declare($id, ESymbolKind.ATTRIBUTE, false);}
    :  id=SYNTAX^ ASSIGN! syntaxDef
    |  id=IMAGE^ ASSIGN! imageDef
    |  id=ACTION^ ASSIGN! actionDef
    |  id=ID^ ASSIGN! actionDef
//  |  USES ASSIGN usesDef     // NOT SUPPORTED IN THE CURRENT VERSION
    ;

syntaxDef
    :  ID DOT^ SYNTAX
    |  attrExpr
    ;

imageDef
    :  ID DOT^ IMAGE
    |  attrExpr
    ;

actionDef
    :  ID DOT^ ACTION
    |  LEFT_BRACE! sequence RIGHT_BRACE!
    ;

/*======================================================================================*/
/* Expresion-like attribute rules(format expressions in the symtax and image attributes)*/
/*======================================================================================*/

attrExpr
    :  STRING_CONST
    |  FORMAT^ LEFT_PARENTH! STRING_CONST (COMMA! formatIdList)? RIGHT_PARENTH!
    ;

formatIdList
    :  formatId (COMMA! formatId)*
    ;

formatId
    :  ID DOT^ (SYNTAX | IMAGE)
    |  expr
    ;

/*======================================================================================*/
/* Sequence statements (for action-like attributes)                                     */
/*======================================================================================*/

sequence
    : (statement SEMI)* -> ^(SEQUENCE statement*) 
    ;

statement
    :  ID
    |  ID DOT^ (ACTION | ID)
    |  location ASSIGN^ expr
    |  conditionalStatement
    |  functionCallStatement
    ;

conditionalStatement
    :  ifStmt
    ;

ifStmt
    :  IF^ expr THEN! sequence elseIfStmt* elseStmt? ENDIF!
    ;

elseIfStmt
    :  ELSEIF^ expr THEN! sequence
    ;

elseStmt
    :  ELSE^ sequence
    ;
    
functionCallStatement
    :  EXCEPTION^ LEFT_PARENTH! STRING_CONST RIGHT_PARENTH!
//  |  ERROR^ LEFT_PARENTH! STRING_CONST RIGHT_PARENTH!
    ;

/*======================================================================================*/
/* Expression rules                                                                     */
/*======================================================================================*/

expr
    :  nonNumExpr
    |  numExpr
    ;

/*======================================================================================*/
/* Non-numeric expressions                                                              */
/*======================================================================================*/

nonNumExpr
    :  ifExpr
    ;

ifExpr
    :  IF^ expr THEN! expr elseIfExpr* elseExpr? ENDIF!
    ;

elseIfExpr
    :  ELSEIF^ expr THEN! expr
    ;

elseExpr
    :  ELSE^ expr
    ;

/*======================================================================================*/
/* Numeric expressions                                                                  */
/*======================================================================================*/

numExpr
    :  orLogicExpr
    ;

orLogicExpr
    :  andLogicExpr (OR^ andLogicExpr)*
    ;

andLogicExpr
    :  orBitExpr (AND^ orBitExpr)*
    ;

orBitExpr
    :  xorBitExpr (VERT_BAR^ xorBitExpr)*
    ;

xorBitExpr
    :  andBitExpr (UP_ARROW^ andBitExpr)*
    ;

andBitExpr
    :  relationExpr (AMPER^ relationExpr)*
    ;

relationExpr
    :  comparisionExpr ((EQ^ | NEQ^) comparisionExpr)*
    ;

comparisionExpr
    :  {!inBitfield}? => shiftExpr ((LEQ^ | GEQ^ | LEFT_BROCKET^ | RIGHT_BROCKET^) shiftExpr)*
    |  shiftExpr
    ;

shiftExpr
    :  plusExpr ((LEFT_SHIFT^ | RIGHT_SHIFT^ | ROTATE_LEFT^ | ROTATE_RIGHT^) plusExpr)*
    ;

plusExpr
    :  mulExpr ((PLUS^ | MINUS^) mulExpr)*
    ;

mulExpr
    :  powExpr ((MUL^ | DIV^ | REM^) powExpr)*
    ;

powExpr
    :  unaryExpr (DOUBLE_STAR^ unaryExpr)*
    ;

unaryExpr
    :  PLUS   unaryExpr -> ^(UPLUS unaryExpr)
    |  MINUS  unaryExpr -> ^(UMINUS unaryExpr)
    |  TILDE^ unaryExpr
    |  NOT^   unaryExpr
    |  atom
    ;

atom
    :  LEFT_PARENTH! expr RIGHT_PARENTH!
    |  letConst
    |  location
    |  CARD_CONST
    |  BINARY_CONST
    |  HEX_CONST
    |  COERCE^ LEFT_PARENTH! typeExpr COMMA! expr RIGHT_PARENTH!
    ;

letConst
    : {isDeclaredAs(input.LT(1), ESymbolKind.LET_CONST)}? ID -> ^(CONST ID)
    ;

/*======================================================================================*/
/* Location rules (rules for accessing model memory)                                    */
/*======================================================================================*/

location
    :  locationExpr -> ^(LOCATION locationExpr)
    ;

locationExpr
    :  locationVal (DOUBLE_COLON^ locationExpr)*
    ;

/*  If the bitFieldExpr expression fires, we rewrite the rule as a bitfield expression,
    otherwise we leave it as it is. */
locationVal
    :  locationAtom (bitFieldExpr -> ^(LOCATION_BITFIELD locationAtom bitFieldExpr) | -> locationAtom)
    ;

locationAtom
    :  ID
    |  ID LEFT_HOOK expr RIGHT_HOOK -> ^(LOCATION_INDEX ID expr)
    ;

bitFieldExpr
@init   {inBitfield = true;}
    :  LEFT_BROCKET! expr (DOUBLE_DOT! expr)? RIGHT_BROCKET!
    ;
finally {inBitfield = false;}

/*======================================================================================*/
