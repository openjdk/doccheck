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

import java.io.PrintWriter;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A Reporter for HTML-formatted reports.
 *
 * @author jjg
 */
public class HtmlReporter extends Reporter {
    private final PrintWriter out;

    HtmlReporter(PrintWriter out, String title) {
        this.out = out;
        out.println("<!doctype html>");
        out.println("<head lang=\"EN\">");
        out.println("<title>" + encode(title) + "</title>");
        out.println("<style>");
        out.println(
                "table { margin: 10px 0 }\n" +
                "table { font-family: \"sans\"; font-size: 10pt }\n" +
                "table, thead { border: 1px solid black; border-collapse: collapse }\n" +
                "th { text-align:left; font-weight: normal; }\n" +
                "th, td { border-left: 1px solid black; padding: 1px 5px }\n" +
                "thead tr { background-color: #ddd }\n" +
                "tbody tr:nth-child(even) { background-color: #eee }\n" +
                "tbody tr:nth-child(odd) { background-color: #fff }");
        out.println("</style>");
        out.println("</head>");
        out.println("<body>");
        out.println("<h1>" + encode(title) + "</h1>");
    }

    @Override
    public void startSection(String name, Log log) {
        out.println("<h2>" + encode(name) + "</h2>");
        if (log.file != null && !log.isEmpty()) {
            out.println("<p>"
                    + "<a href=" + log.file.getFileName()
                    + " type=\"text/plain; charset=utf-8\""
                    + ">Details</a>"
                    + "</p>");
        }
        out.println("<pre>");
    }

    @Override
    public void endSection() {
        out.println("</pre>");
    }

    @Override
    public void startTable(String caption, List<String> headers) {
        out.println("<table>");
        out.println("<caption>" + encode(caption) + "</caption>");
        out.println("<thead>");
        out.print("<tr>");
        headers.forEach(h -> out.print("<th>" + encode(h)));
        out.println();
        out.println("</thead><tbody>");
    }

    @Override
    public void addTableRow(List<String> values) {
        out.print("<tr>");
        values.forEach(v -> {
            out.print("<td style=\"white-space:pre-line\">");
            String ev = encode(v);
            Pattern p = Pattern.compile("http[s]?:\\S+");
            Matcher m = p.matcher(ev);
            int start = 0;
            while (m.find(start)) {
                out.print(ev.substring(start, m.start()));
                String url = m.group();
                out.print("<a href=\"" + url + "\">" + url + "</a>");
                start = m.end();
            }
            out.print(ev.substring(start));
        });
        out.println();
    }

    @Override
    public void endTable() {
        out.println("</table>");
    }

    @Override
    public void report(boolean error, String format, Object... args) {
        out.println(wrap(error, encode(String.format(format, args))));
    }

    @Override
    public void close() {
        out.println("<hr>");
        out.println("<small>Generated at " + (new Date()) + "</small>");
        out.println("</body>");
        out.println("</html>");
    }

    private String encode(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private String wrap(boolean highlight, String html) {
        return highlight ? "<span style=\"background-color: yellow\">" + html + "</span>" : html;
    }
}
