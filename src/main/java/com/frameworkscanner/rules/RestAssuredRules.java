package com.frameworkscanner.rules;

import com.frameworkscanner.model.Action;
import com.frameworkscanner.model.ActionType;
import com.frameworkscanner.model.MethodInfo;
import com.frameworkscanner.model.RuleInfo;
import com.frameworkscanner.model.Severity;

import java.util.List;
import java.util.Set;

/** Request setup and response checks with REST Assured. */
final class RestAssuredRules implements RuleSet {

    static final String REPEATED_SETUP = "REST_REPEATED_REQUEST_SETUP";
    static final String UNCHECKED_RESPONSE = "REST_RESPONSE_NOT_CHECKED";

    private static final Set<String> SETUP = Set.of("baseUri", "basePath", "port", "contentType", "accept", "header",
            "headers", "auth", "basic", "oauth2", "relaxedHTTPSValidation");

    @Override
    public List<RuleInfo> rules() {
        return List.of(
                new RuleInfo(REPEATED_SETUP, "Shared request setup with RequestSpecBuilder", "REST Assured", Severity.MEDIUM,
                        "Base URI, content type, auth and common headers written in every request method are "
                                + "copies: a new header must be added everywhere. Build one RequestSpecification "
                                + "and reuse it with spec(...).",
                        "given().baseUri(\"https://api.x\").contentType(ContentType.JSON).header(\"X-Key\", key)\n    .get(\"/users/1\");\ngiven().baseUri(\"https://api.x\").contentType(ContentType.JSON).header(\"X-Key\", key)\n    .get(\"/orders/7\");",
                        "RequestSpecification api = new RequestSpecBuilder()\n    .setBaseUri(config.apiBaseUrl()).setContentType(ContentType.JSON)\n    .addHeader(\"X-Key\", key).build();\n\ngiven().spec(api).get(\"/users/1\");\ngiven().spec(api).get(\"/orders/7\");"),
                new RuleInfo(UNCHECKED_RESPONSE, "Check the response of a request", "REST Assured", Severity.MEDIUM,
                        "A method that sends a request, does not check it and does not return it hides errors: a "
                                + "500 or 401 goes unnoticed. Check the status (or return the response so the caller "
                                + "checks it).",
                        "public void deleteUser(String id) {\n    given().spec(api).delete(\"/users/\" + id);\n}",
                        "public void deleteUser(String id) {\n    given().spec(api).delete(\"/users/\" + id)\n        .then().statusCode(204);\n}"));
    }

    @Override
    public void check(RuleContext ctx) {
        List<MethodInfo> withSetup = ctx.methods().stream()
                .filter(m -> m.actions.stream().anyMatch(a -> a.type() == ActionType.API_REQUEST))
                .filter(m -> m.actions.stream().filter(a -> a.type() == ActionType.API_SETUP && SETUP.contains(a.verb()))
                        .map(Action::verb).distinct().count() >= 2)
                .toList();
        if (withSetup.size() >= 2) {
            for (MethodInfo m : withSetup) {
                ctx.report(REPEATED_SETUP, m, null, m.name + " builds its own request setup ("
                        + String.join(", ", m.actions.stream().filter(a -> a.type() == ActionType.API_SETUP
                        && SETUP.contains(a.verb())).map(Action::verb).distinct().toList())
                        + "), like " + (withSetup.size() - 1) + " other method(s). Use a shared RequestSpecification.");
            }
        }
        for (MethodInfo m : ctx.methods()) {
            boolean request = m.actions.stream().anyMatch(a -> a.type() == ActionType.API_REQUEST);
            boolean checked = m.actions.stream().anyMatch(a -> a.type() == ActionType.ASSERT);
            if (request && !checked && "void".equals(m.returnType)) {
                ctx.report(UNCHECKED_RESPONSE, m, null, m.name + " sends a request but never checks or returns the response.");
            }
        }
    }
}
