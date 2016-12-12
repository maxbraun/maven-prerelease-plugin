package net.oneandone.maven.plugins.prerelease.util;

import net.oneandone.sushi.fs.file.FileNode;

public class ScmTag {
    private final FileNode base;
    private final FileNode checkout;
    private final String tagname;

    public ScmTag(FileNode base, FileNode checkout, String tagname) {
        this.base = base;
        this.checkout = checkout;
        this.tagname = tagname;
    }

    public FileNode getBase() {
        return base;
    }

    public FileNode getCheckout() {
        return checkout;
    }

    public String getTagname() {
        return tagname;
    }
}
