package com.frameworkscanner.git;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Finds who wrote which line, with {@code git blame}. Each file is blamed once and cached. If git is missing or
 * the project is not a repository, every lookup returns nothing and the report simply has no authors.
 */
public final class GitBlame {

    private final Path root;
    private final boolean available;
    private final Map<String, String[]> cache = new ConcurrentHashMap<>();

    /**
     * Prepares blame for a project.
     *
     * @param root    the project root
     * @param enabled git.blame from scanner.yaml
     */
    public GitBlame(Path root, boolean enabled) {
        this.root = root;
        this.available = enabled && run(List.of("git", "-C", root.toString(), "rev-parse", "--is-inside-work-tree"))
                .stream().anyMatch(l -> l.trim().equals("true"));
    }

    /** @return true if authors can be looked up */
    public boolean available() {
        return available;
    }

    /**
     * The authors of a line range, most lines first.
     *
     * @param file  path relative to the project root
     * @param begin first line
     * @param end   last line
     * @return the authors, empty if unknown
     */
    public List<String> authors(String file, int begin, int end) {
        if (!available) return List.of();
        String[] byLine = cache.computeIfAbsent(file, this::blame);
        Map<String, Integer> counts = new HashMap<>();
        for (int line = begin; line <= end && line < byLine.length; line++) {
            if (byLine[line] != null) counts.merge(byLine[line], 1, Integer::sum);
        }
        return counts.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .map(Map.Entry::getKey).toList();
    }

    /**
     * The author of one line.
     *
     * @param file path relative to the project root
     * @param line the line
     * @return the author, or null
     */
    public String author(String file, int line) {
        List<String> a = authors(file, line, line);
        return a.isEmpty() ? null : a.get(0);
    }

    private String[] blame(String file) {
        List<String> out = run(List.of("git", "-C", root.toString(), "blame", "--line-porcelain", "--", file));
        Map<Integer, String> authors = new LinkedHashMap<>();
        int max = 0;
        int current = -1;
        for (String line : out) {
            if (line.matches("^[0-9a-f]{40} \\d+ \\d+.*")) {
                current = Integer.parseInt(line.split(" ")[2]);
                max = Math.max(max, current);
            } else if (line.startsWith("author ") && current > 0) {
                String name = line.substring(7).trim();
                authors.put(current, name.equals("Not Committed Yet") ? "(not committed)" : name);
            }
        }
        String[] byLine = new String[max + 1];
        authors.forEach((l, a) -> byLine[l] = a);
        return byLine;
    }

    private static List<String> run(List<String> command) {
        List<String> lines = new ArrayList<>();
        try {
            Process p = new ProcessBuilder(command).redirectErrorStream(false).start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) lines.add(line);
            }
            if (!p.waitFor(60, TimeUnit.SECONDS)) p.destroyForcibly();
        } catch (IOException e) {
            return List.of();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return List.of();
        }
        return lines;
    }
}
