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

package doccheck.html;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import doccheck.FileChecker;
import doccheck.Log;
import doccheck.Reporter;

/**
 * Checks the HTML content of a file using the <em>tidy</em> utility program
 *
 * @see <a href="http://www.html-tidy.org/">Tidy</a>
 */
public class TidyChecker implements FileChecker {
    private final Log log;
    private boolean passed;

    private Path path;

    public TidyChecker(Log log) {
        this.log = log;
    }

    @Override
    public void checkFile(Path path) {
        this.path = path;
        files++;
        try {
            Process p = new ProcessBuilder()
                .command("tidy",
                        "-e",
                        "--gnu-emacs", "true",
                        path.toString())
                .redirectErrorStream(true)
                .start();
            try (BufferedReader r =
                    new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                r.lines().forEach(this::checkLine);
            }
        } catch (IOException e) {
            log.error(path, e);
        }
    }

    @Override
    public void report(Reporter r) {
        r.startSection("Tidy Report", log);
        r.report(false, "%6d files read", files);
        r.report((ok < files),
                "%6d files had no errors or warnings", ok);
        r.report((overflow > 0),
                "%6d files reported \"Not all warnings/errors were shown.\"", overflow);
        r.report((errs > 0),
                "%6d errors found", errs);
        r.report((warns > 0), ""
                + "%6d warnings found", warns);
        r.report((css > 0),
                "%6d recommendations to use CSS", css);
        r.report(false, "");
        r.report((ok < files),
                "%6d %% files with no errors or warnings", (ok * 100 / files));
        r.report((errs + warns > 0),
                "%6.2f average errors or warnings per file", ((errs + warns) * 1.0 / files));
        r.report(false, "");

        Map<Integer, Set<String>> sortedCounts = new TreeMap<>(
                new Comparator<Integer>() {
                    @Override
                    public int compare(Integer o1, Integer o2) {
                        return o2.compareTo(o1);
                    }
                });

        for (Map.Entry<Pattern, Integer> e: counts.entrySet()) {
            Pattern p = e.getKey();
            Integer n = e.getValue();
            Set<String> set = sortedCounts.get(n);
            if (set == null)
                sortedCounts.put(n, (set = new TreeSet<>()));
            set.add(p.toString());
        }

        for (Map.Entry<Integer, Set<String>> e: sortedCounts.entrySet()) {
            for (String p: e.getValue()) {
                if (p.startsWith(".*")) p = p.substring(2);
                r.report(true, "%6d: %s", e.getKey(), p);
            }
        }

        r.endSection();
    }

    @Override
    public boolean isOK() {
        return (ok == files)
                && (overflow == 0)
                && (errs == 0)
                && (warns == 0)
                && (css == 0);
    }

    @Override
    public void close() throws IOException {
        log.close();
    }

    void checkLine(String line) {
        Matcher m;
        if (okPattern.matcher(line).matches()) {
            ok++;
        } else if ((m = countPattern.matcher(line)).matches()) {
            warns += Integer.valueOf(m.group(1));
            errs += Integer.valueOf(m.group(2));
            if (m.group(3) != null)
                overflow++;
        } else if ((m = countPattern2.matcher(line)).matches()) {
            warns += Integer.valueOf(m.group(1));
            errs += Integer.valueOf(m.group(2));
            if (m.group(3) != null)
                overflow++;
        } else if (guardPattern.matcher(line).matches()) {
            boolean found = false;
            for (Pattern p: patterns) {
                if (p.matcher(line).matches()) {
                    log.report("%s", line);
                    found = true;
                    count(p);
                    break;
                }
            }
            if (!found)
                log.error(path, "unrecognized line: " + line);
        } else if (cssPattern.matcher(line).matches()) {
            css++;
        }

    }

    Map<Pattern, Integer> counts = new HashMap<>();
    void count(Pattern p) {
        Integer i = counts.get(p);
        counts.put(p, (i == null) ? 1 : i + 1);
    }

    Pattern okPattern = Pattern.compile("No warnings or errors were found.");
    Pattern countPattern = Pattern.compile("([0-9]+) warnings, ([0-9]+) errors were found!.*?(Not all warnings/errors were shown.)?");
    Pattern countPattern2 = Pattern.compile("Tidy found ([0-9]+) warning[s]? and ([0-9]+) error[s]?!.*?(Not all warnings/errors were shown.)?");
    Pattern cssPattern = Pattern.compile("You are recommended to use CSS.*");
    Pattern guardPattern = Pattern.compile("(line [0-9]+ column [0-9]+ - |[^:]+:[0-9]+:[0-9]+: )(Error|Warning):.*");

    Pattern[] patterns = {
        Pattern.compile(".*Error: <.*> is not recognized!"),
        Pattern.compile(".*Error: missing quote mark for attribute value"),
        Pattern.compile(".*Warning: '<' \\+ '/' \\+ letter not allowed here"),
        Pattern.compile(".*Warning: <.*> anchor \".*\" already defined"),
        Pattern.compile(".*Warning: <.*> attribute \".*\" has invalid value \".*\""),
        Pattern.compile(".*Warning: <.*> attribute \".*\" lacks value"),
        Pattern.compile(".*Warning: <.*> attribute \".*\" lacks value"),
        Pattern.compile(".*Warning: <.*> attribute with missing trailing quote mark"),
        Pattern.compile(".*Warning: <.*> dropping value \".*\" for repeated attribute \".*\""),
        Pattern.compile(".*Warning: <.*> inserting \".*\" attribute"),
        Pattern.compile(".*Warning: <.*> is probably intended as </.*>"),
        Pattern.compile(".*Warning: <.*> isn't allowed in <.*> elements"),
        Pattern.compile(".*Warning: <.*> lacks \".*\" attribute"),
        Pattern.compile(".*Warning: <.*> missing '>' for end of tag"),
        Pattern.compile(".*Warning: <.*> proprietary attribute \".*\""),
        Pattern.compile(".*Warning: <.*> unexpected or duplicate quote mark"),
        Pattern.compile(".*Warning: <a> id and name attribute value mismatch"),
        Pattern.compile(".*Warning: <a> cannot copy name attribute to id"),
        Pattern.compile(".*Warning: <a> escaping malformed URI reference"),
        Pattern.compile(".*Warning: <blockquote> proprietary attribute \"pre\""),
        Pattern.compile(".*Warning: discarding unexpected <.*>"),
        Pattern.compile(".*Warning: discarding unexpected </.*>"),
        Pattern.compile(".*Warning: entity \".*\" doesn't end in ';'"),
        Pattern.compile(".*Warning: inserting implicit <.*>"),
        Pattern.compile(".*Warning: inserting missing 'title' element"),
        Pattern.compile(".*Warning: missing <!DOCTYPE> declaration"),
        Pattern.compile(".*Warning: missing <.*>"),
        Pattern.compile(".*Warning: missing </.*> before <.*>"),
        Pattern.compile(".*Warning: nested emphasis <.*>"),
        Pattern.compile(".*Warning: plain text isn't allowed in <.*> elements"),
        Pattern.compile(".*Warning: removing whitespace preceding XML Declaration"),
        Pattern.compile(".*Warning: replacing <p> (by|with) <br>"),
        Pattern.compile(".*Warning: replacing invalid numeric character reference .*"),
        Pattern.compile(".*Warning: replacing obsolete element <xmp> with <pre>"),
        Pattern.compile(".*Warning: replacing unexpected .* (by|with) </.*>"),
        Pattern.compile(".*Warning: trimming empty <.*>"),
        Pattern.compile(".*Warning: unescaped & or unknown entity \".*\""),
        Pattern.compile(".*Warning: unescaped & which should be written as &amp;"),
        Pattern.compile(".*Warning: using <br> in place of <p>"),
        Pattern.compile(".*Warning: <.*> element removed from HTML5"),
        Pattern.compile(".*Warning: <.*> attribute \".*\" not allowed for HTML5"),
        Pattern.compile(".*Warning: The summary attribute on the <table> element is obsolete in HTML5"),
        Pattern.compile(".*Warning: replacing invalid UTF-8 bytes \\(char. code U\\+.*\\)")
    };

    private int files;
    private int ok;
    private int warns;
    private int errs;
    private int css;
    private int overflow;
}
