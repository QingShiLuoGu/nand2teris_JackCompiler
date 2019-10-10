import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class Main {

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.out.println("no file path, exit.");
            return;
        }
        File file = new File(args[0]);
        if (!file.exists()) {
            System.out.println("file does not exit ,so exit.");
            return;
        }

        List<File> compileFiles = new ArrayList<>();
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files == null || files.length == 0)
                throw new RuntimeException("files==null||files.length==0");
            for (File f : files) {
                if (f.getName().endsWith(".jack"))
                    compileFiles.add(f);
            }
        } else
            compileFiles.add(file);

        CompilationEngine compilationEngine = new CompilationEngine();
        for (File f : compileFiles) {
            if (!f.getName().endsWith(".jack"))
                continue;
            String source = readFile(f);
            JackTokenizer tokenizer = new JackTokenizer(source.toCharArray());
            JackParser jackParser = new JackParser();
            String result = jackParser.parse(tokenizer.getTokens());

            String filePath = f.getAbsolutePath().replaceAll(f.getName(), f.getName() + ".xml");
            saveFile(result, filePath);
            String outFilePath = f.getAbsolutePath().replaceAll(f.getName(), f.getName() + ".vm");
            saveFile(compilationEngine.compile(result), outFilePath);
        }

        System.out.println("ended.");
    }

    private static String readFile(File file) throws IOException {
        List<String> aList = new ArrayList<>();
        FileInputStream fileInputStream = new FileInputStream(file);
        InputStreamReader inputStreamReader = new InputStreamReader(fileInputStream);
        BufferedReader bufferedReader = new BufferedReader(inputStreamReader);

        String str;
        boolean isInComment = false;
        while ((str = bufferedReader.readLine()) != null) {
            str = str.trim();
            if (str.startsWith("//") || str.trim().isEmpty())
                continue;
            else if (str.startsWith("/**")) {
                if (!str.endsWith("*/"))
                    isInComment = true;
                continue;
            } else if (str.endsWith("*/")) {
                isInComment = false;
                continue;
            }

            if (isInComment)
                continue;

            int index = str.indexOf("//");
            if (index >= 0) {
                str = str.substring(0, index);
            }
            str = str.trim();
            if (str.isEmpty())
                continue;
            aList.add(str);
        }

        bufferedReader.close();
        inputStreamReader.close();
        fileInputStream.close();

        StringBuilder sourceSb = new StringBuilder();
        for (String s : aList)
            sourceSb.append(s).append(" ");
        return sourceSb.toString();
    }

    public static void saveFile(String result, String filePath) throws IOException {
        BufferedOutputStream bf = new BufferedOutputStream(new FileOutputStream(filePath));
        byte[] bytes = result.getBytes();
        bf.write(bytes, 0, bytes.length);
        bf.flush();
        bf.close();
    }
}
