package lyc.compiler.files;

import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;

public class AsmCodeGenerator implements FileGenerator {

    private static AsmCodeGenerator instance;

    private int tempCounter = 0;
    private final Set<String> temporaries = new LinkedHashSet<>();
    private final Set<String> stringLiterals = new LinkedHashSet<>();
    private final Set<Integer> jumpTargets = new LinkedHashSet<>();
    private final LinkedHashSet<String> operands = new LinkedHashSet<>();

    private AsmCodeGenerator() {}

    public static AsmCodeGenerator getInstance() {
        if (instance == null) {
            instance = new AsmCodeGenerator();
        }
        return instance;
    }

    @Override
    public void generate(FileWriter writer) throws IOException {
        resetState();
        IntermediateCodeGenerator icg = IntermediateCodeGenerator.getInstance();
        SymbolTableGenerator symbolTable = SymbolTableGenerator.getInstance();
        List<String> rpn = icg.getRpnCode();

        performFirstPreScan(rpn);
        performTemporaryDiscovery(rpn);

        writer.write(".MODEL LARGE\n");
        writer.write(".386\n");
        writer.write(".STACK 200h\n\n");

        writer.write(".DATA\n");

        for (String op : operands) {
            String asmLabel = getValidAsmLabel(op);

            if (isNumberLiteral(op)) {
                symbolTable.addToken(op, "Float", op);
                String val = op;
                if (!val.contains(".")) {
                    val += ".0";
                }
                writer.write(String.format("%s dd %s\n", asmLabel, val));
            } else {
                writer.write(String.format("%s dd 0.0\n", asmLabel));
            }
        }

        for (String tempName : temporaries) {
            writer.write(String.format("%s dd 0.0\n", tempName));
            symbolTable.addToken(tempName);
        }

        for (String rawLiteral : stringLiterals) {
            writer.write(getStringLiteralData(rawLiteral));
        }

        writer.write("_NEWLINE db 0DH,0AH,0\n");

        writer.write("\n.CODE\n");
        writer.write("START:\n");
        writer.write("    MOV AX, @DATA\n");
        writer.write("    MOV DS, AX\n");
        writer.write("    MOV ES, AX\n");
        writer.write("    FINIT\n\n");

        Stack<String> evalStack = new Stack<>();

        for (int pc = 0; pc < rpn.size(); pc++) {

            if (jumpTargets.contains(pc)) {
                writer.write(String.format("L%d:\n", pc));
            }

            String token = rpn.get(pc);

            if (token.equals("WRITE")) {
                if (evalStack.isEmpty()) throw new RuntimeException("Error: WRITE sin operando");
                String operand = evalStack.pop(); 
                
                if (isStringLiteral(operand)) {
                    String label = getStringLiteralLabel(operand);
                    writer.write("    MOV DX, OFFSET " + label + "\n");
                    writer.write("    MOV AH, 09h\n");
                    writer.write("    INT 21h\n");
                } else {
                    writer.write("    FLD " + operand + "\n");
                    writer.write("    CALL PRINT_FLOAT\n");
                }
                writer.write("    MOV DX, OFFSET _NEWLINE\n");
                writer.write("    MOV AH, 09h\n");
                writer.write("    INT 21h\n\n");
                continue;
            }
            
            if (token.equals("READ")) {
                if (!evalStack.isEmpty()) {
                    String asmLabel = evalStack.pop();
                    writer.write("    MOV BX, OFFSET " + asmLabel + "\n");
                    writer.write("    CALL READ_INT\n\n");
                }
                continue;
            }

            if (isArithmeticOperator(token)) {
                String op2 = evalStack.pop();

                if (evalStack.isEmpty() && token.equals("-")) {
                    String aux = generateTempName();
                    writer.write(String.format("    FLD %s\n", op2));
                    writer.write("    FCHS\n");
                    writer.write(String.format("    FSTP %s\n\n", aux));
                    evalStack.push(aux);
                    continue;
                }

                String op1 = evalStack.pop();
                String aux = generateTempName();

                writer.write(String.format("    FLD %s\n", op1));
                writer.write(String.format("    FLD %s\n", op2));

                switch (token) {
                    case "+": writer.write("    FADD\n"); break;
                    case "-": writer.write("    FSUB\n"); break;
                    case "*": writer.write("    FMUL\n"); break;
                    case "/": writer.write("    FDIV\n"); break;
                }
                writer.write(String.format("    FSTP %s\n\n", aux));
                evalStack.push(aux);
                continue;
            }

            if (token.equals(":=")) {
                String dst = evalStack.pop();
                String src = evalStack.pop();
                if (!isStringLiteral(src)) {
                    writer.write(String.format("    FLD %s\n", src));
                    writer.write(String.format("    FSTP %s\n\n", dst));
                }
                continue;
            }

            if (token.equals("CMP")) {
                String op2 = evalStack.pop();
                String op1 = evalStack.pop();
                writer.write(String.format("    FLD %s\n", op1));
                writer.write(String.format("    FLD %s\n", op2));
                writer.write("    FXCH\n");
                writer.write("    FCOMPP\n");
                writer.write("    FSTSW ax\n");
                writer.write("    SAHF\n\n");
                continue;
            }

            if (isBranch(token)) {
                if (pc + 1 < rpn.size()) {
                    String dest = rpn.get(pc + 1);
                    writer.write("    " + mapBranchToAsm(token, dest) + "\n\n");
                    pc++; 
                    continue;
                }
            }


            if (isNumberLiteral(token)) {
                evalStack.push(getValidAsmLabel(token)); 
            } else if (isStringLiteral(token)) { 
                evalStack.push(token); 
            } else {
                if (!token.equals("READ")) {
                    evalStack.push(getValidAsmLabel(token));
                }
            }
        }

        if (jumpTargets.contains(rpn.size())) {
            writer.write(String.format("L%d:\n", rpn.size()));
        }

        writer.write("    MOV AX, 4C00h\n");
        writer.write("    INT 21h\n");

        writeReadProc(writer);
        writePrintProc(writer);

        writer.write("END START\n");
        writer.close();
    }

    private String getValidAsmLabel(String rawToken) {
        if (isNumberLiteral(rawToken)) {
            String label = rawToken.replace(".", "_").replace("-", "neg_");
            return "_" + label;
        } else {
            return "_" + rawToken.replace(".", "_");
        }
    }

    private void resetState() {
        tempCounter = 0;
        temporaries.clear();
        stringLiterals.clear();
        jumpTargets.clear();
        operands.clear();
    }

    private void performFirstPreScan(List<String> rpn) {
        for (int i = 0; i < rpn.size(); i++) {
            String tok = rpn.get(i);
            if (isBranch(tok)) {
                if (i + 1 < rpn.size()) {
                    String dest = rpn.get(i + 1);
                    if (dest.matches("^\\d+$")) jumpTargets.add(Integer.parseInt(dest));
                }
                i++; 
            } else if (tok.equals("WRITE")) {
            } else if (tok.equals("READ")) {
            } else if (isStringLiteral(tok)) {
                stringLiterals.add(tok);
            } else if (!isOperator(tok) && !isControlToken(tok)) {
                operands.add(tok);
            }
        }
    }

    private void performTemporaryDiscovery(List<String> rpn) {
        Stack<String> dryRunStack = new Stack<>();
        int dryRunTempCounter = 0;
        for (int pc = 0; pc < rpn.size(); pc++) {
            String token = rpn.get(pc);
            if (token.equals("WRITE")) {
                if (!dryRunStack.isEmpty()) dryRunStack.pop();
            } else if (token.equals("READ")) {
                if (!dryRunStack.isEmpty()) dryRunStack.pop();
            } else if (isArithmeticOperator(token)) {
                if (dryRunStack.isEmpty()) continue;
                if (dryRunStack.size() == 1 && token.equals("-")) {
                    dryRunStack.pop();
                } else if (dryRunStack.size() < 2) {
                    continue;
                } else {
                    dryRunStack.pop(); dryRunStack.pop();
                }
                dryRunTempCounter++;
                String tempName = "@T" + dryRunTempCounter;
                temporaries.add(tempName); 
                dryRunStack.push(tempName);
            } else if (token.equals(":=") || token.equals("CMP")) {
                if (dryRunStack.size() < 2) continue;
                dryRunStack.pop(); dryRunStack.pop();
            } else if (isBranch(token)) {
                pc++;
            } else if (!isOperator(token) && !isControlToken(token)) { 
                dryRunStack.push(token);
            }
        }
    }

    private boolean isOperator(String t) {
        return isArithmeticOperator(t) || t.equals(":=") || t.equals("CMP") || isBranch(t) || t.equals("WRITE") || t.equals("READ");
    }
    private boolean isArithmeticOperator(String t) {
        return t.equals("+") || t.equals("-") || t.equals("*") || t.equals("/");
    }
    private boolean isControlToken(String t) { return t.equals("_PLHDR"); }
    private boolean isBranch(String t) {
        return t.equals("BLE") || t.equals("BGE") || t.equals("BLT") ||
                t.equals("BGT") || t.equals("BEQ") || t.equals("BNE") || t.equals("BI");
    }
    private boolean isStringLiteral(String s) { return s != null && s.startsWith("\"") && s.endsWith("\""); }
    private boolean isNumberLiteral(String s) { return s != null && (s.matches("^-?\\d+$") || s.matches("^-?\\d+\\.\\d+$")); }

    private String mapBranchToAsm(String br, String dest) {
        switch (br) {
            case "BLE": return "JNA L" + dest;
            case "BGE": return "JAE L" + dest;
            case "BLT": return "JB L"  + dest;
            case "BGT": return "JA L"  + dest;
            case "BEQ": return "JE L"  + dest;
            case "BNE": return "JNE L" + dest;
            case "BI":  return "JMP L" + dest;
        }
        return null;
    }

    private String generateTempName() {
        tempCounter++;
        return "@T" + tempCounter;
    }

    private String getStringLiteralLabel(String raw) {
        String clean = raw;
        if (clean.startsWith("\"") && clean.endsWith("\"") && clean.length() >= 2) {
            clean = clean.substring(1, clean.length() - 1);
        }
        return "_STR_" + Math.abs(clean.hashCode());
    }

    private String getStringLiteralData(String raw) {
        String clean = raw;
        if (clean.startsWith("\"") && clean.endsWith("\"") && clean.length() >= 2) {
            clean = clean.substring(1, clean.length() - 1);
        }
        String label = getStringLiteralLabel(raw);
        return label + " db \"" + clean + "$\"\n";
    }

    private void writeReadProc(FileWriter writer) throws IOException {
        writer.write("\n; --------------------------------------------------\n");
        writer.write("; Subrutina para leer un entero del teclado\n");
        writer.write("; BX = direccion de la variable destino (dd float)\n");
        writer.write("; --------------------------------------------------\n");
        writer.write("READ_INT PROC NEAR\n");
        writer.write("    PUSH AX\n");
        writer.write("    PUSH CX\n");
        writer.write("    PUSH DX\n");
        writer.write("    PUSH SI\n");
        writer.write("    PUSH DI\n");
        writer.write("    MOV DI, BX\n");
        writer.write("    XOR SI, SI\n");
        writer.write("    XOR CX, CX\n");
        writer.write("RI_READ:\n");
        writer.write("    MOV AH, 01h\n");
        writer.write("    INT 21h\n");
        writer.write("    CMP AL, 0Dh\n");
        writer.write("    JE RI_DONE\n");
        writer.write("    CMP AL, 2Dh\n");
        writer.write("    JNE RI_DIGIT\n");
        writer.write("    MOV CL, 1\n");
        writer.write("    JMP RI_READ\n");
        writer.write("RI_DIGIT:\n");
        writer.write("    CMP AL, 30h\n");
        writer.write("    JL RI_READ\n");
        writer.write("    CMP AL, 39h\n");
        writer.write("    JG RI_READ\n");
        writer.write("    IMUL SI, SI, 10\n");
        writer.write("    SUB AL, 30h\n");
        writer.write("    XOR AH, AH\n");
        writer.write("    ADD SI, AX\n");
        writer.write("    JMP RI_READ\n");
        writer.write("RI_DONE:\n");
        writer.write("    MOV DL, 0Ah\n");
        writer.write("    MOV AH, 02h\n");
        writer.write("    INT 21h\n");
        writer.write("    CMP CL, 0\n");
        writer.write("    JE RI_STORE\n");
        writer.write("    NEG SI\n");
        writer.write("RI_STORE:\n");
        writer.write("    MOV BX, DI\n");
        writer.write("    MOV [BX], SI\n");
        writer.write("    FILD WORD PTR [BX]\n");
        writer.write("    FSTP DWORD PTR [BX]\n");
        writer.write("    POP DI\n");
        writer.write("    POP SI\n");
        writer.write("    POP DX\n");
        writer.write("    POP CX\n");
        writer.write("    POP AX\n");
        writer.write("    RET\n");
        writer.write("READ_INT ENDP\n");
    }

    private void writePrintProc(FileWriter writer) throws IOException {
        writer.write("\n; --------------------------------------------------\n");
        writer.write("; Subrutina para imprimir un numero flotante (ST0)\n");
        writer.write("; --------------------------------------------------\n");
        writer.write("PRINT_FLOAT PROC NEAR\n");
        writer.write("    PUSH AX\n");
        writer.write("    PUSH BX\n");
        writer.write("    PUSH CX\n");
        writer.write("    PUSH DX\n");
        writer.write("    PUSH SI\n");
        writer.write("    PUSH DI\n");
        writer.write("    PUSH BP\n");
        writer.write("    MOV BP, SP\n\n");

        writer.write("    SUB SP, 4\n\n");

        writer.write("    FSTCW WORD PTR [BP-2]\n");
        writer.write("    MOV AX, WORD PTR [BP-2]\n");
        writer.write("    OR AX, 0C00h\n");
        writer.write("    MOV WORD PTR [BP-4], AX\n");
        writer.write("    FLDCW WORD PTR [BP-4]\n");
        writer.write("    FIST WORD PTR [BP-4]\n");
        writer.write("    FLDCW WORD PTR [BP-2]\n");
        writer.write("    MOV AX, WORD PTR [BP-4]\n");
        writer.write("    CALL PRINT_NUM_INT\n\n");

        writer.write("    MOV DL, '.'\n");
        writer.write("    MOV AH, 02h\n");
        writer.write("    INT 21h\n\n");

        writer.write("    FISUB WORD PTR [BP-4]\n");
        writer.write("    MOV CX, 10000\n");
        writer.write("    MOV WORD PTR [BP-4], CX\n");
        writer.write("    FIMUL WORD PTR [BP-4]\n");
        writer.write("    FSTCW WORD PTR [BP-2]\n");
        writer.write("    MOV AX, WORD PTR [BP-2]\n");
        writer.write("    OR AX, 0C00h\n");
        writer.write("    MOV WORD PTR [BP-4], AX\n");
        writer.write("    FLDCW WORD PTR [BP-4]\n");
        writer.write("    FISTP WORD PTR [BP-4]\n");
        writer.write("    FLDCW WORD PTR [BP-2]\n\n");

        writer.write("    MOV AX, WORD PTR [BP-4]\n");
        writer.write("    CMP AX, 0\n");
        writer.write("    JGE POS_DEC\n");
        writer.write("    NEG AX\n");
        writer.write("POS_DEC:\n");
        writer.write("    CALL PRINT_NUM_INT\n\n");

        writer.write("    ADD SP, 4\n");
        writer.write("    POP BP\n");
        writer.write("    POP DI\n");
        writer.write("    POP SI\n");
        writer.write("    POP DX\n");
        writer.write("    POP CX\n");
        writer.write("    POP BX\n");
        writer.write("    POP AX\n");
        writer.write("    RET\n");
        writer.write("PRINT_FLOAT ENDP\n\n");

        writer.write("PRINT_NUM_INT PROC NEAR\n");
        writer.write("    PUSH AX\n");
        writer.write("    PUSH BX\n");
        writer.write("    PUSH CX\n");
        writer.write("    PUSH DX\n\n");

        writer.write("    TEST AX, AX\n");
        writer.write("    JNS STORE_DIGITS\n");
        writer.write("    PUSH AX\n");
        writer.write("    MOV DL, '-'\n");
        writer.write("    MOV AH, 02h\n");
        writer.write("    INT 21h\n");
        writer.write("    POP AX\n");
        writer.write("    NEG AX\n\n");

        writer.write("STORE_DIGITS:\n");
        writer.write("    MOV CX, 0\n");
        writer.write("    MOV BX, 10\n");
        writer.write("LOOP_DIV:\n");
        writer.write("    MOV DX, 0\n");
        writer.write("    DIV BX\n");
        writer.write("    PUSH DX\n");
        writer.write("    INC CX\n");
        writer.write("    TEST AX, AX\n");
        writer.write("    JNZ LOOP_DIV\n\n");

        writer.write("PRINT_DIGITS:\n");
        writer.write("    POP DX\n");
        writer.write("    ADD DL, '0'\n");
        writer.write("    MOV AH, 02h\n");
        writer.write("    INT 21h\n");
        writer.write("    LOOP PRINT_DIGITS\n\n");

        writer.write("    POP DX\n");
        writer.write("    POP CX\n");
        writer.write("    POP BX\n");
        writer.write("    POP AX\n");
        writer.write("    RET\n");
        writer.write("PRINT_NUM_INT ENDP\n");
    }
}