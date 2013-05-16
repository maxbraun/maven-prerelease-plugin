/**
 * Copyright 1&1 Internet AG, https://github.com/1and1/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.oneandone.maven.plugins.prerelease.util;

import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.launcher.Failure;
import net.oneandone.sushi.launcher.Launcher;
import org.apache.maven.plugin.logging.Log;

public final class Subversion {
    public static Launcher launcher(FileNode dir, String ... args) {
        return new Launcher(dir, "svn", "--non-interactive", "--no-auth-cache").arg(args);
    }

    public static boolean exists(FileNode dir, String svnurl) {
        try {
            Subversion.launcher(dir, "ls", svnurl).exec();
            return true;
        } catch (Failure e) {
            return false;
        }
    }

    public static void sparseCheckout(Log log, FileNode result, String svnurl, String revision, boolean tryChanges) throws Failure {
        log.debug(Subversion.launcher(result.getParent(), "co", "-r", revision, "--depth", "empty", svnurl, result.getName()).exec());
        log.debug(Subversion.launcher(result, "up", "-r", revision, "pom.xml").exec());
        if (tryChanges) {
            log.debug(Subversion.launcher(result, "up", "-r", revision, "--depth", "empty", "src").exec());
            log.debug(Subversion.launcher(result, "up", "-r", revision, "--depth", "empty", "src/changes").exec());
            log.debug(Subversion.launcher(result, "up", "-r", revision, "src/changes/changes.xml").exec());
        }
    }

    private Subversion() {
    }
}
