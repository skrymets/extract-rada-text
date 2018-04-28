/*
 * Copyright 2018 skrymets.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package rada.text;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main1 {

    private static final Logger LOG = LoggerFactory.getLogger(Main1.class);

    public static void showHelp(Options options) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.setWidth(80);
        formatter.printHelp("java -cp <converter>.jar " + Main1.class.getName() + " OPTIONS", options);
    }

    public static void main(String[] args) {

        String[] opts1 = new String[]{
            "--dest",
            "C:\\var\\docker\\laws\\data\\11",
            "--mask",
            //"d45*.htm",
            "d0*.htm",
            "--src",
            "C:\\var\\docker\\laws\\data"
        };

        ProcessingTask task = getProcessingTask(opts1);
        if (task == null) {
            return;
        }

        processFiles(task);
    }

    /**
     * Read command line and get information about the files to be processed
     *
     * @param args user defined input
     *
     * @return a processing task data or <code>null</code> if there is not enough
     *         information about the source data.
     */
    static ProcessingTask getProcessingTask(String[] args) {

        ProcessingTask task = null;

        Option helpOption = Option.builder("h")
                .desc("Show this help and exit")
                .longOpt("help").build();

        Option sourceDirOption = Option.builder("s")
                .desc("A directory that contains all the input files")
                .longOpt("src").argName("DIR").required().hasArg().build();

        Option destinationDirOption = Option.builder("d")
                .desc("A directory where to store the processed files")
                .longOpt("dest").argName("DIR").required().hasArg().build();

        Option maskOption = Option.builder("m")
                .desc("A regular expression that masks files that should be processed. If not specified, then all files will be processed.")
                .longOpt("mask").argName("REGEXP").hasArg().build();

        Options options = new Options()
                .addOption(helpOption)
                .addOption(sourceDirOption)
                .addOption(destinationDirOption)
                .addOption(maskOption);

        try {
            if (args.length == 0 || checkHelpOnlyOption(helpOption, args)) {
                showHelp(options);
                return null;
            }
        } catch (ParseException e) {
            LOG.error(e.getMessage());
            showHelp(options);
            return null;
        }

        try {
            CommandLine cmd = new DefaultParser().parse(options, args);

            // -------------------------------------------------------------------------------
            if (cmd.hasOption(helpOption.getOpt())) {
                showHelp(options);
                return null;
            }

            // -------------------------------------------------------------------------------
            String srcDirName = cmd.getOptionValue(sourceDirOption.getOpt());

            final Path srcDir = Paths.get(srcDirName);
            if (Files.notExists(srcDir) || !Files.isDirectory(srcDir)) {
                LOG.error("Directory not exists {}", srcDirName);
                return null;
            }

            // -------------------------------------------------------------------------------
            String dstDirName = cmd.getOptionValue(destinationDirOption.getOpt());
            final Path dstDir = Paths.get(dstDirName);
            if (Files.notExists(dstDir) || !Files.isDirectory(dstDir)) {
                LOG.error("Directory not exists {}", dstDirName);
                return null;
            }

            // -------------------------------------------------------------------------------
            String mask = cmd.getOptionValue(maskOption.getOpt(), "*.*");

            task = new ProcessingTask(srcDir, dstDir, mask);

        } catch (InvalidPathException e) {
            LOG.error(e.getMessage());
        } catch (ParseException e) {
            LOG.error(e.getMessage());
            showHelp(options);
        }

        return task;

    }

    private static void processFiles(ProcessingTask task) {

        final int ONLY_FIRST_LEVEL = 1;

        try {
            Files.walkFileTree(task.inputDir,
                    EnumSet.noneOf(FileVisitOption.class),
                    ONLY_FIRST_LEVEL,
                    new FileProcessor(task));
        } catch (IOException e) {
            LOG.error(e.getMessage());
        }

    }

    static List<String> readAsUTF8(Path inputFile) throws IOException {
        return convertToUTF8(inputFile, StandardCharsets.UTF_8);
    }
    
    static List<String> convertWin1251ToUTF8(Path inputFile) throws IOException {
        return convertToUTF8(inputFile, Charset.forName("windows-1251"));
    }
    
    static List<String> convertToUTF8(Path inputFile, Charset charset) throws IOException {

        try (Stream<String> lines = Files.lines(inputFile, charset);) {
            return lines.collect(Collectors.toList());
        } catch (Exception e) {
            LOG.error(e.getMessage());
            throw new RuntimeException(e);
        }

    }

    private static void saveToUTF8File(Path outFile, List<String> utf8Strings) throws IOException {
        Files.write(
                outFile,
                utf8Strings,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE
        );
    }

    private static boolean checkHelpOnlyOption(Option helpOption, String[] arguments) throws ParseException {
        CommandLine cl = new DefaultParser().parse(new Options().addOption(helpOption), arguments, true);
        return cl.hasOption(helpOption.getOpt());
    }

    static class ProcessingTask {

        final Path inputDir;
        final Path outputDir;
        final String mask;

        ProcessingTask(Path inputDirectory, Path outputDirectory, String mask) {
            this.inputDir = inputDirectory;
            this.outputDir = outputDirectory;
            this.mask = mask;
        }

    }

    static class FileProcessor extends SimpleFileVisitor<Path> {

        private final ProcessingTask task;

        FileProcessor(ProcessingTask task) {
            this.task = task;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
            return (task.inputDir.equals(dir))
                    ? FileVisitResult.CONTINUE
                    : FileVisitResult.SKIP_SUBTREE;
        }

        @Override
        public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {

            final Path file = path.getFileName();

            if (attrs.isRegularFile() && fileNameMatches(file)) {

                Path outputFile = task.outputDir.resolve(file);
                
                LOG.info("Converting {} --> {}", path.toString(), outputFile.toString());
                List<String> utf8Strings;
                try {
                    utf8Strings = convertWin1251ToUTF8(path);
                    saveToUTF8File(outputFile, utf8Strings);
                } catch (RuntimeException e) {
                    // utf8Strings = readAsUTF8(path);
                    LOG.error("File was skipped {}", path.toString());
                }
                

            }

            return FileVisitResult.CONTINUE;
        }

        private boolean fileNameMatches(final Path file) {
            return FilenameUtils.wildcardMatch(file.toString(), task.mask, IOCase.SYSTEM);
        }
    }

}
