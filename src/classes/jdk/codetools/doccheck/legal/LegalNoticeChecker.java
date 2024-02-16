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

package jdk.codetools.doccheck.legal;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import jdk.codetools.doccheck.FileChecker;
import jdk.codetools.doccheck.Log;
import jdk.codetools.doccheck.Reporter;

/**
 * Checks for the presence of appropriate legal notices at the end of each
 * HTML file.
 */
public class LegalNoticeChecker implements FileChecker {
    private final Log log;
    private final List<Pattern> copyrightPatterns;

    private final CharsetDecoder decoder = Charset.forName("UTF-8").newDecoder()
            .onMalformedInput(CodingErrorAction.IGNORE)
            .onUnmappableCharacter(CodingErrorAction.IGNORE);

    public LegalNoticeChecker(Log log, List<Pattern> copyrightPatterns) {
        this.log = log;
        this.copyrightPatterns = copyrightPatterns;
    }

    @Override
    public void checkFile(Path path) {
        this.path = path.toAbsolutePath().normalize();
        files++;

        foundCopyright = false;
        foundUnknownCopyright = false;

        getLastLines(path, 10).forEach(this::checkLine);

        if (foundCopyright) {
            ok++;
        } else if (foundUnknownCopyright) {
            unknown++;
        } else if(path.getFileName().toString().matches(".*-(no)?frame\\.html")) {
            packageFrames++;
        } else if (isMetaRefresh) {
            metaRefresh++;
        } else if (!path.getFileName().toString().equals("copyright.html")) {
            log.error(this.path, "file appears to have no copyright");
            errs++;
        }
    }

    @Override
    public void report(Reporter r) {
        r.startSection("Legal Notices Report", log);
        r.report(false, "%6d files read", files);
        r.report((ok + packageFrames + metaRefresh < files),
                "%6d files had no issues", ok);
        r.report((unknown > 0),
                "%6d unrecognized copyright lines", unknown);
        r.report(false,
                "%6d files with no copyright expected", packageFrames);
        r.report(false,
                "%6d meta-refresh files with no copyright", metaRefresh);
        r.report((errs > 0),
                "%6d files with no copyright", errs);
        r.endSection();
    }

    @Override
    public boolean isOK() {
        return (ok + packageFrames + metaRefresh == files)
                && (unknown == 0)
                && (errs == 0);
    }

    @Override
    public void close() throws IOException {
        log.close();
    }

    private static final String META_REFRESH = "<meta http-equiv=\"refresh\" content=\"0;url=";

    Stream<String> getLastLines(Path path, int size) {
        Deque<String> lines = new LinkedList<>();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(
                Files.newInputStream(path), decoder))) {
            in.lines().forEach(l -> {
                if (!inBody) {
                    if (l.regionMatches(true, 0, META_REFRESH, 0, META_REFRESH.length())) {
                        isMetaRefresh = true;
                    } else if (l.toLowerCase(Locale.US).contains("<body")) {
                        inBody = true;
                    }
                }
                lines.addLast(l);
                if (lines.size() > size) {
                    lines.removeFirst();
                }
            });

        } catch (IOException e) {
            log.error(path, e);
            errs++;
        }
        return lines.stream();
    }

    void checkLine(String line) {
        if (copyrightPatterns.stream().anyMatch(p -> p.matcher(line).find())) {
            foundCopyright = true;
        } else if (line.toLowerCase(Locale.US).contains("copyright")) {
            log.error(path, "unknown copyright: " + line);
            foundUnknownCopyright = true;
        }
    }

    private int files;
    private int ok;
    private int unknown;
    private int packageFrames;
    private int metaRefresh;
    private int errs;

    private Path path;
    private boolean foundCopyright;
    private boolean foundUnknownCopyright;
    private boolean inBody;
    private boolean isMetaRefresh;
}
