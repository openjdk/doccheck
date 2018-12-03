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
    private int noLang = 0;
    private int noTitle = 0;

    private Path path;
    private boolean foundTitle;

    public AccessibilityChecker(Log log) {
        this.log = log;
        tableChecker = new TableChecker(log);
    }


    @Override
    public void startFile(Path path) {
        this.path = path;
        files++;
        foundTitle = false;
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
            case "html":
                startHtml(line, attrs);
                break;
            case "title":
                foundTitle = true;
                break;
        }
        tableChecker.startElement(line, name, attrs, selfClosing);
    }

    @Override
    public void endElement(int line, String name) {
        tableChecker.endElement(line, name);
    }

    void startHtml(int line, Map<String, String> attrs) {
        if (!attrs.containsKey("lang")) {
            log.error(path, line, "no default language specified");
            noLang++;
        }
    }

    @Override
    public void report(Reporter r) {
        r.startSection("Accessibility Report", log);
        r.report(false, "%6d files checked", files);
        if (noLang == 0) {
            r.report(false, "       All files specified a default language");
        } else {
            r.report(true, "%6d files did not specify a default language", noLang);
        }
        if (noTitle == 0) {
            r.report(false, "       All files specified a title", noTitle);
        } else {
            r.report(true, "%6d files did not specify a title", noTitle);
        }
        r.endSection();

        tableChecker.report(r);
    }

    @Override
    public boolean isOK() {
        return (noTitle == 0)
                && (noLang == 0)
                && tableChecker.isOK();
    }

    @Override
    public void close() throws IOException {
        log.close();
    }

}
