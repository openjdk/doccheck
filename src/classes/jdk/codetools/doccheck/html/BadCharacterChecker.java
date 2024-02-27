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

package jdk.codetools.doccheck.html;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jdk.codetools.doccheck.FileChecker;
import jdk.codetools.doccheck.Log;
import jdk.codetools.doccheck.Reporter;

/**
 * Checks the contents of an HTML file for bad/unmappable characters.
 *
 * The file encoding is determined from the file contents.
 */
public class BadCharacterChecker implements FileChecker {
    private final Log log;

    private int files = 0;
    private int badFiles = 0;
    private int errors = 0;

    public BadCharacterChecker(Log log) {
        this.log = log;
    }

    @Override
    public void checkFile(Path path) {
        files++;
        boolean ok = true;
        try (InputStream in = new BufferedInputStream(Files.newInputStream(path))) {
            CharsetDecoder d = getCharset(in).newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
            BufferedReader r = new BufferedReader(new InputStreamReader(in, d));
            int lineNumber = 0;
            String line;
            try {
                while ((line = r.readLine()) != null) {
                    lineNumber++;
                    int errorsOnLine = 0;
                    for (int i = 0; i < line.length(); i++) {
                        char ch = line.charAt(i);
                        if (ch == 0xFFFD) {
                            errorsOnLine++;
                        }
                    }
                    if (errorsOnLine > 0) {
                        log.error(path, lineNumber, "found %d invalid characters", errorsOnLine);
                        errors++;
                        ok = false;
                    }
                }
            } catch (IOException e) {
                log.error(path, lineNumber, e);
                errors++;
                ok = false;

            }
        } catch (IOException e) {
            log.error(path, e);
            errors++;
            ok = false;
        }
        if (!ok)
            badFiles++;
    }

    private static final Pattern doctype = Pattern.compile("(?i)<!doctype html>");
    private static final Pattern metaCharset = Pattern.compile("(?i)<meta\\s+charset=\"([^\"]+)\">");
    private static final Pattern metaContentType = Pattern.compile("(?i)<meta\\s+http-equiv=\"Content-Type\"\\s+content=\"text/html;charset=([^\"]+)\">");

    private Charset getCharset(InputStream in) throws IOException {
        CharsetDecoder initial = StandardCharsets.US_ASCII.newDecoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE);

        in.mark(1024);
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(in, initial));
            char[] buf = new char[1024];
            int n = r.read(buf, 0, buf.length);
            String head = new String(buf, 0, n);
            boolean html5 = doctype.matcher(head).find();
            Matcher m1 = metaCharset.matcher(head);
            if (m1.find()) {
                return Charset.forName(m1.group(1));
            }
            Matcher m2 = metaContentType.matcher(head);
            if (m2.find()) {
                return Charset.forName(m2.group(1));
            }
            return html5 ? StandardCharsets.UTF_8 : StandardCharsets.ISO_8859_1;
        } finally {
            in.reset();
        }
    }

    @Override
    public void report(Reporter r) {
        r.startSection("Bad Characters Report", log);
        r.report(false, "%6d files read", files);
        if (errors == 0) {
            r.report(false, "       No bad characters found");
        } else {
            r.report(true, "%6d files contained bad characters", badFiles);
            r.report(true, "%6d bad characters or other errors found", errors);
        }
        r.endSection();
    }

    @Override
    public boolean isOK() {
        return (errors == 0);
    }

    @Override
    public void close() throws IOException {
        log.close();
    }

}
