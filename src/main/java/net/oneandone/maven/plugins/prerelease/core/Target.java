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
import org.apache.maven.plugin.logging.Log;

import java.io.IOException;

/**
 * Directory for a prerelease. The prerelease is not necessarily promotable (it might not exist (yet), or it may be broken, or it
 * might already be promoted).
 */
public class Target {
    public static final String REMOVE = "REMOVE";

    private FileNode directory;
    private final long revision;
    private final Scm scm;

    public Target(FileNode directory, long revision, Scm scm) {
        this.directory = directory;
        this.revision = revision;
        this.scm = scm;
    }

    public boolean exists() {
        return directory.exists();
    }

    public Prerelease loadOpt() throws IOException {
        return exists() ? Prerelease.load(this) : null;
    }

    public FileNode join(String ... paths) {
        return directory.join(paths);
    }

    public void scheduleRemove(Log log, String message) throws IOException {
        FileNode remove;

        remove = removeDirectory();
        log.info(message + " - moving prerelease to " + remove);
        directory.move(remove);
        remove.join("CAUSE").writeString(message);
        directory = remove;
    }

    public long getRevision() {
        return revision;
    }

    public void create() throws IOException {
        removeDirectory().deleteTreeOpt();
        directory.mkdirs();
    }
    public FileNode node() {
        return directory;
    }

    public Scm scm() {
        return scm;
    }

    private FileNode removeDirectory() {
        return directory.getParent().join(REMOVE);
    }

    public boolean checkoutLinkOpt(String path) throws IOException {
        if (path == null) {
            return false;
        }
        path = path.trim();
        if (path.isEmpty()) {
            return false;
        }
        return checkoutLink(directory.getWorld().file(path));
    }

    /** @return true when created */
    public boolean checkoutLink(FileNode checkoutLink) throws IOException {
        FileNode tags;
        FileNode workingCopy;

        tags = directory.join("tags");
        if (!tags.exists()) {
            return false;
        }
        workingCopy = null;
        for (FileNode file : tags.list()) {
            if (file.isDirectory() && !file.getName().startsWith(".")) {
                if (workingCopy != null) {
                    throw new IllegalStateException();
                }
                workingCopy = file;
            }
        }
        if (workingCopy == null) {
            return false;
        }
        checkoutLink.deleteTreeOpt();
        checkoutLink.getParent().mkdirsOpt();
        workingCopy.link(checkoutLink);
        return true;
    }

    public String toString() {
        return directory.toString();
    }
}
