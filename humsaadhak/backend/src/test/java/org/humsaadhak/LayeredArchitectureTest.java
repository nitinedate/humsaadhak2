package org.humsaadhak;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Keeps the backend's structure: every module is split into the same layers, and dependencies only point downwards.
 *
 *   controller  ->  service  ->  repository (Spring Data JPA) / dao (SQL with JdbcClient)  ->  entity
 *   dto, model and util are shared by all layers of a module; api is the only part other modules may use.
 *
 * Reads the source files, so it runs without starting Spring.
 */
class LayeredArchitectureTest {
    static final Set<String> LAYERS = Set.of("api", "controller", "service", "repository", "dao", "dto", "entity", "model", "util", "client",
            "config", "exception", "security");
    record Source(String module, String layer, String name, String code, Set<String> imports) {}

    static List<Source> sources() throws Exception {
        Path root = Path.of("src/main/java/org/humsaadhak");
        List<Source> out = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                Path rel = root.relativize(p);
                if (rel.getNameCount() < 3) continue;                      // HumSaadhakApplication, module package-info
                String code = Files.readString(p);
                Set<String> imports = new TreeSet<>();
                Matcher m = Pattern.compile("^import (?:static )?([\\w.]+?)(?:\\.\\*)?;", Pattern.MULTILINE).matcher(code);
                while (m.find()) imports.add(m.group(1));
                out.add(new Source(rel.getName(0).toString(), rel.getName(1).toString(), rel.getFileName().toString(), code, imports));
            }
        }
        return out;
    }

    static List<String> violations(List<Source> sources, java.util.function.Predicate<Source> who, java.util.function.BiPredicate<Source, String> forbidden) {
        List<String> out = new ArrayList<>();
        for (Source s : sources) if (who.test(s)) for (String i : s.imports()) if (forbidden.test(s, i)) out.add(s.module() + "/" + s.layer() + "/" + s.name() + " -> " + i);
        return out;
    }

    static boolean layerOf(String imp, String... layers) {
        String[] p = imp.split("\\.");
        return p.length > 3 && p[0].equals("org") && p[1].equals("humsaadhak") && Arrays.asList(layers).contains(p[3]);
    }

    @Test
    void everyClassLivesInALayerPackage() throws Exception {
        assertThat(sources().stream().filter(s -> !s.name().equals("package-info.java") && !LAYERS.contains(s.layer()))
                .map(s -> s.module() + "/" + s.layer() + "/" + s.name()).toList()).isEmpty();
    }

    @Test
    void controllersOnlyTalkToServices() throws Exception {
        var src = sources();
        assertThat(violations(src, s -> s.layer().equals("controller"),
                (s, i) -> layerOf(i, "repository", "dao", "entity") || i.equals("org.springframework.jdbc.core.simple.JdbcClient"))).isEmpty();
    }

    @Test
    void sqlLivesOnlyInDaos() throws Exception {
        assertThat(sources().stream().filter(s -> !s.layer().equals("dao") && s.code().contains("JdbcClient"))
                .map(s -> s.module() + "/" + s.layer() + "/" + s.name()).toList()).isEmpty();
    }

    @Test
    void dependenciesPointDownwards() throws Exception {
        var src = sources();
        assertThat(violations(src, s -> s.layer().equals("service"), (s, i) -> layerOf(i, "controller"))).isEmpty();
        assertThat(violations(src, s -> Set.of("repository", "dao").contains(s.layer()), (s, i) -> layerOf(i, "controller", "service"))).isEmpty();
        assertThat(violations(src, s -> s.layer().equals("entity"), (s, i) -> layerOf(i, "controller", "service", "repository", "dao", "dto"))).isEmpty();
        assertThat(violations(src, s -> Set.of("dto", "model", "util").contains(s.layer()), (s, i) -> layerOf(i, "controller", "service", "repository"))).isEmpty();
    }

    @Test
    void modulesOnlyUseEachOthersApi() throws Exception {
        var src = sources();
        assertThat(violations(src, s -> true, (s, i) -> {
            String[] p = i.split("\\.");
            if (p.length < 4 || !p[0].equals("org") || !p[1].equals("humsaadhak")) return false;
            String other = p[2];
            return !other.equals(s.module()) && !other.equals("shared") && !p[3].equals("api");
        })).isEmpty();
    }

    /** Every action that changes something, and every download or export, is recorded in the audit trail. */
    @Test
    void everyStateChangingEndpointIsAudited() throws Exception {
        List<String> unaudited = new ArrayList<>();
        Pattern mapping = Pattern.compile("@(Post|Put|Patch|Delete|Get)Mapping(\\([^)]*\\))?");
        for (Source s : sources()) {
            if (!s.layer().equals("controller")) continue;
            Matcher m = mapping.matcher(s.code());
            while (m.find()) {
                int start = Math.max(Math.max(s.code().lastIndexOf(';', m.start()), s.code().lastIndexOf('}', m.start())), s.code().lastIndexOf('{', m.start()));
                int end = s.code().indexOf('{', m.end());                     // the method body: annotations and signature come before it
                String cluster = s.code().substring(start + 1, end < 0 ? m.end() : end);
                boolean sensitiveRead = m.group(1).equals("Get") && cluster.matches("(?s).*(download|\\.csv|templates).*");
                if ((!m.group(1).equals("Get") || sensitiveRead) && !cluster.contains("@AuditAction("))
                    unaudited.add(s.module() + "/" + s.name() + ": " + cluster.strip().replaceAll("\\s+", " "));
            }
        }
        assertThat(unaudited).isEmpty();
    }
}
