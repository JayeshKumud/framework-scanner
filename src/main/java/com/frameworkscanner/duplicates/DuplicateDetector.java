package com.frameworkscanner.duplicates;

import com.frameworkscanner.config.ScannerConfig;
import com.frameworkscanner.model.Action;
import com.frameworkscanner.model.ActionType;
import com.frameworkscanner.model.CloneLevel;
import com.frameworkscanner.model.DuplicateGroup;
import com.frameworkscanner.model.MethodInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds methods that duplicate each other, at four levels (see {@link CloneLevel}):
 * <ol>
 *   <li><b>Exact</b>: equal code tokens.</li>
 *   <li><b>Renamed</b>: equal tokens after names and literals are replaced.</li>
 *   <li><b>Near</b>: most 5-token fragments of the normalised code are shared (Dice coefficient).</li>
 *   <li><b>Same job</b>: the same UI/API actions on the same targets in the same order, however the code is
 *       written (longest common subsequence of the action fingerprints).</li>
 * </ol>
 * Candidate pairs come from hash buckets and an inverted index, not from comparing every pair, so large
 * frameworks stay fast. Linked methods are merged into groups, and each group names the method to keep.
 */
public final class DuplicateDetector {

    /** Fragment length for near copies. */
    private static final int SHINGLE = 5;
    /** Fragments shared by more methods than this are too common to find candidates (braces, getters). */
    private static final int MAX_POSTINGS = 300;
    /** Actions that say what a method does; waits and request setup are incidental. */
    private static final Set<ActionType> JOB_ACTIONS = EnumSet.of(ActionType.UI_NAVIGATE, ActionType.UI_CLICK,
            ActionType.UI_INPUT, ActionType.UI_READ, ActionType.API_REQUEST, ActionType.ASSERT, ActionType.CALL);

    private final ScannerConfig.Duplicates config;

    /**
     * Creates a detector.
     *
     * @param config the duplicates section of scanner.yaml
     */
    public DuplicateDetector(ScannerConfig.Duplicates config) {
        this.config = config;
    }

    /**
     * Finds the duplicate groups.
     *
     * @param methods every library method, tokenised and with actions and call counts
     * @return the groups, strongest level and most removable lines first
     */
    public List<DuplicateGroup> detect(List<MethodInfo> methods) {
        Map<Long, Edge> edges = new LinkedHashMap<>();
        List<MethodInfo> code = methods.stream()
                .filter(m -> m.statementCount >= config.minStatements && m.tokenCount >= config.minTokens).toList();

        bucket(code, m -> String.join(" ", m.exactTokens), CloneLevel.EXACT, edges, methods);
        bucket(code, m -> String.join(" ", m.normalizedTokens), CloneLevel.RENAMED, edges, methods);
        nearCopies(code, edges, methods);
        sameJob(methods, edges);

        return groups(methods, edges);
    }

    private record Edge(int a, int b, CloneLevel level, double similarity) {}

    private interface Key {
        String of(MethodInfo m);
    }

    private static long pairKey(int a, int b) {
        return a < b ? ((long) a << 32) | b : ((long) b << 32) | a;
    }

    private static void addEdge(Map<Long, Edge> edges, int a, int b, CloneLevel level, double similarity) {
        if (a == b) return;
        long key = pairKey(a, b);
        Edge old = edges.get(key);
        if (old == null || level.ordinal() < old.level.ordinal()) {
            edges.put(key, new Edge(Math.min(a, b), Math.max(a, b), level, similarity));
        }
    }

    private void bucket(List<MethodInfo> candidates, Key key, CloneLevel level, Map<Long, Edge> edges,
                        List<MethodInfo> all) {
        Map<String, List<Integer>> buckets = new HashMap<>();
        Map<MethodInfo, Integer> index = indexOf(all);
        for (MethodInfo m : candidates) buckets.computeIfAbsent(key.of(m), k -> new ArrayList<>()).add(index.get(m));
        for (List<Integer> same : buckets.values()) {
            for (int i = 0; i < same.size(); i++) {
                for (int j = i + 1; j < same.size(); j++) addEdge(edges, same.get(i), same.get(j), level, 1.0);
            }
        }
    }

    private void nearCopies(List<MethodInfo> candidates, Map<Long, Edge> edges, List<MethodInfo> all) {
        Map<MethodInfo, Integer> index = indexOf(all);
        Map<Integer, Set<Integer>> shingles = new HashMap<>();
        Map<Integer, List<Integer>> postings = new HashMap<>();
        for (MethodInfo m : candidates) {
            int id = index.get(m);
            Set<Integer> set = shingles(m.normalizedTokens);
            shingles.put(id, set);
            for (int s : set) postings.computeIfAbsent(s, k -> new ArrayList<>()).add(id);
        }
        Set<Long> tried = new HashSet<>();
        for (Map.Entry<Integer, Set<Integer>> entry : shingles.entrySet()) {
            int a = entry.getKey();
            Set<Integer> setA = entry.getValue();
            Map<Integer, Integer> shared = new HashMap<>();
            for (int s : setA) {
                List<Integer> list = postings.get(s);
                if (list.size() > MAX_POSTINGS) continue;
                for (int b : list) if (b != a) shared.merge(b, 1, Integer::sum);
            }
            for (Map.Entry<Integer, Integer> c : shared.entrySet()) {
                int b = c.getKey();
                if (!tried.add(pairKey(a, b))) continue;
                Set<Integer> setB = shingles.get(b);
                int small = Math.min(setA.size(), setB.size());
                int big = Math.max(setA.size(), setB.size());
                if (small == 0 || (double) small / big < 0.5) continue; // too different in size
                if (c.getValue() < config.nearMissThreshold * small * 0.5) continue; // cannot reach the threshold
                double dice = dice(setA, setB);
                if (dice >= config.nearMissThreshold) addEdge(edges, a, b, CloneLevel.NEAR, dice);
            }
        }
    }

    private static Set<Integer> shingles(List<String> tokens) {
        Set<Integer> out = new HashSet<>();
        for (int i = 0; i + SHINGLE <= tokens.size(); i++) out.add(String.join(" ", tokens.subList(i, i + SHINGLE)).hashCode());
        return out;
    }

    private static double dice(Set<Integer> a, Set<Integer> b) {
        Set<Integer> small = a.size() <= b.size() ? a : b;
        Set<Integer> big = small == a ? b : a;
        int common = 0;
        for (int x : small) if (big.contains(x)) common++;
        return 2.0 * common / (a.size() + b.size());
    }

    private void sameJob(List<MethodInfo> all, Map<Long, Edge> edges) {
        Map<Integer, List<String>> jobs = new HashMap<>();
        Map<String, List<Integer>> postings = new HashMap<>();
        for (int i = 0; i < all.size(); i++) {
            List<Action> relevant = all.get(i).actions.stream().filter(a -> JOB_ACTIONS.contains(a.type())).toList();
            if (relevant.size() < config.minActions) continue;
            List<String> prints = relevant.stream().map(Action::fingerprint).toList();
            jobs.put(i, prints);
            // index by known targets only, so "click a given element" alone never links two methods
            for (Action a : relevant) {
                if (a.hasKnownTarget()) postings.computeIfAbsent(a.fingerprint(), k -> new ArrayList<>()).add(i);
            }
        }
        Set<Long> tried = new HashSet<>();
        for (Map.Entry<Integer, List<String>> entry : jobs.entrySet()) {
            int a = entry.getKey();
            Set<Integer> candidates = new HashSet<>();
            for (String p : new HashSet<>(entry.getValue())) {
                List<Integer> list = postings.get(p);
                if (list == null || list.size() > MAX_POSTINGS) continue;
                candidates.addAll(list);
            }
            for (int b : candidates) {
                if (b == a || !tried.add(pairKey(a, b))) continue;
                if (edges.containsKey(pairKey(a, b))) continue; // already a code duplicate
                if (isDelegation(all.get(a), all.get(b))) continue;
                List<String> pa = entry.getValue();
                List<String> pb = jobs.get(b);
                double similarity = 2.0 * lcs(pa, pb) / (pa.size() + pb.size());
                if (similarity >= config.sameJobThreshold) addEdge(edges, a, b, CloneLevel.SAME_JOB, similarity);
            }
        }
    }

    /** One method that only calls the other is reuse, not duplication. */
    private static boolean isDelegation(MethodInfo a, MethodInfo b) {
        return a.actions.stream().anyMatch(x -> x.type() == ActionType.CALL && x.target().equals(b.name))
                || b.actions.stream().anyMatch(x -> x.type() == ActionType.CALL && x.target().equals(a.name));
    }

    private static int lcs(List<String> a, List<String> b) {
        int[] prev = new int[b.size() + 1];
        int[] cur = new int[b.size() + 1];
        for (int i = 1; i <= a.size(); i++) {
            for (int j = 1; j <= b.size(); j++) {
                cur[j] = a.get(i - 1).equals(b.get(j - 1)) ? prev[j - 1] + 1 : Math.max(prev[j], cur[j - 1]);
            }
            int[] t = prev;
            prev = cur;
            cur = t;
        }
        return prev[b.size()];
    }

    private List<DuplicateGroup> groups(List<MethodInfo> all, Map<Long, Edge> edges) {
        int[] parent = new int[all.size()];
        for (int i = 0; i < parent.length; i++) parent[i] = i;
        for (Edge e : edges.values()) union(parent, e.a, e.b);

        Map<Integer, List<Integer>> members = new LinkedHashMap<>();
        for (Edge e : edges.values()) {
            members.computeIfAbsent(find(parent, e.a), k -> new ArrayList<>());
        }
        for (int i = 0; i < all.size(); i++) {
            List<Integer> list = members.get(find(parent, i));
            if (list != null) list.add(i);
        }
        List<DuplicateGroup> groups = new ArrayList<>();
        for (Map.Entry<Integer, List<Integer>> entry : members.entrySet()) {
            int rootId = entry.getKey();
            List<Integer> ids = entry.getValue();
            List<Edge> inside = edges.values().stream().filter(e -> find(parent, e.a) == rootId)
                    .sorted(Comparator.comparing((Edge e) -> e.level).thenComparing(e -> -e.similarity)).toList();
            groups.add(group(all, ids, inside));
        }
        groups.sort(Comparator.comparing((DuplicateGroup g) -> g.level).thenComparing(g -> -g.removableLines));
        for (int i = 0; i < groups.size(); i++) groups.get(i).number = i + 1;
        return groups;
    }

    private static DuplicateGroup group(List<MethodInfo> all, List<Integer> ids, List<Edge> inside) {
        DuplicateGroup g = new DuplicateGroup();
        g.level = inside.stream().map(Edge::level).max(Comparator.naturalOrder()).orElse(CloneLevel.EXACT);
        g.levelLabel = g.level.label();
        g.levelMeaning = g.level.meaning();
        List<MethodInfo> methods = ids.stream().map(all::get).toList();
        MethodInfo keep = methods.stream().max(Comparator.comparingDouble(DuplicateDetector::keepScore)).orElseThrow();
        g.keepId = keep.id;
        g.keepReason = keepReason(keep, methods);
        g.memberIds.add(keep.id);
        methods.stream().filter(m -> m != keep).sorted(Comparator.comparing(m -> m.id)).forEach(m -> g.memberIds.add(m.id));
        g.removableLines = methods.stream().filter(m -> m != keep).mapToInt(MethodInfo::lines).sum();
        for (Edge e : inside) {
            g.pairs.add(new DuplicateGroup.Pair(all.get(e.a).id, all.get(e.b).id, e.level,
                    Math.round(e.similarity * 100) / 100.0));
        }
        return g;
    }

    /** Prefer the most used, documented, shared (non-test) method. */
    private static double keepScore(MethodInfo m) {
        double score = m.callCount * 10 + Math.max(0, m.stepUsageCount) * 10;
        if (m.javadoc != null) score += 5;
        if ("public".equals(m.visibility)) score += 3;
        String cls = m.className.toLowerCase();
        if (cls.contains("util") || cls.contains("helper") || cls.contains("common") || cls.contains("base")) score += 2;
        if (m.file.contains("/test/") && !m.file.contains("/main/")) score -= 1;
        score -= m.lines() / 1000.0; // shorter wins a tie
        return score;
    }

    private static String keepReason(MethodInfo keep, List<MethodInfo> methods) {
        List<String> reasons = new ArrayList<>();
        int maxOtherCalls = methods.stream().filter(m -> m != keep).mapToInt(m -> m.callCount).max().orElse(0);
        if (keep.callCount > maxOtherCalls) reasons.add("most calls (" + keep.callCount + ")");
        if (keep.javadoc != null && methods.stream().filter(m -> m != keep).anyMatch(m -> m.javadoc == null)) {
            reasons.add("documented");
        }
        if ("public".equals(keep.visibility)) reasons.add("public");
        String cls = keep.className.toLowerCase();
        if (cls.contains("util") || cls.contains("helper") || cls.contains("common") || cls.contains("base")) {
            reasons.add("in a shared class (" + keep.className + ")");
        }
        if (reasons.isEmpty()) reasons.add("shortest");
        return String.join(", ", reasons);
    }

    private static Map<MethodInfo, Integer> indexOf(List<MethodInfo> all) {
        Map<MethodInfo, Integer> index = new HashMap<>();
        for (int i = 0; i < all.size(); i++) index.put(all.get(i), i);
        return index;
    }

    private static int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    private static void union(int[] parent, int a, int b) {
        int ra = find(parent, a);
        int rb = find(parent, b);
        if (ra != rb) parent[rb] = ra;
    }
}
