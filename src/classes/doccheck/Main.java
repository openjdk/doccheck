/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package doccheck;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import doccheck.accessibility.AccessibilityChecker;
import doccheck.html.BadCharacterChecker;
import doccheck.html.DocTypeChecker;
import doccheck.html.ExtLinkChecker;
import doccheck.html.LinkChecker;
import doccheck.legal.LegalNoticeChecker;
import doccheck.html.TidyChecker;

/**
 * Main entry point for doccheck.
 *
 * @author jjg
 */
public class Main implements java.util.spi.ToolProvider {
    enum Check { ACCESSIBILITY, BAD_CHARS, DOCTYPE, HTML, LEGAL, LINKS, EXTLINKS }

    enum Result { OK, WARNS, ERRS, BADARGS, SYSERR }

    static class BadArgs extends Exception {
        private static final long serialVersionUID = 1L;
        BadArgs(String message) {
            super(message);
        }
    }

    enum Option {
        BASE_DIR("--base-directory", true) {
            @Override
            void process(String arg, ListIterator<String> argIter, Options options) throws BadArgs {
                String value = getValue(arg, argIter);
                try {
                    Path baseDir = Path.of(value);
                    if (!Files.exists(baseDir)) {
                        throw new BadArgs("--base-directory: not found: " + value);
                    }
                    if (!Files.isDirectory(baseDir)) {
                        throw new BadArgs("--base-directory: not a directory: " + value);
                    }
                    options.baseDir = baseDir;
                } catch (IllegalArgumentException e) {
                    throw new BadArgs("--base-directory: bad path: " + value);
                }
            }
            @Override
            void help(PrintWriter out) {
                out.println("  --base-directory <dir>");
                out.println("      Set a base directory for paths on command line and in log files.");
            }
        },

        CHECK("--check", true) {
            @Override
            void process(String arg, ListIterator<String> argIter, Options options) throws BadArgs {
                decode(options.checks, getValue(arg, argIter));
            }

            void decode(Set<Check> checks, String list) throws BadArgs {
                for (String item : list.trim().split(",")) {
                    switch (item) {
                        case "all":
                            checks.addAll(EnumSet.allOf(Check.class));
                            break;
                        case "none":
                            checks.clear();
                            break;
                        default:
                            boolean add = true;
                            if (item.startsWith("-")) {
                                add = false;
                                item = item.substring(1);
                            }   Check c;
                            try {
                                c = Check.valueOf(item.toUpperCase().replace("-", "_"));
                            } catch (IllegalArgumentException e) {
                                throw new BadArgs("check not found: " + item);
                            }   if (add) {
                                checks.add(c);
                            } else {
                                checks.remove(c);
                            }   break;
                    }
                }
            }

            @Override
            void help(PrintWriter out) {
                out.println("  --check <key>(,<key>)*");
                out.println("      Check aspects of HTML files.");
                out.println("      Supported keys are:");
                out.println("        all            Enable all checks");
                out.println("        accessibility  Check accessibility");
                out.println("        bad_chars      Check for bad characters in the files");
                out.println("        doctype        Check for HTML doctype");
                out.println("        html           Check HTML");
                out.println("        legal          Check legal items at the foot of each page");
                out.println("        links          Check internal links");
                out.println("        extlinks       Check external links");
                out.println("        none           Disable all checks");
                out.println("      Precede a key by '-' to negate its effect.");
            }
        },

        COPYRIGHT("--copyright", true) {
            @Override
            void process(String arg, ListIterator<String> argIter, Options options) throws BadArgs {
                String value = getValue(arg, argIter);
                try {
                    Pattern p = Pattern.compile(value);
                    options.copyrightPatterns.add(p);
                } catch (PatternSyntaxException e) {
                    throw new BadArgs("--copyright: bad pattern" + e.getMessage());
                }
            }

            @Override
            void help(PrintWriter out) {
                out.println("  --copyright <regex>");
                out.println("     Specify a regex for an acceptable copyright line for the 'legal' checker.");
            }

        },

        EXCLUDE("--exclude", true) {
            @Override
            void process(String arg, ListIterator<String> argIter, Options options) throws BadArgs {
                String value = getValue(arg, argIter);
                try {
                    options.excludeFiles.add(Path.of(value));
                } catch (IllegalArgumentException e) {
                    throw new BadArgs("--exclude: bad path: " + value);
                }
            }

            @Override
            void help(PrintWriter out) {
                out.println("  --exclude <file>");
                out.println("     Exclude a file or directory when scanning for files to check.");
            }
        },

        HELP("--help", false) {
            @Override
            void process(String arg, ListIterator<String> argIter, Options options) throws BadArgs {
                options.help = true;
            }

            @Override
            void help(PrintWriter out) {
                out.println("  --help");
                out.println("      Display this text.");
            }
        },

        IGNORE_URLS("--ignore-urls", true) {
            @Override
            void process(String arg, ListIterator<String> argIter, Options options) throws BadArgs {
                String value = getValue(arg, argIter);
                try {
                    for (String s: value.split("\\s+")) {
                        Pattern p = Pattern.compile(s);
                        options.ignoreURLs.add(p);
                    }
                } catch (PatternSyntaxException e) {
                    throw new BadArgs("--ignore-urls: bad pattern" + e.getMessage());
                }
            }

            @Override
            void help(PrintWriter out) {
                out.println("  --ignore-url <regex>");
                out.println("     Specify one or more regex for URLs to ignore in the 'extLinks' checker.");
            }
        },

        IGNORE_URL_REDIRECTS("--ignore-url-redirects", false) {
            @Override
            void process(String arg, ListIterator<String> argIter, Options options) throws BadArgs {
                options.ignoreURLRedirects = true;
            }

            @Override
            void help(PrintWriter out) {
                out.println("  --ignore-url-redirects");
                out.println("     Do not warn about URL redirects in the 'extLinks' checker.");
            }
        },

        VERBOSE("--verbose", false) {
            @Override
            void process(String arg, ListIterator<String> argIter, Options options) throws BadArgs {
                options.verbose = true;
            }

            @Override
            void help(PrintWriter out) {
                out.println("  --verbose");
                out.println("      Trace execution.");
            }
        },

        JBS("--jbs", true) {
            @Override
            void process(String arg, ListIterator<String> argIter, Options options) throws BadArgs {
                options.jbsLink = getValue(arg, argIter);
            }

            @Override
            void help(PrintWriter out) {
                out.println("  --jbs <url>");
                out.println("      Specify a URL to include in the report that queries for");
                out.println("      related open issues.");
            }
        },

        JDK("--jdk", true) {
            @Override
            void process(String arg, ListIterator<String> argIter, Options options) throws BadArgs {
                String value = getValue(arg, argIter);
                try {
                    options.jdk = Path.of(value);
                } catch (IllegalArgumentException e) {
                    throw new BadArgs("--jdk: bad path: " + value);
                }
            }

            @Override
            void help(PrintWriter out) {
                out.println("  --jdk <jdk-docs-directory>");
                out.println("      Generate a report for each module in a JDK docs bundle.");
                out.println("      The set of modules will be determined from the runtime.");
                out.println("      used to run doccheck.");
            }
        },

        MODULE("--module", true) {
            @Override
            void process(String arg, ListIterator<String> argIter, Options options) throws BadArgs {
                String list = getValue(arg, argIter);
                options.jdkModules = Arrays.asList(list.trim().split(","));
            }

            @Override
            void help(PrintWriter out) {
                out.println("  --module <module>(,<module>)*");
                out.println("      Specify the set of modules to be analyzed");
                out.println("      when generating a JDK report.");
            }

        },

        TITLE("--title", true) {
            @Override
            void process(String arg, ListIterator<String> argIter, Options options) throws BadArgs {
                options.title = getValue(arg, argIter);
            }

            @Override
            void help(PrintWriter out) {
                out.println("  --title <string>");
                out.println("      Specify a title for the report that is generated.");
            }
        },

        REPORT("--report", true) {
            @Override
            void process(String arg, ListIterator<String> argIter, Options options) throws BadArgs {
                String value = getValue(arg, argIter);
                try {
                    options.outFile = Path.of(value);
                    if (value.endsWith(File.separator) && !Files.exists(options.outFile)) {
                        Files.createDirectories(options.outFile);
                    }
                } catch (IllegalArgumentException e) {
                    throw new BadArgs("--report: bad path: " + value);
                } catch (IOException e) {
                    throw new BadArgs("--report: could not create directory " + value);
                }
            }

            @Override
            void help(PrintWriter out) {
                out.println("  --report <file>");
                out.println("      Specify where to write the report.");
                out.println("      If the file is an existing directory, the report will");
                out.println("      be split into separate files; otherwise, all the output");
                out.println("      will be written to a single file.");
            }
        };

        final String name;
        final boolean hasArg;

        Option(String name, boolean hasArg) {
            this.name = name;
            this.hasArg = hasArg;
        }

        boolean matches(String arg, ListIterator<String> argIter) {
            return hasArg
                    ? arg.equals(name) && argIter.hasNext() || arg.startsWith(name + "=")
                    : arg.equals(name);
        }

        abstract void process(String arg, ListIterator<String> argIter, Options options) throws BadArgs;

        protected String getValue(String arg, ListIterator<String> argIter) {
            return arg.equals(name)
                    ? argIter.next()
                    : arg.substring(name.length() + 1);
        }

        abstract void help(PrintWriter out);
    }

    class Options {
        Set<Check> checks = EnumSet.noneOf(Check.class);
        List<Pattern> copyrightPatterns = new ArrayList<>();
        List<Pattern> ignoreURLs = new ArrayList<>();
        boolean ignoreURLRedirects;
        Path baseDir;
        List<Path> files = new ArrayList<>();
        Set<Path> excludeFiles = new HashSet<>();
        String title = "DocCheck Report";  // default value
        Path outFile = null;
        Path jdk = null;
        List<String> jdkModules = null;
        boolean skipSubdirs = false;
        String jbsLink = null;
        boolean help;
        boolean verbose;

        Options() { }

        Options(String... args) throws BadArgs {
            handleOptions(args);
        }

        void handleOptions(String... args) throws BadArgs {
            ListIterator<String> argIter = Arrays.asList(args).listIterator();
            while (argIter.hasNext()) {
                String arg = argIter.next();
                if (handleOption(arg, argIter))
                    continue;

                if (arg.startsWith("--")) {
                    throw new BadArgs("unrecognized option: " + arg);
                } else {
                    try {
                        Path p = Path.of(arg);
                        if (!p.isAbsolute() && baseDir != null) {
                            p = baseDir.resolve(p).normalize();
                        }
                        if (!Files.exists(p)) {
                            throw new BadArgs("file not found: " + p);
                        }
                        files.add(p);
                    } catch (IllegalArgumentException e) {
                        throw new BadArgs("bad path: " + arg);
                    }
                }
            }
        }

        private boolean handleOption(String arg, ListIterator<String> argIter) throws BadArgs {
            for (Option o : Option.values()) {
                if (o.matches(arg, argIter)) {
                    o.process(arg, argIter, this);
                    return true;
                }
            }

            return false;
        }
    }

    public static void main(String... args) {
        try {
            new Main().run(args);
        } catch (BadArgs e) {
            System.err.println(e.getMessage());
            System.exit(Result.BADARGS.ordinal());
        } catch (IOException e) {
            System.err.println("IO exception: " + e);
            System.exit(Result.SYSERR.ordinal());
        }
    }

    @Override // java.util.spi.ToolProvider
    public String name() {
        return "doccheck";
    }

    @Override // java.util.spi.ToolProvider
    public int run(PrintWriter out, PrintWriter err, String... args) {
        try {
            run(args);
            return Result.OK.ordinal();
        } catch (BadArgs e) {
            System.err.println(e.getMessage());
            return Result.BADARGS.ordinal();
        } catch (IOException e) {
            System.err.println(e);
            return Result.SYSERR.ordinal();
        }
    }

    void run(String... args) throws BadArgs, IOException {
        Options options = new Options(args);
        if (args.length == 0 || options.help) {
            PrintWriter out = new PrintWriter(System.out);
            out.println("Usage:");
            out.println("  java -jar doccheck.jar <options> <files>");
            out.println("where <files> are HTML files or directories that will");
            out.println("be searched for HTML files, and where <options> include:");
            for (Option option: Option.values()) {
                option.help(out);
            }
            out.flush();
            if (options.checks.isEmpty()
                    && options.jdk == null
                    && options.files.isEmpty()) {
                return;
            }
        }

        if (options.checks.isEmpty()) {
            throw new BadArgs("no checks specified");
        }

        if (options.jdk == null) {
            if (options.files.isEmpty()) {
                throw new BadArgs("no files or directories specified");
            }
            check(options);
        } else {
            if (options.outFile == null) {
                throw new BadArgs("no output directory specified in "
                        + "combination with --jdk option");
            }
            if (!Files.isDirectory(options.outFile)) {
                throw new BadArgs("output should be a directory when used in "
                        + "combination with --jdk option");
            }
            checkJDK(options, options.jdk);
        }
    }

    private void checkJDK(Options options, Path docs) throws IOException {
        boolean noModuleDirs = Files.exists(docs.resolve("api/java.base-summary.html"));
        Path index = options.outFile.resolve("index.html");
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(index))) {
            out.println("<!doctype html>");
            out.println("<html lang=\"en\">");
            out.println("<head>");
            out.println("<title>" + escape(options.title) + "</title>");
            out.println("<style type=\"text/css\">");
            out.println("table { font-family: sans-serif; font-size: 10pt }");
            out.println("th { background-color: lightgrey; font-weight: normal; padding: 2px 1em }");
            out.println("th:first-child { text-align:left }");
            out.println("td { text-align:center }");
            out.println(".pass { background-color: #dfd; color: #0a0 }");
            out.println(".fail { background-color: #fdd; color: #a00 }");
            out.println("hr { margin-top: .25in }");
            out.println("</style>");
            out.println("</head>");
            out.println("<body>");
            out.println("<h1>" + escape(options.title) + "</h1>");
            out.println("<table>");
            out.print("<tr><th scope=\"col\">Module/Group");
            for (Check check : options.checks) {
                out.print("<th scope=\"col\">" + check.toString().toLowerCase());
            }
            out.println();

            for (Map.Entry<String, Set<String>> e : getModules(options.jdkModules).entrySet()) {
                String module = e.getKey();
                Set<String> packages = e.getValue();

                List<Path> files = new ArrayList<>();
                boolean skipSubdirs;
                if (noModuleDirs) {
                    // old style layout
                    addIfExists(files, docs.resolve("api/" + module + "-summary.html"));
                    for (String pkg : packages) {
                        Path pkgDir = docs.resolve("api/" + pkg.replace(".", "/"));
                        addIfExists(files, pkgDir);
                        addIfExists(files, pkgDir.resolve("doc-files"));
                    }
                    skipSubdirs = true;
                } else {
                    // new style layout
                    addIfExists(files, docs.resolve("api/" + module));
                    skipSubdirs = false;
                }
                checkGroup(options, module, "Module " + module,
                        options.outFile.resolve(module), files, skipSubdirs, out);
            }

            if (options.jdkModules == null || options.jdkModules.contains("top-files")) {
                if (noModuleDirs) {
                    System.err.println("top-files not supported for old-style module layout");
                } else {
                    List<Path> topFiles = new ArrayList<>();
                    try (DirectoryStream<Path> ds = Files.newDirectoryStream(docs)) {
                        for (Path p : ds) {
                            if (Files.isRegularFile(p) && p.getFileName().toString().endsWith(".html")) {
                                topFiles.add(p);
                            }
                        }
                    }
                    if (!topFiles.isEmpty()) {
                        checkGroup(options, "Top Files", "Files in top directory",
                                options.outFile.resolve("topFiles"),
                                topFiles, true,
                                out);

                    }
                }
            }

            if (options.jdkModules == null || options.jdkModules.contains("specs")) {
                if (Files.exists(docs.resolve("specs"))) {
                    checkGroup(options, "Other Specs", "Other Specifications",
                            options.outFile.resolve("specs"),
                            Arrays.asList(docs.resolve("specs")), false,
                            out);
                }
            }


            out.println("</table>");
            if (options.jbsLink != null) {
                out.println("<p>");
                out.println("<a href=\"" + options.jbsLink + "\">Open Issues</a>");
            }
            out.println("<hr>");
            out.println("<small>Generated at " + (new Date()) + "</small>");
            out.println("</body>");
            out.println("</html>");
        }
    }

    private void checkGroup(Options options, String rowName, String subtitle,
            Path outFile, List<Path> files, boolean skipSubdirs,
            PrintWriter indexOut) throws IOException {
        Options grpOpts = new Options();
        grpOpts.baseDir = options.baseDir;
        grpOpts.checks = options.checks;
        grpOpts.copyrightPatterns = options.copyrightPatterns;
        grpOpts.ignoreURLs = options.ignoreURLs;
        grpOpts.ignoreURLRedirects = options.ignoreURLRedirects;
        grpOpts.files = files;
        if (options.title == null) {
            grpOpts.title = subtitle;
        } else {
            grpOpts.title = options.title + ": " + subtitle;
        }
        Files.createDirectories(outFile);
        grpOpts.outFile = outFile;
        grpOpts.skipSubdirs = skipSubdirs;

        System.err.println(subtitle);
//            System.err.println(modOutFile + ", " + modOpts.files);
        if (!files.isEmpty()) {
            Map<Check, Checker> results = check(grpOpts);
//                System.err.println(results);
            indexOut.print("<tr><th scope=\"row\"><a href=\"" + outFile.getFileName()
                    + "/report.html\"" + ">" + rowName + "</a>");
            for (Check c : Check.values()) {
                Checker chkr = results.get(c);
                if (chkr != null) {
                    boolean ok = chkr.isOK();
                    String styleClass = ok ? "pass" : "fail";
                    String text = ok ? "&check;" : "&cross;";
                    indexOut.print("<td class=\"" + styleClass + "\">");
                    indexOut.print(text);
                }
            }
            indexOut.println();
        }

    }

    void addIfExists(List<Path> files, Path file) {
        if (Files.exists(file)) {
            files.add(file);
        }
    }

    String escape(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    Map<Check, Checker> check(Options options) throws IOException {

        Map<Check, Checker> checkers = new EnumMap<>(Check.class);
        fileCheckers = new ArrayList<>();
        List<HtmlChecker> htmlCheckers = new ArrayList<>();

        for (Check check : options.checks) {
            Log log;
            if (options.outFile == null) {
                log = new Log(System.err);
            } else {
                Path logFile;
                String tail = check.name().toLowerCase() + ".log";
                Path f = options.outFile;
                if (Files.isDirectory(f)) {
                    logFile = f.resolve(tail);
                } else {
                    Path dir = (f.getParent() == null) ? Path.of(".") : f.getParent();
                    logFile = dir.resolve(f.getFileName()
                            .toString()
                            .replaceAll("\\.[a-z]+$", "-" + tail));
                }
                log = new Log(logFile);
            }
            if (options.baseDir != null) {
                log.setBaseDirectory(options.baseDir);
            }

            Checker checker;
            switch (check) {
                case ACCESSIBILITY:
                    checker = new AccessibilityChecker(log);
                    break;
                case BAD_CHARS:
                    checker = new BadCharacterChecker(log);
                    break;
                case DOCTYPE:
                    checker = new DocTypeChecker(log);
                    break;
                case HTML:
                    checker = new TidyChecker(log);
                    break;
                case LEGAL:
                    checker = new LegalNoticeChecker(log, options.copyrightPatterns);
                    break;
                case LINKS:
                    checker = new LinkChecker(log);
                    break;
                case EXTLINKS:
                    checker = new ExtLinkChecker(log, options.ignoreURLs, options.ignoreURLRedirects);
                    break;
                default:
                    throw new Error();
            }

            if (log.errors > 0) {
                log.report("Errors reported; checking cancelled");
                log.close();
                return Collections.emptyMap();
            }

            checkers.put(check, checker);
            if (checker instanceof HtmlChecker) {
                htmlCheckers.add((HtmlChecker) checker);
            } else {
                fileCheckers.add((FileChecker) checker);
            }
        }

        if (!htmlCheckers.isEmpty()) {
            Log log = new Log(System.err);
            if (options.baseDir != null) {
                log.setBaseDirectory(options.baseDir);
            }
            fileCheckers.add(new HtmlFileChecker(log, htmlCheckers));
        }

        boolean html;
        PrintWriter out;
        if (options.outFile == null) {
            out = new PrintWriter(System.out);
            html = false;
        } else if (Files.isDirectory(options.outFile)) {
            out = new PrintWriter(Files.newBufferedWriter(options.outFile.resolve("report.html")));
            html = true;
        } else {
            out = new PrintWriter(Files.newBufferedWriter(options.outFile));
            html = (options.outFile.getFileName().toString().endsWith(".html"));
        }
        Reporter reporter = html ? new HtmlReporter(out, options.title) : new TextReporter(out);
        try {
            checkFiles(options.files, options.skipSubdirs, options.excludeFiles);

            LinkChecker lc;
            if ((lc = (LinkChecker) checkers.get(Check.LINKS)) != null) {
                Log log = new Log(System.err);
                if (options.baseDir != null)
                    log.setBaseDirectory(options.baseDir);
                lc.setCheckInwardReferencesOnly(true);
                HtmlFileChecker fc = new HtmlFileChecker(log, Arrays.asList(lc));
                for (Path file : lc.getUncheckedFiles()) {
//                    System.err.println("CHECKING: " + file);
                    fc.checkFile(file);
                }
            }

            report(fileCheckers, reporter);

            for (Checker c : fileCheckers) {
                try {
                    c.close();
                } catch (IOException e) {
                    System.err.println(e);
                }
            }
        } finally {
            reporter.close();
            out.close();
        }

        return checkers;
    }

    void checkFiles(List<Path> paths, boolean skipSubdirs, Set<Path> excludeFiles) throws IOException {
        for (Path path : paths) {
            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                int depth = 0;

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if ((skipSubdirs && depth > 0) || excludeFiles.contains(dir)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    depth++;
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path p, BasicFileAttributes attrs) {
                    if (excludeFiles.contains(p)) {
                        return FileVisitResult.CONTINUE;
                    }

                    if (Files.isRegularFile(p) && p.getFileName().toString().endsWith(".html")) {
                        checkFile(p);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException e) throws IOException {
                    depth--;
                    return super.postVisitDirectory(dir, e);
                }
            });
        }
    }

    void report(List<? extends Checker> checkers, Reporter r) {
        for (Checker c : checkers) {
            c.report(r);
        }
    }

    void checkFile(Path p) {
        for (FileChecker checker : fileCheckers) {
            checker.checkFile(p);
        }
    }

    Map<String, Set<String>> getModules(List<String> modules) {
        try {
            Class<?> moduleFinderClass = Class.forName("java.lang.module.ModuleFinder");
            Method ofSystemMethod = moduleFinderClass.getDeclaredMethod("ofSystem");
            Object finder = ofSystemMethod.invoke(null);
            Method findAllMethod = moduleFinderClass.getDeclaredMethod("findAll");
            Set<?> moduleReferences = (Set<?>) findAllMethod.invoke(finder);
            Class<?> moduleReferenceClass = Class.forName("java.lang.module.ModuleReference");
            Method descriptorMethod = moduleReferenceClass.getDeclaredMethod("descriptor");
            Class<?> moduleDescriptorClass = Class.forName("java.lang.module.ModuleDescriptor");
            Method nameMethod = moduleDescriptorClass.getDeclaredMethod("name");
            Method exportsMethod = moduleDescriptorClass.getDeclaredMethod("exports");
            Class<?> moduleDescriptorExportsClass = Class.forName("java.lang.module.ModuleDescriptor$Exports");
            Method isQualifiedMethod = moduleDescriptorExportsClass.getDeclaredMethod("isQualified");
            Method sourceMethod = moduleDescriptorExportsClass.getDeclaredMethod("source");

            Map<String, Set<String>> map = new TreeMap<>();
            for (Object moduleReference : moduleReferences) {
                Object moduleDescriptor = descriptorMethod.invoke(moduleReference);
                String name = (String) nameMethod.invoke(moduleDescriptor);
                Set<?> exports = (Set<?>) exportsMethod.invoke(moduleDescriptor);
                Set<String> unqualifiedExports = new TreeSet<>();
                for (Object export : exports) {
                    boolean isQualified = (boolean) isQualifiedMethod.invoke(export);
                    if (!isQualified) {
                        String source = (String) sourceMethod.invoke(export);
                        unqualifiedExports.add(source);
                    }
                }
                if (modules == null || modules.contains(name)) {
                    map.put(name, unqualifiedExports);
                }
            }
            return map;
        } catch (Throwable t) {
            throw new Error("Error getting module info", t);
        }

    }

    private List<FileChecker> fileCheckers;
}

