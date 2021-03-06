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
package net.oneandone.maven.plugins.prerelease;

import net.oneandone.sushi.fs.Node;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.io.OS;
import net.oneandone.sushi.util.Separator;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Find an delete stale locks. JVM crashes or kill -9 does not properly cleanup locks. This goals repairs them by looking for locks
 * from processes that no longer exists.
 *
 * Note that this locking cleanup should run in a single, separate process to avoid running into its own locking problems. It
 * must not be included in the prepareLock file creating itself because there are multiple processes involved.
 */
@Mojo(name = "locksmith", requiresProject = false)
public class Locksmith extends Base {
    /**
     * Fail when stale locks are detected?
     */
    @Parameter(property = "prerelease.locksmith.fail")
    private boolean fail = false;

    /**
     * Delete stale locks?
     */
    @Parameter(property = "prerelease.locksmith.delete")
    private boolean delete = false;

    private static final long COUNT = 3;
    private static final long HOUR = 1000L * 60 * 60;

    @Override
    public void doExecute() throws Exception {
        Map<String, Long> started;
        FileNode primary;
        String pid;
        Long time;
        int errors;
        List<FileNode> locks;

        started = startedMap();
        errors = 0;
        primary = storages().get(0);
        if (!primary.exists()) {
            return;
        }
        locks = primary.find("*/*.LOCK");
        for (Node file : locks) {
            try {
                pid = file.readString();
            } catch (IOException e) {
                if (file.isFile()) {
                    throw e;
                } else {
                    // prepareLock has already been removed, probably by a concurrent build that finished
                    continue;
                }
            }
            if (pid.trim().isEmpty()) {
                throw new IOException(file + ": old prepareLock file format");
            }
            time = started.get(pid);
            if (time == null) {
                getLog().info(file + ": stale prepareLock - no process with id " + pid);
            } else if (file.getLastModified() < time) {
                getLog().info(file + ": stale prepareLock - process with id " + pid + " younger than prepareLock file");
            } else {
                getLog().debug(file + " ok");
                continue;
            }
            errors++;
            if (delete) {
                getLog().info("deleting " + file);
                file.deleteFile();
            }
        }
        getLog().info("locks checked: " + locks.size() + ", locks stale: " + errors);
        if (errors > 0 && fail) {
            throw new MojoExecutionException("stale locks: " + errors);
        }
    }

    private static final SimpleDateFormat TODAY = new SimpleDateFormat("HH:mm:ss");
    private static final SimpleDateFormat OTHER = new SimpleDateFormat("MMM dd", Locale.US);
    private static final SimpleDateFormat MAC = new SimpleDateFormat("EEE MMM dd HH:mm:ss yyyy");

    private Map<String, Long> startedMap() throws IOException {
        String[] cmd;
        Map<String, Long> result;
        boolean first;
        int idx;
        String pid;
        String startedStr;
        Date startedDate;

        result = new HashMap<>();
        first = true;
        if (OS.CURRENT == OS.MAC) {
            cmd = new String[] { "ps", "ax", "-o", "pid", "-o", "lstart"};
        } else {
            cmd = new String[] { "ps", "ax", "--format", "pid,start" };
        }

        for (String line : Separator.RAW_LINE.split(((FileNode) world.getWorking()).exec(cmd))) {
            if (first) {
                if (!line.contains("PID")) {
                    throw new IllegalStateException(line);
                }
                first = false;
            } else {
                line = line.trim();
                idx = line.indexOf(' ');
                if (idx == -1) {
                    throw new IllegalStateException(line);
                }
                pid = line.substring(0, idx);
                startedStr = line.substring(idx + 1).trim();
                try {
                    if (OS.CURRENT == OS.MAC) {
                        startedDate = MAC.parse(startedStr);
                    } else {
                        startedDate = (startedStr.indexOf(':') == -1 ? OTHER: TODAY).parse(startedStr);
                    }
                } catch (ParseException e) {
                    throw new IllegalStateException("invalid date in line " + line, e);
                }
                result.put(pid, startedDate.getTime());
            }
        }
        return result;
    }
}
