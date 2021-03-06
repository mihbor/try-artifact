package net.unit8.erebus.tryartifact.tool;

/**
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 * <p>
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 * <p>
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 * <p>
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 * <p>
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

import jdk.jshell.*;
import jdk.jshell.JShell.Subscription;
import jdk.jshell.Snippet.Status;
import jdk.jshell.Snippet.SubKind;
import jdk.jshell.SourceCodeAnalysis.CompletionInfo;
import jdk.jshell.SourceCodeAnalysis.Suggestion;
import net.unit8.erebus.tryartifact.debug.InternalDebugControl;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static net.unit8.erebus.tryartifact.tool.Feedback.*;
import static java.nio.file.StandardOpenOption.*;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static jdk.jshell.Snippet.Kind.METHOD;
import static jdk.jshell.Snippet.SubKind.VAR_VALUE_SUBKIND;

/**
 * Command line REPL tool for Java using the JShell API.

 * @author Robert Field
 * @author kawasima

 */
public class TryJShellTool {

    private static final String LINE_SEP = System.getProperty("line.separator");
    private static final Pattern LINEBREAK = Pattern.compile("\\R");
    private static final Pattern HISTORY_ALL_START_FILENAME = Pattern.compile(
            "((?<cmd>(all|history|start))(\\z|\\p{javaWhitespace}+))?(?<filename>.*)");
    private static final String RECORD_SEPARATOR = "\u241E";

    final InputStream cmdin;
    final PrintStream cmdout;
    final PrintStream cmderr;
    final PrintStream console;
    final InputStream userin;
    final PrintStream userout;
    final PrintStream usererr;

    final Feedback feedback = new Feedback();

    /**
     * The constructor for the tool (used by tool launch via main and by test
     * harnesses to capture ins and outs.
     * @param cmdin command line input -- snippets and commands
     * @param cmdout command line output, feedback including errors
     * @param cmderr start-up errors and debugging info
     * @param console console control interaction
     * @param userin code execution input (not yet functional)
     * @param userout code execution output  -- System.out.printf("hi")
     * @param usererr code execution error stream  -- System.err.printf("Oops")
     */
    public TryJShellTool(InputStream cmdin, PrintStream cmdout, PrintStream cmderr,
                         PrintStream console,
                         InputStream userin, PrintStream userout, PrintStream usererr) {
        this.cmdin = cmdin;
        this.cmdout = cmdout;
        this.cmderr = cmderr;
        this.console = console;
        this.userin = userin;
        this.userout = userout;
        this.usererr = usererr;
        initializeFeedbackModes();
    }

    /**
     * Create the default set of feedback modes
     */
    final void initializeFeedbackModes() {
        // Initialize normal feedback mode
        cmdSet("newmode normal command");
        cmdSet("prompt normal '\n-> ' '>> '");
        cmdSet("field normal pre '|  '");
        cmdSet("field normal post '%n'");
        cmdSet("field normal errorpre '|  '");
        cmdSet("field normal errorpost '%n'");
        cmdSet("field normal action 'Added' added-primary");
        cmdSet("field normal action 'Modified' modified-primary");
        cmdSet("field normal action 'Replaced' replaced-primary");
        cmdSet("field normal action 'Overwrote' overwrote-primary");
        cmdSet("field normal action 'Dropped' dropped-primary");
        cmdSet("field normal action 'Rejected' rejected-primary");
        cmdSet("field normal action '  Update added' added-update");
        cmdSet("field normal action '  Update modified' modified-update");
        cmdSet("field normal action '  Update replaced' replaced-update");
        cmdSet("field normal action '  Update overwrote' overwrote-update");
        cmdSet("field normal action '  Update dropped' dropped-update");
        cmdSet("field normal action '  Update rejected' rejected-update");
        cmdSet("field normal resolve '' ok-*");
        cmdSet("field normal resolve ', however, it cannot be invoked until%s is declared' defined-primary");
        cmdSet("field normal resolve ', however, it cannot be referenced until%s is declared' notdefined-primary");
        cmdSet("field normal resolve ' which cannot be invoked until%s is declared' defined-update");
        cmdSet("field normal resolve ' which cannot be referenced until%s is declared' notdefined-update");
        cmdSet("field normal name '%s'");
        cmdSet("field normal type '%s'");
        cmdSet("field normal result '%s'");

        cmdSet("format normal '' *-*-*");

        cmdSet("format normal '{pre}{action} class {name}{resolve}{post}' class");
        cmdSet("format normal '{pre}{action} interface {name}{resolve}{post}' interface");
        cmdSet("format normal '{pre}{action} enum {name}{resolve}{post}' enum");
        cmdSet("format normal '{pre}{action} annotation interface {name}{resolve}{post}' annotation");

        cmdSet("format normal '{pre}{action} method {name}({type}){resolve}{post}' method");

        cmdSet("format normal '{pre}{action} variable {name} of type {type}{resolve}{post}' vardecl");
        cmdSet("format normal '{pre}{action} variable {name} of type {type} with initial value {result}{resolve}{post}' varinit");
        cmdSet("format normal '{pre}{action} variable {name}{resolve}{post}' vardeclrecoverable");
        cmdSet("format normal '{pre}{action} variable {name}, reset to null{post}' varreset-*-update");

        cmdSet("format normal '{pre}Expression value is: {result}{post}" +
                "{pre}  assigned to temporary variable {name} of type {type}{post}' expression");
        cmdSet("format normal '{pre}Variable {name} of type {type} has value {result}{post}' varvalue");
        cmdSet("format normal '{pre}Variable {name} has been assigned the value {result}{post}' assignment");

        cmdSet("feedback normal");

        // Initialize off feedback mode
        cmdSet("newmode off quiet");
        cmdSet("prompt off '-> ' '>> '");
        cmdSet("field off pre '|  '");
        cmdSet("field off post '%n'");
        cmdSet("field off errorpre '|  '");
        cmdSet("field off errorpost '%n'");
        cmdSet("format off '' *-*-*");
    }

    public JShell getState() {
        return state;
    }

    private IOContext input = null;
    private boolean regenerateOnDeath = true;
    private boolean live = false;

    SourceCodeAnalysis analysis;
    JShell state = null;
    Subscription shutdownSubscription = null;

    private boolean debug = false;
    private boolean displayPrompt = true;
    public boolean testPrompt = false;
    private String cmdlineClasspath = null;
    private String cmdlineStartup = null;
    private String[] editor = null;

    // Commands and snippets which should be replayed
    private List<String> replayableHistory;
    private List<String> replayableHistoryPrevious;

    static final Preferences PREFS = Preferences.userRoot().node("tool/JShell");

    static final String STARTUP_KEY = "STARTUP";
    static final String REPLAY_RESTORE_KEY = "REPLAY_RESTORE";

    static final String DEFAULT_STARTUP =
            "\n" +
                    "import java.util.*;\n" +
                    "import java.io.*;\n" +
                    "import java.math.*;\n" +
                    "import java.net.*;\n" +
                    "import java.util.concurrent.*;\n" +
                    "import java.util.prefs.*;\n" +
                    "import java.util.regex.*;\n" +
                    "void printf(String format, Object... args) { System.out.printf(format, args); }\n";

    // Tool id (tid) mapping: the three name spaces
    NameSpace mainNamespace;
    NameSpace startNamespace;
    NameSpace errorNamespace;

    // Tool id (tid) mapping: the current name spaces
    NameSpace currentNameSpace;

    Map<Snippet, SnippetInfo> mapSnippet;

    /**
     * Is the input/output currently interactive
     *
     * @return true if console
     */
    boolean interactive() {
        return input != null && input.interactiveOutput();
    }

    public void debug(String format, Object... args) {
        if (debug) {
            cmderr.printf(format + "\n", args);
        }
    }

    /**
     * Base output for command output -- no pre- or post-fix
     *
     * @param format format
     * @param args args
     */
    public void rawout(String format, Object... args) {
        cmdout.printf(format, args);
    }

    /**
     * Must show command output
     *
     * @param format printf format
     * @param args printf args
     */
    public void hard(String format, Object... args) {
        rawout(feedback.getPre() + format + feedback.getPost(), args);
    }

    /**
     * Error command output
     *
     * @param format printf format
     * @param args printf args
     */
    public void error(String format, Object... args) {
        rawout(feedback.getErrorPre() + format + feedback.getErrorPost(), args);
    }

    /**
     * Optional output
     *
     * @param format printf format
     * @param args printf args
     */
    public void fluff(String format, Object... args) {
        if (feedback.shouldDisplayCommandFluff() && interactive()) {
            hard(format, args);
        }
    }

    /**
     * Optional output -- with embedded per- and post-fix
     *
     * @param format printf format
     * @param args printf args
     */
    public void fluffRaw(String format, Object... args) {
        if (feedback.shouldDisplayCommandFluff() && interactive()) {
            rawout(format, args);
        }
    }

    <T> void hardPairs(Stream<T> stream, Function<T, String> a, Function<T, String> b) {
        Map<String, String> a2b = stream.collect(toMap(a, b,
                (m1, m2) -> m1,
                () -> new LinkedHashMap<>()));
        int aLen = 0;
        for (String av : a2b.keySet()) {
            aLen = Math.max(aLen, av.length());
        }
        String format = "   %-" + aLen + "s -- %s";
        String indentedNewLine = LINE_SEP + feedback.getPre()
                + String.format("   %-" + (aLen + 4) + "s", "");
        for (Entry<String, String> e : a2b.entrySet()) {
            hard(format, e.getKey(), e.getValue().replaceAll("\n", indentedNewLine));
        }
    }

    /**
     * User custom feedback mode only
     *
     * @param fcase Event to report
     * @param update Is this an update (rather than primary)
     * @param fa Action
     * @param fr Resolution status
     * @param name Name string
     * @param type Type string or null
     * @param result Result value or null
     * @param unresolved The unresolved symbols
     */
    public void custom(Feedback.FormatCase fcase, boolean update, Feedback.FormatAction fa, Feedback.FormatResolve fr,
                String name, String type, String unresolved, String result) {
        String format = feedback.getFormat(fcase,
                (update ? Feedback.FormatWhen.UPDATE : Feedback.FormatWhen.PRIMARY), fa, fr,
                name != null, type != null, result != null);
        fluffRaw(format, unresolved, name, type, result);
    }

    /**
     * Trim whitespace off end of string
     *
     * @param s
     * @return
     */
    static String trimEnd(String s) {
        int last = s.length() - 1;
        int i = last;
        while (i >= 0 && Character.isWhitespace(s.charAt(i))) {
            --i;
        }
        if (i != last) {
            return s.substring(0, i + 1);
        } else {
            return s;
        }
    }

    /**
     * Normal start entry point
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        new TryJShellTool(System.in, System.out, System.err, System.out,
                new ByteArrayInputStream(new byte[0]), System.out, System.err)
                .start(args);
    }

    public void start(String[] args) throws Exception {
        List<String> loadList = processCommandArgs(args);
        if (loadList == null) {
            // Abort
            return;
        }
        try (IOContext in = new ConsoleIOContext(this, cmdin, console)) {
            start(in, loadList);
        }
    }

    private void start(IOContext in, List<String> loadList) {
        resetState(); // Initialize

        // Read replay history from last jshell session into previous history
        String prevReplay = PREFS.get(REPLAY_RESTORE_KEY, null);
        if (prevReplay != null) {
            replayableHistoryPrevious = Arrays.asList(prevReplay.split(RECORD_SEPARATOR));
        }

        for (String loadFile : loadList) {
            cmdOpen(loadFile);
        }

        if (regenerateOnDeath) {
            hard("Welcome to JShell -- Version %s", version());
            hard("Type /help for help");
        }

        try {
            while (regenerateOnDeath) {
                if (!live) {
                    resetState();
                }
                run(in);
            }
        } finally {
            closeState();
        }
    }

    /**
     * Process the command line arguments.
     * Set options.
     * @param args the command line arguments
     * @return the list of files to be loaded
     */
    private List<String> processCommandArgs(String[] args) {
        List<String> loadList = new ArrayList<>();
        Iterator<String> ai = Arrays.asList(args).iterator();
        while (ai.hasNext()) {
            String arg = ai.next();
            if (arg.startsWith("-")) {
                switch (arg) {
                    case "-classpath":
                    case "-cp":
                        if (cmdlineClasspath != null) {
                            cmderr.printf("Conflicting -classpath option.\n");
                            return null;
                        }
                        if (ai.hasNext()) {
                            cmdlineClasspath = ai.next();
                        } else {
                            cmderr.printf("Argument to -classpath missing.\n");
                            return null;
                        }
                        break;
                    case "-help":
                        printUsage();
                        return null;
                    case "-version":
                        cmdout.printf("jshell %s\n", version());
                        return null;
                    case "-fullversion":
                        cmdout.printf("jshell %s\n", fullVersion());
                        return null;
                    case "-startup":
                        if (cmdlineStartup != null) {
                            cmderr.printf("Conflicting -startup or -nostartup option.\n");
                            return null;
                        }
                        if (ai.hasNext()) {
                            String filename = ai.next();
                            try {
                                byte[] encoded = Files.readAllBytes(Paths.get(filename));
                                cmdlineStartup = new String(encoded);
                            } catch (AccessDeniedException e) {
                                hard("File '%s' for start-up is not accessible.", filename);
                            } catch (NoSuchFileException e) {
                                hard("File '%s' for start-up is not found.", filename);
                            } catch (Exception e) {
                                hard("Exception while reading start-up file: %s", e);
                            }
                        } else {
                            cmderr.printf("Argument to -startup missing.\n");
                            return null;
                        }
                        break;
                    case "-nostartup":
                        if (cmdlineStartup != null && !cmdlineStartup.isEmpty()) {
                            cmderr.printf("Conflicting -startup option.\n");
                            return null;
                        }
                        cmdlineStartup = "";
                        break;
                    default:
                        cmderr.printf("Unknown option: %s\n", arg);
                        printUsage();
                        return null;
                }
            } else {
                loadList.add(arg);
            }
        }
        return loadList;
    }

    private void printUsage() {
        rawout("Usage:   jshell <options> <load files>\n");
        rawout("where possible options include:\n");
        rawout("  -classpath <path>          Specify where to find user class files\n");
        rawout("  -cp <path>                 Specify where to find user class files\n");
        rawout("  -startup <file>            One run replacement for the start-up definitions\n");
        rawout("  -nostartup                 Do not run the start-up definitions\n");
        rawout("  -help                      Print a synopsis of standard options\n");
        rawout("  -version                   Version information\n");
    }

    private void resetState() {
        closeState();

        // Initialize tool id mapping
        mainNamespace = new NameSpace("main", "");
        startNamespace = new NameSpace("start", "s");
        errorNamespace = new NameSpace("error", "e");
        mapSnippet = new LinkedHashMap<>();
        currentNameSpace = startNamespace;

        // Reset the replayable history, saving the old for restore
        replayableHistoryPrevious = replayableHistory;
        replayableHistory = new ArrayList<>();

        state = JShell.builder()
                .in(userin)
                .out(userout)
                .err(usererr)
                .tempVariableNameGenerator(() -> "$" + currentNameSpace.tidNext())
                .idGenerator((sn, i) -> (currentNameSpace == startNamespace || state.status(sn).isActive())
                        ? currentNameSpace.tid(sn)
                        : errorNamespace.tid(sn))
                .build();
        analysis = state.sourceCodeAnalysis();
        shutdownSubscription = state.onShutdown((JShell deadState) -> {
            if (deadState == state) {
                hard("State engine terminated.");
                hard("Restore definitions with: /reload restore");
                live = false;
            }
        });
        live = true;

        if (cmdlineClasspath != null) {
            state.addToClasspath(cmdlineClasspath);
        }

        String start;
        if (cmdlineStartup == null) {
            start = PREFS.get(STARTUP_KEY, "<nada>");
            if (start.equals("<nada>")) {
                start = DEFAULT_STARTUP;
                PREFS.put(STARTUP_KEY, DEFAULT_STARTUP);
            }
        } else {
            start = cmdlineStartup;
        }
        try (IOContext suin = new FileScannerIOContext(new StringReader(start))) {
            run(suin);
        } catch (Exception ex) {
            hard("Unexpected exception reading start-up: %s\n", ex);
        }
        currentNameSpace = mainNamespace;
    }

    private void closeState() {
        live = false;
        JShell oldState = state;
        if (oldState != null) {
            oldState.unsubscribe(shutdownSubscription); // No notification
            oldState.close();
        }
    }

    /**
     * Main loop
     * @param in the line input/editing context
     */
    private void run(IOContext in) {
        IOContext oldInput = input;
        input = in;
        try {
            String incomplete = "";
            while (live) {
                String prompt;
                if (displayPrompt) {
                    prompt = testPrompt
                            ? incomplete.isEmpty()
                            ? "\u0005" //ENQ
                            : "\u0006" //ACK
                            : incomplete.isEmpty()
                            ? feedback.getPrompt(currentNameSpace.tidNext())
                            : feedback.getContinuationPrompt(currentNameSpace.tidNext())
                    ;
                } else {
                    prompt = "";
                }
                String raw;
                try {
                    raw = in.readLine(prompt, incomplete);
                } catch (IOContext.InputInterruptedException ex) {
                    //input interrupted - clearing current state
                    incomplete = "";
                    continue;
                }
                if (raw == null) {
                    //EOF
                    if (in.interactiveOutput()) {
                        // End after user ctrl-D
                        regenerateOnDeath = false;
                    }
                    break;
                }
                String trimmed = trimEnd(raw);
                if (!trimmed.isEmpty()) {
                    String line = incomplete + trimmed;

                    // No commands in the middle of unprocessed source
                    if (incomplete.isEmpty() && line.startsWith("/") && !line.startsWith("//") && !line.startsWith("/*")) {
                        processCommand(line.trim());
                    } else {
                        incomplete = processSourceCatchingReset(line);
                    }
                }
            }
        } catch (IOException ex) {
            hard("Unexpected exception: %s\n", ex);
        } finally {
            input = oldInput;
        }
    }

    private void addToReplayHistory(String s) {
        if (currentNameSpace == mainNamespace) {
            replayableHistory.add(s);
        }
    }

    private String processSourceCatchingReset(String src) {
        try {
            input.beforeUserCode();
            return processSource(src);
        } catch (IllegalStateException ex) {
            hard("Resetting...");
            live = false; // Make double sure
            return "";
        } finally {
            input.afterUserCode();
        }
    }

    private void processCommand(String cmd) {
        if (cmd.startsWith("/-")) {
            try {
                //handle "/-[number]"
                cmdUseHistoryEntry(Integer.parseInt(cmd.substring(1)));
                return;
            } catch (NumberFormatException ex) {
                //ignore
            }
        }
        String arg = "";
        int idx = cmd.indexOf(' ');
        if (idx > 0) {
            arg = cmd.substring(idx + 1).trim();
            cmd = cmd.substring(0, idx);
        }
        Command[] candidates = findCommand(cmd, c -> c.kind.isRealCommand);
        if (candidates.length == 0) {
            if (!rerunHistoryEntryById(cmd.substring(1))) {
                error("No such command or snippet id: %s", cmd);
                fluff("Type /help for help.");
            }
        } else if (candidates.length == 1) {
            Command command = candidates[0];

            // If comand was successful and is of a replayable kind, add it the replayable history
            if (command.run.apply(arg) && command.kind == CommandKind.REPLAY) {
                addToReplayHistory((command.command + " " + arg).trim());
            }
        } else {
            error("Command: %s is ambiguous: %s", cmd, Arrays.stream(candidates).map(c -> c.command).collect(Collectors.joining(", ")));
            fluff("Type /help for help.");
        }
    }

    private Command[] findCommand(String cmd, Predicate<Command> filter) {
        Command exact = commands.get(cmd);
        if (exact != null)
            return new Command[]{exact};

        return commands.values()
                .stream()
                .filter(filter)
                .filter(command -> command.command.startsWith(cmd))
                .toArray(size -> new Command[size]);
    }

    public static Path toPathResolvingUserHome(String pathString) {
        if (pathString.replace(File.separatorChar, '/').startsWith("~/"))
            return Paths.get(System.getProperty("user.home"), pathString.substring(2));
        else
            return Paths.get(pathString);
    }

    public static final class Command {
        public final String command;
        public final String params;
        public final String description;
        public final String help;
        public final Function<String, Boolean> run;
        public final CompletionProvider completions;
        public final CommandKind kind;

        // NORMAL Commands
        public Command(String command, String params, String description, String help,
                       Function<String, Boolean> run, CompletionProvider completions) {
            this(command, params, description, help,
                    run, completions, CommandKind.NORMAL);
        }

        // Documentation pseudo-commands
        public Command(String command, String description, String help,
                       CommandKind kind) {
            this(command, null, description, help,
                    arg -> {
                        throw new IllegalStateException();
                    },
                    EMPTY_COMPLETION_PROVIDER,
                    kind);
        }

        public Command(String command, String params, String description, String help,
                       Function<String, Boolean> run, CompletionProvider completions, CommandKind kind) {
            this.command = command;
            this.params = params;
            this.description = description;
            this.help = help;
            this.run = run;
            this.completions = completions;
            this.kind = kind;
        }

    }

    public interface CompletionProvider {
        List<Suggestion> completionSuggestions(String input, int cursor, int[] anchor);
    }

    public enum CommandKind {
        NORMAL(true, true, true),
        REPLAY(true, true, true),
        HIDDEN(true, false, false),
        HELP_ONLY(false, true, false),
        HELP_SUBJECT(false, false, false);

        final boolean isRealCommand;
        final boolean showInHelp;
        final boolean shouldSuggestCompletions;

        CommandKind(boolean isRealCommand, boolean showInHelp, boolean shouldSuggestCompletions) {
            this.isRealCommand = isRealCommand;
            this.showInHelp = showInHelp;
            this.shouldSuggestCompletions = shouldSuggestCompletions;
        }
    }

    static final class FixedCompletionProvider implements CompletionProvider {

        private final String[] alternatives;

        public FixedCompletionProvider(String... alternatives) {
            this.alternatives = alternatives;
        }

        @Override
        public List<Suggestion> completionSuggestions(String input, int cursor, int[] anchor) {
            List<Suggestion> result = new ArrayList<>();

            for (String alternative : alternatives) {
                if (alternative.startsWith(input)) {
                    result.add(new SuggestionImpl(alternative, false));
                }
            }

            anchor[0] = 0;

            return result;
        }

    }

    private static final CompletionProvider EMPTY_COMPLETION_PROVIDER = new FixedCompletionProvider();
    private static final CompletionProvider KEYWORD_COMPLETION_PROVIDER = new FixedCompletionProvider("all ", "start ", "history ");
    private static final CompletionProvider RELOAD_OPTIONS_COMPLETION_PROVIDER = new FixedCompletionProvider("restore", "quiet");
    private static final CompletionProvider FILE_COMPLETION_PROVIDER = fileCompletions(p -> true);
    private final Map<String, Command> commands = new LinkedHashMap<>();

    public void registerCommand(Command cmd) {
        commands.put(cmd.command, cmd);
    }

    private static CompletionProvider fileCompletions(Predicate<Path> accept) {
        return (code, cursor, anchor) -> {
            int lastSlash = code.lastIndexOf('/');
            String path = code.substring(0, lastSlash + 1);
            String prefix = lastSlash != (-1) ? code.substring(lastSlash + 1) : code;
            Path current = toPathResolvingUserHome(path);
            List<Suggestion> result = new ArrayList<>();
            try (Stream<Path> dir = Files.list(current)) {
                dir.filter(f -> accept.test(f) && f.getFileName().toString().startsWith(prefix))
                        .map(f -> new SuggestionImpl(f.getFileName() + (Files.isDirectory(f) ? "/" : ""), false))
                        .forEach(result::add);
            } catch (IOException ex) {
                //ignore...
            }
            if (path.isEmpty()) {
                StreamSupport.stream(FileSystems.getDefault().getRootDirectories().spliterator(), false)
                        .filter(root -> accept.test(root) && root.toString().startsWith(prefix))
                        .map(root -> new SuggestionImpl(root.toString(), false))
                        .forEach(result::add);
            }
            anchor[0] = path.length();
            return result;
        };
    }

    private static CompletionProvider classPathCompletion() {
        return fileCompletions(p -> Files.isDirectory(p) ||
                p.getFileName().toString().endsWith(".zip") ||
                p.getFileName().toString().endsWith(".jar"));
    }

    private CompletionProvider editCompletion() {
        return (prefix, cursor, anchor) -> {
            anchor[0] = 0;
            return state.snippets()
                    .flatMap(k -> (k instanceof DeclarationSnippet)
                            ? Stream.of(String.valueOf(k.id()), ((DeclarationSnippet) k).name())
                            : Stream.of(String.valueOf(k.id())))
                    .filter(k -> k.startsWith(prefix))
                    .map(k -> new SuggestionImpl(k, false))
                    .collect(Collectors.toList());
        };
    }

    private CompletionProvider editKeywordCompletion() {
        return (code, cursor, anchor) -> {
            List<Suggestion> result = new ArrayList<>();
            result.addAll(KEYWORD_COMPLETION_PROVIDER.completionSuggestions(code, cursor, anchor));
            result.addAll(editCompletion().completionSuggestions(code, cursor, anchor));
            return result;
        };
    }

    private static CompletionProvider saveCompletion() {
        return (code, cursor, anchor) -> {
            List<Suggestion> result = new ArrayList<>();
            int space = code.indexOf(' ');
            if (space == (-1)) {
                result.addAll(KEYWORD_COMPLETION_PROVIDER.completionSuggestions(code, cursor, anchor));
            }
            result.addAll(FILE_COMPLETION_PROVIDER.completionSuggestions(code.substring(space + 1), cursor - space - 1, anchor));
            anchor[0] += space + 1;
            return result;
        };
    }

    private static CompletionProvider reloadCompletion() {
        return (code, cursor, anchor) -> {
            List<Suggestion> result = new ArrayList<>();
            int pastSpace = code.indexOf(' ') + 1; // zero if no space
            result.addAll(RELOAD_OPTIONS_COMPLETION_PROVIDER.completionSuggestions(code.substring(pastSpace), cursor - pastSpace, anchor));
            anchor[0] += pastSpace;
            return result;
        };
    }

    // Table of commands -- with command forms, argument kinds, help message, implementation, ...

    {
        registerCommand(new Command("/list", "[all|start|<name or id>]", "list the source you have typed",
                "Show the source of snippets, prefaced with the snippet id.\n\n" +
                        "/list\n" +
                        "  -- List the currently active snippets of code that you typed or read with /open\n" +
                        "/list start\n" +
                        "  -- List the automatically evaluated start-up snippets\n" +
                        "/list all\n" +
                        "  -- List all snippets including failed, overwritten, dropped, and start-up\n" +
                        "/list <name>\n" +
                        "  -- List snippets with the specified name (preference for active snippets)\n" +
                        "/list <id>\n" +
                        "  -- List the snippet with the specified snippet id\n",
                arg -> cmdList(arg),
                editKeywordCompletion()));
        registerCommand(new Command("/edit", "<name or id>", "edit a source entry referenced by name or id",
                "Edit a snippet or snippets of source in an external editor.\n" +
                        "The editor to use is set with /set editor.\n" +
                        "If no editor has been set, a simple editor will be launched.\n\n" +
                        "/edit <name>\n" +
                        "  -- Edit the snippet or snippets with the specified name (preference for active snippets)\n" +
                        "/edit <id>\n" +
                        "  -- Edit the snippet with the specified snippet id\n" +
                        "/edit\n" +
                        "  -- Edit the currently active snippets of code that you typed or read with /open\n",
                arg -> cmdEdit(arg),
                editCompletion()));
        registerCommand(new Command("/drop", "<name or id>", "delete a source entry referenced by name or id",
                "Drop a snippet -- making it inactive.\n\n" +
                        "/drop <name>\n" +
                        "  -- Drop the snippet with the specified name\n" +
                        "/drop <id>\n" +
                        "  -- Drop the snippet with the specified snippet id\n",
                arg -> cmdDrop(arg),
                editCompletion(),
                CommandKind.REPLAY));
        registerCommand(new Command("/save", "[all|history|start] <file>", "Save snippet source to a file.",
                "Save the specified snippets and/or commands to the specified file.\n\n" +
                        "/save <file>\n" +
                        "  -- Save the source of current active snippets to the file\n" +
                        "/save all <file>\n" +
                        "  -- Save the source of all snippets to the file\n" +
                        "     Includes source including overwritten, failed, and start-up code\n" +
                        "/save history <file>\n" +
                        "  -- Save the sequential history of all commands and snippets entered since jshell was launched\n" +
                        "/save start <file>\n" +
                        "  -- Save the default start-up definitions to the file\n",
                arg -> cmdSave(arg),
                saveCompletion()));
        registerCommand(new Command("/open", "<file>", "open a file as source input",
                "Open a file and read its contents as snippets and commands.\n\n" +
                        "/open <file>\n" +
                        "  -- Read the specified file as jshell input.\n",
                arg -> cmdOpen(arg),
                FILE_COMPLETION_PROVIDER));
        registerCommand(new Command("/vars", null, "list the declared variables and their values",
                "List the type, name, and value of the current active jshell variables.\n",
                arg -> cmdVars(),
                EMPTY_COMPLETION_PROVIDER));
        registerCommand(new Command("/methods", null, "list the declared methods and their signatures",
                "List the name, parameter types, and return type of the current active jshell methods.\n",
                arg -> cmdMethods(),
                EMPTY_COMPLETION_PROVIDER));
        registerCommand(new Command("/classes", null, "list the declared classes",
                "List the current active jshell classes, interfaces, and enums.\n",
                arg -> cmdClasses(),
                EMPTY_COMPLETION_PROVIDER));
        registerCommand(new Command("/imports", null, "list the imported items",
                "List the current active jshell imports.\n",
                arg -> cmdImports(),
                EMPTY_COMPLETION_PROVIDER));
        registerCommand(new Command("/exit", null, "exit jshell",
                "Leave the jshell tool.  No work is saved.\n" +
                        "Save any work before using this command\n",
                arg -> cmdExit(),
                EMPTY_COMPLETION_PROVIDER));
        registerCommand(new Command("/reset", null, "reset jshell",
                "Reset the jshell tool code and execution state:\n" +
                        "   * All entered code is lost.\n" +
                        "   * Start-up code is re-executed.\n" +
                        "   * The execution state is restarted.\n" +
                        "   * The classpath is cleared.\n" +
                        "Tool settings are maintained, as set with: /set ...\n" +
                        "Save any work before using this command\n",
                arg -> cmdReset(),
                EMPTY_COMPLETION_PROVIDER));
        registerCommand(new Command("/reload", "[restore] [quiet]", "reset and replay relevant history -- current or previous (restore)",
                "Reset the jshell tool code and execution state then replay each\n" +
                        "jshell valid command and valid snippet in the order they were entered.\n\n" +
                        "/reload\n" +
                        "  -- Reset and replay the valid history since jshell was entered, or\n" +
                        "     a /reset, or /reload command was executed -- whichever is most\n" +
                        "     recent.\n" +
                        "/reload restore\n" +
                        "  -- Reset and replay the valid history between the previous and most\n" +
                        "     recent time that jshell was entered, or a /reset, or /reload\n" +
                        "     command was executed. This can thus be used to restore a previous\n" +
                        "     jshell tool sesson.\n" +
                        "/reload [restore] quiet\n" +
                        "  -- With the 'quiet' argument the replay is not shown.  Errors will display.\n",
                arg -> cmdReload(arg),
                reloadCompletion()));
        registerCommand(new Command("/classpath", "<path>", "add a path to the classpath",
                "Append a additional path to the classpath.\n",
                arg -> cmdClasspath(arg),
                classPathCompletion(),
                CommandKind.REPLAY));
        registerCommand(new Command("/history", null, "history of what you have typed",
                "Display the history of snippet and command input since this jshell was launched.\n",
                arg -> cmdHistory(),
                EMPTY_COMPLETION_PROVIDER));
        registerCommand(new Command("/debug", null, "toggle debugging of the jshell",
                "Display debugging information for the jshelll implementation.\n" +
                        "0: Debugging off\n" +
                        "r: Debugging on\n" +
                        "g: General debugging on\n" +
                        "f: File manager debugging on\n" +
                        "c': Completion analysis debugging on\n" +
                        "d': Dependency debugging on\n" +
                        "e': Event debugging on\n",
                arg -> cmdDebug(arg),
                EMPTY_COMPLETION_PROVIDER,
                CommandKind.HIDDEN));
        registerCommand(new Command("/help", "[<command>|<subject>]", "get information about jshell",
                "Display information about jshell.\n" +
                        "/help\n" +
                        "  -- List the jshell commands and help subjects.\n" +
                        "/help <command>\n" +
                        "  -- Display information about the specified comand. The slash must be included.\n" +
                        "     Only the first few letters of the command are needed -- if more than one\n" +
                        "     each will be displayed.  Example:  /help /li\n" +
                        "/help <subject>\n" +
                        "  -- Display information about the specified help subject. Example: /help intro\n",
                arg -> cmdHelp(arg),
                EMPTY_COMPLETION_PROVIDER));
        registerCommand(new Command("/set", "editor|start|feedback|newmode|prompt|format|field ...", "set jshell configuration information",
                "Set jshell configuration information, including:\n" +
                        "the external editor to use, the start-up definitions to use, a new feedback mode,\n" +
                        "the command prompt, the feedback mode to use, or the format of output.\n" +
                        "\n" +
                        "/set editor <command> <optional-arg>...\n" +
                        "  -- Specify the command to launch for the /edit command.\n" +
                        "     The <command> is an operating system dependent string.\n" +
                        "\n" +
                        "/set start <file>\n" +
                        "  -- The contents of the specified <file> become the default start-up snippets and commands.\n" +
                        "\n" +
                        "/set feedback <mode>\n" +
                        "  -- Set the feedback mode describing displayed feedback for entered snippets and commands.\n" +
                        "\n" +
                        "/set newmode <new-mode> [command|quiet [<old-mode>]]\n" +
                        "  -- Create a user-defined feedback mode, optionally copying from an existing mode.\n" +
                        "\n" +
                        "/set prompt <mode> \"<prompt>\" \"<continuation-prompt>\"\n" +
                        "  -- Set the displayed prompts for a given feedback mode.\n" +
                        "\n" +
                        "/set format <mode> \"<format>\" <selector>...\n" +
                        "  -- Configure a feedback mode by setting the format to use in a specified set of cases.\n" +
                        "\n" +
                        "/set field name|type|result|when|action|resolve|pre|post|errorpre|errorpost \"<format>\"  <format-case>...\n" +
                        "  -- Set the format of a field within the <format-string> of a \"/set format\" command\n" +
                        "\n" +
                        "To get more information about one of these forms, use /help with the form specified.\n" +
                        "For example:   /help /set format\n",
                arg -> cmdSet(arg),
                new FixedCompletionProvider("format", "field", "feedback", "prompt", "newmode", "start", "editor")));
        registerCommand(new Command("/?", "", "get information about jshell",
                "Display information about jshell (abbreviation for /help).\n" +
                        "/?\n" +
                        "  -- Display list of commands and help subjects.\n" +
                        "/? <command>\n" +
                        "  -- Display information about the specified comand. The slash must be included.\n" +
                        "     Only the first few letters of the command are needed -- if more than one\n" +
                        "     match, each will be displayed.  Example:  /? /li\n" +
                        "/? <subject>\n" +
                        "  -- Display information about the specified help subject. Example: /? intro\n",
                arg -> cmdHelp(arg),
                EMPTY_COMPLETION_PROVIDER));
        registerCommand(new Command("/!", "", "re-run last snippet",
                "Reevaluate the most recently entered snippet.\n",
                arg -> cmdUseHistoryEntry(-1),
                EMPTY_COMPLETION_PROVIDER));

        // Documentation pseudo-commands

        registerCommand(new Command("/<id>", "re-run snippet by id",
                "",
                CommandKind.HELP_ONLY));
        registerCommand(new Command("/-<n>", "re-run n-th previous snippet",
                "",
                CommandKind.HELP_ONLY));
        registerCommand(new Command("intro", "An introduction to the jshell tool",
                "The jshell tool allows you to execute Java code, getting immediate results.\n" +
                        "You can enter a Java definition (variable, method, class, etc), like:  int x = 8\n" +
                        "or a Java expression, like:  x + x\n" +
                        "or a Java statement or import.\n" +
                        "These little chunks of Java code are called 'snippets'.\n\n" +
                        "There are also jshell commands that allow you to understand and\n" +
                        "control what you are doing, like:  /list\n\n" +
                        "For a list of commands: /help",
                CommandKind.HELP_SUBJECT));
        registerCommand(new Command("shortcuts", "Describe shortcuts",
                "Supported shortcuts include:\n\n" +
                        "<tab>            -- After entering the first few letters of a Java identifier,\n" +
                        "                    a jshell command, or, in some cases, a jshell command argument,\n" +
                        "                    press the <tab> key to complete the input.\n" +
                        "                    If there is more than one completion, show possible completions.\n" +
                        "Shift-<tab>      -- After the name and open parenthesis of a method or constructor invocation,\n" +
                        "                    hold the <shift> key and press the <tab> to see a synopsis of all\n" +
                        "                    matching methods/constructors.\n" +
                        "<fix-shortcut> v -- After a complete expression, press \"<fix-shortcut> v\" to introduce a new variable\n" +
                        "                    whose type is based on the type of the expression.\n" +
                        "                    The \"<fix-shortcut>\" is either Alt-F1 or Alt-Enter, depending on the platform.\n" +
                        "<fix-shortcut> i -- After an unresolvable identifier, press \"<fix-shortcut> i\" and jshell will propose\n" +
                        "                    possible fully qualified names based on the content of the specified classpath.\n" +
                        "                    The \"<fix-shortcut>\" is either Alt-F1 or Alt-Enter, depending on the platform.\n",
                CommandKind.HELP_SUBJECT));
    }

    public List<Suggestion> commandCompletionSuggestions(String code, int cursor, int[] anchor) {
        String prefix = code.substring(0, cursor);
        int space = prefix.indexOf(' ');
        Stream<Suggestion> result;

        if (space == (-1)) {
            result = commands.values()
                    .stream()
                    .distinct()
                    .filter(cmd -> cmd.kind.shouldSuggestCompletions)
                    .map(cmd -> cmd.command)
                    .filter(key -> key.startsWith(prefix))
                    .map(key -> new SuggestionImpl(key + " ", false));
            anchor[0] = 0;
        } else {
            String arg = prefix.substring(space + 1);
            String cmd = prefix.substring(0, space);
            Command[] candidates = findCommand(cmd, c -> true);
            if (candidates.length == 1) {
                result = candidates[0].completions.completionSuggestions(arg, cursor - space, anchor).stream();
                anchor[0] += space + 1;
            } else {
                result = Stream.empty();
            }
        }

        return result.sorted((s1, s2) -> s1.continuation().compareTo(s2.continuation()))
                .collect(Collectors.toList());
    }

    public String commandDocumentation(String code, int cursor) {
        code = code.substring(0, cursor);
        int space = code.indexOf(' ');

        if (space != (-1)) {
            String cmd = code.substring(0, space);
            Command command = commands.get(cmd);
            if (command != null) {
                return command.description;
            }
        }

        return null;
    }

    // --- Command implementations ---

    private static final String[] setSub = new String[]{
            "format", "field", "feedback", "newmode", "prompt", "editor", "start"};

    // The /set command.  Currently /set format, /set field and /set feedback.
    // Other commands will fold here, see: 8148317
    final boolean cmdSet(String arg) {
        ArgTokenizer at = new ArgTokenizer(arg.trim());
        String which = setSubCommand(at);
        if (which == null) {
            return false;
        }
        switch (which) {
            case "format":
                return feedback.setFormat(this, at);
            case "field":
                return feedback.setField(this, at);
            case "feedback":
                return feedback.setFeedback(this, at);
            case "newmode":
                return feedback.setNewMode(this, at);
            case "prompt":
                return feedback.setPrompt(this, at);
            case "editor": {
                String prog = at.next();
                if (prog == null) {
                    hard("The '/set editor' command requires a path argument");
                    return false;
                } else {
                    List<String> ed = new ArrayList<>();
                    ed.add(prog);
                    String n;
                    while ((n = at.next()) != null) {
                        ed.add(n);
                    }
                    editor = ed.toArray(new String[ed.size()]);
                    fluff("Editor set to: %s", arg);
                    return true;
                }
            }
            case "start": {
                String filename = at.next();
                if (filename == null) {
                    hard("The '/set start' command requires a filename argument.");
                } else {
                    try {
                        byte[] encoded = Files.readAllBytes(toPathResolvingUserHome(filename));
                        String init = new String(encoded);
                        PREFS.put(STARTUP_KEY, init);
                    } catch (AccessDeniedException e) {
                        hard("File '%s' for /set start is not accessible.", filename);
                        return false;
                    } catch (NoSuchFileException e) {
                        hard("File '%s' for /set start is not found.", filename);
                        return false;
                    } catch (Exception e) {
                        hard("Exception while reading start set file: %s", e);
                        return false;
                    }
                }
                return true;
            }
            default:
                hard("Error: Invalid /set argument: %s", which);
                return false;
        }
    }

    boolean printSetHelp(ArgTokenizer at) {
        String which = setSubCommand(at);
        if (which == null) {
            return false;
        }
        switch (which) {
            case "format":
                feedback.printFormatHelp(this);
                return true;
            case "field":
                feedback.printFieldHelp(this);
                return true;
            case "feedback":
                feedback.printFeedbackHelp(this);
                return true;
            case "newmode":
                feedback.printNewModeHelp(this);
                return true;
            case "prompt":
                feedback.printPromptHelp(this);
                return true;
            case "editor":
                hard("Specify the command to launch for the /edit command.");
                hard("");
                hard("/set editor <command> <optional-arg>...");
                hard("");
                hard("The <command> is an operating system dependent string.");
                hard("The <command> may include space-separated arguments (such as flags) -- <optional-arg>....");
                hard("When /edit is used, the temporary file to edit will be appended as the last argument.");
                return true;
            case "start":
                hard("Set the start-up configuration -- a sequence of snippets and commands read at start-up.");
                hard("");
                hard("/set start <file>");
                hard("");
                hard("The contents of the specified <file> become the default start-up snippets and commands --");
                hard("which are run when the jshell tool is started or reset.");
                return true;
            default:
                hard("Error: Invalid /set argument: %s", which);
                return false;
        }
    }

    String setSubCommand(ArgTokenizer at) {
        String[] matches = at.next(setSub);
        if (matches == null) {
            error("The /set command requires arguments. See: /help /set");
            return null;
        } else if (matches.length == 0) {
            error("Not a valid argument to /set: %s", at.val());
            fluff("/set is followed by one of: %s", Arrays.stream(setSub)
                    .collect(Collectors.joining(", "))
            );
            return null;
        } else if (matches.length > 1) {
            error("Ambiguous argument to /set: %s", at.val());
            fluff("Use one of: %s", Arrays.stream(matches)
                    .collect(Collectors.joining(", "))
            );
            return null;
        }
        return matches[0];
    }

    boolean cmdClasspath(String arg) {
        if (arg.isEmpty()) {
            hard("/classpath requires a path argument");
            return false;
        } else {
            state.addToClasspath(toPathResolvingUserHome(arg).toString());
            fluff("Path %s added to classpath", arg);
            return true;
        }
    }

    boolean cmdDebug(String arg) {
        if (arg.isEmpty()) {
            debug = !debug;
            InternalDebugControl.setDebugFlags(state, debug ? InternalDebugControl.DBG_GEN : 0);
            fluff("Debugging %s", debug ? "on" : "off");
        } else {
            int flags = 0;
            for (char ch : arg.toCharArray()) {
                switch (ch) {
                    case '0':
                        flags = 0;
                        debug = false;
                        fluff("Debugging off");
                        break;
                    case 'r':
                        debug = true;
                        fluff("REPL tool debugging on");
                        break;
                    case 'g':
                        flags |= InternalDebugControl.DBG_GEN;
                        fluff("General debugging on");
                        break;
                    case 'f':
                        flags |= InternalDebugControl.DBG_FMGR;
                        fluff("File manager debugging on");
                        break;
                    case 'c':
                        flags |= InternalDebugControl.DBG_COMPA;
                        fluff("Completion analysis debugging on");
                        break;
                    case 'd':
                        flags |= InternalDebugControl.DBG_DEP;
                        fluff("Dependency debugging on");
                        break;
                    case 'e':
                        flags |= InternalDebugControl.DBG_EVNT;
                        fluff("Event debugging on");
                        break;
                    default:
                        hard("Unknown debugging option: %c", ch);
                        fluff("Use: 0 r g f c d");
                        return false;
                }
            }
            InternalDebugControl.setDebugFlags(state, flags);
        }
        return true;
    }

    private boolean cmdExit() {
        regenerateOnDeath = false;
        live = false;
        if (!replayableHistory.isEmpty()) {
            PREFS.put(REPLAY_RESTORE_KEY, replayableHistory.stream().reduce(
                    (a, b) -> a + RECORD_SEPARATOR + b).get());
        }
        fluff("Goodbye\n");
        return true;
    }

    boolean cmdHelp(String arg) {
        ArgTokenizer at = new ArgTokenizer(arg);
        String subject = at.next();
        if (subject != null) {
            Command[] matches = commands.values().stream()
                    .filter(c -> c.command.startsWith(subject))
                    .toArray(size -> new Command[size]);
            at.mark();
            String sub = at.next();
            if (sub != null && matches.length == 1 && matches[0].command.equals("/set")) {
                at.rewind();
                return printSetHelp(at);
            }
            if (matches.length > 0) {
                for (Command c : matches) {
                    hard("");
                    hard("%s", c.command);
                    hard("");
                    hard("%s", c.help.replaceAll("\n", LINE_SEP + feedback.getPre()));
                }
                return true;
            } else {
                error("No commands or subjects start with the provided argument: %s\n\n", arg);
            }
        }
        hard("Type a Java language expression, statement, or declaration.");
        hard("Or type one of the following commands:");
        hard("");
        hardPairs(commands.values().stream()
                        .filter(cmd -> cmd.kind.showInHelp),
                cmd -> (cmd.params != null)
                        ? cmd.command + " " + cmd.params
                        : cmd.command,
                cmd -> cmd.description
        );
        hard("");
        hard("For more information type '/help' followed by the name of command or a subject.");
        hard("For example '/help /list' or '/help intro'.  Subjects:");
        hard("");
        hardPairs(commands.values().stream()
                        .filter(cmd -> cmd.kind == CommandKind.HELP_SUBJECT),
                cmd -> cmd.command,
                cmd -> cmd.description
        );
        return true;
    }

    private boolean cmdHistory() {
        cmdout.println();
        for (String s : input.currentSessionHistory()) {
            // No number prefix, confusing with snippet ids
            cmdout.printf("%s\n", s);
        }
        return true;
    }

    /**
     * Avoid parameterized varargs possible heap pollution warning.
     */
    private interface SnippetPredicate extends Predicate<Snippet> {
    }

    /**
     * Apply filters to a stream until one that is non-empty is found.
     * Adapted from Stuart Marks
     *
     * @param supplier Supply the Snippet stream to filter
     * @param filters Filters to attempt
     * @return The non-empty filtered Stream, or null
     */
    private static Stream<Snippet> nonEmptyStream(Supplier<Stream<Snippet>> supplier,
                                                  SnippetPredicate... filters) {
        for (SnippetPredicate filt : filters) {
            Iterator<Snippet> iterator = supplier.get().filter(filt).iterator();
            if (iterator.hasNext()) {
                return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator, 0), false);
            }
        }
        return null;
    }

    private boolean inStartUp(Snippet sn) {
        return mapSnippet.get(sn).space == startNamespace;
    }

    private boolean isActive(Snippet sn) {
        return state.status(sn).isActive();
    }

    private boolean mainActive(Snippet sn) {
        return !inStartUp(sn) && isActive(sn);
    }

    private boolean matchingDeclaration(Snippet sn, String name) {
        return sn instanceof DeclarationSnippet
                && ((DeclarationSnippet) sn).name().equals(name);
    }

    /**
     * Convert a user argument to a Stream of snippets referenced by that argument
     * (or lack of argument).
     *
     * @param arg The user's argument to the command, maybe be the empty string
     * @return a Stream of referenced snippets or null if no matches to specific arg
     */
    private Stream<Snippet> argToSnippets(String arg, boolean allowAll) {
        Stream<Snippet> snippets = state.snippets();
        if (allowAll && arg.equals("all")) {
            // all snippets including start-up, failed, and overwritten
            return snippets;
        } else if (allowAll && arg.equals("start")) {
            // start-up snippets
            return snippets
                    .filter(this::inStartUp);
        } else if (arg.isEmpty()) {
            // Default is all active user snippets
            return snippets
                    .filter(this::mainActive);
        } else {
            Stream<Snippet> result =
                    nonEmptyStream(
                            () -> snippets,
                            // look for active user declarations matching the name
                            sn -> isActive(sn) && matchingDeclaration(sn, arg),
                            // else, look for any declarations matching the name
                            sn -> matchingDeclaration(sn, arg),
                            // else, look for an id of this name
                            sn -> sn.id().equals(arg)
                    );
            return result;
        }
    }

    private boolean cmdDrop(String arg) {
        if (arg.isEmpty()) {
            hard("In the /drop argument, please specify an import, variable, method, or class to drop.");
            hard("Specify by id or name. Use /list to see ids. Use /reset to reset all state.");
            return false;
        }
        Stream<Snippet> stream = argToSnippets(arg, false);
        if (stream == null) {
            hard("No definition or id named %s found.  See /classes, /methods, /vars, or /list", arg);
            return false;
        }
        List<Snippet> snippets = stream
                .filter(sn -> state.status(sn).isActive() && sn instanceof PersistentSnippet)
                .collect(toList());
        if (snippets.isEmpty()) {
            hard("The argument did not specify an active import, variable, method, or class to drop.");
            return false;
        }
        if (snippets.size() > 1) {
            hard("The argument references more than one import, variable, method, or class.");
            hard("Try again with one of the ids below:");
            for (Snippet sn : snippets) {
                cmdout.printf("%4s : %s\n", sn.id(), sn.source().replace("\n", "\n       "));
            }
            return false;
        }
        PersistentSnippet psn = (PersistentSnippet) snippets.get(0);
        state.drop(psn).forEach(this::handleEvent);
        return true;
    }

    private boolean cmdEdit(String arg) {
        Stream<Snippet> stream = argToSnippets(arg, true);
        if (stream == null) {
            hard("No definition or id named %s found.  See /classes, /methods, /vars, or /list", arg);
            return false;
        }
        Set<String> srcSet = new LinkedHashSet<>();
        stream.forEachOrdered(sn -> {
            String src = sn.source();
            switch (sn.subKind()) {
                case VAR_VALUE_SUBKIND:
                    break;
                case ASSIGNMENT_SUBKIND:
                case OTHER_EXPRESSION_SUBKIND:
                case TEMP_VAR_EXPRESSION_SUBKIND:
                    if (!src.endsWith(";")) {
                        src = src + ";";
                    }
                    srcSet.add(src);
                    break;
                default:
                    srcSet.add(src);
                    break;
            }
        });
        StringBuilder sb = new StringBuilder();
        for (String s : srcSet) {
            sb.append(s);
            sb.append('\n');
        }
        String src = sb.toString();
        Consumer<String> saveHandler = new SaveHandler(src, srcSet);
        Consumer<String> errorHandler = s -> hard("Edit Error: %s", s);
        if (editor == null) {
            EditPad.edit(errorHandler, src, saveHandler);
        } else {
            ExternalEditor.edit(editor, errorHandler, src, saveHandler, input);
        }
        return true;
    }

    //where
    // receives editor requests to save
    private class SaveHandler implements Consumer<String> {

        String src;
        Set<String> currSrcs;

        SaveHandler(String src, Set<String> ss) {
            this.src = src;
            this.currSrcs = ss;
        }

        @Override
        public void accept(String s) {
            if (!s.equals(src)) { // quick check first
                src = s;
                try {
                    Set<String> nextSrcs = new LinkedHashSet<>();
                    boolean failed = false;
                    while (true) {
                        CompletionInfo an = analysis.analyzeCompletion(s);
                        if (!an.completeness().isComplete()) {
                            break;
                        }
                        String tsrc = trimNewlines(an.source());
                        if (!failed && !currSrcs.contains(tsrc)) {
                            failed = processCompleteSource(tsrc);
                        }
                        nextSrcs.add(tsrc);
                        if (an.remaining().isEmpty()) {
                            break;
                        }
                        s = an.remaining();
                    }
                    currSrcs = nextSrcs;
                } catch (IllegalStateException ex) {
                    hard("Resetting...");
                    resetState();
                    currSrcs = new LinkedHashSet<>(); // re-process everything
                }
            }
        }

        private String trimNewlines(String s) {
            int b = 0;
            while (b < s.length() && s.charAt(b) == '\n') {
                ++b;
            }
            int e = s.length() - 1;
            while (e >= 0 && s.charAt(e) == '\n') {
                --e;
            }
            return s.substring(b, e + 1);
        }
    }

    private boolean cmdList(String arg) {
        if (arg.equals("history")) {
            return cmdHistory();
        }
        Stream<Snippet> stream = argToSnippets(arg, true);
        if (stream == null) {
            // Check if there are any definitions at all
            if (argToSnippets("", false).iterator().hasNext()) {
                hard("No definition or id named %s found.  Try /list without arguments.", arg);
            } else {
                hard("No definition or id named %s found.  There are no active definitions.", arg);
            }
            return false;
        }

        // prevent double newline on empty list
        boolean[] hasOutput = new boolean[1];
        stream.forEachOrdered(sn -> {
            if (!hasOutput[0]) {
                cmdout.println();
                hasOutput[0] = true;
            }
            cmdout.printf("%4s : %s\n", sn.id(), sn.source().replace("\n", "\n       "));
        });
        return true;
    }

    private boolean cmdOpen(String filename) {
        if (filename.isEmpty()) {
            hard("The /open command requires a filename argument.");
            return false;
        } else {
            try {
                run(new FileScannerIOContext(toPathResolvingUserHome(filename).toString()));
            } catch (FileNotFoundException e) {
                hard("File '%s' is not found: %s", filename, e.getMessage());
                return false;
            } catch (Exception e) {
                hard("Exception while reading file: %s", e);
                return false;
            }
        }
        return true;
    }

    private boolean cmdReset() {
        live = false;
        fluff("Resetting state.");
        return true;
    }

    private boolean cmdReload(String arg) {
        Iterable<String> history = replayableHistory;
        boolean echo = true;
        if (arg.length() > 0) {
            if ("restore".startsWith(arg)) {
                if (replayableHistoryPrevious == null) {
                    hard("No previous history to restore\n", arg);
                    return false;
                }
                history = replayableHistoryPrevious;
            } else if ("quiet".startsWith(arg)) {
                echo = false;
            } else {
                hard("Invalid argument to reload command: %s\nUse 'restore', 'quiet', or no argument\n", arg);
                return false;
            }
        }
        fluff("Restarting and restoring %s.",
                history == replayableHistoryPrevious
                        ? "from previous state"
                        : "state");
        resetState();
        run(new ReloadIOContext(history,
                echo ? cmdout : null));
        return true;
    }

    private boolean cmdSave(String arg_filename) {
        Matcher mat = HISTORY_ALL_START_FILENAME.matcher(arg_filename);
        if (!mat.find()) {
            hard("Malformed argument to the /save command: %s", arg_filename);
            return false;
        }
        boolean useHistory = false;
        String saveAll = "";
        boolean saveStart = false;
        String cmd = mat.group("cmd");
        if (cmd != null) switch (cmd) {
            case "all":
                saveAll = "all";
                break;
            case "history":
                useHistory = true;
                break;
            case "start":
                saveStart = true;
                break;
        }
        String filename = mat.group("filename");
        if (filename == null || filename.isEmpty()) {
            hard("The /save command requires a filename argument.");
            return false;
        }
        try (BufferedWriter writer = Files.newBufferedWriter(toPathResolvingUserHome(filename),
                Charset.defaultCharset(),
                CREATE, TRUNCATE_EXISTING, WRITE)) {
            if (useHistory) {
                for (String s : input.currentSessionHistory()) {
                    writer.write(s);
                    writer.write("\n");
                }
            } else if (saveStart) {
                writer.append(DEFAULT_STARTUP);
            } else {
                Stream<Snippet> stream = argToSnippets(saveAll, true);
                if (stream != null) {
                    for (Snippet sn : stream.collect(toList())) {
                        writer.write(sn.source());
                        writer.write("\n");
                    }
                }
            }
        } catch (FileNotFoundException e) {
            hard("File '%s' for save is not accessible: %s", filename, e.getMessage());
            return false;
        } catch (Exception e) {
            hard("Exception while saving: %s", e);
            return false;
        }
        return true;
    }

    private boolean cmdVars() {
        state.variables().forEach(vk -> {
            String val = state.status(vk) == Status.VALID
                    ? state.varValue(vk)
                    : "(not-active)";
            hard("  %s %s = %s", vk.typeName(), vk.name(), val);
        });
        return true;
    }

    private boolean cmdMethods() {
        state.methods().forEach(mk -> {
            hard("  %s %s", mk.name(), mk.signature());
        });
        return true;
    }

    private boolean cmdClasses() {
        state.types().forEach(ck -> {
            String kind;
            switch (ck.subKind()) {
                case INTERFACE_SUBKIND:
                    kind = "interface";
                    break;
                case CLASS_SUBKIND:
                    kind = "class";
                    break;
                case ENUM_SUBKIND:
                    kind = "enum";
                    break;
                case ANNOTATION_TYPE_SUBKIND:
                    kind = "@interface";
                    break;
                default:
                    assert false : "Wrong kind" + ck.subKind();
                    kind = "class";
                    break;
            }
            hard("  %s %s", kind, ck.name());
        });
        return true;
    }

    private boolean cmdImports() {
        state.imports().forEach(ik -> {
            hard("  import %s%s", ik.isStatic() ? "static " : "", ik.fullname());
        });
        return true;
    }

    private boolean cmdUseHistoryEntry(int index) {
        List<Snippet> keys = state.snippets().collect(toList());
        if (index < 0)
            index += keys.size();
        else
            index--;
        if (index >= 0 && index < keys.size()) {
            rerunSnippet(keys.get(index));
        } else {
            hard("Cannot find snippet %d", index + 1);
            return false;
        }
        return true;
    }

    private boolean rerunHistoryEntryById(String id) {
        Optional<Snippet> snippet = state.snippets()
                .filter(s -> s.id().equals(id))
                .findFirst();
        return snippet.map(s -> {
            rerunSnippet(s);
            return true;
        }).orElse(false);
    }

    private void rerunSnippet(Snippet snippet) {
        String source = snippet.source();
        cmdout.printf("%s\n", source);
        input.replaceLastHistoryEntry(source);
        processSourceCatchingReset(source);
    }

    /**
     * Filter diagnostics for only errors (no warnings, ...)
     * @param diagnostics input list
     * @return filtered list
     */
    List<Diag> errorsOnly(Stream<Diag> diagnostics) {
        return diagnostics
                .filter(d -> d.isError())
                .collect(toList());
    }

    void printDiagnostics(String source, Stream<Diag> diagnostics, boolean embed) {
        String padding = embed ? "    " : "";
        diagnostics.forEach(diag -> {
            //assert diag.getSource().equals(source);

            if (!embed) {
                if (diag.isError()) {
                    hard("Error:");
                } else {
                    hard("Warning:");
                }
            }

            for (String line : diag.getMessage(null).split("\\r?\\n")) { // TODO: Internationalize
                if (!line.trim().startsWith("location:")) {
                    hard("%s%s", padding, line);
                }
            }

            int pstart = (int) diag.getStartPosition();
            int pend = (int) diag.getEndPosition();
            Matcher m = LINEBREAK.matcher(source);
            int pstartl = 0;
            int pendl = -2;
            while (m.find(pstartl)) {
                pendl = m.start();
                if (pendl >= pstart) {
                    break;
                } else {
                    pstartl = m.end();
                }
            }
            if (pendl < pstart) {
                pendl = source.length();
            }
            fluff("%s%s", padding, source.substring(pstartl, pendl));

            StringBuilder sb = new StringBuilder();
            int start = pstart - pstartl;
            for (int i = 0; i < start; ++i) {
                sb.append(' ');
            }
            sb.append('^');
            boolean multiline = pend > pendl;
            int end = (multiline ? pendl : pend) - pstartl - 1;
            if (end > start) {
                for (int i = start + 1; i < end; ++i) {
                    sb.append('-');
                }
                if (multiline) {
                    sb.append("-...");
                } else {
                    sb.append('^');
                }
            }
            fluff("%s%s", padding, sb.toString());

            debug("printDiagnostics start-pos = %d ==> %d -- wrap = %s", diag.getStartPosition(), start, this);
            debug("Code: %s", diag.getCode());
            debug("Pos: %d (%d - %d)", diag.getPosition(),
                    diag.getStartPosition(), diag.getEndPosition());
        });
    }

    private String processSource(String srcInput) throws IllegalStateException {
        while (true) {
            CompletionInfo an = analysis.analyzeCompletion(srcInput);
            if (!an.completeness().isComplete()) {
                return an.remaining();
            }
            boolean failed = processCompleteSource(an.source());
            if (failed || an.remaining().isEmpty()) {
                return "";
            }
            srcInput = an.remaining();
        }
    }

    //where
    private boolean processCompleteSource(String source) throws IllegalStateException {
        debug("Compiling: %s", source);
        boolean failed = false;
        boolean isActive = false;
        List<SnippetEvent> events = state.eval(source);
        for (SnippetEvent e : events) {
            // Report the event, recording failure
            failed |= handleEvent(e);

            // If any main snippet is active, this should be replayable
            // also ignore var value queries
            isActive |= e.causeSnippet() == null &&
                    e.status().isActive() &&
                    e.snippet().subKind() != VAR_VALUE_SUBKIND;
        }
        // If this is an active snippet and it didn't cause the backend to die,
        // add it to the replayable history
        if (isActive && live) {
            addToReplayHistory(source);
        }

        return failed;
    }

    private boolean handleEvent(SnippetEvent ste) {
        Snippet sn = ste.snippet();
        if (sn == null) {
            debug("Event with null key: %s", ste);
            return false;
        }
        Stream<Diag> diagnostics = state.diagnostics(sn);
        String source = sn.source();
        if (ste.causeSnippet() == null) {
            // main event
            printDiagnostics(source, diagnostics, false);
            if (ste.status().isActive()) {
                if (ste.exception() != null) {
                    if (ste.exception() instanceof EvalException) {
                        printEvalException((EvalException) ste.exception());
                        return true;
                    } else if (ste.exception() instanceof UnresolvedReferenceException) {
                        printUnresolved((UnresolvedReferenceException) ste.exception());
                    } else {
                        hard("Unexpected execution exception: %s", ste.exception());
                        return true;
                    }
                } else {
                    displayDeclarationAndValue(ste, false, ste.value());
                }
            } else if (ste.status() == Status.REJECTED) {
                if (diagnostics.count() == 0) {
                    hard("Failed.");
                }
                return true;
            }
        } else if (ste.status() == Status.REJECTED) {
            //TODO -- I don't believe updates can cause failures any more
            hard("Caused failure of dependent %s --", ((DeclarationSnippet) sn).name());
            printDiagnostics(source, diagnostics, true);
        } else {
            // Update
            if (sn instanceof DeclarationSnippet) {
                // display update information
                displayDeclarationAndValue(ste, true, ste.value());

                List<Diag> other = errorsOnly(diagnostics);
                if (other.size() > 0) {
                    printDiagnostics(source, other.stream(), true);
                }
            }
        }
        return false;
    }

    @SuppressWarnings("fallthrough")
    private void displayDeclarationAndValue(SnippetEvent ste, boolean update, String value) {
        Snippet key = ste.snippet();
        FormatAction action;
        Status status = ste.status();
        switch (status) {
            case VALID:
            case RECOVERABLE_DEFINED:
            case RECOVERABLE_NOT_DEFINED:
                if (ste.previousStatus().isActive()) {
                    action = ste.isSignatureChange()
                            ? FormatAction.REPLACED
                            : FormatAction.MODIFIED;
                } else {
                    action = FormatAction.ADDED;
                }
                break;
            case OVERWRITTEN:
                action = FormatAction.OVERWROTE;
                break;
            case DROPPED:
                action = FormatAction.DROPPED;
                break;
            case REJECTED:
                action = FormatAction.REJECTED;
                break;
            case NONEXISTENT:
            default:
                // Should not occur
                error("Unexpected status: " + ste.previousStatus().toString() + "=>" + status.toString());
                return;
        }
        FormatResolve resolution;
        String unresolved;
        if (key instanceof DeclarationSnippet && (status == Status.RECOVERABLE_DEFINED || status == Status.RECOVERABLE_NOT_DEFINED)) {
            resolution = (status == Status.RECOVERABLE_NOT_DEFINED)
                    ? FormatResolve.NOTDEFINED
                    : FormatResolve.DEFINED;
            unresolved = unresolved((DeclarationSnippet) key);
        } else {
            resolution = FormatResolve.OK;
            unresolved = "";
        }
        switch (key.subKind()) {
            case CLASS_SUBKIND:
                custom(FormatCase.CLASS, update, action, resolution,
                        ((TypeDeclSnippet) key).name(), null, unresolved, null);
                break;
            case INTERFACE_SUBKIND:
                custom(FormatCase.INTERFACE, update, action, resolution,
                        ((TypeDeclSnippet) key).name(), null, unresolved, null);
                break;
            case ENUM_SUBKIND:
                custom(FormatCase.ENUM, update, action, resolution,
                        ((TypeDeclSnippet) key).name(), null, unresolved, null);
                break;
            case ANNOTATION_TYPE_SUBKIND:
                custom(FormatCase.ANNOTATION, update, action, resolution,
                        ((TypeDeclSnippet) key).name(), null, unresolved, null);
                break;
            case METHOD_SUBKIND:
                custom(FormatCase.METHOD, update, action, resolution,
                        ((MethodSnippet) key).name(), ((MethodSnippet) key).parameterTypes(), unresolved, null);
                break;
            case VAR_DECLARATION_SUBKIND:
            case VAR_DECLARATION_WITH_INITIALIZER_SUBKIND: {
                VarSnippet vk = (VarSnippet) key;
                if (status == Status.RECOVERABLE_NOT_DEFINED) {
                    custom(FormatCase.VARDECLRECOVERABLE, update, action, resolution,
                            vk.name(), null, unresolved, null);
                } else if (update && ste.isSignatureChange()) {
                    custom(FormatCase.VARRESET, update, action, resolution,
                            vk.name(), null, unresolved, value);
                } else if (key.subKind() == SubKind.VAR_DECLARATION_WITH_INITIALIZER_SUBKIND) {
                    custom(FormatCase.VARINIT, update, action, resolution,
                            vk.name(), vk.typeName(), unresolved, value);
                } else {
                    custom(FormatCase.VARDECL, update, action, resolution,
                            vk.name(), vk.typeName(), unresolved, value);
                }
                break;
            }
            case TEMP_VAR_EXPRESSION_SUBKIND: {
                VarSnippet vk = (VarSnippet) key;
                custom(FormatCase.EXPRESSION, update, action, resolution,
                        vk.name(), vk.typeName(), null, value);
                break;
            }
            case OTHER_EXPRESSION_SUBKIND:
                error("Unexpected expression form -- value is: %s", (value));
                break;
            case VAR_VALUE_SUBKIND: {
                ExpressionSnippet ek = (ExpressionSnippet) key;
                custom(FormatCase.VARVALUE, update, action, resolution,
                        ek.name(), ek.typeName(), null, value);
                break;
            }
            case ASSIGNMENT_SUBKIND: {
                ExpressionSnippet ek = (ExpressionSnippet) key;
                custom(FormatCase.ASSIGNMENT, update, action, resolution,
                        ek.name(), ek.typeName(), null, value);
                break;
            }
            case SINGLE_TYPE_IMPORT_SUBKIND:
            case TYPE_IMPORT_ON_DEMAND_SUBKIND:
            case SINGLE_STATIC_IMPORT_SUBKIND:
            case STATIC_IMPORT_ON_DEMAND_SUBKIND:
                custom(FormatCase.IMPORT, update, action, resolution,
                        ((ImportSnippet) key).name(), null, null, null);
                break;
            case STATEMENT_SUBKIND:
                custom(FormatCase.STATEMENT, update, action, resolution,
                        null, null, null, null);
                break;
        }
    }

    //where
    void printStackTrace(StackTraceElement[] stes) {
        for (StackTraceElement ste : stes) {
            StringBuilder sb = new StringBuilder();
            String cn = ste.getClassName();
            if (!cn.isEmpty()) {
                int dot = cn.lastIndexOf('.');
                if (dot > 0) {
                    sb.append(cn.substring(dot + 1));
                } else {
                    sb.append(cn);
                }
                sb.append(".");
            }
            if (!ste.getMethodName().isEmpty()) {
                sb.append(ste.getMethodName());
                sb.append(" ");
            }
            String fileName = ste.getFileName();
            int lineNumber = ste.getLineNumber();
            String loc = ste.isNativeMethod()
                    ? "Native Method"
                    : fileName == null
                    ? "Unknown Source"
                    : lineNumber >= 0
                    ? fileName + ":" + lineNumber
                    : fileName;
            hard("      at %s(%s)", sb, loc);

        }
    }

    //where
    void printUnresolved(UnresolvedReferenceException ex) {
        DeclarationSnippet corralled = ex.getSnippet();
        List<Diag> otherErrors = errorsOnly(state.diagnostics(corralled));
        StringBuilder sb = new StringBuilder();
        if (otherErrors.size() > 0) {
            if (state.unresolvedDependencies(corralled).count() > 0) {
                sb.append(" and");
            }
            if (otherErrors.size() == 1) {
                sb.append(" this error is addressed --");
            } else {
                sb.append(" these errors are addressed --");
            }
        } else {
            sb.append(".");
        }

        String format = corralled.kind() == METHOD
                ? "Attempted to call %s which cannot be invoked until%s"
                : "Attempted to use %s which cannot be accessed until%s";
        hard(format, corralled.name(),
                unresolved(corralled), sb.toString());
        if (otherErrors.size() > 0) {
            printDiagnostics(corralled.source(), otherErrors.stream(), true);
        }
    }

    //where
    void printEvalException(EvalException ex) {
        if (ex.getMessage() == null) {
            hard("%s thrown", ex.getExceptionClassName());
        } else {
            hard("%s thrown: %s", ex.getExceptionClassName(), ex.getMessage());
        }
        printStackTrace(ex.getStackTrace());
    }

    //where
    String unresolved(DeclarationSnippet key) {
        Stream<String> unr = state.unresolvedDependencies(key);
        StringBuilder sb = new StringBuilder();
        long fromLast = unr.count();
        if (fromLast > 0) {
            sb.append(" ");
        }
        for (String u : unr.collect(Collectors.toList())) {
            --fromLast;
            sb.append(u);
            if (fromLast == 0) {
                // No suffix
            } else if (fromLast == 1) {
                sb.append(", and ");
            } else {
                sb.append(", ");
            }
        }
        return sb.toString();
    }

    /** The current version number as a string.
     */
    static String version() {
        return version("release");  // mm.nn.oo[-milestone]
    }

    /** The current full version number as a string.
     */
    static String fullVersion() {
        return version("full"); // mm.mm.oo[-milestone]-build
    }

    private static final String versionRBName = "jdk.internal.jshell.tool.resources.version";
    private static ResourceBundle versionRB;

    private static String version(String key) {
        if (versionRB == null) {
            try {
                versionRB = ResourceBundle.getBundle(versionRBName);
            } catch (MissingResourceException e) {
                return "(version info not available)";
            }
        }
        try {
            return versionRB.getString(key);
        } catch (MissingResourceException e) {
            return "(version info not available)";
        }
    }

    class NameSpace {
        final String spaceName;
        final String prefix;
        private int nextNum;

        NameSpace(String spaceName, String prefix) {
            this.spaceName = spaceName;
            this.prefix = prefix;
            this.nextNum = 1;
        }

        String tid(Snippet sn) {
            String tid = prefix + nextNum++;
            mapSnippet.put(sn, new SnippetInfo(sn, this, tid));
            return tid;
        }

        String tidNext() {
            return prefix + nextNum;
        }
    }

    static class SnippetInfo {
        final Snippet snippet;
        final NameSpace space;
        final String tid;

        SnippetInfo(Snippet snippet, NameSpace space, String tid) {
            this.snippet = snippet;
            this.space = space;
            this.tid = tid;
        }
    }
}
