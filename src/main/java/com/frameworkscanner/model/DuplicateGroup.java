package com.frameworkscanner.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Methods that duplicate each other, with the one to keep.
 */
public class DuplicateGroup {

    /** 1-based number shown in the report. */
    public int number;
    /** The weakest level linking the members: EXACT if all are exact copies of each other. */
    public CloneLevel level;
    public String levelLabel;
    public String levelMeaning;
    /** Ids of the members (see {@link MethodInfo#id}), the method to keep first. */
    public List<String> memberIds = new ArrayList<>();
    /** Id of the method to keep. */
    public String keepId;
    /** Why that one. */
    public String keepReason;
    /** Lines that go away when the others are replaced by the kept method. */
    public int removableLines;
    /** The compared pairs, for the side-by-side view. */
    public List<Pair> pairs = new ArrayList<>();

    /**
     * Two members and how similar they are.
     *
     * @param a          id of the first method
     * @param b          id of the second method
     * @param level      the strongest level that matched
     * @param similarity 0..1 (1 for exact and renamed copies)
     */
    public record Pair(String a, String b, CloneLevel level, double similarity) {}
}
