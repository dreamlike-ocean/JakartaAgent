package io.github.dreamlike.scanner;

import java.io.IOException;

public class Main {
    static void main(String[] args) throws IOException {
        // java -jar javax-scanner.jar mode [mode params]
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: JavaxScanner <mode> [mode params]");
        }
        String mode = args[0].toLowerCase();
        switch (mode) {
            case "scanner" -> new ServletScanner(sliceArgs(args)).scan();
            case "replace" -> {}
            default -> throw new IllegalArgumentException("Unknown mode: " + mode);
        }
    }

    private static String[] sliceArgs(String[] args) {
        String[] strings = new String[args.length - 1];
        System.arraycopy(args, 1, strings, 0, args.length - 1);
        return strings;
    }
}
