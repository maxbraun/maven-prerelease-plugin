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

import net.oneandone.maven.plugins.prerelease.core.Archive;
import net.oneandone.maven.plugins.prerelease.core.WorkingCopy;
import net.oneandone.sushi.fs.file.FileNode;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Lists available pre-preleases and display up-to-date information.
 */
@Mojo(name = "status")
public class Status extends ProjectBase {
    @Override
    public void doExecute(Archive archive) throws Exception {
        String revision;
        String name;

        if (archive.directory.exists()) {
            revision = Long.toString(WorkingCopy.load(basedir()).revision());
            for (FileNode prerelease : archive.directory.list()) {
                name = prerelease.getName();
                if (name.equals(revision)) {
                    name = name + " <- CURRENT";
                } else {
                    name = name + " (out-dated)";
                }
                getLog().info(name);
            }
        }
    }

    @Override
    public boolean definesTarget() {
        return false;
    }
}
