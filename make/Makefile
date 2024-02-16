#
# Copyright (c) 1999, 2024, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.  Oracle designates this
# particular file as subject to the "Classpath" exception as provided
# by Oracle in the LICENSE file that accompanied this code.
#
# This code is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
# FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
# version 2 for more details (a copy is included in the LICENSE file that
# accompanied this code).
#
# You should have received a copy of the GNU General Public License version
# 2 along with this work; if not, write to the Free Software Foundation,
# Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
#
# Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
# or visit www.oracle.com if you need additional information or have any
# questions.
#

TOPDIR = ..

include Defs.gmk

default: build

all: build test

#----------------------------------------------------------------------

include doccheck.gmk
include $(TOPDIR)/test/*/*.gmk
include Rules.gmk

build: check-build-vars $(BUILDFILES)

images: $(VERBOSEZIPFILES)

test:

clean:
	$(RM) $(BUILDDIR)

.NO_PARALLEL: clean

sanity:
	@echo "JDKHOME               = $(JDKHOME)"
	@echo "JUNIT_JAR             = $(JUNIT_JAR)"
	@echo "JCOMMANDER_JAR        = $(JCOMMANDER_JAR)"
	@echo "JAVADIFFUTILS_JAR     = $(JAVADIFFUTILS_JAR)"
	@echo "JAVADIFFUTILS_LICENSE = $(JAVADIFFUTILS_LICENSE)"
	@echo "DAISYDIFF_JAR         = $(DAISYDIFF_JAR)"
	@echo "DAISYDIFF_LICENSE     = $(DAISYDIFF_LICENSE)"
	@echo "HTMLCLEANER_JAR       = $(HTMLCLEANER_JAR)"
	@echo "HTMLCLEANER_LICENSE   = $(HTMLCLEANER_LICENSE)"

check-build-vars:
	@if [ -z "$(JDKHOME)" ]; then \
		echo "JDKHOME not set; must be JDK 17 or later" ; exit 1 ; \
	fi

#----------------------------------------------------------------------


.PHONY: default all build test images clean sanity


