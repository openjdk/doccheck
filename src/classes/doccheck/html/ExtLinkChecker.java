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

package doccheck.html;

import doccheck.HtmlChecker;
import doccheck.Log;
import doccheck.Reporter;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLConnection;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Checks the external links referenced in HTML files.
 */
public class ExtLinkChecker implements HtmlChecker {
    private final Log log;
    private final List<Pattern> ignoreURLs;
    private final boolean ignoreURLRedirects;
    private final Map<URI, Set<Path>> allURIs;
    private final Map<URI, URI> redirects;
    private int files;
    private int badURIs;
    private final Map<String, Integer> hostCounts;
    private final Map<String, Integer> ignoreURLCounts;
    private final Map<String, Integer> schemeCounts;
    private final Map<String, Integer> exceptionCounts;
    private final Map<String, Integer> exceptionInstanceCounts;
    private final Map<String, Integer> statusCounts;
    private final Map<String, Integer> statusInstanceCounts;
    private static final boolean verbose = Boolean.getBoolean("extLinks.verbose");

    private Path currFile;

    public ExtLinkChecker(Log log, List<Pattern> ignoreURLs, boolean ignoreURLRedirects) {
        this.log = log;
        this.ignoreURLs = ignoreURLs;
        this.ignoreURLRedirects = ignoreURLRedirects;
        allURIs = new TreeMap<>();
        redirects = new TreeMap<>();
        hostCounts = new TreeMap<>();
        ignoreURLCounts = new TreeMap<>();
        schemeCounts = new TreeMap<>();
        exceptionCounts = new TreeMap<>();
        exceptionInstanceCounts = new TreeMap<>();
        statusCounts = new TreeMap<>();
        statusInstanceCounts = new TreeMap<>();
    }

    @Override
    public void startFile(Path path) {
        currFile = path.toAbsolutePath().normalize();
        files++;
    }

    @Override
    public void endFile() {  }

    @Override
    public void xml(int line, Map<String, String> attrs) { }

    @Override
    public void docType(int line, String doctype) { }

    @Override @SuppressWarnings("fallthrough")
    public void startElement(int line, String name, Map<String, String> attrs, boolean selfClosing) {
        switch (name) {
            case "a":
            case "link":
                String href = attrs.get("href");
                if (href != null) {
                    foundReference(line, href);
                }
                break;
        }
    }

    @Override
    public void endElement(int line, String name) { }

    private void foundReference(int line, String ref) {
        try {
            URI uri = new URI(ref);
            if (uri.isAbsolute()) {
                if (Objects.equals(uri.getScheme(), "javascript")) {
                    // ignore JavaScript URIs
                    return;
                }
                String fragment = uri.getRawFragment();
                URI noFrag = new URI(uri.toString().replaceAll("#\\Q" + fragment + "\\E$", ""));
                allURIs.computeIfAbsent(noFrag, _u -> new LinkedHashSet<>()).add(currFile);
            }
        } catch (URISyntaxException e) {
            log.error(currFile, line, "invalid URI: " + e);
        }
    }
    @Override
    public void report(Reporter r) {
        checkURIs();

        r.startSection("External Link Report", log);
        r.report(false, "Checked " + files + " files.");
        r.report(false, "Found references to " + allURIs.size() + " external URIs.");

        if (!redirects.isEmpty()) {
            r.startTable("Redirects", List.of("URL", "Redirected to"));
            redirects.forEach((url, red) -> r.addTableRow(List.of(url.toString(), red.toString())));
            r.endTable();
        }

        Pattern ORACLE_COM = Pattern.compile("(?i)oracle.com");
        Pattern SE_VERSION = Pattern.compile("(?i)[=/](java)?se(/)?(?<v>[0-9]+)/");

        Map<Integer, Set<URI>> refs = new TreeMap<>();
        for (Map.Entry<URI, Set<Path>> entry : allURIs.entrySet()) {
            URI uri = entry.getKey();
            if (ORACLE_COM.matcher(uri.getHost()).find()) {
                Matcher m = SE_VERSION.matcher(uri.toString());
                if (m.find()) {
                    int v = Integer.parseInt(m.group("v"));
                    refs.computeIfAbsent(v, s -> new TreeSet<>()).add(uri);
                }
            }
        }
        if (!refs.isEmpty()) {
            r.startTable("References to Earlier Releases",
                    List.of("Version", "URL", "Referenced in file"));
            for (Map.Entry<Integer, Set<URI>> e1 : refs.entrySet()) {
                String v = String.valueOf(e1.getKey());
                for (URI uri : e1.getValue()) {
                    Set<Path> paths = allURIs.get(uri);
                    String list = paths.stream()
                            .map(Object::toString)
                            .collect(Collectors.joining("\n"));
                    r.addTableRow(List.of(v, uri.toString(), list));
                }
            }
            r.endTable();
        }

        if (ignoreURLCounts.size() > 0) {
            r.report(false, "");
            r.report(false, "Ignored URLs");
            ignoreURLCounts.forEach((u, n) -> r.report(false, "%6d %s", n, u));
        }

        if (schemeCounts.size() > 0) {
            r.report(false, "");
            r.report(false, "Schemes");
            schemeCounts.forEach((s, n) -> r.report(!isSchemeOK(s), "%6d %s", n, s));
        }

        if (hostCounts.size() > 0) {
            r.report(false, "");
            r.report(false, "Hosts");
            hostCounts.forEach((h, n) -> r.report(false, "%6d %s", n, h));
        }

        if (exceptionCounts.size() > 0) {
            r.report(false, "");
            r.report(false, "Exceptions");
            exceptionCounts.forEach((s, n) -> r.report(true, "%6d %s", n, s));
        }

        if (exceptionInstanceCounts.size() > 0) {
            r.report(false, "");
            r.report(false, "Exception Instances");
            exceptionInstanceCounts.forEach((s, n) -> r.report(true, "%6d %s", n, s));
        }

        if (statusCounts.size() > 0) {
            r.report(false, "");
            r.report(false, "Http Status");
            statusCounts.forEach((s, n) -> r.report(!isStatusOK(s), "%6d %s", n, s));
        }

        if (statusInstanceCounts.size() > 0) {
            r.report(false, "");
            r.report(false, "Http Status Instances");
            statusInstanceCounts.forEach((s, n) -> r.report(!isStatusOK(s), "%6d %s", n, s));
        }

        r.report(false, "");
        r.endSection();
    }

    private boolean isStatusOK(String s) {
        Pattern p = Pattern.compile("([0-9]+).*");
        Matcher m = p.matcher(s);
        if (m.matches()) {
            int c = Integer.parseInt(m.group(1));
            switch (c) {
                case 200: // OK
                case 302: // Found
                case 307: // Temporary Redirect
                    return true;
                default:
                    if (ignoreURLRedirects && c >= 300 && c < 400) {
                        return true;
                    }
            }
        }
        return false;
    }

    @Override
    public boolean isOK() {
        return badURIs == 0;
    }

    @Override
    public void close() throws IOException {
        log.close();
    }

    private void checkURIs() {
        if (verbose) {
            System.err.println("ExtLinkChecker: checking external links");
        }

        allURIs.forEach((uri, files) -> {
            if (verbose) {
                System.err.println(uri);
            }
            checkURI(uri, files);
        });

        if (verbose) {
            System.err.println("ExtLinkChecker: finished checking external links");
        }
    }

    private void checkURI(URI uri, Set<Path> files) {
        boolean ignoreURL = ignoreURLs.stream()
                .map(p -> p.matcher(uri.toString()))
                .anyMatch(Matcher::matches);
        if (ignoreURL) {
            count(ignoreURLCounts, uri.toString(), files.size());
            return;
        }
        try {
            switch (uri.getScheme()) {
                case "ftp":
                    checkFTP(uri, files);
                    break;
                case "http":
                case "https":
                    checkHttp(uri, files);
                    break;
                default:
                    warning(files, "URI not supported: %s", uri);
            }
        } catch (Throwable t) {
            badURIs++;
            count(exceptionCounts, t.toString());
            count(exceptionInstanceCounts, t.toString(), files.size());
            error(files, "Exception accessing uri: %s%n    [%s]", uri, t);
        }

    }

    private void checkFTP(URI uri, Set<Path> files) throws IOException {
        URLConnection c = uri.toURL().openConnection();
        c.setConnectTimeout(10 * 1000);
        c.setReadTimeout(10 * 1000);
        c.connect();
        String type = c.getContentType();
        long size = c.getContentLengthLong();
        if (verbose) {
            System.err.println(uri + ": " + type + " " + size);
        }
        if (type != null && type.equals("text/html")) {
            warning(files, "Suspicious content type for %s: %s", uri, type);
        }
        try (InputStream in = c.getInputStream()) { }

        count(schemeCounts, uri.getScheme());
        count(hostCounts, uri.getHost());
    }

    private void checkHttp(URI uri, Set<Path> files) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(uri).build();
        HttpClient c = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
        HttpResponse<String> r = c.send(req, HttpResponse.BodyHandlers.ofString());

        r.previousResponse().ifPresent(prev -> {
            StringBuilder details = new StringBuilder();
            addHistory(details, prev);
            if (!ignoreURLRedirects) {
                warning(files, "URI redirected: %s%s", uri, details);
            }
        });

        if (isRedirect(r)) {
            r.headers().firstValue("location")
                    .ifPresent(l -> redirects.put(uri, URI.create(l)));
        }

        boolean ok = (r.statusCode() == 200) || (isRedirect(r) && ignoreURLRedirects);
        if (!ok) {
            error(files, "HTTP status %2$s: %1$s", uri, getStatusString(r));
            badURIs++;
        }

        count(schemeCounts, uri.getScheme());
        count(hostCounts, uri.getHost());
        countStatus(r, files);
    }

    private void countStatus(HttpResponse<?> r, Set<Path> files) {
        r.previousResponse().ifPresent(prev -> countStatus(prev, files));
        count(statusCounts, getStatusString(r.statusCode()));
        count(statusInstanceCounts, getStatusString(r.statusCode()), files.size());
        if (isRedirect(r)) {
            r.headers().firstValue("location")
                    .ifPresent(l -> redirects.put(r.request().uri(), URI.create(l)));
        }
    }

    private void addHistory(StringBuilder details, HttpResponse<String> r) {
        r.previousResponse().ifPresent(prev -> addHistory(details, prev));
        HttpHeaders h = r.headers();
        details.append("\n").append("    [").append(getStatusString(r));
        List<String> locn = h.allValues("location");
        switch (locn.size()) {
            case 0:
                break;
            case 1:
                details.append(", ").append(locn.get(0));
                break;
            default:
                details.append(", ").append(locn);
        }
        details.append("]");
    }

    private String getStatusString(HttpResponse<?> r) {
        return getStatusString(r.statusCode());
    }

    private boolean isRedirect(HttpResponse<?> r) {
        return r.statusCode() / 100 == 3;
    }

    private String getStatusString(int c) {
        return c + " " + getStatusName(c);
    }

    private String getStatusName(int c) {
        switch (c) {
            case 200: return "OK";
            case 301: return "Moved Permanently";
            case 302: return "Found";
            case 303: return "See Other";
            case 307: return "Temporary Redirect";
            case 401: return "Unauthorized";
            case 403: return "Forbidden";
            case 404: return "Not Found";
            case 500: return "Internal Server Error";
            default:  return "Unknown";
        }
    }

    private void warning(Set<Path> files, String format, Object... args) {
        Iterator<Path> iter = files.iterator();
        Path first = iter.next();
        log.warn(first, format, args);
        reportAlsoFoundIn(iter);
    }

    private void error(Set<Path> files, String format, Object... args) {
        Iterator<Path> iter = files.iterator();
        Path first = iter.next();
        log.error(first, format, args);
        reportAlsoFoundIn(iter);
    }

    private void reportAlsoFoundIn(Iterator<Path> iter) {
        int MAX_EXTRA = 10;
        int n = 0;
        while (iter.hasNext()) {
            log.report("    Also found in %s", log.againstBaseDir(iter.next()));
            if (n++ == MAX_EXTRA) {
                int rest = 0;
                while (iter.hasNext()) {
                    iter.next();
                    rest++;
                }
                log.report("    ... and %d more", rest);
            }
        }
    }

    private <K> void count(Map<K, Integer> map, K key) {
        count(map, key, 1);
    }

    private <K> void count(Map<K, Integer> map, K key, int incr) {
        Integer i = map.get(key);
        map.put(key, i == null ? 1 : i + incr);
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
}
