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

package jdk.codetools.doccheck.html;

import jdk.codetools.doccheck.HtmlChecker;
import jdk.codetools.doccheck.Log;
import jdk.codetools.doccheck.Reporter;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Checks the links defined by and referenced in HTML files.
 */
public class LinkChecker implements HtmlChecker {
    static class Position implements Comparable<Position> {
        Path path;
        int line;

        Position(Path path, int line) {
            this.path = path;
            this.line = line;
        }

        @Override
        public int compareTo(Position o) {
            int v = path.compareTo(o.path);
            return v != 0 ? v : Integer.compare(line, o.line);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            } else if (obj == null || getClass() != obj.getClass()) {
                return false;
            } else {
                final Position other = (Position) obj;
                return Objects.equals(this.path, other.path)
                        && this.line == other.line;
            }
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(path) * 37 + line;
        }
    }

    static class IDInfo {
        boolean declared;
        Set<Position> references;

        Set<Position> getReferences() {
            return (references) == null ? Collections.emptySet() : references;
        }
    }

    class IDTable {
            private String pathOrURI;
        private boolean checked;
        private final Map<String, IDInfo> map = new HashMap<>();

        IDTable(Path path) {
            this.pathOrURI = path.toString();
        }

        IDTable(URI uri) {
            this.pathOrURI = uri.toString();
        }

        void addID(int line, String name) {
            if (checked) {
                throw new IllegalStateException("Adding ID after file has been read");
            }
            Objects.requireNonNull(name);
            IDInfo info = map.computeIfAbsent(name, x -> new IDInfo());
            if (info.declared) {
                if (info.references != null || !checkInwardReferencesOnly) {
                    // don't report error if we're only checking inbound references
                    // and there are no references to this ID.
                    log.error(currFile, line, "name already declared: " + name);
                    duplicateIds++;
                }
            } else {
                info.declared = true;
            }
        }

        void addReference(String name, Path from, int line) {
            if (checked) {
                if (name != null) {
                    IDInfo id = map.get(name);
                    if (id == null || !id.declared) {
                        log.error(from, line, "id not found: " + this.pathOrURI + "#" + name);
                    }
                }
            } else {
                IDInfo id = map.computeIfAbsent(name, x -> new IDInfo());
                if (id.references == null) {
                    id.references = new TreeSet<>();
                }
                id.references.add(new Position(from, line));
            }
        }

        void check() {
            map.forEach((name, id) -> {
                if (name != null && !id.declared) {
                    //log.error(currFile, 0, "id not declared: " + name);
                    for (Position ref : id.references) {
                        log.error(ref.path, ref.line, "id not found: " + this.pathOrURI + "#" + name);
                    }
                    missingIds++;
                }
            });
            checked = true;
        }
    }

    private final Log log;
    private final Map<Path, IDTable> allFiles;
    private final Map<URI, IDTable> allURIs;
    private boolean checkInwardReferencesOnly;

    private int files;
    private int links;
    private int duplicateIds;
    private int missingFiles;
    private int missingIds;
    private boolean passed;
    private int badSchemes;

    private Path currFile;
    private IDTable currTable;
    private boolean html5;
    private boolean xml;

    public LinkChecker(Log log) {
        this.log = log;
        allFiles = new HashMap<>();
        allURIs = new HashMap<>();
    }

    public void setCheckInwardReferencesOnly(boolean checkInwardReferencesOnly) {
        this.checkInwardReferencesOnly = checkInwardReferencesOnly;
    }

    @Override
    public void startFile(Path path) {
        currFile = path.toAbsolutePath().normalize();
        currTable = allFiles.computeIfAbsent(currFile, p -> new IDTable(log.againstBaseDir(p)));
        html5 = false;
        files++;
    }

    @Override
    public void endFile() {
        currTable.check();
    }

    public List<Path> getUncheckedFiles() {
        return allFiles.entrySet().stream()
                .filter(e -> !e.getValue().checked
                            && e.getKey().toString().endsWith(".html")
                            && Files.exists(e.getKey()))
                .map(e -> e.getKey())
                .collect(Collectors.toList());
    }

    public List<Path> getMissingFiles() {
        return allFiles.entrySet().stream()
                .filter(e -> !Files.exists(e.getKey()))
                .map(e -> e.getKey())
                .collect(Collectors.toList());
    }

    @Override
    public void xml(int line, Map<String, String> attrs) {
        xml = true;
    }

    @Override
    public void docType(int line, String doctype) {
        html5 = doctype.matches("(?i)<\\?doctype\\s+html>");
    }

    @Override @SuppressWarnings("fallthrough")
    public void startElement(int line, String name, Map<String, String> attrs, boolean selfClosing) {
        switch (name) {
            case "a":
                String nameAttr = html5 ? null : attrs.get("name");
                if (nameAttr != null) {
                    foundAnchor(line, nameAttr);
                }
                // fallthrough
            case "link":
                String href = attrs.get("href");
                if (href != null && !checkInwardReferencesOnly) {
                    foundReference(line, href);
                }
                break;
        }

        String idAttr = attrs.get("id");
        if (idAttr != null) {
            foundAnchor(line, idAttr);
        }
    }

    @Override
    public void endElement(int line, String name) {
    }

    @Override
    public void report(Reporter r) {
        List<Path> missingFiles = getMissingFiles();
        if (!missingFiles.isEmpty()) {
            log.report("");
            log.report("Missing files: (" + missingFiles.size() + ")");
            missingFiles.stream()
                    .sorted()
                    .forEach(this::reportMissingFile);

        }

        if (!allURIs.isEmpty()) {
            log.report("");
            log.report("External URLs:");
            allURIs.keySet().stream()
                    .sorted(new URIComparator())
                    .forEach(uri -> log.report("%s", uri.toString()));
        }

        int anchors = 0;
        for (IDTable t : allFiles.values()) {
            anchors += t.map.values().stream()
                    .filter(e -> !e.getReferences().isEmpty())
                    .count();
        }
        for (IDTable t : allURIs.values()) {
            anchors += t.map.values().stream()
                    .filter(e -> !e.references.isEmpty())
                    .count();
        }

        r.startSection("Link Report", log);
        r.report(false, "Checked " + files + " files.");
        r.report(false, "Found " + links + " references to " + anchors + " anchors "
                + "in " + allFiles.size() + " files and " + allURIs.size() + " other URIs.");
        r.report(!missingFiles.isEmpty(),   "%6d missing files", missingFiles.size());
        r.report(duplicateIds > 0, "%6d duplicate ids", duplicateIds);
        r.report(missingIds > 0,   "%6d missing ids", missingIds);

        Map<String, Integer> schemeCounts = new TreeMap<>();
        Map<String, Integer> hostCounts = new TreeMap<>(new HostComparator());
        for (URI uri : allURIs.keySet()) {
            String scheme = uri.getScheme();
            if (scheme != null) {
                schemeCounts.put(scheme, schemeCounts.computeIfAbsent(scheme, s -> 0) + 1);
            }
            String host = uri.getHost();
            if (host != null) {
                hostCounts.put(host, hostCounts.computeIfAbsent(host, h -> 0) + 1);
            }
        }

        if (schemeCounts.size() > 0) {
            r.report(false, "");
            r.report(false, "Schemes");
            schemeCounts.forEach((s, n) -> {
                boolean schemeOK = isSchemeOK(s);
                r.report(!schemeOK, "%6d %s", n, s);
                if (!schemeOK) {
                    badSchemes++;
                }
            });
        }

        if (hostCounts.size() > 0) {
            r.report(false, "");
            r.report(false, "Hosts");
            hostCounts.forEach((h, n) -> r.report(false, "%6d %s", n, h));
        }

        r.report(false, "");
        r.endSection();
    }

    private void reportMissingFile(Path file) {
        log.report(log.againstBaseDir(file).toString());
        IDTable table = allFiles.get(file);
        Set<Path> refs = new TreeSet<>();
        for (IDInfo id : table.map.values()) {
            if (id.references != null) {
                for (Position ref : id.references) {
                    refs.add(ref.path);
                }
            }
        }
        int n = 0;
        int MAX_REFS = 10;
        for (Path ref : refs) {
            log.report("    in " + log.againstBaseDir(ref));
            if (++n == MAX_REFS) {
                log.report("    ... and %d more", refs.size() - n);
                break;
            }
        }
        missingFiles++;
    }

    @Override
    public boolean isOK() {
        return duplicateIds == 0
                && missingIds == 0
                && missingFiles == 0
                && badSchemes == 0;
    }

    @Override
    public void close() throws IOException {
        log.close();
    }

    private void foundAnchor(int line, String name) {
        currTable.addID(line, name);
    }

    private void foundReference(int line, String ref) {
        links++;
        try {
            URI uri = new URI(ref);
            if (uri.isAbsolute()) {
                foundReference(line, uri);
            } else {
                Path p;
                String uriPath = uri.getPath();
                if (uriPath == null || uriPath.isEmpty()) {
                    p = currFile;
                } else {
                    p = currFile.getParent().resolve(uriPath).normalize();
                }
                var fragment = uri.getFragment();
                if (fragment != null && !fragment.isEmpty()) {
                    foundReference(line, p, fragment);
                }
            }
        } catch (URISyntaxException e) {
            log.error(currFile, line, "invalid URI: " + e);
        }
    }

    private void foundReference(int line, Path p, String fragment) {
        IDTable t = allFiles.computeIfAbsent(p, key -> new IDTable(log.againstBaseDir(key)));
        t.addReference(fragment, currFile, line);
    }

    private void foundReference(int line, URI uri) {
        if (!isSchemeOK(uri.getScheme()) && !checkInwardReferencesOnly) {
            log.error(currFile, line, "bad scheme in URI");
            badSchemes++;
        }

        String fragment = uri.getRawFragment();
        if (fragment != null && !fragment.isEmpty()) {
            try {
                URI noFrag = new URI(uri.toString().replaceAll("#\\Q" + fragment + "\\E$", ""));
                IDTable t = allURIs.computeIfAbsent(noFrag, key -> new IDTable(key));
                t.addReference(fragment, currFile, line);
            } catch (URISyntaxException e) {
                throw new Error(e);
            }
        }
    }

    private boolean isSchemeOK(String uriScheme) {
        if (uriScheme == null) {
            return true;
        }

        switch (uriScheme) {
            case "ftp":
            case "http":
            case "https":
            case "javascript":
                return true;

            default:
                return false;
        }
    }

    static class URIComparator implements Comparator<URI> {
        final HostComparator hostComparator = new HostComparator();

        @Override
        public int compare(URI o1, URI o2) {
            if (o1.isOpaque() || o2.isOpaque()) {
                return o1.compareTo(o2);
            }
            String h1 = o1.getHost();
            String h2 = o2.getHost();
            String s1 = o1.getScheme();
            String s2 = o2.getScheme();
            if (h1 == null || h1.isEmpty() || s1 == null || s1.isEmpty()
                    || h2 == null || h2.isEmpty() || s2 == null || s2.isEmpty()) {
                return o1.compareTo(o2);
            }
            int v = hostComparator.compare(h1, h2);
            if (v != 0) {
                return v;
            }
            v = s1.compareTo(s2);
            if (v != 0) {
                return v;
            }
            return o1.compareTo(o2);
        }
    }

    static class HostComparator implements Comparator<String> {
        @Override
        public int compare(String h1, String h2) {
            List<String> l1 = new ArrayList<>(Arrays.asList(h1.split("\\.")));
            Collections.reverse(l1);
            String r1 = String.join(".", l1);
            List<String> l2 = new ArrayList<>(Arrays.asList(h2.split("\\.")));
            Collections.reverse(l2);
            String r2 = String.join(".", l2);
            return r1.compareTo(r2);
        }
    }

}
