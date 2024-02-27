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

package jdk.codetools.doccheck;

import java.io.PrintWriter;
import java.util.Iterator;
import java.util.List;

/**
 *
 * @author jjg
 */
public class TextReporter extends Reporter {
    private final PrintWriter out;

    TextReporter(PrintWriter out) {
        this.out = out;
    }

    @Override
    public void startSection(String name, Log log) {
        out.printf("*** %s ***%n%n", name);
    }

    @Override
    public void endSection() {
        out.printf("%n");
    }

    private List<String> headers;
    private int maxHeaderWidth;
    private String headFormat;

    @Override
    public void startTable(String caption, List<String> headers) {
        out.println(caption);
        this.headers = headers;
        maxHeaderWidth = headers.stream()
                .map(s -> s.length())
                .max(Integer::compareTo)
                .orElse(0);
        headFormat = "%-" + maxHeaderWidth + "s: ";
    }

    @Override
    public void addTableRow(List<String> values) {
        Iterator<String> headIter = headers.iterator();
        for (var value : values) {
            if (headIter.hasNext()) {
                out.printf(headFormat, headIter.next());
                out.print(" ");
            }
            out.println(value);
        }
        while (headIter.hasNext()) {
            out.printf(headFormat, headIter.next());
            out.println();
        }
        out.println();
    }

    @Override
    public void endTable() {

    }

    @Override
    public void report(boolean error, String format, Object... args) {
        out.printf(format + "%n", args);
    }

}
