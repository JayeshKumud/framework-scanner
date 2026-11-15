package com.frameworkscanner.duplicates;

import com.frameworkscanner.model.MethodInfo;
import com.github.javaparser.JavaToken;
import com.github.javaparser.TokenRange;
import com.github.javaparser.ast.stmt.BlockStmt;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns a method body into tokens for comparison.
 * <ul>
 *   <li><b>exact</b>: every code token, without whitespace and comments. Equal lists mean an exact copy.</li>
 *   <li><b>normalised</b>: names of variables, fields, parameters and types become {@code ID}, literal values
 *       become {@code LIT}. Method names that are called stay, because they say what the code does
 *       ({@code click} is not {@code fill}). Equal lists mean a renamed copy.</li>
 * </ul>
 */
public final class TokenNormalizer {

    private TokenNormalizer() {}

    /**
     * Sets {@link MethodInfo#exactTokens}, {@link MethodInfo#normalizedTokens} and {@link MethodInfo#tokenCount}.
     *
     * @param m a parsed method
     */
    public static void tokenize(MethodInfo m) {
        if (m.node == null || m.node.getBody().isEmpty()) return;
        BlockStmt body = m.node.getBody().get();
        List<JavaToken> code = codeTokens(body);
        List<String> exact = new ArrayList<>(code.size());
        List<String> normalized = new ArrayList<>(code.size());
        for (int i = 0; i < code.size(); i++) {
            JavaToken t = code.get(i);
            exact.add(t.getText());
            JavaToken.Category c = t.getCategory();
            if (c == JavaToken.Category.LITERAL) {
                normalized.add("LIT");
            } else if (c == JavaToken.Category.IDENTIFIER) {
                boolean called = i + 1 < code.size() && code.get(i + 1).getText().equals("(")
                        && (i == 0 || !code.get(i - 1).getText().equals("new"));
                normalized.add(called ? t.getText() : "ID");
            } else {
                normalized.add(t.getText());
            }
        }
        m.exactTokens = exact;
        m.normalizedTokens = normalized;
        m.tokenCount = code.size();
    }

    private static List<JavaToken> codeTokens(BlockStmt body) {
        List<JavaToken> out = new ArrayList<>();
        TokenRange range = body.getTokenRange().orElse(null);
        if (range == null) return out;
        for (JavaToken t : range) {
            if (t.getCategory().isWhitespaceOrComment()) continue;
            out.add(t);
        }
        // drop the outer braces of the body
        if (out.size() >= 2) return out.subList(1, out.size() - 1);
        return out;
    }
}
