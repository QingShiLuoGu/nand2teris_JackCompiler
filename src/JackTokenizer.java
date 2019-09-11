import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class JackTokenizer {
    private static final char[] symbols = {
            '{', '}', '(', ')', '[', ']', '.', ',', ';', '+', '-', '*', '/', '&', '|', '<', '>', '=', '-'
    };

    private static final String[] keywords = {
            "class", "constructor", "function", "method", "field", "static", "var", "int", "char", "boolean", "void", "true", "false",
            "null", "this", "let", "do", "if", "else", "while", "return"
    };
    private static final char[] ops = {
            '+', '-', '*', '/', '&', '|', '<', '>', '='
    };

    private static final char[] unaryOps = {'-', '~'};

    private static final String[] keyWordConstant = {
            "true", "false", "null", "this"
    };

    private static final String[] STATEMENTS = {
            "let", "if", "while", "do", "return"
    };

    private char[] source;
    private int sourceChIndex = 0;

    public JackTokenizer(char[] source) {
        this.source = source;
        this.sourceChIndex = 0;
    }

    public void resetIndex() {
        sourceChIndex = 0;
    }

    public static boolean isSymbol(char ch) {
        for (char c : symbols) {
            if (c == ch)
                return true;
        }
        return false;
    }

    public static boolean isKeyword(String str) {
        for (String s : keywords) {
            if (s.equals(str))
                return true;
        }
        return false;
    }

    public static boolean isStatement(String str) {
        for (String s : STATEMENTS) {
            if (s.equals(str))
                return true;
        }
        return false;
    }

    public static boolean isKeywordConstant(String str) {
        for (String s : keyWordConstant) {
            if (s.equals(str))
                return true;
        }
        return false;
    }

    public static boolean isOp(char ch) {
        for (char c : ops) {
            if (c == ch)
                return true;
        }
        return false;
    }

    public static boolean isUnaryOp(char ch) {
        for (char c : unaryOps) {
            if (c == ch)
                return true;
        }
        return false;
    }

    private String nextToken() {
        int start = -1;
        int end = -1;
        boolean isString = false;
        for (int i = sourceChIndex; i < source.length; i++) {
            if (i == 550)
                System.out.println();
            if (source[i] == '\"') {
                if (isString && start != -1) {
                    end = i;
                    sourceChIndex = i + 1;
                    break;
                } else {
                    isString = true;
                    start = i + 1;
                }
            } else if (source[i] == '\'') {
                start = i + 1;
                sourceChIndex = end = start + 1;
                break;
            } else if (source[i] != ' ' && !isSymbol(source[i]) && !isUnaryOp(source[i])) {
                if (start == -1)
                    start = i;
            } else if (source[i] == ' ') {
                if (start != -1 && !isString) {
                    sourceChIndex = end = i;
                    break;
                }
            } else if ((isSymbol(source[i]) || isUnaryOp(source[i])) && !isString) {
                if (start != -1) {
                    sourceChIndex = end = i;
                } else {
                    start = i;
                    sourceChIndex = end = i + 1;
                }
                break;
            }
        }

        if (start != -1 && end == -1)
            sourceChIndex = end = source.length;
        if (start == -1 && sourceChIndex == (source.length - 1))
            sourceChIndex = source.length;
        String ret = start == -1 ? null : new String(Arrays.copyOfRange(source, start, end));
        if (ret != null && isString)
            ret = "\"" + ret + "\"";
        return ret;
    }

    private boolean hasMoreToken() {
        return sourceChIndex < source.length;
    }


    public List<String> getTokens() {
        List<String> list = new ArrayList<>();
        while (hasMoreToken()) {
            list.add(nextToken());
        }
        return list;
    }
}

