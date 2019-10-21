import java.util.List;

public class JackParser {

    public String parse(List<String> list) {
        StringBuilder sb = new StringBuilder();
        return writeClass(sb, 0, list);
    }

    private String writeClass(StringBuilder sb, int pos, List<String> sourceTokens) {
        if (pos < 0 || !sourceTokens.get(pos).equals("class"))
            throw new RuntimeException("pos < 0 || !sourceTokens.get(pos).equals(\"class\")");
        sb.append("<class>\n");
        writeKeyword(sourceTokens.get(pos), sb);
        writeIdentifier(sourceTokens.get(pos + 1), sb);
        writeSymbol(sourceTokens.get(pos + 2), sb);
        int startPos = pos + 3;
        startPos = writeClassVarDec(startPos, sourceTokens, sb);
        startPos = writeSubRountineDec(startPos, sourceTokens, sb);
        writeSymbol(sourceTokens.get(startPos), sb);
        sb.append("</class>\n");
        return sb.toString();
    }

    private int writeSubRountineDec(int startPos, List<String> sourceTokens, StringBuilder sb) {
        while (sourceTokens.get(startPos).equals("constructor") ||
                sourceTokens.get(startPos).equals("function") ||
                sourceTokens.get(startPos).equals("method")) {
            sb.append("<subroutineDec>\n");
            for (int i = startPos; i < startPos + 3; i++) {
                writeCommmon(sourceTokens.get(i), sb);
            }
            startPos = writeParmList(startPos + 3, sourceTokens, sb);
            startPos = writeSubroutineBody(startPos, sourceTokens, sb);
            sb.append("</subroutineDec>\n");
        }
        return startPos;
    }

    private int writeSubroutineBody(int startPos, List<String> sourceTokens, StringBuilder sb) {
        sb.append("<subroutineBody>\n");
        writeSymbol("{", sb);
        startPos = writeVarDec(startPos + 1, sourceTokens, sb);
        startPos = writeStatements(startPos, sourceTokens, sb);
        writeSymbol("}", sb);
        sb.append("</subroutineBody>\n");
        return startPos + 1;
    }

    private int writeStatements(int startPos, List<String> sourceTokens, StringBuilder sb) {
        sb.append("<statements>\n");
        while (JackTokenizer.isStatement(sourceTokens.get(startPos))) {
            String token = sourceTokens.get(startPos);
            if (token.equals("let"))
                startPos = writeLetStatements(startPos, sourceTokens, sb);
            else if (token.equals("if"))
                startPos = writeIfStatements(startPos, sourceTokens, sb);
            else if (token.equals("do"))
                startPos = writeDoStatements(startPos, sourceTokens, sb);
            else if (token.equals("return"))
                startPos = writeReturnStatements(startPos, sourceTokens, sb);
            else if (token.equals("while"))
                startPos = writeWhileStatement(startPos, sourceTokens, sb);
        }
        sb.append("</statements>\n");
        return startPos;
    }

    private int writeWhileStatement(int startPos, List<String> sourceTokens, StringBuilder sb) {
        sb.append("<whileStatement>\n");
        writeCommmon(sourceTokens.get(startPos), sb);
        writeCommmon(sourceTokens.get(startPos + 1), sb);

        int index = -1;
        for (int i = startPos; i < sourceTokens.size(); i++) {
            if (sourceTokens.get(i).equals("{")) {
                index = i;
                break;
            }
        }
        writeExpression(startPos + 2, index - 2, sourceTokens, sb);
        writeCommmon(sourceTokens.get(index - 1), sb);
        writeCommmon(sourceTokens.get(index), sb);
        startPos = writeStatements(index + 1, sourceTokens, sb);
        writeCommmon(sourceTokens.get(startPos), sb);
        sb.append("</whileStatement>\n");

        return startPos + 1;
    }

    private int writeReturnStatements(int startPos, List<String> sourceTokens, StringBuilder sb) {
        sb.append("<returnStatement>\n");
        int index = findIndex(";", startPos + 1, sourceTokens);
        writeCommmon(sourceTokens.get(startPos), sb);
        if (index > startPos + 1) {
            writeExpression(startPos + 1, index - 1, sourceTokens, sb);
        }
        writeCommmon(sourceTokens.get(index), sb);
        sb.append("</returnStatement>\n");
        return index + 1;
    }

    private int writeDoStatements(int startPos, List<String> sourceTokens, StringBuilder sb) {
        int index = -1;
        int endIndex = -1;
        boolean writeCommon = true;
        sb.append("<doStatement>\n");
        for (int i = startPos; i < sourceTokens.size(); i++) {
            if (writeCommon)
                writeCommmon(sourceTokens.get(i), sb);
            if (sourceTokens.get(i).equals("(") && index == -1) {
                index = i;
                writeCommon = false;
            } else if (sourceTokens.get(i).equals(";")) {
                endIndex = i;
                break;
            }
        }

        writeExpressionList(index + 1, endIndex - 2, sourceTokens, sb);
        writeCommmon(sourceTokens.get(endIndex - 1), sb);
        writeCommmon(sourceTokens.get(endIndex), sb);
        sb.append("</doStatement>\n");
        return endIndex + 1;
    }

    private int writeIfStatements(int startPos, List<String> sourceTokens, StringBuilder sb) {
        sb.append("<ifStatement>\n");

        writeCommmon(sourceTokens.get(startPos), sb);
        writeCommmon(sourceTokens.get(startPos + 1), sb);

        int index = findIndex("{", startPos + 2, sourceTokens);
        if (index != -1) {
            writeExpression(startPos + 2, index - 2, sourceTokens, sb);
        } else
            throw new RuntimeException("index==-1");
        writeCommmon(sourceTokens.get(index - 1), sb);
        writeCommmon(sourceTokens.get(index), sb);
        startPos = writeStatements(index + 1, sourceTokens, sb);
        writeCommmon(sourceTokens.get(startPos), sb);
        startPos = startPos + 1;
        if (sourceTokens.get(startPos).equals("else")) {
            writeCommmon(sourceTokens.get(startPos), sb);
            writeCommmon(sourceTokens.get(startPos + 1), sb);
            startPos = writeStatements(startPos + 2, sourceTokens, sb);
            writeCommmon(sourceTokens.get(startPos), sb);
            startPos = startPos + 1;
        }
        sb.append("</ifStatement>\n");
        return startPos;
    }

    private int findIndex(String str, int startPos, List<String> sourceTokens) {
        for (int i = startPos; i < sourceTokens.size(); i++) {
            if (sourceTokens.get(i).equals(str))
                return i;
        }
        return -1;
    }

    private int writeLetStatements(int startPos, List<String> sourceTokens, StringBuilder sb) {
        sb.append("<letStatement>\n");
        writeCommmon(sourceTokens.get(startPos), sb);
        writeCommmon(sourceTokens.get(startPos + 1), sb);

        int endIndex = -1;
        int statementEndIndex = -1;
        if (sourceTokens.get(startPos + 2).equals("[")) {
            int scopeCount = 1;
            for (int i = startPos + 3; i < sourceTokens.size(); i++) {
                if (sourceTokens.get(i).equals("["))
                    scopeCount++;
                else if (sourceTokens.get(i).equals("]")) {
                    scopeCount--;
                    if (scopeCount == 0) {
                        endIndex = i;
                        break;
                    }
                }
            }
            writeCommmon(sourceTokens.get(startPos + 2), sb);
            writeExpression(startPos + 3, endIndex - 1, sourceTokens, sb);
            writeCommmon(sourceTokens.get(endIndex), sb);
            startPos = endIndex - 1;
        }

        for (int i = startPos + 3; i < sourceTokens.size(); i++) {
            if (sourceTokens.get(i).equals(";")) {
                statementEndIndex = i;
                break;
            }
        }
        writeCommmon(sourceTokens.get(startPos + 2), sb);
        writeExpression(startPos + 3, statementEndIndex - 1, sourceTokens, sb);
        writeCommmon(sourceTokens.get(statementEndIndex), sb);
        sb.append("</letStatement>\n");
        startPos = statementEndIndex + 1;
        return startPos;
    }

    private int writeExpression(int startPos, int endPos, List<String> sourceTokens, StringBuilder sb) {
        int scopeCount1 = 0, scopeCount2 = 0;
        int lastStartPos = startPos;
        sb.append("<expression>\n");
        for (int i = startPos; i <= endPos; i++) {
            String token = sourceTokens.get(i);
            if (token.equals("("))
                scopeCount1++;
            else if (token.equals("["))
                scopeCount2++;
            else if (token.equals(")")) {
                scopeCount1--;
            } else if (token.equals("]"))
                scopeCount2--;
            else if (token.length() == 1 && JackTokenizer.isOp(token.charAt(0)) && scopeCount1 == 0 && scopeCount2 == 0 && i != startPos) {
                writeTerm(lastStartPos, i - 1, sourceTokens, sb);
                writeCommmon(sourceTokens.get(i), sb);
                lastStartPos = i + 1;
            }

            if (i == endPos) {
                writeTerm(lastStartPos, i, sourceTokens, sb);
            }
        }
        sb.append("</expression>\n");
        startPos = endPos + 1;

        return startPos;
    }

    private int writeTerm(int startPos, int endPos, List<String> sourceTokens, StringBuilder sb) {
        sb.append("<term>\n");
        String token = sourceTokens.get(startPos);
        if (startPos == endPos) {
            boolean isInteger = true;
            try {
                Integer.parseInt(token);
            } catch (NumberFormatException e) {
                isInteger = false;
//                e.printStackTrace();
            }
            //integer
            if (isInteger) {
                writeIntegerConstant(token, sb);
            }
            //string constant
            else if (token.startsWith("\"") && token.endsWith("\"")) {
//                token = token.substring(1, token.length() - 1);
                writeStringConstant(token, sb);
            } else if (JackTokenizer.isKeyword(token)) {//keyword
                writeKeyword(token, sb);
            } else {//varName
                writeCommmon(token, sb);
            }
        } else if (token.length() == 1 && JackTokenizer.isUnaryOp(token.charAt(0))) {//unaryOp term
            writeSymbol(token, sb);
            writeTerm(startPos + 1, endPos, sourceTokens, sb);
        } else if (sourceTokens.get(startPos).equals("(") && sourceTokens.get(endPos).equals(")")) {// '(' expression ')'
            writeSymbol(sourceTokens.get(startPos), sb);
            writeExpression(startPos + 1, endPos - 1, sourceTokens, sb);
            writeSymbol(sourceTokens.get(endPos), sb);
        } else {
            for (int i = startPos; i <= endPos; i++) {
                //varName['expression']
                if (sourceTokens.get(i).equals("[")) {
                    for (int j = startPos; j <= i; j++)
                        writeCommmon(sourceTokens.get(j), sb);
                    writeExpression(i + 1, endPos - 1, sourceTokens, sb);
                    writeCommmon(sourceTokens.get(endPos), sb);
                    break;
                } else if (sourceTokens.get(i).equals("(")) {// subroutineName('expressionList')
                    for (int j = startPos; j <= i; j++)
                        writeCommmon(sourceTokens.get(j), sb);
                    writeExpressionList(i + 1, endPos - 1, sourceTokens, sb);
                    writeCommmon(sourceTokens.get(endPos), sb);
                    break;
                }
            }
        }

        sb.append("</term>\n");

        return startPos;
    }

    private int writeExpressionList(int startPos, int endPos, List<String> sourceTokens, StringBuilder sb) {
        sb.append("<expressionList>\n");
        int lastStartPos = startPos;
        for (int i = startPos; i <= endPos; i++) {
            if (sourceTokens.get(i).equals(",")) {
                writeExpression(lastStartPos, i - 1, sourceTokens, sb);
                writeCommmon(sourceTokens.get(i), sb);
                lastStartPos = i + 1;
            } else if (i == endPos) {
                writeExpression(lastStartPos, i, sourceTokens, sb);
            }
        }
        sb.append("</expressionList>\n");
        return startPos;
    }

    private void writeStringConstant(String token, StringBuilder sb) {
        sb.append("<stringConstant> ").append(token).append(" </stringConstant>\n");
    }

    private void writeIntegerConstant(String token, StringBuilder sb) {
        sb.append("<integerConstant> ").append(token).append(" </integerConstant>\n");
    }

    private int writeParmList(int startPos, List<String> sourceTokens, StringBuilder sb) {
        writeSymbol("(", sb);
        sb.append("<parameterList>\n");
        for (int i = startPos + 1; i < sourceTokens.size(); i++) {
            if (sourceTokens.get(i).equals(")")) {
                startPos = i + 1;
                break;
            }
            String token = sourceTokens.get(i);
            writeCommmon(token, sb);
        }
        sb.append("</parameterList>\n");
        writeSymbol(")", sb);
        return startPos;
    }

    private int writeClassVarDec(int startPos, List<String> sourceTokens, StringBuilder sb) {
        while (sourceTokens.get(startPos).equals("static") ||
                sourceTokens.get(startPos).equals("field")) {
            int end = -1;
            for (int i = startPos; i < sourceTokens.size(); i++) {
                if (sourceTokens.get(i).equals(";")) {
                    end = i;
                    break;
                }
            }
            if (end == -1)
                throw new RuntimeException("end == -1");
            sb.append("<classVarDec>\n");
            for (int i = startPos; i <= end; i++) {
                String token = sourceTokens.get(i);
                writeCommmon(token, sb);
            }
            sb.append("</classVarDec>\n");
            startPos = end + 1;
        }
        return startPos;
    }

    private int writeVarDec(int startPos, List<String> sourceTokens, StringBuilder sb) {
        while (sourceTokens.get(startPos).equals("var")) {
            int end = -1;
            for (int i = startPos; i < sourceTokens.size(); i++) {
                if (sourceTokens.get(i).equals(";")) {
                    end = i;
                    break;
                }
            }
            if (end == -1)
                throw new RuntimeException("end == -1");
            sb.append("<varDec>\n");
            for (int i = startPos; i <= end; i++) {
                String token = sourceTokens.get(i);
                writeCommmon(token, sb);
            }
            sb.append("</varDec>\n");
            startPos = end + 1;
        }
        return startPos;
    }

    private void writeCommmon(String token, StringBuilder sb) {
        if (JackTokenizer.isKeyword(token))
            writeKeyword(token, sb);
        else if (token.length() == 1 && JackTokenizer.isSymbol(token.charAt(0)))
            writeSymbol(token, sb);
        else
            writeIdentifier(token, sb);
    }

    private void writeKeyword(String keyword, StringBuilder sb) {
        sb.append("<keyword> ").append(keyword).append(" </keyword>\n");
    }

    private void writeIdentifier(String string, StringBuilder sb) {
        sb.append("<identifier> ").append(string).append(" </identifier>\n");
    }

    private void writeSymbol(String string, StringBuilder sb) {
        if (string.equals("<"))
            string = "&lt;";
        else if (string.equals(">"))
            string = "&gt;";
        else if (string.equals("&"))
            string = "&amp;";
        sb.append("<symbol> ").append(string).append(" </symbol>\n");
    }
}
