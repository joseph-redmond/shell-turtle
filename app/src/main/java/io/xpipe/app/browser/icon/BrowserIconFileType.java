package io.xpipe.app.browser.icon;

import io.github.pixee.security.BoundedLineReader;
import io.xpipe.app.core.AppResources;
import io.xpipe.core.store.FileKind;
import io.xpipe.core.store.FileSystem;
import lombok.Getter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

public interface BrowserIconFileType {

    List<BrowserIconFileType> ALL = new ArrayList<>();

    static BrowserIconFileType byId(String id) {
        return ALL.stream()
                .filter(fileType -> fileType.getId().equals(id))
                .findAny()
                .orElseThrow();
    }

    static void loadDefinitions() {
        AppResources.with(AppResources.XPIPE_MODULE, "file_list.txt", path -> {
            try (var reader =
                    new BufferedReader(new InputStreamReader(Files.newInputStream(path), StandardCharsets.UTF_8))) {
                String line;
                while ((line = BoundedLineReader.readLine(reader, 5_000_000)) != null) {
                    var split = line.split("\\|");
                    var id = split[0].trim();
                    var filter = Arrays.stream(split[1].split(","))
                            .map(s -> {
                                var r = s.trim();
                                if (r.startsWith(".")) {
                                    return r;
                                }

                                if (r.contains(".")) {
                                    return r;
                                }

                                return "." + r;
                            })
                            .collect(Collectors.toSet());
                    var darkIcon = split[2].trim();
                    var lightIcon = split.length > 3 ? split[3].trim() : darkIcon;
                    ALL.add(new BrowserIconFileType.Simple(id, lightIcon, darkIcon, filter));
                }
            }
        });
    }

    String getId();

    boolean matches(FileSystem.FileEntry entry);

    String getIcon();

    @Getter
    class Simple implements BrowserIconFileType {

        private final String id;
        private final IconVariant icon;
        private final Set<String> endings;

        public Simple(String id, String lightIcon, String darkIcon, Set<String> endings) {
            this.icon = new IconVariant(lightIcon, darkIcon);
            this.id = id;
            this.endings = endings;
        }

        @Override
        public boolean matches(FileSystem.FileEntry entry) {
            if (entry.getKind() == FileKind.DIRECTORY) {
                return false;
            }

            return (entry.getExtension() != null
                            && endings.contains("." + entry.getExtension().toLowerCase(Locale.ROOT)))
                    || endings.contains(entry.getName());
        }

        @Override
        public String getIcon() {
            return icon.getIcon();
        }
    }
}
