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
package net.oneandone.maven.plugins.prerelease.core;

import net.oneandone.maven.plugins.prerelease.util.Scm;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.launcher.Failure;
import net.oneandone.sushi.util.Strings;
import org.apache.maven.plugin.logging.Log;

import java.util.List;
import java.util.SortedSet;

public class WorkingCopy {

    //--

    public final FileNode directory;
    public final List<FileNode> modifications;
    public final SortedSet<Long> revisions;
    public final SortedSet<Long> changes;
    public final List<String> pendingUpdates;
    private final Scm scm;

    public WorkingCopy(FileNode directory, List<FileNode> modifications, SortedSet<Long> revisions, SortedSet<Long> changes,
                       List<String> pendingUpdates, Scm scm) {
        this.directory = directory;
        this.modifications = modifications;
        this.revisions = revisions;
        this.changes = changes;
        this.pendingUpdates = pendingUpdates;
        this.scm = scm;
    }

    public long revision() {
        return changes.last();
    }

    public void check() throws UncommitedChanges, PendingUpdates {
        if (!modifications.isEmpty()) {
            throw new UncommitedChanges(modifications);
        }
        if (!pendingUpdates.isEmpty()) {
            throw new PendingUpdates(revisions.last(), pendingUpdates);
        }
    }

    public Descriptor checkCompatibility(Descriptor descriptor) throws Exception {
        String svnurlWorkspace;

        svnurlWorkspace = scm.workspaceUrl(directory);
        svnurlWorkspace = Strings.removeRightOpt(svnurlWorkspace, "/");
        if (!svnurlWorkspace.equals(descriptor.svnOrig)) {
            throw new SvnUrlMismatch(svnurlWorkspace, descriptor.svnOrig);
        }
        if (revision() != descriptor.revision) {
            throw new RevisionMismatch(revision(), descriptor.revision);
        }
        return descriptor;
    }

    public void update(Log log) throws Failure {
        log.info(scm.prepareUpdate(directory).exec());
    }
}
