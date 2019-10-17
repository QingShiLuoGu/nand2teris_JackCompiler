import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

public class CompilationEngine {
    private String className;
    private int expressionListSize = -1;
    private String funcName;
    private int whileStatementGlobalsCount = -1;
    private int ifStatementsGlobalCount = -1;

    private HashMap<String, VarModel> defaultVars = new HashMap<>();
    private HashMap<String, VarModel> staticVars = new HashMap<>();
    private HashMap<String, VarModel> filedVars = new HashMap<>();
    private HashMap<String, VarModel> argumentVars = new HashMap<>();
    private HashMap<String, VarModel> localVars = new HashMap<>();

    private boolean isConstructor;
    private boolean isMethod;
    private StringBuilder result = new StringBuilder();

    private void reset() {
        className = null;
        expressionListSize = -1;
        funcName = null;
        whileStatementGlobalsCount = -1;
        ifStatementsGlobalCount = -1;
        staticVars.clear();
        filedVars.clear();
        argumentVars.clear();
        localVars.clear();
        result.delete(0,result.length());
    }


    static class VarModel {
        public static final int VAR_TYPE_STATIC = 0;
        public static final int VAR_TYPE_FIELD = 1;
        public static final int VAR_TYPE_ARGUMENT = 2;
        public static final int VAR_TYPE_LOCAL = 3;
        public static final int VAR_TYPE_DEFAULT = 4;

        public int varType;
        public int index;
        public String type;
        public String name;
        private String segment;

        public String getSegment() {
            switch (varType) {
                case VAR_TYPE_STATIC:
                    return "static";
                case VAR_TYPE_FIELD:
                    return "this";
                case VAR_TYPE_ARGUMENT:
                    return "argument";
                case VAR_TYPE_LOCAL:
                    return "local";
                case VAR_TYPE_DEFAULT:
                    return "pointer";

                default:
                    throw new RuntimeException();
            }

        }
    }

    static class TokenXmlLine {
        public boolean isComplete;
        public boolean isSingle;
        public boolean isTagStart;
        public boolean isTagEnd;
        public String tag;
        public String value;
        public String origin;

        public TokenXmlLine(String srcLine) {
            if (srcLine == null || srcLine.isEmpty())
                throw new RuntimeException("srcLine == null || srcLine.isEmpty()");
            origin = srcLine;
            srcLine = srcLine.trim();
            isComplete = srcLine.indexOf('>') != (srcLine.length() - 1);
            isSingle = !isComplete;
            if (isSingle) {
                isTagEnd = srcLine.indexOf("</") == 0;
                isTagStart = !isTagEnd;
            }

            String startChars = (isComplete || isTagStart) ? "<" : "</";
            int offset = isTagEnd ? 2 : 1;
            tag = srcLine.substring(srcLine.indexOf(startChars) + offset, srcLine.indexOf('>')).trim();

            if (isComplete) {
                int index = srcLine.indexOf('>');
                value = srcLine.substring(index + 1, srcLine.indexOf("<", index)).trim();
            }
        }
    }

    String compile(String tokensXml) {
        reset();
        String[] tokenXmlStrs = tokensXml.split("\n");
        List<TokenXmlLine> lines = new ArrayList<>();
        for (String s : tokenXmlStrs) {
            if (s == null || s.isEmpty())
                continue;
            lines.add(new TokenXmlLine(s));
        }
        if (lines.get(0).tag.equals("class"))
            compileClass(lines, 0);
        return result.toString().trim();
    }

    int compileClass(List<TokenXmlLine> lines, int start) {
        if (!lines.get(start).tag.equals("class"))
            throw new RuntimeException("start tag is not class");

        dealDefaultVars();

        className = lines.get(start + 2).value;
        int subrunTineStartIndex = findSingleTagIndex("subroutineDec", start, true, lines);

        int classVarDecStartIndex = findSingleTagIndex("classVarDec", start, true, lines);
        if (classVarDecStartIndex != -1)
            compileclassVarsDec(lines, classVarDecStartIndex, subrunTineStartIndex);

        while (lines.get(subrunTineStartIndex).tag.equals("subroutineDec"))
            subrunTineStartIndex = compileSubRuntineDec(lines, subrunTineStartIndex);

        return subrunTineStartIndex + 2;
    }

    int compileSubRuntineDec(List<TokenXmlLine> lines, int start) {
        if (!lines.get(start).tag.equals("subroutineDec"))
            throw new RuntimeException("ex");

        ifStatementsGlobalCount = -1;
        whileStatementGlobalsCount = -1;

        isConstructor = Objects.equals(lines.get(start + 1).value, "constructor");
        isMethod = Objects.equals(lines.get(start + 1).value, "method");
        String returnType = lines.get(start + 2).value;
        boolean isReturnVoid = returnType.equals("void");
        funcName = lines.get(start + 3).value;

        int paraListTagStartIndex = findSingleTagIndex("parameterList", start + 3, true, lines);
        start = compileParaList(lines, paraListTagStartIndex);
        int subruntineBodyTagStartIndex = findSingleTagIndex("subroutineBody", start, true, lines);
        start = compileSubruntineBody(lines, subruntineBodyTagStartIndex);

        int subruntineDecTagEndIndex = findSingleTagIndex("subroutineDec", start, false, lines);
        return subruntineDecTagEndIndex + 1;
    }

    private void constructorAlloc(int size) {
        result.append("push constant ").append(size).append("\n");
        result.append("call Memory.alloc 1").append("\n");
        popIdentifier("this");
    }


    int compileSubruntineBody(List<TokenXmlLine> lines, int start) {
        if (!lines.get(start).tag.equals("subroutineBody"))
            throw new RuntimeException("ex");
        int statementsTagStartIndex = findSingleTagIndex("statements", start + 1, true, lines);
        compileLocalVarsDec(lines, start + 1, statementsTagStartIndex);

        result.append("function").append(" ").
                append(className).append(".").append(funcName).append(" ").append(localVars.size()).append("\n");

        if (isConstructor && filedVars.size() > 0)
            constructorAlloc(filedVars.size());

        if (isMethod) {
            result.append("push argument 0").append("\n");
            result.append("pop pointer 0").append("\n");
        }

        start = compileStatements(lines, statementsTagStartIndex);
        return findSingleTagIndex("subroutineBody", start, false, lines);
    }

    int compileLocalVarsDec(List<TokenXmlLine> lines, int start, int end) {
        localVars.clear();
        while (start < end) {
            if (lines.get(start).tag.equals("varDec")) {
                VarModel varModel = new VarModel();
                varModel.varType = VarModel.VAR_TYPE_LOCAL;
                varModel.index = localVars.size();
                varModel.name = lines.get(start + 3).value;
                String type = varModel.type = lines.get(start + 2).value;
                localVars.put(varModel.name, varModel);
                int index = start + 4;
                while (Objects.equals(lines.get(index).value, ",")) {
                    varModel = new VarModel();
                    varModel.varType = VarModel.VAR_TYPE_LOCAL;
                    varModel.index = localVars.size();
                    varModel.name = lines.get(index + 1).value;
                    varModel.type = type;
                    localVars.put(varModel.name, varModel);
                    index += 2;
                }

                start = index + 2;
            } else
                start++;
        }

        return start;
    }

    private void dealDefaultVars() {
        defaultVars.clear();

        VarModel thisVar = new VarModel();
        thisVar.varType = VarModel.VAR_TYPE_DEFAULT;
        thisVar.index = 0;
        thisVar.name = "this";
        defaultVars.put(thisVar.name, thisVar);

        VarModel thatVar = new VarModel();
        thatVar.varType = VarModel.VAR_TYPE_DEFAULT;
        thatVar.index = 1;
        thatVar.name = "that";
        defaultVars.put(thatVar.name, thatVar);
    }

    void compileclassVarsDec(List<TokenXmlLine> lines, int start, int end) {
        localVars.clear();
        while (start < end) {
            if (lines.get(start).tag.equals("classVarDec")) {
                int varType;
                HashMap<String, VarModel> map;
                VarModel varModel = new VarModel();
                String type = lines.get(start + 2).value;
                if (Objects.equals(lines.get(start + 1).value, "field")) {
                    varType = VarModel.VAR_TYPE_FIELD;
                    map = filedVars;
                } else {
                    varType = VarModel.VAR_TYPE_STATIC;
                    map = staticVars;
                }
                varModel.name = lines.get(start + 3).value;
                varModel.type = type;
                varModel.varType = varType;
                varModel.index = map.size();
                map.put(varModel.name, varModel);

                int index = start + 4;
                while (Objects.equals(lines.get(index).value, ",")) {
                    varModel = new VarModel();
                    varModel.varType = varType;
                    varModel.index = map.size();
                    varModel.name = lines.get(index + 1).value;
                    varModel.type = type;
                    map.put(varModel.name, varModel);
                    index += 2;
                }

                start = index + 2;
            } else
                start++;
        }
    }

    int compileStatements(List<TokenXmlLine> lines, int start) {
        if (!lines.get(start).tag.equals("statements"))
            throw new RuntimeException("ex");

        int statementsTagEndIndex = recursiveFindTagEndIndex("statements", start, lines);
        int startIndex = start + 1;
        while (startIndex < statementsTagEndIndex) {
            if (lines.get(startIndex).tag == null)
                throw new RuntimeException("ex");
            switch (lines.get(startIndex).tag) {
                case "doStatement":
                    startIndex = compileDoStatement(lines, startIndex);
                    break;
                case "returnStatement":
                    startIndex = compileReturnStatement(lines, startIndex);
                    break;
                case "letStatement":
                    startIndex = compileLetStatement(lines, startIndex);
                    break;
                case "whileStatement":
                    startIndex = compileWhileStatement(lines, startIndex);
                    break;
                case "ifStatement":
                    startIndex = compileIfStatement(lines, startIndex);
                    break;
            }
        }

        return startIndex;
    }

    int compileIfStatement(List<TokenXmlLine> lines, int start) {
        if (!lines.get(start).tag.equals("ifStatement"))
            throw new RuntimeException("ex");
        ifStatementsGlobalCount++;
        int ifStatementsCount = ifStatementsGlobalCount;
        int expressionTagStartIndex = findSingleTagIndex("expression", start, true, lines);
        start = compileExpression(lines, expressionTagStartIndex);

        result.append("if-goto IF_TRUE").append(ifStatementsCount).append("\n");
        result.append("goto IF_FALSE").append(ifStatementsCount).append("\n");

        int statementsStartIndex = findSingleTagIndex("statements", start, true, lines);
        result.append("label IF_TRUE").append(ifStatementsCount).append("\n");
        start = compileStatements(lines, statementsStartIndex);

        if (Objects.equals(lines.get(start + 2).value, "else")) {
            result.append("goto IF_END").append(ifStatementsCount).append("\n");

            statementsStartIndex = findSingleTagIndex("statements", start, true, lines);
            result.append("label IF_FALSE").append(ifStatementsCount).append("\n");
            start = compileStatements(lines, statementsStartIndex);

            result.append("label IF_END").append(ifStatementsCount).append("\n");
        } else
            result.append("label IF_FALSE").append(ifStatementsCount).append("\n");

        return findSingleTagIndex("ifStatement", start, false, lines) + 1;
    }

    int compileWhileStatement(List<TokenXmlLine> lines, int start) {
        if (!lines.get(start).tag.equals("whileStatement"))
            throw new RuntimeException("ex");
        whileStatementGlobalsCount++;
        int whileStatementsCount = whileStatementGlobalsCount;
        result.append("label ").append("WHILE_EXP").append(whileStatementsCount).append("\n");
        int expressionTagStartIndex = findSingleTagIndex("expression", start, true, lines);
        start = compileExpression(lines, expressionTagStartIndex);

        result.append("not").append("\n");
        result.append("if-goto ").append("WHILE_END").append(whileStatementsCount).append("\n");

        int stateMentsStartIndex = findSingleTagIndex("statements", start, true, lines);
        start = compileStatements(lines, stateMentsStartIndex);

        result.append("goto WHILE_EXP").append(whileStatementsCount).append("\n");

        result.append("label ").append("WHILE_END").append(whileStatementsCount).append("\n");

        int whileTagEndIndex = findSingleTagIndex("whileStatement", start, false, lines);

        return whileTagEndIndex + 1;
    }

    int compileLetStatement(List<TokenXmlLine> lines, int start) {
        if (!lines.get(start).tag.equals("letStatement"))
            throw new RuntimeException("ex");
        if (!lines.get(start + 1).value.equals("let") || !lines.get(start + 3).value.equals("="))
            throw new RuntimeException("ex");
        String varName = lines.get(start + 2).value;

        compileExpression(lines, start + 4);

        popIdentifier(varName);

        start = findSingleTagIndex("letStatement", start + 4, false, lines) + 1;

        return start;
    }


    int compileDoStatement(List<TokenXmlLine> lines, int start) {
        if (!lines.get(start).tag.equals("doStatement"))
            throw new RuntimeException("ex");


        String objectName;
        String funcName;
        boolean isMethodOfThis = !Objects.equals(lines.get(start + 3).value, ".");
        if (isMethodOfThis) {
            objectName = "this";
            funcName = lines.get(start + 2).value;
        } else {
            objectName = lines.get(start + 2).value;
            funcName = lines.get(start + 4).value;
        }

        int expressionTagStartIndex = findSingleTagIndex("expressionList", start, true, lines);
        start = compileExpressionList(lines, expressionTagStartIndex);

        VarModel varModel = findIdentifier(objectName);
        if (varModel != null) {
            pushIdentifier(objectName);
            expressionListSize++;
            objectName = varModel.type;
        }

        result.append("call ").append(isMethodOfThis ? className : objectName).append(".").append(funcName).append(" ").append(expressionListSize).append("\n");
        result.append("pop temp 0").append("\n");
        return findSingleTagIndex("doStatement", start, false, lines) + 1;
    }

    int compileExpressionList(List<TokenXmlLine> lines, int start) {
        if (!lines.get(start).tag.equals("expressionList"))
            throw new RuntimeException("ex");
        expressionListSize = 0;
        int expressionListTagEndIndex = findSingleTagIndex("expressionList", start + 1, false, lines);
        int startIndex = start + 1;
        while (startIndex < expressionListTagEndIndex) {
            //加1是因为 expressionList 中的 expression 之间有分隔符
            startIndex = compileExpression(lines, startIndex) + 1;
            expressionListSize++;
        }

        return expressionListTagEndIndex + 1;
    }

    int compileExpression(List<TokenXmlLine> lines, int start) {
        if (!lines.get(start).tag.equals("expression"))
            throw new RuntimeException("ex");
        int startIndex = start + 1;
        int endIndex = recursiveFindTagEndIndex("expression", start, lines);
        while (startIndex < endIndex) {
            if (lines.get(startIndex).tag.equals("term"))
                startIndex = compileTerm(lines, startIndex);
            else if (lines.get(startIndex).tag.equals("symbol")) {
                int symbolIndex = startIndex;
                startIndex = compileTerm(lines, startIndex + 1);
                compileOp(lines.get(symbolIndex).value);
            }
        }

        return endIndex + 1;
    }

    int compileTerm(List<TokenXmlLine> lines, int start) {
        if (!lines.get(start).tag.equals("term"))
            throw new RuntimeException("ex");
        int originStart = start;
        TokenXmlLine secondLine = lines.get(start + 1);
        int termTagEndIndex = recursiveFindTagEndIndex("term", start, lines);
        if (secondLine.tag.equals("integerConstant")) {
            result.append("push constant ").append(Integer.valueOf(secondLine.value)).append("\n");
        } else if (secondLine.tag.equals("symbol") && secondLine.value.equals("(")) {
            compileExpression(lines, start + 2);
        } else if (secondLine.tag.equals("symbol") && secondLine.value.equals("~")) {
            if (lines.get(start + 2).tag.equals("term")) {
                compileTerm(lines, start + 2);
            } else {
                throw new RuntimeException();
            }
            result.append("not").append("\n");
        } else if (secondLine.tag.equals("symbol") && secondLine.value.equals("-")) {
            if (lines.get(start + 2).tag.equals("term")) {
                compileTerm(lines, start + 2);
            } else throw new RuntimeException();
            result.append("neg").append("\n");
        } else if (secondLine.tag.equals("identifier")) {
            if (termTagEndIndex == start + 2) {
                pushIdentifier(secondLine.value);
            } else {
                //这是一个 函数调用的形式
                if (!Objects.equals(lines.get(start + 2).value, "."))
                    throw new RuntimeException();
                String obName = lines.get(start + 1).value;
                String funcName = lines.get(start + 3).value;

                int expressionTagStartIndex = findSingleTagIndex("expressionList", start, true, lines);
                compileExpressionList(lines, expressionTagStartIndex);
                VarModel varModel = findIdentifier(obName);
                if (varModel != null) {
                    pushIdentifier(obName);
                    expressionListSize++;
                    obName = varModel.type;
                }

                result.append("call ").append(obName).append(".").append(funcName).append(" ").append(expressionListSize).append("\n");
            }
        } else if (secondLine.tag.equals("keyword"))
            pushKeyword(secondLine.value);

        start = termTagEndIndex + 1;

        if (start == originStart)
            throw new RuntimeException();

        return start;
    }

    private void pushKeyword(String keyword) {
        if (keyword == null || keyword.isEmpty())
            return;
        if (Objects.equals(keyword, "true") || Objects.equals(keyword, "false"))
            result.append("push constant 0").append("\n");
        else if (Objects.equals(keyword, "this"))
            result.append("push pointer 0").append("\n");
        else
            throw new RuntimeException();

        if (Objects.equals(keyword, "true"))
            result.append("not").append("\n");
    }

    private void pushIdentifier(String identifier) {
        pushOrPopIdentifier(true, identifier);
    }

    private void popIdentifier(String identifier) {
        pushOrPopIdentifier(false, identifier);
    }

    private void pushOrPopIdentifier(boolean push, String identifier) {
        if (identifier == null || identifier.isEmpty())
            return;
        VarModel varModel = null;
        if (localVars.containsKey(identifier)) {
            varModel = localVars.get(identifier);
        } else if (argumentVars.containsKey(identifier)) {
            varModel = argumentVars.get(identifier);
        } else if (filedVars.containsKey(identifier)) {
            varModel = filedVars.get(identifier);
        } else if (staticVars.containsKey(identifier)) {
            varModel = staticVars.get(identifier);
        } else if (defaultVars.containsKey(identifier))
            varModel = defaultVars.get(identifier);

        if (varModel == null)
            throw new RuntimeException();
        result.append(push ? "push " : "pop ");
        result.append(varModel.getSegment()).append(" ").append(varModel.index).append("\n");
    }

    private VarModel findIdentifier(String identifier) {
        if (identifier == null)
            return null;
        if (staticVars.containsKey(identifier))
            return staticVars.get(identifier);
        else if (filedVars.containsKey(identifier))
            return filedVars.get(identifier);
        else if (argumentVars.containsKey(identifier))
            return argumentVars.get(identifier);
        else if (localVars.containsKey(identifier))
            return localVars.get(identifier);
        else if (defaultVars.containsKey(identifier))
            return defaultVars.get(identifier);
        return null;
    }


    void compileOp(String tag) {
        switch (tag) {
            case "+":
                result.append("add").append("\n");
                break;
            case "-":
                result.append("sub").append("\n");
                break;
            case "*":
                result.append("call Math.multiply 2").append("\n");
                break;
            case "/":
                result.append("call Math.divide 2").append("\n");
                break;
            case "&gt;":
                result.append("gt").append("\n");
                break;
            case "&lt;":
                result.append("lt").append("\n");
                break;
            case "&amp;":
                result.append("and").append("\n");
                break;
            case "=":
                result.append("eq").append("\n");
                break;
            default:
                throw new RuntimeException("ex");
        }
    }


    int compileReturnStatement(List<TokenXmlLine> lines, int start) {
        if (!lines.get(start).tag.equals("returnStatement"))
            throw new RuntimeException("ex");

        int endIndex = findSingleTagIndex("returnStatement", start + 1, false, lines);

        if (endIndex == start + 3) {
            result.append("push constant 0\n");
        } else {
            int expressionStartIndex = findSingleTagIndex("expression", start, true, lines);
            compileExpression(lines, expressionStartIndex);
        }
        result.append("return").append("\n");

        return endIndex + 1;
    }


    int findSingleTagIndex(String tag, int startIndex, boolean isStart, List<TokenXmlLine> lines) {
        for (int i = startIndex; i < lines.size(); i++) {
            TokenXmlLine line = lines.get(i);
            if (line.isSingle && (line.isTagStart == isStart) && line.tag.equals(tag))
                return i;
        }
        return -1;
    }

    int recursiveFindTagEndIndex(String tag, int startIndex, List<TokenXmlLine> lines) {
        int pairTagNum = 0;
        for (int i = startIndex; i < lines.size(); i++) {
            TokenXmlLine line = lines.get(i);
            if (line.tag.equals(tag)) {
                if (line.isTagStart)
                    pairTagNum++;
                else if (line.isTagEnd) {
                    pairTagNum--;
                    if (pairTagNum <= 0)
                        return i;
                }
            }
        }

        return -1;
    }

    int compileParaList(List<TokenXmlLine> lines, int start) {
        if (!lines.get(start).tag.equals("parameterList"))
            throw new RuntimeException("ex");
        int end = findSingleTagIndex("parameterList", start + 1, false, lines);
        start += 1;
        argumentVars.clear();
        while (start < end) {
            if (Objects.equals(lines.get(start).value, ",")) {
                start++;
                continue;
            }
            VarModel varModel = new VarModel();
            varModel.index = argumentVars.size();
            varModel.varType = VarModel.VAR_TYPE_ARGUMENT;
            varModel.type = lines.get(start).value;
            varModel.name = lines.get(start + 1).value;
            argumentVars.put(varModel.name, varModel);
            start += 2;
        }

        return end + 1;
    }

}
