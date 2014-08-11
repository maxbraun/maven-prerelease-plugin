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

import net.oneandone.sushi.fs.Node;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Storages {
    private final List<FileNode> storages;
    private final List<Archive> opened;

    public Storages(List<FileNode> storages) {
        if (storages.isEmpty()) {
            throw new IllegalArgumentException();
        }
        this.storages = storages;
        this.opened = new ArrayList<>();
    }

    public World getWorld() {
        return storages.get(0).getWorld();
    }

    //-- main methods

    public Archive open(MavenProject project, int timeout, Log log) throws IOException {
        Archive result;
        Prerelease prerelease;
        Archive archive;

        if (!opened.isEmpty()) {
            throw new IllegalStateException();
        }
        result = openOne(Project.forMavenProject(project), timeout, log);
        try {
            for (org.apache.maven.artifact.Artifact artifact : project.getArtifacts()) {
                if (Descriptor.isSnapshot(artifact.getVersion())) {
                    if (artifact.getFile() == null) {
                        throw new IllegalStateException(artifact.toString());
                    }
                    archive = openOne(new Project(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion()), timeout, log);
                    prerelease = archive.lookupArtifact(getWorld().file(artifact.getFile()), this);
                    if (prerelease == null) {
                        opened.remove(opened.size() - 1);
                        archive.close();
                    }
                }
            }
        } catch (RuntimeException | IOException e) {
            try {
                close();
            } catch (Exception nested) {
                e.addSuppressed(nested);
            }
            throw e;
        }
        return result;
    }

    public Archive openOne(Project project, int timeout, Log log) throws IOException {
        Archive archive;

        archive = new Archive(project, directories(project));
        archive.open(timeout, log);
        opened.add(archive);
        return archive;
    }

    public Archive get(Project project) {
        for (Archive archive : opened) {
            if (archive.project.equals(project)) {
                return archive;
            }
        }
        throw new IllegalStateException(project.toString());
    }

    public void close() throws IOException {
        IOException failures;

        failures = null;
        for (Archive archive : opened) {
            try {
                archive.close();
            } catch (IOException e) {
                if (failures == null) {
                    failures = e;
                } else {
                    failures.addSuppressed(e);
                }
            }
        }
        opened.clear();
        if (failures != null) {
            throw failures;
        }
    }

    //--

    public Set<Project> list(Log log) throws IOException {
        int level;
        FileNode storage;
        List<Node> archives;
        Set<Project> result;
        Node parent;
        Node parentParent;

        result = new HashSet<>();
        for (level = 0; level < storages.size(); level++) {
            storage = storages.get(level);
            log.info("storage " + (level + 1) + ": " + storage.getAbsolute());
            archives = storage.find("*/*/*");
            for (Node candidate : archives) {
                if (!candidate.isDirectory()) {
                    continue;
                }
                parent = candidate.getParent();
                parentParent = parent.getParent();

                result.add(new Project(parentParent.getName(), parent.getName(), candidate.getName()));
            }
        }
        return result;
    }

    public List<Node> locks() throws IOException {
        FileNode primary;

        primary = storages.get(0);
        if (!primary.exists()) {
            return new ArrayList<>();
        }
        return primary.find("*/*.LOCK");
    }

    public int levels() {
        return storages.size();
    }

    public int findLevel(Node prerelease) {
        for (int level = 0; level < storages.size(); level++) {
            if (prerelease.hasAnchestor(storages.get(level))) {
                return level;
            }
        }
        throw new IllegalStateException(prerelease.toString());
    }

    public FileNode directory(Project project, int level) {
        return storages.get(level).join(project.groupId, project.artifactId, Descriptor.releaseVersion(project.version));
    }

    public List<FileNode> directories(Project project) {
        List<FileNode> directories;

        directories = new ArrayList<>(storages.size());
        for (int i = 0; i < storages.size(); i++) {
            directories.add(directory(project, i));
        }
        return directories;
    }
}
