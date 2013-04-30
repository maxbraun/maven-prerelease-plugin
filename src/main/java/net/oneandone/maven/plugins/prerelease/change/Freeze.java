package net.oneandone.maven.plugins.prerelease.change;

import com.oneandone.devel.devreg.model.User;
import com.oneandone.devel.maven.Maven;
import net.oneandone.sushi.cli.ArgumentException;
import net.oneandone.sushi.cli.Console;
import net.oneandone.sushi.cli.Remaining;
import net.oneandone.sushi.cli.Value;
import net.oneandone.sushi.util.Separator;
import net.oneandone.sushi.xml.XmlException;
import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

public class Freeze extends Change {
    @Value(name = "end", position = 1)
    private String end;

    private final List<String> message = new ArrayList<>();

    @Remaining
    public void addRemaining(String word) {
        message.add(word);
    }

    public Freeze(Console console, User user, Maven maven, MavenProject project) {
        super(console, user, maven, project);
    }

    private static final SimpleDateFormat FORMAT = new SimpleDateFormat("yyyy-MM-dd");

    @Override
    public String change(File file) throws XmlException, VersionException {
        String updated;
        String freeze;

        if (message.isEmpty()) {
            throw new ArgumentException("missing message");
        }
        console.verbose.println("changes-check version: " + changesCheckVersion(project));
        try {
            FORMAT.parse(end);
        } catch (ParseException e) {
            throw new ArgumentException("invalid end date: " + end + ". Format is " + FORMAT.toPattern(), e);
        }
        try {
            updated = file.getDescription(releaseVersion()) != null ? "UPDATED " : "";
        } catch (ArgumentException e) {
            // "no such version"
            updated = "";
        }
        freeze = "FREEZE " + updated + "until " + end + ": " + Separator.SPACE.join(message) + " (by " + user.getEmail() + ")";
        file.setDescription(releaseVersion(), freeze);
        return freeze;
    }

    private String changesCheckVersion(MavenProject mp) throws VersionException {
        String version;

        version = pluginVersion(mp, "com.oneandone.devel.maven.plugins:changes-check");
        if (version == null) {
            throw new VersionException("changes-check is not configured. You can usually fix this by updating your parent pom.");
        }
        if (version.startsWith("0.")) {
            throw new VersionException("changes-check plugin is too old: " + version
                    + "\nYou can usually fix this by updating your parent pom.");
        }
        return version;
    }

    private static String pluginVersion(MavenProject mp, String pluginKey) {
        Plugin plugin;

        plugin = mp.getPlugin(pluginKey);
        if (plugin == null) {
            plugin = mp.getPluginManagement().getPluginsAsMap().get(pluginKey);
        }
        return plugin == null ? null : plugin.getVersion();
    }

}