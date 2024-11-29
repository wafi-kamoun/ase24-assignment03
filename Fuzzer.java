import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Fuzzer {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java Fuzzer \"<command_to_fuzz>\"");
            System.exit(1);
        }
        String commandToFuzz = args[0];
        String workingDirectory = "./";

        if (!Files.exists(Paths.get(workingDirectory, commandToFuzz))) {
            throw new RuntimeException("Could not find command '%s'.".formatted(commandToFuzz));
        }

        String seedInput = "<html a=\"value\">...</html>";

        ProcessBuilder builder = getProcessBuilderForCommand(commandToFuzz, workingDirectory);
        System.out.printf("Command: %s\n", builder.command());

        List<Function<String, String>> mutators = List.of(
            input -> input.replace("<html", "<htm"),                          // Corrupt opening tag
            input -> input + "</unclosed>",                                  // Add unclosed tag
            input -> input.replace("value", "💣"),                           // Insert unexpected Unicode
            input -> input.substring(0, input.length() / 2),                // Truncate input
            input -> input.replace(">", ""),                                // Remove closing brackets
            input -> "<script>alert('xss')</script>",                       // Inject malicious code
            input -> "<html><div></html>",                                  // Mismatched nested tags
            input -> "<html a=!!@##>",                                      // Invalid attribute
            input -> "",                                                    // Empty input
            input -> "<html>".repeat(1000)                                  // Extremely large input
        );

        List<String> mutatedInputs = getMutatedInputs(seedInput, mutators);
        boolean foundCrash = runCommand(builder, seedInput, mutatedInputs);
        System.exit(foundCrash ? 1 : 0);
    }

    private static ProcessBuilder getProcessBuilderForCommand(String command, String workingDirectory) {
        ProcessBuilder builder = new ProcessBuilder();
        boolean isWindows = System.getProperty("os.name").toLowerCase().startsWith("windows");
        if (isWindows) {
            builder.command("cmd.exe", "/c", command);
        } else {
            builder.command("sh", "-c", command);
        }
        builder.directory(new File(workingDirectory));
        builder.redirectErrorStream(true);
        return builder;
    }

    private static boolean runCommand(ProcessBuilder builder, String seedInput, List<String> mutatedInputs) {
        boolean crashFound = false;

        for (String input : Stream.concat(Stream.of(seedInput), mutatedInputs.stream()).toList()) {
            System.out.printf("Testing input: %s\n", input);
            try {
                Process process = builder.start();
                try (OutputStream stdin = process.getOutputStream()) {
                    stdin.write(input.getBytes());
                    stdin.flush();
                }

                int exitCode = process.waitFor();
                String output = readStreamIntoString(process.getInputStream());
                System.out.printf("Exit code: %d\nOutput:\n%s\n", exitCode, output);

                if (exitCode != 0) {
                    System.err.printf("Crash detected with input: %s\n", input);
                    crashFound = true;
                }
            } catch (Exception e) {
                System.err.printf("Error while running command: %s\n", e.getMessage());
                crashFound = true;
            }
        }

        return crashFound;
    }

    private static String readStreamIntoString(InputStream inputStream) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            return reader.lines().collect(Collectors.joining(System.lineSeparator()));
        } catch (IOException e) {
            return "";
        }
    }

    private static List<String> getMutatedInputs(String seedInput, Collection<Function<String, String>> mutators) {
        return mutators.stream()
                       .map(mutator -> mutator.apply(seedInput))
                       .collect(Collectors.toList());
    }
}
