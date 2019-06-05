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

package doccheck.accessibility;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.TreeMap;

import doccheck.HtmlChecker;
import doccheck.Log;
import doccheck.Reporter;

/**
 * Checks that HTML tables have suitable row and column headers,
 * in accordance with accessibility rules and guidelines.
 */
public class TableChecker implements HtmlChecker {

    private int files;
    private int tables;
    private final Map<String, Integer> tableStyleCounts = new TreeMap<>();
    private final Map<String, Integer> tableStyleOKCounts = new TreeMap<>();
    private final Log log;
    private boolean passed;
    private int tablesWithErrors;
    private int errors;

    private Path currFile;
    private boolean html5;

    private final Stack<Table> tableStack = new Stack<>();

    TableChecker(Log log) {
        this.log = log;
    }

    @Override
    public void report(Reporter r) {
        r.startSection("Accessibility: Tables Report", log);
        r.report(false, "Checked " + tables + " tables in " + files + " files.");
        r.report(false, "");
        int total = 0;
        int totalOK = 0;
        for (Map.Entry<String, Integer> e : tableStyleCounts.entrySet()) {
            String name = e.getKey();
            int count = e.getValue();
            int ok = tableStyleOKCounts.getOrDefault(name, 0);
            int percent = ok * 100 / count;
            r.report((percent != 100),
                    "%-17s %5d %5d %3d%%", name, count, ok, percent);

            total += count;
            totalOK += ok;
        }
        if (total > 0) {
            int totalPercent = totalOK * 100 / total;
            r.report((totalPercent != 100),
                    "%-17s %5d %5d %3d%%", "TOTAL", total, totalOK, totalPercent);
        }

        r.report(false, "");

        r.report(errors > 0,
                "%6d errors", errors);
        r.report(tablesWithErrors > 0,
                "%6d tables with errors", tablesWithErrors);
        r.endSection();
        passed = (totalOK == total) && (tablesWithErrors == 0) && (errors == 0);
    }

    @Override
    public void startFile(Path path) {
        currFile = path;
        html5 = false;
        files++;
    }

    @Override
    public void endFile() { }

    @Override
    public void xml(int line, Map<String, String> attrs) {
    }

    @Override
    public void docType(int line, String doctype) {
        html5 = doctype.matches("(?i)<\\?doctype\\s+html>");
    }


    @Override
    public void startElement(int line, String name, Map<String, String> attrs, boolean selfClosing) {
        switch (name) {
            case "table":
                tableStack.push(new Table(line, attrs));
                break;
            case "tr":
                getCurrTable(line).startRow(line, attrs);
                break;
            case "th":
                getCurrTable(line).startCell(line, true, attrs);
                break;
            case "td":
                getCurrTable(line).startCell(line, false, attrs);
                break;
            case "caption":
                getCurrTable(line).startCaption(line);
        }
    }

    @Override
    public void endElement(int line, String name) {
        switch (name) {
            case "table":
                Table t = getCurrTable(line);
                t.endTable(line);
                if (t.errors > 0) {
                    tablesWithErrors++;
                    errors += t.errors;
                }
                tableStack.pop();
        }
    }

    @Override
    public boolean isOK() {
        return passed;
    }

    @Override
    public void close() throws IOException {
        log.close();
    }

    private Table getCurrTable(int line) {
        if (tableStack.isEmpty()) {
            log.error(currFile, line, "missing <table>");
            Map<String, String> syntheticAttrs = new HashMap<>();
            syntheticAttrs.put("class", "<synthesized>");
            tableStack.push(new Table(line, syntheticAttrs));
        }
        return tableStack.peek();
    }

    class Table {
        private final List<Row> rows;
        private final Map<String, Cell> ids;
        private final BitSet colHeaders;
        private final BitSet rowHeaders;
        private final boolean presentation;
        private int currRow;
        private int currCol;
        private int maxRows;
        private int maxCols;
        private final String styleClass;
        private int errors;

        Table(int line, Map<String, String> attrs) {
            tables++;
            styleClass = attrs.getOrDefault("class", "<unset>");
            tableStyleCounts.put(styleClass, tableStyleCounts.computeIfAbsent(styleClass, s -> 0) + 1);
            presentation = attrs.getOrDefault("role", "").equals("presentation");

            rows = new ArrayList<>();
            ids = new LinkedHashMap<>();
            colHeaders = new BitSet();
            rowHeaders = new BitSet();

            currRow = -1;
        }

        void startCaption(int line) {
            if (presentation) {
                error(line, "Can't use <caption> in a layout table");
            }
        }

        void startRow(int line, Map<String,String> attrs) {
            currRow++;
            Row row = getRow(currRow);
            row.attrs = attrs;
            currCol = -1;
        }

        void startCell(int line, boolean header, Map<String,String> attrs) {
            if (header && presentation) {
                error(line, "Can't use <th> in a layout table");
            }

            currCol++;
            while (!isVacant(currRow, currCol)) {
                currCol++;
            }
            Cell cell = new Cell(line, currRow, currCol, header, attrs);
            String id = attrs.get("id");
            if (id != null) {
                ids.put(id, cell);
            }
            int rowSpan = cell.getRowSpan();
            int colSpan = cell.getColSpan();
            for (int r = currRow; r < (currRow + rowSpan); r++) {
                for (int c = currCol; c < (currCol + colSpan); c++) {
                    set(r, c, cell);
                }
            }

            if (header || !html5) {
                String scope = attrs.get("scope");
                if (scope == null) {
                    if (currRow == 1) {
                        colHeaders.set(currCol);
                    }
                } else {
                    switch (scope) {
                        case "row":
                            if (rowSpan == 1) {
                                rowHeaders.set(currRow);
                            }
                            break;
                        case "col":
                            if (colSpan == 1) {
                                colHeaders.set(currCol);
                            }
                            break;
                    }
                }
            }
        }

        void endTable(int line) {
            if (!presentation) {
                for (Row row : rows) {
                    for (Cell cell : row.cells) {
                        if (cell != null && !cell.header) {
                            checkCellHeaders(cell);
                        }
                    }
                }
            }

            if (errors == 0) {
                tableStyleOKCounts.put(styleClass, tableStyleOKCounts.computeIfAbsent(styleClass, s -> 0) + 1);
            }
        }

        void checkCellHeaders(Cell cell) {
            String headers = cell.attrs.get("headers");
            if (headers != null) {
                boolean haveRowHeader = false;
                boolean haveColHeader = false;
                for (String hdrId : headers.split("\\s+")) {
                    Cell hdrCell = ids.get(hdrId);
                    if (hdrCell == null) {
                        error(cell.lineNumber,
                                "can't find cell with id '" + hdrId + "'");
                    } else {
                        boolean isRowHeader =
                                (hdrCell.row < cell.row + cell.getRowSpan()
                                && (hdrCell.row + hdrCell.getRowSpan() > cell.row));
                        boolean isColHeader =
                                (hdrCell.col < cell.col + cell.getColSpan()
                                && (hdrCell.col + hdrCell.getColSpan() > cell.col));
                        if (!isRowHeader && !isColHeader) {
                            error(cell.lineNumber,
                                    "header cell with id '" + hdrId + "' "
                                    + "is neither a row heading nor a column heading for this cell");
                        }
                        haveRowHeader |= isRowHeader;
                        haveColHeader |= isColHeader;
                    }
                }
                if (!haveRowHeader) {
                    error(cell.lineNumber, "no row heading in list");
                }
                if (!haveColHeader) {
                    error(cell.lineNumber, "no column heading in list");
                }
            } else if (cell.row > 0 && cell.col > 0
                    || rows.size() == 1 && getRow(0).cells.size() == 1) {
                // Give a conditional waiver to (0,0).
                // If there's no row header, it will be reported by any other data cells in the row.
                // If there's no column header, it will be reported by any other data cells in the column.
                // If all the other cells in (0,*) and (*,0) are headers, this is the no-op top-left corner.
                if (!rowHeaders.get(cell.row)) {
                    error(cell.lineNumber, "no row header for row " + cell.row);
                }
                if (!colHeaders.get(cell.col)) {
                    error(cell.lineNumber, "no column header for column " + cell.col);
                }
            }
        }

        boolean isVacant(int r, int c) {
            if (r < rows.size()) {
                Row row = rows.get(r);
                if (c < row.cells.size()) {
                    return (row.cells.get(c) == null);
                } else {
                    return true;
                }
            } else {
                return true;
            }
        }

        Row getRow(int r) {
            Row row = (r < rows.size()) ? rows.get(r) : null;
            if (row == null) {
                row = new Row();
                if (r < rows.size()) {
                    rows.set(r, row);
                } else {
                    while (r > rows.size())
                        rows.add(null);
                    rows.add(row);
                }
            }
            return row;
        }

        void set(int r, int c, Cell cell) {
            Row row = getRow(r);
            List<Cell> cells = row.cells;
            if (c < cells.size()) {
                if (cells.get(c) != null) {
                    warn(cell.lineNumber, "overlapping cells");
                }
                cells.set(c, cell);
            } else {
                while (c > cells.size())
                    cells.add(null);
                cells.add(cell);
            }
            maxRows = Math.max(maxRows, r);
            maxCols = Math.max(maxCols, c);
        }

        Cell get(int r, int c) {
            if (r < rows.size()) {
                Row row = rows.get(r);
                if (c < row.cells.size()) {
                    return row.cells.get(c);
                } else {
                    return null;
                }
            } else {
                return null;
            }
        }

        void error(int line, String message, Object... args) {
            log.error(currFile, line, message, args);
            errors++;
        }

        void warn(int line, String message, Object... args) {
            log.warn(currFile, line, message, args);
        }
    }

    static class Row {
        Map<String, String> attrs;
        int lineNumber;
        List<Cell> cells = new ArrayList<>();
    }

    static class Cell {
        final int row;
        final int col;
        final boolean header;
        final Map<String, String> attrs;
        final int lineNumber;

        Cell(int line, int row, int col, boolean header, Map<String, String> attrs) {
            this.row = row;
            this.col = col;
            this.header = header;
            this.attrs = attrs;
            this.lineNumber = line;
        }

        int getRowSpan() {
            return getInt(attrs, "rowspan", 1);
        }

        int getColSpan() {
            return getInt(attrs, "colspan", 1);
        }

        String getScope() {
            return attrs.get("scope");
        }

        private int getInt(Map<String, String> attrs, String name, int deflt) {
            String v = attrs.get(name);
            return (v != null && v.matches("[0-9]+")) ? Integer.valueOf(v) : deflt;
        }
    }
}
