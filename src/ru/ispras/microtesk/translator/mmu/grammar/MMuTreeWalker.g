/*======================================================================================*/
/* README SECTION                                                                       */
/*                                                                                      */
/* TODO:                                                                                */
/* - Brief description of the tree rules' structure and format                          */
/* - Instructions on how to debug and extend the rules                                  */
/* - "TODO" notes                                                                       */     
/*======================================================================================*/

tree grammar MMuTreeWalker;

/*======================================================================================*/
/* Options                                                                              */
/*======================================================================================*/

options {
  language=Java;
  tokenVocab=MMuParser;
  ASTLabelType=CommonTree;
  superClass=TreeWalkerBase;
}

@rulecatch {
catch(SemanticException se) {
    reportError(se);
    recover(input,se);
}
catch (RecognitionException re) { // Default behavior
    reportError(re);
    recover(input,re);
}
}

/*======================================================================================*/
/* Header for the generated tree walker Java class file (header comments, imports, etc).*/
/*======================================================================================*/

@header {
/*
 * Copyright (c) 2012 ISPRAS (www.ispras.ru)
 * 
 * Institute for System Programming of Russian Academy of Sciences
 * 
 * 25 Alexander Solzhenitsyn st. Moscow 109004 Russia
 * 
 * All rights reserved.
 *
 * WARNING: THIS FILE IS AUTOMATICALLY GENERATED. PLEASE DO NOT MODIFY IT. 
 */

package ru.ispras.microtesk.translator.mmu.grammar;
import ru.ispras.microtesk.translator.antlrex.SemanticException;

import ru.ispras.microtesk.translator.mmu.antlrex.TreeWalkerBase;
import ru.ispras.microtesk.translator.mmu.ESymbolKind;

import ru.ispras.microtesk.translator.mmu.ir.*;
import ru.ispras.microtesk.translator.mmu.ir.expression.*;
}

/*======================================================================================*/
/* Members of the generated tree walker class.                                          */
/*======================================================================================*/

@members {
}

/*======================================================================================*/
/* Root Rules of Processor Specification                                                */ 
/*======================================================================================*/

startRule 
	:  bufferoraddress*
	;
	
bufferoraddress
    :  addressRule
    |  bufferRule
    ;

addressRule
@init {
System.out.println("MMu: " + $addressRule.text);
}
	:  address
	;

bufferRule 
@init {
System.out.println("MMu: " + $bufferRule.text);
}
    :  buffer
    ;
  
/*=======================================================================================*/
/* Address Rules                                                                         */
/*=======================================================================================*/
    
address
			:  ^(ADDRESS id=ID addr=addressExpr)
  
{
checkNotNull($id, $addr.res, $addr.text);
getIR().add($id.text, $addr.res);
}
			;
			
addressExpr returns [AddressExpr res]
			: ce = constExpr[0] { if (null != $ce.res) { $res = new AddressExpr($ce.res.getText(), $ce.res.getValue());} }
	  		;
	  		
/*======================================================================================*/
/* Buffer Rules                                                                         */
/*======================================================================================*/

buffer
    		:  ^(BUFFER id=ID buf=bufferExpr)
{  
checkNotNull($id, $buf.res, $buf.text);
getIR().add($id.text, $buf.res);

System.out.println("buf OK!");
}
    ;

bufferExpr returns [BufferExpr res]
    :
    	p=parameter*    { $res = new BufferExpr($p.res); }
    ;

parameter returns [ParameterExpr res]
:	as=associativity
|	s=sets
|	l=line
|	in=index
|	ma=match
|	po=policy { $res = new ParameterExpr($as.res, $s.res, $l.res, $in.res, $ma.res, $po.res); }  
;

	
/*======================================================================================*/
/* Associativity Sets Rules                                                                     */
/*======================================================================================*/

associativity returns [AssociativityExpr res]
	:  ^(id=ASSOCIATIVITY ass=associativityExpr) {$res = $ass.res; }
{ 
checkNotNull($id, $ass.res, $ass.text);
getIR().add($id.text, $ass.res);

System.out.println("associativity OK!");
}
    ;

associativityExpr returns [AssociativityExpr res]
	:  ce = constExpr[0] { if (null != $ce.res) { $res = new AssociativityExpr($ce.res.getText(), $ce.res.getValue());} }
	;


sets returns [SetsExpr res]
		:  ^(id=SETS sete=setsExpr) {$res = $sete.res; }
{ 
checkNotNull($id, $sete.res, $sete.text);
getIR().add($id.text, $sete.res);

System.out.println("sets OK!");
}
    ;

setsExpr returns [SetsExpr res]
	:  ce = constExpr[0] { if (null != $ce.res) { $res = new SetsExpr($ce.res.getText(), $ce.res.getValue());} }
	;
	

/*======================================================================================*/
/*  Line Rules                                                                          */
/*======================================================================================*/
	
line returns [LineExpr res]
	:  ^(id=LINE l=lineExpr)  {$res = $l.res; }
{
checkNotNull($id, $l.res, $l.text);
getIR().add($id.text, $l.res);

System.out.println("line OK!");
}
	;
	
	lineExpr returns [LineExpr res]
		:	
			t=tag 
			d=data { $res = new LineExpr($t.res, $d.res); }
		;
		
tag returns [LengthExpr res]
	:  ^(id=TAG lengthExpr) {$res = $lengthExpr.res; }
    ;
    	
    	lengthExpr returns [LengthExpr res]
    	:  ce = constExpr[0] { if (null != $ce.res) { $res = new LengthExpr();} }
		;		
	
data returns [LengthExpr res]
	:  ^(id=DATA lengthExpr) {$res = $lengthExpr.res; }
	;
	
/*======================================================================================*/
/* Index Match Rules																	*/
/*======================================================================================*/

index returns [IndexExpr res]
	:  ^(INDEX id=ID ind=indexExpr) {$res = $ind.res; }
{ 
getIR().add($id.text, $ind.res);

System.out.println("index OK!");
}
    ;

indexExpr returns [IndexExpr res]
	:   ce = constDotExpr { if (null != $ce.res) { $res = new IndexExpr();} }
	;	
	
match returns [MatchExpr res]
	:	^(MATCH id=ID mat=matchExpr) {$res = $mat.res; }
{ 
getIR().add($id.text, $mat.res);

System.out.println("match OK!");
}	
	;
	
matchExpr returns [MatchExpr res]
	:	ce = constDotExpr { if (null != $ce.res) { $res = new MatchExpr();} }
	;

/*======================================================================================*/
/*  Policy Type Rules                                                                   */
/*======================================================================================*/

policy returns [EPolicyType res]
	:  ^(id=POLICY pol=policyExpr) {$res = $pol.res; }
{ 
checkNotNull($id, $pol.res, $pol.text);
getIR().add($id.text, $pol.res);

System.out.println("policy OK!");
}
    ;

policyExpr returns [EPolicyType res]
	:  LRU  {$res = EPolicyType.LRU;}
	|  PLRU  {$res = EPolicyType.PLRU;}
	|  FIFO {$res = EPolicyType.FIFO;}
	;
catch [RecognitionException re] {
    reportError(re);
    recover(input,re);
}	

/*======================================================================================*/
/* Constant Rules                                                                       */
/*======================================================================================*/
   
constExpr [int depth] returns [ConstExpr res]
scope {
int exprCount;
}
@init {
if (0 == depth) { $constExpr::exprCount = 0; }
else            { $constExpr::exprCount++; }
}
    :   be=constBinaryExpr[depth] { $res = $be.res; }  
    |   ue=constUnaryExpr[depth]  { $res = $ue.res; }
    |   a=constAtom               { $res = $a.res;  }
    ;

constBinaryExpr [int depth] returns [ConstExpr res]
@init {
final ConstExprFactory factory = getConstExprFactory();
}
@after {
checkNotNull($op, $e1.res, $e1.text);
checkNotNull($op, $e2.res, $e2.text);
$res=factory.createBinaryExpr(where($op), $op.text, $e1.res, $e2.res);
}
    :   ^(op=OR e1=constExpr[depth + 1] e2=constExpr[depth + 1])
    |   ^(op=AND e1=constExpr[depth + 1] e2=constExpr[depth + 1])
    |   ^(op=VERT_BAR e1=constExpr[depth + 1] e2=constExpr[depth + 1])
    |   ^(op=UP_ARROW e1=constExpr[depth + 1] e2=constExpr[depth + 1])
    |   ^(op=AMPER e1=constExpr[depth + 1] e2=constExpr[depth + 1])
    |   ^(op=EQ e1=constExpr[depth + 1] e2=constExpr[depth + 1])
    |   ^(op=NEQ e1=constExpr[depth + 1] e2=constExpr[depth + 1])
    |   ^(op=LEQ e1=constExpr[depth + 1] e2=constExpr[depth + 1])
    |   ^(op=GEQ e1=constExpr[depth + 1] e2=constExpr[depth + 1])
    |   ^(op=LEFT_BROCKET e1=constExpr[depth + 1] e2=constExpr[depth + 1])
    |   ^(op=RIGHT_BROCKET e1=constExpr[depth + 1] e2=constExpr[depth + 1])
    |   ^(op=LEFT_SHIFT e1=constExpr[depth + 1] e2=constExpr[depth + 1])
    |   ^(op=RIGHT_SHIFT e1=constExpr[depth + 1] e2=constExpr[depth + 1])
    |   ^(op=ROTATE_LEFT e1=constExpr[depth + 1] e2=constExpr[depth + 1])
    |   ^(op=ROTATE_RIGHT e1=constExpr[depth + 1] e2=constExpr[depth + 1])
    |   ^(op=PLUS e1=constExpr[depth + 1] e2=constExpr[depth + 1])
    |   ^(op=MINUS e1=constExpr[depth + 1] e2=constExpr[depth + 1])
    |   ^(op=MUL e1=constExpr[depth + 1] e2=constExpr[depth + 1])
    |   ^(op=DIV e1=constExpr[depth + 1] e2=constExpr[depth + 1])
    |   ^(op=REM e1=constExpr[depth + 1] e2=constExpr[depth + 1])
    |   ^(op=DOUBLE_STAR e1=constExpr[depth + 1] e2=constExpr[depth + 1])
    ;

constUnaryExpr [int depth] returns [ConstExpr res]
@init {
final ConstExprFactory factory = getConstExprFactory();
}
@after {
checkNotNull($op, $e.res, $e.text);
$res=factory.createUnaryExpr(where($op), $op.text, $e.res);
}
    :   ^(op=UNARY_PLUS e=constExpr[depth + 1])
    |   ^(op=UNARY_MINUS e=constExpr[depth + 1])
    |   ^(op=TILDE e=constExpr[depth + 1])
    |   ^(op=NOT e=constExpr[depth + 1])
    ;

constAtom returns [ConstExpr res]
@init {
final ConstExprFactory factory = getConstExprFactory();
}
    :   token=ID             { $res=factory.createReference($token.text);        }
    |   token=CARD_CONST     { $res=factory.createIntegerConst($token.text, 10); }
    |   token=BINARY_CONST   { $res=factory.createIntegerConst($token.text, 2);  }
    |   token=HEX_CONST      { $res=factory.createIntegerConst($token.text, 16); }
    |   token=FIXED_CONST    { $res=factory.createRealConst($token.text);        }
    ;

constDotExpr returns [ConstExpr res]
@init {
final ConstExprFactory factory = getConstExprFactory();
}
    : ^(DOUBLE_DOT constExpr[0] constExpr[0])
	;