package net.oneandone.maven.plugins.prerelease.change;

import com.oneandone.devel.devreg.model.User;
import com.oneandone.devel.maven.Maven;
import net.oneandone.sushi.cli.ArgumentException;
import net.oneandone.sushi.cli.Console;
import net.oneandone.sushi.cli.Option;
import net.oneandone.sushi.cli.Remaining;
import net.oneandone.sushi.util.Separator;
import net.oneandone.sushi.xml.XmlException;
import org.apache.maven.project.MavenProject;

import java.util.ArrayList;
import java.util.List;

/**
 * Adds an action to a changes file.
 */
public class Add extends Change {
    @Option("r")
    private boolean releaseDate;

    private final List<String> typeAndMessageWords = new ArrayList<>();

    @Remaining
    public void typeAndMessage(String word) {
        typeAndMessageWords.add(word);
    }

    public Add(Console console, User user, Maven maven, MavenProject project) {
        super(console, user, maven, project);
    }

    public String change(File file) throws XmlException {
        String typeAndMessage;
        String[] splitted;

        typeAndMessage = Separator.SPACE.join(typeAndMessageWords);
        console.verbose.println("type and message: '" + typeAndMessage + "'");
        if (!typeAndMessage.isEmpty()) {
            splitted = split(typeAndMessage);
            if (splitted == null) {
                throw new ArgumentException("cannot determin type for '" + typeAndMessage + "'");
            }
            file.addAction(releaseVersion(), now, splitted[0], splitted[1], user.getLogin());
        }
        if (releaseDate) {
            if (!file.releaseDate(releaseVersion(), now)) {
                throw new ArgumentException("release not found: " + releaseVersion());
            }
            if (typeAndMessage.isEmpty()) {
                typeAndMessage = "adjusted release date";
            }
        }
        return typeAndMessage;
    }

    public static String[] split(String typeAndMessage) {
        int idx;
        String type;

        idx = typeAndMessage.indexOf(':');
        if (idx != -1 && !typeAndMessage.substring(0, idx).contains(" ")) {
            return new String[] {typeAndMessage.substring(0, idx), typeAndMessage.substring(idx + 1).trim()};
        }
        type = guessType(typeAndMessage);
        if (type != null) {
            return new String[] {type, typeAndMessage};
        }
        return null;
    }

    public static String guessType(String typeAndMessage) {
        typeAndMessage = typeAndMessage.toLowerCase();
        for (String type : new String[] {"add", "remove", "fix", "update"}) {
            if (typeAndMessage.startsWith(type)) {
                return type;
            }
        }
        return null;
    }
}
