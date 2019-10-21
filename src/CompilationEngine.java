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
    private List<TokenXmlLine> lines = new ArrayList<>();
    private String lineSeparator = System.lineSeparator();

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
        result.delete(0, result.length());
        lines.clear();
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

                //对字符串特殊处理
                if (value.startsWith("\"") && value.endsWith("\""))
                    value = value.substring(1, value.length() - 1);
            }
        }
    }

    String compile(String tokensXml) {
        reset();
        String[] tokenXmlStrs = tokensXml.split(lineSeparator);
        for (String s : tokenXmlStrs) {
            if (s == null || s.isEmpty())
                continue;
            lines.add(new TokenXmlLine(s));
        }
        if (Objects.equals(lines.get(0).tag, "class"))
            compileClass(0);
        return result.toString().trim();
    }

    int compileClass(int start) {
        if (!Objects.equals(lines.get(start).tag, "class"))
            throw new RuntimeException("start tag is not class");

        makeDefaultVarsMap();

        className = lines.get(start + 2).value;
        int subruntineStartIndex = findSingleTagIndex("subroutineDec", start, true);

        int classVarDecStartIndex = findSingleTagIndex("classVarDec", start, true);
        if (classVarDecStartIndex != -1)
            makeClassVarsMap(classVarDecStartIndex, subruntineStartIndex);

        while (Objects.equals(lines.get(subruntineStartIndex).tag, "subroutineDec"))
            subruntineStartIndex = compileSubRuntineDec(subruntineStartIndex);

        return subruntineStartIndex + 2;
    }

    int compileSubRuntineDec(int start) {
        if (!Objects.equals(lines.get(start).tag, "subroutineDec"))
            throw new RuntimeException("ex");

        ifStatementsGlobalCount = -1;
        whileStatementGlobalsCount = -1;

        isConstructor = Objects.equals(lines.get(start + 1).value, "constructor");
        isMethod = Objects.equals(lines.get(start + 1).value, "method");
        funcName = lines.get(start + 3).value;

        int paraListTagStartIndex = findSingleTagIndex("parameterList", start + 3, true);
        start = compileParaList(paraListTagStartIndex);
        int subruntineBodyTagStartIndex = findSingleTagIndex("subroutineBody", start, true);
        start = compileSubruntineBody(subruntineBodyTagStartIndex);

        int subruntineDecTagEndIndex = findSingleTagIndex("subroutineDec", start, false);
        return subruntineDecTagEndIndex + 1;
    }

    private void alloc(int size) {
        result.append("push constant ").append(size).append(lineSeparator);
        result.append("call Memory.alloc 1").append(lineSeparator);
    }

    private void constructorAlloc(int size) {
        alloc(size);
        popIdentifier("this");
    }


    int compileSubruntineBody(int start) {
        if (!Objects.equals(lines.get(start).tag, "subroutineBody"))
            throw new RuntimeException("ex");
        int statementsTagStartIndex = findSingleTagIndex("statements", start + 1, true);
        makeLocalVarsMap(start + 1, statementsTagStartIndex);

        result.append("function").append(" ").
                append(className).append(".").append(funcName).append(" ").append(localVars.size()).append(lineSeparator);

        if (isConstructor && filedVars.size() > 0)
            constructorAlloc(filedVars.size());

        if (isMethod) {
            result.append("push argument 0").append(lineSeparator);
            result.append("pop pointer 0").append(lineSeparator);
        }

        start = compileStatements(statementsTagStartIndex);
        return findSingleTagIndex("subroutineBody", start, false);
    }

    int makeLocalVarsMap(int start, int end) {
        localVars.clear();
        while (start < end) {
            if (Objects.equals(lines.get(start).tag, "varDec")) {
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

    private void makeDefaultVarsMap() {
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

    void makeClassVarsMap(int start, int end) {
        filedVars.clear();
        staticVars.clear();
        while (start < end) {
            if (Objects.equals(lines.get(start).tag, "classVarDec")) {
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

    int compileStatements(int start) {
        if (!Objects.equals(lines.get(start).tag, "statements"))
            throw new RuntimeException("ex");

        int statementsTagEndIndex = recursiveFindTagEndIndex("statements", start);
        int startIndex = start + 1;
        while (startIndex < statementsTagEndIndex) {
            if (lines.get(startIndex).tag == null)
                throw new RuntimeException("ex");
            switch (lines.get(startIndex).tag) {
                case "doStatement":
                    startIndex = compileDoStatement(startIndex);
                    break;
                case "returnStatement":
                    startIndex = compileReturnStatement(startIndex);
                    break;
                case "letStatement":
                    startIndex = compileLetStatement(startIndex);
                    break;
                case "whileStatement":
                    startIndex = compileWhileStatement(startIndex);
                    break;
                case "ifStatement":
                    startIndex = compileIfStatement(startIndex);
                    break;
            }
        }

        return startIndex;
    }

    int compileIfStatement(int start) {
        if (!Objects.equals(lines.get(start).tag, "ifStatement"))
            throw new RuntimeException("ex");
        ifStatementsGlobalCount++;
        int ifStatementsCount = ifStatementsGlobalCount;
        int expressionTagStartIndex = findSingleTagIndex("expression", start, true);
        start = compileExpression(expressionTagStartIndex);

        result.append("if-goto IF_TRUE").append(ifStatementsCount).append(lineSeparator);
        result.append("goto IF_FALSE").append(ifStatementsCount).append(lineSeparator);

        int statementsStartIndex = findSingleTagIndex("statements", start, true);
        result.append("label IF_TRUE").append(ifStatementsCount).append(lineSeparator);
        start = compileStatements(statementsStartIndex);

        if (Objects.equals(lines.get(start + 2).value, "else")) {
            result.append("goto IF_END").append(ifStatementsCount).append(lineSeparator);

            statementsStartIndex = findSingleTagIndex("statements", start, true);
            result.append("label IF_FALSE").append(ifStatementsCount).append(lineSeparator);
            start = compileStatements(statementsStartIndex);

            result.append("label IF_END").append(ifStatementsCount).append(lineSeparator);
        } else
            result.append("label IF_FALSE").append(ifStatementsCount).append(lineSeparator);

        return findSingleTagIndex("ifStatement", start, false) + 1;
    }

    int compileWhileStatement(int start) {
        if (!Objects.equals(lines.get(start).tag, "whileStatement"))
            throw new RuntimeException("ex");
        whileStatementGlobalsCount++;
        int whileStatementsCount = whileStatementGlobalsCount;
        result.append("label ").append("WHILE_EXP").append(whileStatementsCount).append(lineSeparator);
        int expressionTagStartIndex = findSingleTagIndex("expression", start, true);
        start = compileExpression(expressionTagStartIndex);

        result.append("not").append(lineSeparator);
        result.append("if-goto ").append("WHILE_END").append(whileStatementsCount).append(lineSeparator);

        int statementsStartIndex = findSingleTagIndex("statements", start, true);
        start = compileStatements(statementsStartIndex);

        result.append("goto WHILE_EXP").append(whileStatementsCount).append(lineSeparator);

        result.append("label ").append("WHILE_END").append(whileStatementsCount).append(lineSeparator);

        int whileTagEndIndex = findSingleTagIndex("whileStatement", start, false);

        return whileTagEndIndex + 1;
    }

    int compileLetStatement(int start) {
        if (!Objects.equals(lines.get(start).tag, "letStatement"))
            throw new RuntimeException("ex");
        if (!Objects.equals(lines.get(start + 1).value, "let"))
            throw new RuntimeException("ex");

        String varName = lines.get(start + 2).value;
        boolean isArrayEntry = Objects.equals(lines.get(start + 3).value, "[");
        if (isArrayEntry) {
            //计算数组的那个位置
            start = compileArrayReference(start + 2);
            compileExpression(start + 1);
            //将等号右边的expression结果临时保存一下
            result.append("pop temp 0").append(lineSeparator);
            //将计算出的数组的位置保存在pointer 0(用that 0 可以引用到这个地址)
            result.append("pop pointer 1").append(lineSeparator);
            //将等号右边的expression结果放到计算出的数组地址上
            result.append("push temp 0").append(lineSeparator);
            result.append("pop that 0").append(lineSeparator);
        } else {
            compileExpression(start + 4);
            popIdentifier(varName);
        }

        return findSingleTagIndex("letStatement", start + 4, false) + 1;
    }

    private int compileArrayReference(int start) {
        if (!Objects.equals(lines.get(start + 1).value, "["))
            throw new RuntimeException("ex");
        int index = compileExpression(start + 2);
        if (!Objects.equals(lines.get(index).value, "]"))
            throw new RuntimeException("ex");
        pushIdentifier(lines.get(start).value);
        result.append("add").append(lineSeparator);
        return index + 1;
    }


    int compileDoStatement(int start) {
        if (!Objects.equals(lines.get(start).tag, "doStatement"))
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

        expressionListSize = 0;
        VarModel varModel = findIdentifier(objectName);
        if (varModel != null) {
            pushIdentifier(objectName);
            expressionListSize++;
            objectName = varModel.type;
        }

        int expressionTagStartIndex = findSingleTagIndex("expressionList", start, true);
        start = compileExpressionList(expressionTagStartIndex);


        result.append("call ").append(isMethodOfThis ? className : objectName).append(".").append(funcName).append(" ").append(expressionListSize).append(lineSeparator);
        result.append("pop temp 0").append(lineSeparator);
        return findSingleTagIndex("doStatement", start, false) + 1;
    }

    int compileExpressionList(int start) {
        if (!Objects.equals(lines.get(start).tag, "expressionList"))
            throw new RuntimeException("ex");
        int expressionListTagEndIndex = findSingleTagIndex("expressionList", start + 1, false);
        int startIndex = start + 1;
        while (startIndex < expressionListTagEndIndex) {
            //加1是因为 expressionList 中的 expression 之间有分隔符
            startIndex = compileExpression(startIndex) + 1;
            expressionListSize++;
        }

        return expressionListTagEndIndex + 1;
    }

    int compileExpression(int start) {
        if (!Objects.equals(lines.get(start).tag, "expression"))
            throw new RuntimeException("ex");
        int startIndex = start + 1;
        int endIndex = recursiveFindTagEndIndex("expression", start);
        while (startIndex < endIndex) {
            if (Objects.equals(lines.get(startIndex).tag, "term"))
                startIndex = compileTerm(startIndex);
            else if (Objects.equals(lines.get(startIndex).tag, "symbol")) {
                int symbolIndex = startIndex;
                startIndex = compileTerm(startIndex + 1);
                compileOp(lines.get(symbolIndex).value);
            }
        }

        return endIndex + 1;
    }

    int compileTerm(int start) {
        if (!Objects.equals(lines.get(start).tag, "term"))
            throw new RuntimeException("ex");

        int originStart = start;
        TokenXmlLine secondLine = lines.get(start + 1);
        int termTagEndIndex = recursiveFindTagEndIndex("term", start);
        if (Objects.equals(secondLine.tag, "integerConstant")) {
            result.append("push constant ").append(Integer.valueOf(secondLine.value)).append(lineSeparator);
        } else if (Objects.equals(secondLine.tag, "symbol") && Objects.equals(secondLine.value, "(")) {
            compileExpression(start + 2);
        } else if (Objects.equals(secondLine.tag, "symbol") && Objects.equals(secondLine.value, "~")) {
            if (Objects.equals(lines.get(start + 2).tag, "term")) {
                compileTerm(start + 2);
            } else {
                throw new RuntimeException();
            }
            result.append("not").append(lineSeparator);
        } else if (Objects.equals(secondLine.tag, "symbol") && Objects.equals(secondLine.value, "-")) {
            if (Objects.equals(lines.get(start + 2).tag, "term")) {
                compileTerm(start + 2);
            } else throw new RuntimeException();
            result.append("neg").append(lineSeparator);
        } else if (Objects.equals(secondLine.tag, "identifier")) {
            if (Objects.equals(lines.get(start + 2).value, "[")) { //like var[index]
                compileExpression(start + 3);
                pushIdentifier(lines.get(start + 1).value);
                result.append("add").append(lineSeparator);
                result.append("pop pointer 1").append(lineSeparator);
                result.append("push that 0").append(lineSeparator);
            } else if (termTagEndIndex == start + 2) {
                pushIdentifier(secondLine.value);
            } else {
                //这是一个 函数调用的形式
                String obName;
                String funcName;

                boolean isMethodOfThis = !Objects.equals(lines.get(start + 2).value, ".");
                if (isMethodOfThis) {
                    obName = "this";
                    funcName = lines.get(start + 1).value;
                } else {
                    obName = lines.get(start + 1).value;
                    funcName = lines.get(start + 3).value;
                }

                expressionListSize = 0;
                VarModel varModel = findIdentifier(obName);
                if (varModel != null) {
                    pushIdentifier(obName);
                    expressionListSize++;
                    obName = varModel.type;
                }
                int expressionTagStartIndex = findSingleTagIndex("expressionList", start, true);
                compileExpressionList(expressionTagStartIndex);

                result.append("call ").append(isMethodOfThis ? className : obName).
                        append(".").append(funcName).append(" ").append(expressionListSize).append(lineSeparator);
            }
        } else if (Objects.equals(secondLine.tag, "keyword"))
            pushKeyword(secondLine.value);
        else if (Objects.equals(secondLine.tag, "stringConstant")) {
            compileString(secondLine.value);
        } else
            throw new RuntimeException();

        start = termTagEndIndex + 1;

        if (start == originStart)
            throw new RuntimeException();

        return start;
    }

    private void compileString(String input) {
        if (input == null || input.length() == 0)
            throw new RuntimeException();
        char[] chars = input.toCharArray();

        result.append("push constant ").append(chars.length).append(lineSeparator);
        result.append("call String.new 1").append(lineSeparator);
        for (char c : chars) {
            result.append("push constant ").append((int) c).append(lineSeparator);
            result.append("call String.appendChar 2").append(lineSeparator);
        }
    }

    private void pushKeyword(String keyword) {
        if (keyword == null || keyword.isEmpty())
            return;
        if (Objects.equals(keyword, "true") || Objects.equals(keyword, "false"))
            result.append("push constant 0").append(lineSeparator);
        else if (Objects.equals(keyword, "this"))
            result.append("push pointer 0").append(lineSeparator);
        else
            throw new RuntimeException();

        if (Objects.equals(keyword, "true"))
            result.append("not").append(lineSeparator);
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
        result.append(varModel.getSegment()).append(" ").append(varModel.index).append(lineSeparator);
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
                result.append("add").append(lineSeparator);
                break;
            case "-":
                result.append("sub").append(lineSeparator);
                break;
            case "*":
                result.append("call Math.multiply 2").append(lineSeparator);
                break;
            case "/":
                result.append("call Math.divide 2").append(lineSeparator);
                break;
            case "&gt;":
                result.append("gt").append(lineSeparator);
                break;
            case "&lt;":
                result.append("lt").append(lineSeparator);
                break;
            case "&amp;":
                result.append("and").append(lineSeparator);
                break;
            case "=":
                result.append("eq").append(lineSeparator);
                break;
            case "|":
                result.append("or").append(lineSeparator);
                break;
            default:
                throw new RuntimeException("ex");
        }
    }


    int compileReturnStatement(int start) {
        if (!Objects.equals(lines.get(start).tag, "returnStatement"))
            throw new RuntimeException("ex");

        int endIndex = findSingleTagIndex("returnStatement", start + 1, false);

        if (endIndex == start + 3) {
            result.append("push constant 0\n");
        } else {
            int expressionStartIndex = findSingleTagIndex("expression", start, true);
            compileExpression(expressionStartIndex);
        }
        result.append("return").append(lineSeparator);

        return endIndex + 1;
    }


    int findSingleTagIndex(String tag, int startIndex, boolean isStart) {
        for (int i = startIndex; i < lines.size(); i++) {
            TokenXmlLine line = lines.get(i);
            if (line.isSingle && (line.isTagStart == isStart) && Objects.equals(line.tag, tag))
                return i;
        }
        return -1;
    }

    int recursiveFindTagEndIndex(String tag, int startIndex) {
        int pairTagNum = 0;
        for (int i = startIndex; i < lines.size(); i++) {
            TokenXmlLine line = lines.get(i);
            if (Objects.equals(line.tag, tag)) {
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

    int compileParaList(int start) {
        if (!Objects.equals(lines.get(start).tag, "parameterList"))
            throw new RuntimeException("ex");
        int end = findSingleTagIndex("parameterList", start + 1, false);
        start += 1;
        argumentVars.clear();
        //purpose: size of arguments add 1
        if (isMethod)
            argumentVars.put("ob", new VarModel());
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
