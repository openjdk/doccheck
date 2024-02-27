/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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

package jdk.codetools.doccheck.html;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jdk.codetools.doccheck.HtmlChecker;
import jdk.codetools.doccheck.Log;
import jdk.codetools.doccheck.Reporter;

/**
 * Checks the DocType declared at the head of an HTML file.
 *
 * @see <a href="https://www.w3.org/TR/html5/syntax.html#syntax-doctype">
 *  W3C HTML5 8.1.1 The DOCTYPE</a>
 */
public class DocTypeChecker implements HtmlChecker {
    private final Log log;

    private int html5;
    private int html5_legacy;
    private int xml;
    private final Map<String, Integer> counts = new HashMap<>();
    private int other;

    private Path path;

    public DocTypeChecker(Log log) {
        this.log = log;
    }

    @Override
    public void startFile(Path path) {
        this.path = path;
    }

    @Override
    public void endFile() {
    }

    @Override
    public void xml(int line, Map<String, String> attrs) {
        xml++;
    }

    @Override
    public void docType(int line, String docType) {
        if (docType.equalsIgnoreCase("doctype html")) {
            html5++;
        } else {
            Pattern p = Pattern.compile("(?i)doctype"
                    + "\\s+html"
                    + "\\s+([a-z]+)"
                    + "\\s+\"([^\"]+)\""
                    + "(?:\\s+\"([^\"]+)\")?"
                    + "\\s*");
            Matcher m = p.matcher(docType);
            if (m.matches()) {
                // See http://www.w3.org/tr/html52/syntax.html#the-doctype
                if (m.group(1).equalsIgnoreCase("system")
                    && m.group(2).equals("about:legacy-compat")) {
                    html5_legacy++;
                } else {
                    String version = m.group(2);
                    List<String> allowedVersions = List.of(
                            "-//W3C//DTD XHTML 1.0 Strict//EN"
                    );
                    if (!allowedVersions.stream().anyMatch(v -> v.equals(version))) {
                        log.error(path, line, "unexpected doctype: " + version);
                    }
                    counts.put(version, counts.getOrDefault(version, 0) + 1);
                }
            } else {
                log.error(path, line, "doctype not recognized: " + docType);
                other++;
            }
        }
    }

    @Override
    public void startElement(int line, String name, Map<String, String> attrs, boolean selfClosing) {
    }

    @Override
    public void endElement(int line, String name) {
    }

    @Override
    public void report(Reporter r) {
        r.startSection("DocType Report", log);
        if (xml > 0) {
            r.report(false, "%6d: XHTML%n", xml);
        }
        if (html5 > 0) {
            r.report(false, "%6d: HTML5%n", html5);
        }
        if (html5_legacy > 0) {
            r.report(false, "%6d: HTML5 (legacy)%n", html5_legacy);
        }

        Map<Integer, Set<String>> sortedCounts = new TreeMap<>(
                new Comparator<Integer>() {
                    @Override
                    public int compare(Integer o1, Integer o2) {
                        return o2.compareTo(o1);
                    }
                });

        for (Map.Entry<String, Integer> e: counts.entrySet()) {
            String s = e.getKey();
            Integer n = e.getValue();
            Set<String> set = sortedCounts.get(n);
            if (set == null)
                sortedCounts.put(n, (set = new TreeSet<>()));
            set.add(s);
        }

        for (Map.Entry<Integer, Set<String>> e: sortedCounts.entrySet()) {
            for (String p: e.getValue()) {
                r.report(true, "%6d: %s%n", e.getKey(), p);
            }
        }

        if (other > 0) {
            r.report(true, "%6d: other/unrecognized%n", other);
        }

        r.endSection();
    }

    @Override
    public boolean isOK() {
        return counts.isEmpty() && (other == 0);
    }

    @Override
    public void close() throws IOException {
        log.close();
    }

}
