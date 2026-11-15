package com.frameworkscanner.analysis;

import com.frameworkscanner.model.Action;
import com.frameworkscanner.model.ActionType;
import com.frameworkscanner.model.ClassInfo;
import com.frameworkscanner.model.MethodInfo;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Writes what a method does in one sentence, without AI: from its Javadoc if it has one, otherwise from its
 * Cucumber step text and its actions ("Fills #username, #password, then clicks #login"), and as a last resort
 * from its name ("getUserName" becomes "Gets user name"). Also sorts methods and classes into purposes and roles,
 * so the catalog can be filtered by what things are for.
 */
public final class Describer {

    /** Most phrases in a generated description. */
    private static final int MAX_PHRASES = 7;

    private static final Map<String, String> VERB_WORDS = Map.ofEntries(
            Map.entry("get", "Gets"), Map.entry("set", "Sets"), Map.entry("is", "Checks whether it is"),
            Map.entry("has", "Checks whether it has"), Map.entry("can", "Checks whether it can"),
            Map.entry("should", "Checks that it should"), Map.entry("create", "Creates"), Map.entry("add", "Adds"),
            Map.entry("remove", "Removes"), Map.entry("delete", "Deletes"), Map.entry("update", "Updates"),
            Map.entry("generate", "Generates"), Map.entry("verify", "Verifies"), Map.entry("validate", "Validates"),
            Map.entry("check", "Checks"), Map.entry("assert", "Asserts"), Map.entry("click", "Clicks"),
            Map.entry("tap", "Taps"), Map.entry("enter", "Enters"), Map.entry("type", "Types"),
            Map.entry("input", "Enters"), Map.entry("fill", "Fills"), Map.entry("select", "Selects"),
            Map.entry("choose", "Chooses"), Map.entry("wait", "Waits for"), Map.entry("open", "Opens"),
            Map.entry("navigate", "Navigates to"), Map.entry("goto", "Goes to"), Map.entry("go", "Goes to"),
            Map.entry("read", "Reads"), Map.entry("fetch", "Fetches"), Map.entry("load", "Loads"),
            Map.entry("send", "Sends"), Map.entry("post", "Posts"), Map.entry("put", "Puts"), Map.entry("call", "Calls"),
            Map.entry("convert", "Converts"), Map.entry("parse", "Parses"), Map.entry("format", "Formats"),
            Map.entry("build", "Builds"), Map.entry("init", "Initialises"), Map.entry("initialize", "Initialises"),
            Map.entry("setup", "Sets up"), Map.entry("teardown", "Tears down"), Map.entry("close", "Closes"),
            Map.entry("login", "Logs in"), Map.entry("logout", "Logs out"), Map.entry("signin", "Signs in"),
            Map.entry("search", "Searches"), Map.entry("find", "Finds"), Map.entry("save", "Saves"),
            Map.entry("upload", "Uploads"), Map.entry("download", "Downloads"), Map.entry("scroll", "Scrolls"),
            Map.entry("hover", "Hovers over"), Map.entry("switch", "Switches"), Map.entry("accept", "Accepts"),
            Map.entry("dismiss", "Dismisses"), Map.entry("compare", "Compares"), Map.entry("calculate", "Calculates"),
            Map.entry("extract", "Extracts"), Map.entry("store", "Stores"), Map.entry("clear", "Clears"),
            Map.entry("do", "Does"), Map.entry("perform", "Performs"), Map.entry("run", "Runs"),
            Map.entry("execute", "Executes"), Map.entry("start", "Starts"), Map.entry("stop", "Stops"),
            Map.entry("take", "Takes"), Map.entry("capture", "Captures"), Map.entry("print", "Prints"),
            Map.entry("log", "Logs"), Map.entry("to", "Converts to"), Map.entry("apply", "Applies"),
            Map.entry("launch", "Launches"), Map.entry("join", "Joins"), Map.entry("trim", "Trims"),
            Map.entry("split", "Splits"), Map.entry("sort", "Sorts"), Map.entry("count", "Counts"),
            Map.entry("submit", "Submits"), Map.entry("refresh", "Refreshes"), Map.entry("reset", "Resets"),
            Map.entry("random", "Generates a random"));

    /** Verbs a method name can start with (for the naming rule). */
    public static final Set<String> KNOWN_VERBS = Set.copyOf(VERB_WORDS.keySet());

    private Describer() {}

    /**
     * Sets {@link MethodInfo#description}, {@link MethodInfo#descriptionSource} and {@link MethodInfo#purpose}.
     *
     * @param m a method with its actions
     */
    public static void describe(MethodInfo m) {
        String fromCode = fromActions(m.actions);
        if (m.javadoc != null) {
            m.description = m.javadoc;
            m.descriptionSource = "JAVADOC";
        } else if (m.isStep()) {
            m.description = "Step \"" + m.stepKeyword + " " + m.stepText + "\""
                    + (fromCode.isEmpty() ? "." : ": " + lowerFirst(fromCode));
            m.descriptionSource = "STEP";
        } else if (!fromCode.isEmpty()) {
            m.description = fromCode + returns(m);
            m.descriptionSource = "CODE";
        } else {
            m.description = fromName(m) + "." + returns(m);
            m.descriptionSource = "NAME";
        }
        m.purpose = purpose(m);
    }

    /**
     * A sentence built from actions.
     *
     * @param actions the actions in order
     * @return the sentence ending with a dot, or "" if there are no actions
     */
    static String fromActions(List<Action> actions) {
        List<String> phrases = new ArrayList<>();
        String lastVerbPhrase = null;
        List<String> targets = new ArrayList<>();
        for (Action a : actions) {
            if (a.type() == ActionType.API_SETUP && !a.verb().equals("baseUri") && !a.verb().equals("basePath")) {
                continue; // headers and bodies are noise in a one-line description
            }
            String verbPhrase = verbPhrase(a);
            String target = targetPhrase(a);
            if (verbPhrase.equals(lastVerbPhrase) && target != null) {
                if (!targets.contains(target)) targets.add(target);
                continue;
            }
            flush(phrases, lastVerbPhrase, targets);
            lastVerbPhrase = verbPhrase;
            targets = new ArrayList<>();
            if (target != null) targets.add(target);
        }
        flush(phrases, lastVerbPhrase, targets);
        if (phrases.isEmpty()) return "";
        int more = phrases.size() - MAX_PHRASES;
        List<String> shown = more > 0 ? phrases.subList(0, MAX_PHRASES) : phrases;
        String text = shown.size() == 1 ? shown.get(0)
                : String.join(", ", shown.subList(0, shown.size() - 1)) + ", then " + shown.get(shown.size() - 1);
        if (more > 0) text += " (and " + more + " more step" + (more == 1 ? "" : "s") + ")";
        return upperFirst(text) + ".";
    }

    private static void flush(List<String> phrases, String verb, List<String> targets) {
        if (verb == null) return;
        if (verb.contains("%s")) {
            phrases.add(verb.replace("%s", targets.isEmpty() ? "a given element" : String.join(", ", targets)));
        } else {
            phrases.add(targets.isEmpty() ? verb : verb + " " + String.join(", ", targets));
        }
    }

    private static String verbPhrase(Action a) {
        String v = a.verb();
        return switch (a.type()) {
            case UI_NAVIGATE -> switch (v) {
                case "navigate" -> "opens";
                case "goBack" -> "goes back";
                case "goForward" -> "goes forward";
                default -> "reloads the page";
            };
            case UI_CLICK -> switch (v) {
                case "dblclick" -> "double-clicks";
                case "check", "setChecked" -> "checks";
                case "uncheck" -> "unchecks";
                case "hover" -> "hovers over";
                case "press" -> "presses a key on";
                case "focus" -> "focuses";
                case "dragTo" -> "drags";
                default -> "clicks";
            };
            case UI_INPUT -> switch (v) {
                case "selectOption" -> "selects an option in";
                case "setInputFiles" -> "uploads a file to";
                case "clear" -> "clears";
                case "type", "pressSequentially" -> "types into";
                default -> "fills";
            };
            case UI_READ -> switch (v) {
                case "inputValue" -> "reads the value of";
                case "getAttribute" -> "reads an attribute of";
                case "isVisible", "isHidden" -> "checks whether %s is visible";
                case "isEnabled", "isDisabled" -> "checks whether %s is enabled";
                case "isChecked" -> "checks whether %s is checked";
                case "count" -> "counts";
                case "title" -> "reads the page title";
                case "url" -> "reads the page URL";
                default -> "reads the text of";
            };
            case UI_WAIT -> switch (v) {
                case "waitForLoadState" -> "waits for the page to load";
                case "waitForURL" -> "waits for the URL";
                case "waitForResponse" -> "waits for the response";
                case "waitForNavigation" -> "waits for navigation";
                case "waitForPopup" -> "waits for a popup";
                default -> "waits for";
            };
            case HARD_WAIT -> "waits a fixed time";
            case API_REQUEST -> "sends " + v;
            case API_SETUP -> "uses base URI";
            case ASSERT -> assertPhrase(v);
            case CALL -> "calls";
        };
    }

    private static String assertPhrase(String v) {
        return switch (v) {
            case "statusCode" -> "asserts status code";
            case "isVisible" -> "asserts %s is visible";
            case "isHidden", "not().isVisible" -> "asserts %s is hidden";
            case "hasText", "containsText" -> "asserts the text of";
            case "hasValue" -> "asserts the value of";
            case "hasURL" -> "asserts the page URL";
            case "hasTitle" -> "asserts the page title";
            case "hasCount" -> "asserts the number of";
            case "isEnabled" -> "asserts %s is enabled";
            case "isChecked" -> "asserts %s is checked";
            case "body" -> "asserts the response body field";
            case "contentType" -> "asserts the content type";
            default -> "asserts (" + v + ")";
        };
    }

    private static String targetPhrase(Action a) {
        if (a.type() == ActionType.HARD_WAIT) return a.hasKnownTarget() ? "(" + a.target() + " ms)" : null;
        if (a.type() == ActionType.UI_NAVIGATE && !a.verb().equals("navigate")) return null;
        if ("page".equals(a.target())) return null;
        if (a.type() == ActionType.ASSERT && !a.hasKnownTarget()) return null;
        if (a.type() == ActionType.UI_WAIT && !a.verb().equals("waitFor") && !a.verb().equals("waitForSelector")
                && !a.verb().equals("waitForURL")) {
            return null;
        }
        if (!a.hasKnownTarget()) {
            return switch (a.type()) {
                case UI_NAVIGATE -> "a given URL";
                case API_REQUEST -> "to a given endpoint";
                case UI_WAIT -> "an element";
                default -> "a given element";
            };
        }
        String t = a.target().length() > 60 ? a.target().substring(0, 57) + "..." : a.target();
        return a.type() == ActionType.CALL ? t + "()" : t;
    }

    /**
     * A sentence built from a method name, for methods without actions.
     *
     * @param m the method
     * @return for example "Gets user name"
     */
    static String fromName(MethodInfo m) {
        List<String> words = splitWords(m.name);
        if (words.isEmpty()) return "Does " + m.name;
        String first = words.get(0).toLowerCase(Locale.ROOT);
        String rest = String.join(" ", words.subList(1, words.size())).toLowerCase(Locale.ROOT);
        String verb = VERB_WORDS.get(first);
        String text = verb != null ? verb + (rest.isEmpty() ? "" : " " + rest)
                : "Handles " + String.join(" ", words).toLowerCase(Locale.ROOT);
        if (!m.parameterNames.isEmpty()) {
            text += " (using " + String.join(", ", m.parameterNames.stream().map(Describer::humanise).toList()) + ")";
        }
        return text;
    }

    private static String returns(MethodInfo m) {
        if (m.returnType == null || m.returnType.equals("void")) return "";
        String r = m.returnType;
        if (r.equals(m.className)) return " Returns this page/object for chaining.";
        return " Returns " + r + ".";
    }

    /**
     * Splits camelCase, PascalCase, snake_case and digits into words.
     *
     * @param name an identifier
     * @return its words, for example [get, User, Name] or [URL, Builder]
     */
    public static List<String> splitWords(String name) {
        List<String> words = new ArrayList<>();
        for (String part : name.split("[_$\\s]+")) {
            if (part.isEmpty()) continue;
            for (String w : part.split("(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])|(?<=[A-Za-z])(?=[0-9])")) {
                if (!w.isEmpty()) words.add(w);
            }
        }
        return words;
    }

    private static String humanise(String identifier) {
        return String.join(" ", splitWords(identifier)).toLowerCase(Locale.ROOT);
    }

    /**
     * Sorts a method into a purpose for the catalog.
     *
     * @param m the method with its actions
     * @return for example "UI action", "API request", "Assertion / verification"
     */
    static String purpose(MethodInfo m) {
        if (m.isStep()) return "Step definition";
        if (m.annotations.contains("Test")) return "Test";
        if (m.annotations.stream().anyMatch(a -> Set.of("Before", "After", "BeforeAll", "AfterAll", "BeforeStep",
                "AfterStep", "BeforeMethod", "AfterMethod", "BeforeClass", "AfterClass", "BeforeSuite", "AfterSuite",
                "BeforeEach", "AfterEach").contains(a))) {
            return "Hook / setup";
        }
        Map<ActionType, Integer> counts = new EnumMap<>(ActionType.class);
        m.actions.forEach(a -> counts.merge(a.type(), 1, Integer::sum));
        String lower = m.name.toLowerCase(Locale.ROOT);
        boolean verifyName = lower.startsWith("verify") || lower.startsWith("assert") || lower.startsWith("validate")
                || lower.startsWith("check") || lower.startsWith("should") || lower.startsWith("expect");
        if (counts.containsKey(ActionType.ASSERT) && (verifyName || counts.size() == 1)) return "Assertion / verification";
        if (counts.containsKey(ActionType.API_REQUEST)) return "API request";
        if (counts.containsKey(ActionType.UI_NAVIGATE) || counts.containsKey(ActionType.UI_CLICK)
                || counts.containsKey(ActionType.UI_INPUT)) {
            return "UI action";
        }
        if (counts.containsKey(ActionType.UI_READ)) return "UI read";
        if (counts.containsKey(ActionType.UI_WAIT) || counts.containsKey(ActionType.HARD_WAIT)) return "Wait / synchronisation";
        if (verifyName) return "Assertion / verification";
        if (lower.matches(".*(random|generate|fake|faker|testdata|unique).*")) return "Test data";
        if (lower.matches(".*(config|propert|env|setting).*")) return "Configuration";
        if (lower.matches(".*(file|json|excel|xls|csv|yaml|xml|read|write).*")) return "File / data access";
        if (counts.containsKey(ActionType.CALL)) return "Workflow (calls other methods)";
        if (m.name.startsWith("get") || m.name.startsWith("is") || m.name.startsWith("set")) return "Accessor";
        return "Utility";
    }

    /**
     * Sorts a class into a role for the catalog.
     *
     * @param c the class with its described methods
     * @return for example "Page object", "Step definitions", "API client"
     */
    public static String role(ClassInfo c) {
        String n = c.name;
        if (c.kind.equals("enum")) return "Enum";
        if (c.methods.stream().anyMatch(MethodInfo::isStep)) return "Step definitions";
        if (c.methods.stream().anyMatch(m -> m.annotations.contains("Test")) || n.endsWith("Test") || n.endsWith("Tests")) {
            return "Test class";
        }
        if (n.matches(".*(Hooks?)$")) return "Hooks";
        if (n.endsWith("Runner")) return "Test runner";
        boolean ui = c.methods.stream().flatMap(m -> m.actions.stream())
                .anyMatch(a -> a.type().name().startsWith("UI_")) || !c.locatorFields.isEmpty();
        boolean api = c.methods.stream().flatMap(m -> m.actions.stream()).anyMatch(a -> a.type() == ActionType.API_REQUEST);
        if (n.matches(".*(Page|Screen|Component|Section|Dialog|Modal|Widget)$") || ui && !n.matches(".*(Util|Utils|Helper|Helpers)$")) {
            return "Page object";
        }
        if (api || n.matches(".*(Api|API|Client|Service|Endpoint|Endpoints|Resource)$")) return "API client";
        if (n.matches(".*(Util|Utils|Utility|Utilities|Helper|Helpers|Tools?)$")) return "Utility / helper";
        if (n.matches(".*(Config|Configuration|Properties|Constants|Settings|Env|Environment)$")) {
            return "Configuration / constants";
        }
        if (n.matches(".*(Data|Factory|Builder|Generator|Provider)$")) return "Test data";
        if (c.kind.equals("interface")) return "Interface";
        if (c.kind.equals("record") || n.matches(".*(Request|Response|Dto|DTO|Model|Pojo|POJO)$")) return "Model / POJO";
        return "Other";
    }

    private static String upperFirst(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String lowerFirst(String s) {
        return s.isEmpty() ? s : Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }
}
