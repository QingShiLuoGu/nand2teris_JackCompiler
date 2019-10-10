import java.util.ArrayList;
import java.util.List;

public class CompilationEngine {
    private String className;
    private int expressionListSize = -1;

    class TokenXmlLine {
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
        StringBuilder result = new StringBuilder();
        String[] tokenXmlStrs = tokensXml.split("\n");
        List<TokenXmlLine> lines = new ArrayList<>();
        for (String s : tokenXmlStrs) {
            if (s == null || s.isEmpty())
                continue;
            lines.add(new TokenXmlLine(s));
        }
        if (lines.get(0).tag.equals("class"))
            compileClass(lines, 0, result);
        return result.toString();
    }

    int compileClass(List<TokenXmlLine> lines, int start, StringBuilder result) {
        if (!lines.get(start).tag.equals("class"))
            throw new RuntimeException("start tag is not class");
        className = lines.get(start + 2).value;
        int subrunTineStartIndex = start + 4;
        while (lines.get(subrunTineStartIndex).tag.equals("subroutineDec"))
            subrunTineStartIndex = compileSubRuntineDec(lines, subrunTineStartIndex, result);

        return subrunTineStartIndex + 2;
    }

    int compileSubRuntineDec(List<TokenXmlLine> lines, int start, StringBuilder result) {
        if (!lines.get(start).tag.equals("subroutineDec"))
            throw new RuntimeException("ex");
        boolean isFunction = lines.get(start + 1).value.equals("function");
        String returnType = lines.get(start + 2).value;
        boolean isReturnVoid = returnType.equals("void");
        String funcName = lines.get(start + 3).value;

        result.append(isFunction ? "function" : "method").append(" ").
                append(className).append(".").append(funcName).append(" ").append("0").append("\n");

        int paraListTagStartIndex = findSingleTagIndex("parameterList", start + 3, true, lines);
        start = compileParaList(lines, paraListTagStartIndex, result);
        int subruntineBodyTagStartIndex = findSingleTagIndex("subroutineBody", start, true, lines);
        start = compileSubruntineBody(lines, subruntineBodyTagStartIndex, result);

        int subruntineDecTagEndIndex = findSingleTagIndex("subroutineDec", start, false, lines);
        return subruntineDecTagEndIndex + 2;
    }

    int compileSubruntineBody(List<TokenXmlLine> lines, int start, StringBuilder result) {
        if (!lines.get(start).tag.equals("subroutineBody"))
            throw new RuntimeException("ex");
        int statementsTagStartIndex = findSingleTagIndex("statements", start + 1, true, lines);
        start = compileStatements(lines, statementsTagStartIndex, result);
        return findSingleTagIndex("subroutineBody", start, false, lines);
    }

    int compileStatements(List<TokenXmlLine> lines, int start, StringBuilder result) {
        if (!lines.get(start).tag.equals("statements"))
            throw new RuntimeException("ex");

        int statementsTagEndIndex = findSingleTagIndex("statements", start, false, lines);
        int startIndex = start + 1;
        while (startIndex < statementsTagEndIndex) {
            if (lines.get(startIndex).tag == null)
                throw new RuntimeException("ex");
            switch (lines.get(startIndex).tag) {
                case "doStatement":
                    startIndex = compileDoStatement(lines, startIndex, result);
                    break;
                case "returnStatement":
                    startIndex = compileReturnStatement(lines, startIndex, result);
                    break;
            }
        }

        return startIndex;
    }

    int compileDoStatement(List<TokenXmlLine> lines, int start, StringBuilder result) {
        if (!lines.get(start).tag.equals("doStatement"))
            throw new RuntimeException("ex");
        String objectName = lines.get(start + 2).value;
        String funcName = lines.get(start + 4).value;

        boolean isMethod = false;

        int expressionTagStartIndex = findSingleTagIndex("expressionList", start, true, lines);
        start = compileExpressionList(lines, expressionTagStartIndex, result);

        result.append("call ").append(objectName).append(".").append(funcName).append(" ").append(expressionListSize).append("\n");
        return findSingleTagIndex("doStatement", start, false, lines) + 1;
    }

    int compileExpressionList(List<TokenXmlLine> lines, int start, StringBuilder result) {
        if (!lines.get(start).tag.equals("expressionList"))
            throw new RuntimeException("ex");
        expressionListSize = 0;
        int expressionListTagEndIndex = findSingleTagIndex("expressionList", start + 1, false, lines);
        int startIndex = start + 1;
        while (startIndex < expressionListTagEndIndex) {
            startIndex = compileExpression(lines, startIndex, result);
            expressionListSize++;
        }

        return expressionListTagEndIndex + 1;
    }

    int compileExpression(List<TokenXmlLine> lines, int start, StringBuilder result) {
        if (!lines.get(start).tag.equals("expression"))
            throw new RuntimeException("ex");
        int startIndex = start + 1;
        int endIndex = recursiveFindTagEndIndex("expression", start, lines);
        while (startIndex < endIndex) {
            if (lines.get(startIndex).tag.equals("term"))
                startIndex = compileTerm(lines, startIndex, result);
            else if (lines.get(startIndex).tag.equals("symbol")) {
                int symbolIndex = startIndex;
                startIndex = compileTerm(lines, startIndex + 1, result);
                compileOp(lines.get(symbolIndex).value, result);
            }
        }

        return endIndex + 1;
    }

    int compileTerm(List<TokenXmlLine> lines, int start, StringBuilder result) {
        if (!lines.get(start).tag.equals("term"))
            throw new RuntimeException("ex");
        TokenXmlLine secondLine = lines.get(start + 1);
        if (secondLine.tag.equals("integerConstant")) {
            result.append("push constant ").append(Integer.valueOf(secondLine.value)).append("\n");
            start = start + 3;
        } else if (secondLine.tag.equals("symbol") && secondLine.value.equals("(")) {
            start = compileExpression(lines, start + 2, result) + 2;
        }
        return start;
    }

    void compileOp(String tag, StringBuilder result) {
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
            default:
                throw new RuntimeException("ex");
        }
    }


    int compileReturnStatement(List<TokenXmlLine> lines, int start, StringBuilder result) {
        if (!lines.get(start).tag.equals("returnStatement"))
            throw new RuntimeException("ex");
        result.append("pop temp 0\n");
        result.append("push constant 0\n");
        result.append("return").append("\n");

        return findSingleTagIndex("returnStatement", start + 1, false, lines) + 1;
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

    int compileParaList(List<TokenXmlLine> lines, int start, StringBuilder result) {
        if (!lines.get(start).tag.equals("parameterList"))
            throw new RuntimeException("ex");
        return findSingleTagIndex("parameterList", start + 1, false, lines) + 1;
    }

}
