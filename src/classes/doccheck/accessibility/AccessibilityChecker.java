/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

package doccheck.accessibility;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Stack;

import doccheck.HtmlChecker;
import doccheck.Log;
import doccheck.Reporter;

/**
 * Checks the content of an HTML file for compliance with accessibility rules and
 * guidelines.
 * This is limited to the checks that are easily verifiable, without any
 * manual intervention.
 */
public class AccessibilityChecker implements HtmlChecker {
    private final Log log;
    private final TableChecker tableChecker;
    private int files;

    /** Number of files with {@code }<html>} without a lang attribute. */
    private int noLangFiles = 0;

    /** Number of files without a {@code <title>}. */
    private int noTitle = 0;

    /** Number of files without a {@code <h1>}. */
    private int noH1Files = 0;

    /** Number of files without too many {@code <h1>}. */
    private int excessH1Files = 0;

    /** Number of files with out of order (or missing?) headings. */
    private int unorderedHeadingsFiles = 0;

    /** Number of files with content outside any region. */
    private int contentOutsideRegionFiles = 0;

    /** Current file path. */
    private Path path;
    /** Current file has a {@code <title>}. */
    private boolean foundTitle;
    /** Number of {@code <h1>} in the current file. */
    private int h1Count;
    /** Line number for first occurrence of {@code <h1>} in the current file. */
    private int h1FirstLine;
    /** Current heading level (0, 1-6) in the current file. */
    private int currentHeading;
    /** Line number of most recent heading in the current file, or -1. */
    private int currentHeadingLine;
    /** Number of unordered headings in the current file. */
    private int unorderedHeadingsCount;
    /** Whether the scan is within a {@code <body>} tag in the current file. */
    private boolean inBody;
    /** Whether or not to check for content outside a region in the current file. */
    private boolean checkContent;
    /** The stack of active regions. */
    private Stack<String> activeRegions;
    /** Whether there is content outside any region in the current file. */
    private boolean contentOutsideRegion;
    /** Whether or not we have reported content outside a region in this part of the current file. */
    private boolean reportedContentOutsideRegion;

    public AccessibilityChecker(Log log) {
        this.log = log;
        tableChecker = new TableChecker(log);
    }

    @Override
    public void startFile(Path path) {
        this.path = path;
        files++;
        foundTitle = false;
        inBody = false;
        checkContent = false;
        contentOutsideRegion = false;
        activeRegions = new Stack<>();
        tableChecker.startFile(path);
    }

    @Override
    public void endFile() {
        tableChecker.endFile();
        if (!foundTitle) {
            log.error(path, "no title found in document");
            noTitle++;
        }
    }

    @Override
    public void xml(int line, Map<String, String> attrs) {
        tableChecker.xml(line, attrs);
    }

    @Override
    public void docType(int line, String docType) {
        tableChecker.docType(line, docType);
    }

    @Override
    public void startElement(int line, String name, Map<String, String> attrs, boolean selfClosing) {
        switch (name) {
            case "aside": case "footer": case "header": case "main": case "nav":
                startRegion(line, name);
                break;

            case "body":
                inBody = true;
                checkContent = true;
                h1Count = 0;
                unorderedHeadingsCount = 0;
                currentHeading = 0;
                currentHeadingLine = -1;
                break;

            case "h1": case "h2": case "h3": case "h4": case "h5": case "h6":
                startHeading(line, name);
                break;

            case "html":
                startHtml(line, attrs);
                break;

            case "script":
            case "noscript":
                checkContent = false;
                break;

            case "title":
                foundTitle = true;
                break;
        }
        tableChecker.startElement(line, name, attrs, selfClosing);
    }

    @Override
    public void endElement(int line, String name) {
        switch (name) {
            case "aside": case "footer": case "header": case "main": case "nav":
                endRegion(line, name);
                break;

            case "body":
                inBody = false;
                checkContent = false;
                if (h1Count == 0) {
                    log.error(path, line, "no <h1> found");
                    noH1Files++;
                } else if (h1Count > 1) {
                    excessH1Files++;
                }
                if (unorderedHeadingsCount > 0) {
                    unorderedHeadingsFiles++;
                }
                if (contentOutsideRegion) {
                    contentOutsideRegionFiles++;
                }
                break;

            case "script":
            case "noscript":
                checkContent = inBody;
                break;
        }
        tableChecker.endElement(line, name);
    }

    private void startHtml(int line, Map<String, String> attrs) {
        if (!attrs.containsKey("lang")) {
            log.error(path, line, "no default language specified");
            noLangFiles++;
        }
    }

    private void startHeading(int line, String name) {
        int level = Character.digit(name.charAt(1), 10);
        if (level == 1) {
            if (h1Count == 0) {
                h1FirstLine = line;
            } else if (h1Count > 0) {
                log.error(path, line, "repeated use of <h1>");
                log.note(path, h1FirstLine, "This is the first <h1>");
            }
            h1Count++;
        }
        if (level > currentHeading + 1) {
            log.error(path, line, "headings omitted");
            if (currentHeadingLine > 0) {
                log.note(path, currentHeadingLine, "This is the previous heading");
            }
            unorderedHeadingsCount++;
        }
        currentHeading = level;
        currentHeadingLine = line;
    }

    private void startRegion(int line, String name) {
        activeRegions.push(name);
    }

    private void endRegion(int line, String name) {
        // minimal checking; rely on html checker for full checking
        if (!activeRegions.isEmpty() && activeRegions.peek().equals(name)) {
            activeRegions.pop();
            if (activeRegions.isEmpty()) {
                // reset the flag so that additional content outside regions will be reported.
                reportedContentOutsideRegion = false;
            }
        }
    }

    @Override
    public void content(int line, String content) {
        if (checkContent && activeRegions.isEmpty() && !content.isBlank()) {
            contentOutsideRegion = true;
            if (!reportedContentOutsideRegion) {
                log.error(path, line, "content outside of a region");
                reportedContentOutsideRegion = true;
            }
        }
    }

    @Override
    public void report(Reporter r) {
        r.startSection("Accessibility Report", log);
        r.report(false, "%6d files checked", files);
        if (noLangFiles == 0) {
            r.report(false, "       All files specified a default language");
        } else {
            r.report(true, "%6d files did not specify a default language", noLangFiles);
        }
        if (noTitle == 0) {
            r.report(false, "       All files specified a title", noTitle);
        } else {
            r.report(true, "%6d files did not specify a title", noTitle);
        }
        if (noH1Files == 0 && excessH1Files == 0) {
            r.report(false, "       All files contained a single <h1> heading");
        } else {
            r.report(noH1Files > 0, "%6d files did not contain a <h1> heading", noH1Files);
            r.report(excessH1Files > 0, "%6d files contained multiple <h1> headings", excessH1Files);
        }
        if (unorderedHeadingsFiles == 0) {
            r.report(false, "       All files contained well-ordered headings");
        } else {
            r.report(true, "%6d files had badly ordered or missing headings", unorderedHeadingsFiles);
        }
        if (contentOutsideRegionFiles == 0) {
            r.report(false, "       All files contained all content within regions");
        } else {
            r.report(true, "%6d files had content outside of any region", contentOutsideRegionFiles);
        }
        r.endSection();

        tableChecker.report(r);
    }

    @Override
    public boolean isOK() {
        return (noTitle == 0)
                && (noLangFiles == 0)
                && (noH1Files == 0 && excessH1Files == 0)
                && (unorderedHeadingsFiles == 0)
                && (contentOutsideRegionFiles == 0)
                && tableChecker.isOK();
    }

    @Override
    public void close() throws IOException {
        log.close();
    }

}
