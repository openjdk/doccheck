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

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Provides a means to report notable conditions while checking files.
 */
public class Log implements AutoCloseable {
    public final Path file;
    private final PrintWriter out;
    private Path baseDir;

    public Log(PrintStream out) {
        this.file = null;
        this.out = new PrintWriter(new OutputStreamWriter(out));
    }

    public Log(Path file) throws IOException {
        this.file = file;
        this.out = new PrintWriter(Files.newBufferedWriter(file, Charset.forName("UTF-8")));
    }

    public void setBaseDirectory(Path baseDir) {
        this.baseDir = baseDir.toAbsolutePath();
    }

    public Path againstBaseDir(Path path) {
        return baseDir != null && path.startsWith(baseDir) ? baseDir.relativize(path) : path;
    }

    public void error(Path path, int line, String message, Object... args) {
//        System.err.println("Errors reported to " + file);
        out.println(againstBaseDir(path) + ":" + line + ": " + String.format(message, args));
        errors++;
    }

    public void error(Path path, String message, Object... args) {
        out.println(againstBaseDir(path) + ": " + String.format(message, args));
        errors++;
    }

    public void error(Path path, int line, Throwable t) {
        out.println(againstBaseDir(path) + ":" + line + ": " + t);
        errors++;
    }

    public void error(Path path, Throwable t) {
        out.println(againstBaseDir(path) + ": " + t);
        errors++;
    }

    public void warn(Path path, String message, Object... args) {
        out.println(againstBaseDir(path) + ": Warning: " + String.format(message, args));
        warnings++;
    }

    public void warn(Path path, int line, String message, Object... args) {
        out.println(againstBaseDir(path) + ":" + line + ": Warning: " + String.format(message, args));
        warnings++;
    }

    public void note(Path path, String message, Object... args) {
        out.println(againstBaseDir(path) + ": Note: " + String.format(message, args));
    }

    public void note(Path path, int line, String message, Object... args) {
        out.println(againstBaseDir(path) + ":" + line + ": Note: " + String.format(message, args));
    }

    public void report(String message, Object... args) {
        out.println(String.format(message, args));
        reports++;
    }

    public int errors() {
        return errors;
    }

    public int warnings() {
        return warnings;
    }

    public boolean isEmpty() {
        return (errors == 0 && warnings == 0 && reports == 0);
    }

    public void printStackTrace(Throwable t) {
        t.printStackTrace(out);
    }

    @Override
    public void close() throws IOException {
        if (file != null) {
//            System.err.println("closing " + file  + ", " + errors + " errors, "  + warnings + " warnings, " + reports + " reports");
            out.close();
        }
    }

    int errors;
    int warnings;
    int reports;
}
