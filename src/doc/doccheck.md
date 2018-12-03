% DocCheck

`doccheck` is a utility that provides a variety of checks for HTML documentation,
such as the JDK documentation bundle. It is not meant to replace more authoritative
checkers; instead, it is more focused on providing a convenient, easy overview
of any possible issues.

It supports the following checks:

+   *HTML* -- `doccheck` leverages the standard [tidy] utility to check for HTML 
    compliance, according to the declared version of HTML. The output from `tidy` 
    is analysed to generate a report summarizing any issues that were found.

+   *Accessibility* -- `doccheck` provides some very basic checking for
    accessibility, such as declaring a default language, tables having captions
    and row/column headers, and so on.

+   *Bad Characters* -- `doccheck` assumes that HTML files are encoded in UTF-8,
    and reports any character encoding issues that it finds.

+   *DocType* -- `doccheck` assumes that HTML files should use HTML5, and reports
    any files for which that is not the case.

+   *Legal* -- `doccheck` checks for the presence of expected legal text, such as
    copyright notices, near the end of each file.

+   *Links* -- `doccheck` checks links within a set of files, and reports on links
    to external resources, without otherwise checking them.

+   *External Links* -- `doccheck` scans the files for URLs that refer to
    external resources, and validates those references. Each external reference 
    is only checked once; but if an issue is found, all the files containing the 
    reference will be reported.

These checks can be independently enabled/disabled using the `--check` option
on the `doccheck` command line.


USAGE
-----

`$ java -jar` _/path/to/_`doccheck.jar`  _options_  _files_  

The _files_ can be any series of HTML files or directories containing HTML files.

The _options_ include:  

`--base-directory` _dir_
:   Set a base directory for paths on command line and in log files.

`--check` _key(,key)\*_
:   Check aspects of HTML files.
    Supported keys are:
    
    Key              Use
    --------------   ----------------------------------------
    `all`            Enable all checks 
    `accessibility`  Check accessibility
    `bad_chars`      Check for bad characters in the files
    `doctype`        Check for HTML doctype
    `html`           Check HTML
    `legal`          Check legal items at the foot of each page
    `links`          Check internal links
    `extlinks`       Check external links
    `none`           Disable all checks
  
    Precede any key (except `all` and `none`) by '-' to negate its effect.  For example,
    `--check all,-legal`

`--copyright` _regex_
:   Specify a regular expression for an acceptable copyright line for the 'legal' checker.
    The option may be given multiple times.  
    If not given, the 'legal' checker will report "unknown copyright" for lines
    that contain the word "Copyright" near the end of each HTML file.

`--exclude` _file_
:   Exclude a file or directory when scanning for files to check.

`--help`
:   Display this text.

`--ignore-url` _regex_
:   Specify one or more regular expressions, separated by white-space, for URLs to 
    ignore in the 'extLinks' checker. The URLs will still be listed in the report,
    but will not be checked for validity and/or cause any errors.
   
`--ignore-url-redirects`
:   Do not warn about URL redirects in the 'extLinks' checker.
    The redirected URLs will still be listed in the report, but will not cause
    any warnings or errors.
   
`--verbose`
:   Trace execution.
    
`--jbs` _url_
:   Specify a URL to include in the report. This may be used to specify a URL
    that is a query for open issues related to errors in the documentation.
    
`--jdk` _jdk-docs-directory_
:   Generate a report for each module in a JDK docs bundle.
    Information about the modules and their contents will be reflectively
    determined from the runtime used to execute `doccheck`.
    By default, all modules with unqualified exports will be included in the
    report, as well as files in the top-level directory, and in the `specs`
    subdirectory. 
    The set of modules can be specified explicitly with the `--module` option.
    
`--module` _name(_ `,` _name)\*_
:   Specify the set of modules to be analyzed when generating a JDK report.
    In general, the names should be the names of modules in the JDK
    runtime and documentation, but two additional names are also recognized:
    
    * `specs` -- the set of files in the `specs` directory (and its subdirectories)
    * `top-files` -- the set of files in the top level documentation directory
    
`--title` _string_
:   Specify a title for the report that is generated.
    
`--report` _file_
:   Specify where to write the report.
    If the file is an existing directory, or ends with `/`, 
    the report will be split into separate files in the directory; 
    otherwise, all the output will be written to a single file.

Reports
-------

The report can either be a single text file, or a directory of files
that are a mix of an HTML report file, and plain-text logs that give
details of any errors that are found. 

To generate a report in a directory, the `--output` option should either
name an existing directory, or it should end with the platform file separator
(`\` on Windows, `/` otherwise).

When the `--jdk` option is used, a special mode is invoked, such that the checks 
are invoked separately on the set of files for each of the specified modules, 
writing a report for each invocation into subdirectories of the output directory. 
An umbrella report is then generated in the output directory itself:
this report is a table containing one line per module, giving a simple visual 
indication of the outcome of the selected checks.

A title for the report can be specified with the `--title` option; 
a JBS URL to include at the end of the report can be specified with the `--jbs` option.

EXAMPLES
--------

### Generating a report for invalid HTML and internal broken links in the JDK documentation

The simplest way to run `doccheck` is to specify the desired checks, the directory
containing the files to be checked, and a directory or file in which to write the report.


````shell
$jdk/bin/java \
	-jar /w/jjg/work/doccheck/dist/doccheck.jar \
	--report myReport/ \
	--check html,links \
	build/linux-x86_64-server-release/images/docs
````


### Generating a report for all the JDK documentation

When using the 'extLinks' checker, you may need to set up proxies to access external URLs.
Use the standard Java [networking properties] to set up any necessary proxies.
Also, some external URLs may not be available before a version of JDK is released. 
To suppress the checks for these URLs, specify one or more regular expressions with
the `--ignore-urls` option.

When using the 'legal' checker, it is recommended to specify one or more regular expressions 
for approved "legal" text that should be found near the end of each file.
Note that different files may use different paths (using a different number of leading `../`
strings) depending on the position of the file in the overall documentation hierarchy.

````shell
# uncomment as needed
# PROXYHOST=... # set your proxy host here
# PROXIES="-Dhttp.proxyHost=$PROXYHOST -Dhttps.proxyHost=$PROXYHOST -Dhttps.proxyPort=80 -Dftp.proxyHost=$PROXYHOST -Dftp.proxyPort=80"

IGNORE_URLS='\Qhttps://docs.oracle.com/pls/topic/lookup?ctx=javase12&id=\E[A-Za-z0-9_]+ \Qhttps://www.oracle.com/technetwork/java/javase/terms/license/java12speclicense.html\E'

COPYRIGHTYEAR=2018
LICENSE=https://www.oracle.com/technetwork/java/javase/terms/license/java12speclicense.html
REDIST_POLICY=https://www.oracle.com/technetwork/java/redist-137594.html
COPYRIGHT='\Q<a href="\E(\./)?(\.\./)*\Qlegal/copyright.html">Copyright</a> &copy; 1993, '$COPYRIGHTYEAR', Oracle and/or its affiliates, 500 Oracle Parkway, Redwood Shores, CA 94065 USA.<br>All rights reserved. Use is subject to <a href="'$LICENSE'">license terms</a> and the <a href="'$REDIST_POLICY'">documentation redistribution policy</a>.\E'

$jdk/bin/java \
	$PROXIES \
	-jar /w/jjg/work/doccheck/dist/doccheck.jar \
	--report myReport/ \
	--copyright "$COPYRIGHT" \
	--ignore-urls "$IGNORE_URLS" \
	--ignore-url-redirects \
	build/linux-x86_64-server-release/images/docs
````


### Generating a report for all the JDK documentation, grouped by module

To generate a report for JDK documentation that contains details for each module,
use the `--jdk` option to specify the root of the JDK documentation. This is
typically the directory that contains the `api/` and `specs/` subdirectories.
When using the `--jdk` option, you should not specify any additional files on 
the command line.

````shell
# uncomment as needed
# PROXYHOST=... # set your proxy host here
# PROXIES="-Dhttp.proxyHost=$PROXYHOST -Dhttps.proxyHost=$PROXYHOST -Dhttps.proxyPort=80 -Dftp.proxyHost=$PROXYHOST -Dftp.proxyPort=80"

COPYRIGHTYEAR=2018
LICENSE=https://www.oracle.com/technetwork/java/javase/terms/license/java12speclicense.html
REDIST_POLICY=https://www.oracle.com/technetwork/java/redist-137594.html
COPYRIGHT='\Q<a href="\E(\./)?(\.\./)*\Qlegal/copyright.html">Copyright</a> &copy; 1993, '$COPYRIGHTYEAR', Oracle and/or its affiliates, 500 Oracle Parkway, Redwood Shores, CA 94065 USA.<br>All rights reserved. Use is subject to <a href="'$LICENSE'">license terms</a> and the <a href="'$REDIST_POLICY'">documentation redistribution policy</a>.\E'

IGNORE_URLS='\Qhttps://docs.oracle.com/pls/topic/lookup?ctx=javase12&id=\E[A-Za-z0-9_]+ \Qhttps://www.oracle.com/technetwork/java/javase/terms/license/java12speclicense.html\E'

$jdk/bin/java \
	$PROXIES \
	-jar /w/jjg/work/doccheck/dist/doccheck.jar \
	--report myReport/ \
	--copyright "$COPYRIGHT" \
	--ignore-urls "$IGNORE_URLS" \
	--ignore-url-redirects \
	--jdk build/linux-x86_64-server-release/images/docs
````


### Generating a report for links in selected modules in the JDK documentation

To generate a report for selected modules in the JDK documentation, use the
`--module` option in conjunction with the `--jdk` option.

````shell
IGNORE_URLS='\Qhttps://docs.oracle.com/pls/topic/lookup?ctx=javase12&id=\E[A-Za-z0-9_]+ \Qhttps://www.oracle.com/technetwork/java/javase/terms/license/java12speclicense.html\E'

# uncomment as needed
# PROXYHOST=... # set your proxy host here
# PROXIES="-Dhttp.proxyHost=$PROXYHOST -Dhttps.proxyHost=$PROXYHOST -Dhttps.proxyPort=80 -Dftp.proxyHost=$PROXYHOST -Dftp.proxyPort=80"

$jdk/bin/java \
	$PROXIES \
	-jar /w/jjg/work/doccheck/dist/doccheck.jar \
	--report myReport/ \
	--ignore-urls "$IGNORE_URLS" \
	--ignore-url-redirects \
	--jdk build/linux-x86_64-server-release/images/docs \
	--module java.compiler,jdk.compiler \
	--check links,extlinks
````

DocCheck vs. DocLint
--------------------

DocLint is a utility built into javac and javadoc that performs some amount of
checking of the content of documentation comments. Although there is some overlap
in functionality, the two utilities are different and each has its own strengths
and weaknesses.

*   `doccheck` checks the end result of any generated documentation. This includes
    content from all sources, such as documentation comments, the standard doclet,
    user-provided taglets, and content supplied via command-line options. Because
    it is analyzing complete HTML pages, it can do more complete checks than can
    DocLint.

    However, when problems are found in generated pages, it can be harder to
    track down exactly where the problem needs to be fixed. (This was a major
    motivation for writing DocLint.)

*   DocLint checks the content of documentation comments. This makes it very easy
    to identify the exact position of any issues that may be found. DocLint can
    also detect some semantic errors in documentation comments that DocCheck
    cannot detect, such as using an `@return` tag in a method returning `void`,
    or a `@param` tag describing a non-existent parameter.

    But by its nature, DocLint cannot report on problems such as missing links,
    or errors in user-provided custom taglets, or problems in the standard
    doclet itself. It also cannot reliably detect errors in documentation
    comments at the boundaries between content in a documentation comment
    and content generated by a custom taglet.

    DocLint does not currently check the content of any HTML pages in `doc-files`
    subdirectories. These files are simply copied to the output directory, along
    with any other files in such directories.

[networking properties]: https://docs.oracle.com/javase/10/docs/api/java/net/doc-files/net-properties.html
[tidy]: http://www.html-tidy.org