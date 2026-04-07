package lyc.compiler;

import java_cup.runtime.Symbol;
import lyc.compiler.ParserSym;
import lyc.compiler.model.*;
import lyc.compiler.constants.Constants;
import lyc.compiler.files.SymbolTableGenerator;

%%

%public
%class Lexer
%unicode
%cup
%line
%column
%throws CompilerException
%state COMMENT
%eofval{
  return symbol(ParserSym.EOF);
%eofval}


%{
  private Symbol symbol(int type) {
    return new Symbol(type, yyline, yycolumn);
  }
  private Symbol symbol(int type, Object value) {
    return new Symbol(type, yyline, yycolumn, value);
  }
  private void saveToken() {
  	SymbolTableGenerator.getInstance().addToken(yytext());
  }
  private void saveTokenCTE(String dataType){
  	SymbolTableGenerator.getInstance().addToken(yytext(),dataType);
  }
  private boolean isValidStringLength() {
  	return yylength() <= Constants.MAX_STRING_LITERAL_LENGTH;
  }
%}


LineTerminator = \r|\n|\r\n
InputCharacter = [^\r\n]
Identation =  [ \t\f]

Plus = "+"
Mult = "*"
Sub = "-"
Div = "/"
Assig = ":="
Eq = "="
Gt = ">"
Lt = "<"
Ge = ">="
Le = "<="
And = "AND"
Or = "OR"
Not = "NOT"
Else = "else"

OpenBracket = "("
CloseBracket = ")"
OpenBrace = "{"
CloseBrace = "}"
OpenSquare  = "["
CloseSquare = "]"

Letter = [a-zA-Z]
Digit = [0-9]

Comma = ","
Colon = ":"
Semicolon = ";"


WhiteSpace = {LineTerminator} | {Identation}
Identifier = {Letter} ({Letter}|{Digit})*
IntegerConstant = {Digit}+
FloatConstant    = {Digit}+"."{Digit}*|("."{Digit}+)
Text =	[\"].*[\"]

Read = "read"
Write = "write"
If = "if"
While = "while"
IsZero = "isZero"
For = "FOR"
To = "TO"
Step = "STEP"
Next = "NEXT"

/* Nueva palabra reservada: función del sistema */
TriangleAreaMaximum = "triangleAreaMaximum"

/* Data Types */
TypeInt          = "Int"
TypeFloat        = "Float"
TypeString       = "String"

Init             = "init"

%%

/* keywords */

<YYINITIAL> {

 /* PALABRAS RESERVADAS */
 {Read}           { return symbol(ParserSym.READ); }
 {Write}          { return symbol(ParserSym.WRITE); }
 {Init}           { return symbol(ParserSym.INIT); }
 {TypeInt}        { return symbol(ParserSym.TYPE_INT); }
 {TypeFloat}      { return symbol(ParserSym.TYPE_FLOAT); }
 {TypeString}     { return symbol(ParserSym.TYPE_STRING); }
 {If}             { return symbol(ParserSym.IF); }
 {Else}           { return symbol(ParserSym.ELSE); }
 {And}	          {	return symbol(ParserSym.AND,yytext()); }
 {Or}	          {	return symbol(ParserSym.OR,yytext());  }
 {Not}            { return symbol(ParserSym.NOT,yytext()); }
 {While}          { return symbol(ParserSym.WHILE); }
 {IsZero}         { return symbol(ParserSym.IS_ZERO); }
 {For}            { return symbol(ParserSym.FOR); }
 {To}             { return symbol(ParserSym.TO); }
 {Step}           { return symbol(ParserSym.STEP); }
 {Next}           { return symbol(ParserSym.NEXT); }
 {TriangleAreaMaximum} { return symbol(ParserSym.TRIANGLE_AREA_MAXIMUM); }  /* NUEVO */

 /* IDENTIFICADOR */
 {Identifier}     { return symbol(ParserSym.IDENTIFIER, yytext()); }

 /* CONSTANTES Y LITERALES */

 /* INT con validación de cotas */
 {IntegerConstant} {
    try {
        long v = Long.parseLong(yytext());
        if (v < Constants.INT_MIN || v > Constants.INT_MAX) {
            throw new NumberOutOfRangeException("Integer constant out of bounds: " + yytext()
              + " (allowed " + Constants.INT_MIN + ".." + Constants.INT_MAX + ")");
        }
    } catch (NumberFormatException nfe) {
        throw new InvalidNumericConstantException("Invalid integer constant: " + yytext());
    }
    saveTokenCTE("Int");
    return symbol(ParserSym.INTEGER_CONSTANT, yytext());
 }

 /* FLOAT con validación de cotas (|x| <= FLOAT_ABS_MAX) */
 {FloatConstant} {
    try {
        java.math.BigDecimal v = new java.math.BigDecimal(yytext().replace("+",""));
        if (v.abs().compareTo(Constants.FLOAT_ABS_MAX) > 0) {
            throw new NumberOutOfRangeException("Float constant out of bounds: " + yytext()
              + " (|x| <= " + Constants.FLOAT_ABS_MAX.toPlainString() + ")");
        }
    } catch (NumberFormatException | java.lang.ArithmeticException ex) {
        throw new InvalidNumericConstantException("Invalid float constant: " + yytext());
    }
    saveTokenCTE("Float");
    return symbol(ParserSym.FLOAT_CONSTANT, yytext());
 }


 /* STRING (valida longitud) */
 {Text}            {
                     if(!isValidStringLength())
                       throw new InvalidLengthException("\"" + yytext() + "\""+ " string length not allowed");
                     saveTokenCTE("string");
                     return symbol(ParserSym.TEXT, yytext());
                   }

/* EMPEZAR COMENTARIO MULTI-LINEA */
  "#+" { yybegin(COMMENT); }

  /* COMENTARIO EN UNA LINEA — usa la macro InputCharacter para evitar warning */
  "#" {InputCharacter}* { /* Ignore single-line comment */ }

 /* OPERADORES ARITMÉTICOS Y DE ASIGNACIÓN */
 {Plus}           { return symbol(ParserSym.PLUS); }
 {Sub}            { return symbol(ParserSym.SUB); }
 {Mult}           { return symbol(ParserSym.MULT); }
 {Div}            { return symbol(ParserSym.DIV); }
 {Assig}          { return symbol(ParserSym.ASSIG); }

 /* OPERADORES RELACIONALES */
 {Eq}             { return symbol(ParserSym.EQ); }
 {Gt}             { return symbol(ParserSym.GT); }
 {Lt}             { return symbol(ParserSym.LT); }
 {Ge}             { return symbol(ParserSym.GE); }
 {Le}             { return symbol(ParserSym.LE); }

 /* SÍMBOLOS DE PUNTUACIÓN Y AGRUPACIÓN */
 {Comma}          { return symbol(ParserSym.COMMA); }
 {Colon}          { return symbol(ParserSym.COLON); }
 {OpenBracket}    { return symbol(ParserSym.OPEN_BRACKET); }
 {CloseBracket}   { return symbol(ParserSym.CLOSE_BRACKET); }
 {OpenBrace}      { return symbol(ParserSym.OPEN_BRACE); }
 {CloseBrace}     { return symbol(ParserSym.CLOSE_BRACE); }
 {OpenSquare}     { return symbol(ParserSym.OPEN_SQUARE); }
 {CloseSquare}    { return symbol(ParserSym.CLOSE_SQUARE); }
 {Semicolon}      { return symbol(ParserSym.SEMICOLON); }

 /* ESPACIOS EN BLANCO (IGNORAR) */
 {WhiteSpace}     { /* ignore */ }

}

/* COMENTARIO MULTI-LINEA — versión simple y sin warnings */
<COMMENT> {
  "+#" { yybegin(YYINITIAL); /* End of multi-line comment */ }
  [^]  { /* ignore any char inside multi-line comment */ }
}

/* ========================================================================== */

/* error fallback */
[^]               { throw new UnknownCharacterException(yytext()); }
