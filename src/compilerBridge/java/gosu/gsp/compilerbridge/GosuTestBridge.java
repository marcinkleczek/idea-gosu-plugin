package gosu.gsp.compilerbridge;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/** Executes lightweight Gosu tests. Deliberately separate from the non-executing compiler worker. */
public final class GosuTestBridge {
  private static final PrintStream PROTOCOL = System.out;
  private GosuTestBridge() {}
  public static void main(String[] args) {
    String suite = args.length == 0 ? "Gosu tests" : args[0];
    event("testSuiteStarted", "name", suite, "locationHint", "gosutest://" + suite);
    int failures = 0;
    try {
      List<Object> roots = new ArrayList<>();
      Object fs = Class.forName("gw.config.CommonServices").getMethod("getFileSystem").invoke(null);
      Method directory = Class.forName("gw.lang.reflect.module.IFileSystem").getMethod("getIDirectory", File.class);
      for (int i = 2; i < args.length; i++) roots.add(directory.invoke(fs, new File(args[i])));
      Class.forName("gw.lang.Gosu").getMethod("init", List.class).invoke(null, roots);
      Object type = Class.forName("gw.lang.reflect.TypeSystem").getMethod("getByFullName", String.class).invoke(null, suite);
      if (!(boolean) Class.forName("gw.lang.reflect.IType").getMethod("isValid").invoke(type)) {
        throw (Throwable) Class.forName("gw.lang.reflect.gs.IGosuClass").getMethod("getParseResultsException").invoke(type);
      }
      Class<?> cls = (Class<?>) Class.forName("gw.lang.reflect.IHasJavaClass").getMethod("getBackingClass").invoke(type);
      // Framework-specific lifecycle must not silently be skipped by the lightweight runner.
      for (Class<?> parent = cls.getSuperclass(); parent != null; parent = parent.getSuperclass()) {
        if (parent.getName().equals("junit.framework.TestCase") || parent.getName().equals("gw.test.TestClass"))
          throw new IllegalArgumentException("This runner supports plain Gosu test classes; framework-specific lifecycle is not supported: " + parent.getName());
      }
      var tests = Arrays.stream(cls.getMethods()).filter(m -> m.getName().startsWith("test") && m.getParameterCount() == 0
          && m.getReturnType() == void.class && !Modifier.isStatic(m.getModifiers()) && !m.isSynthetic())
          .filter(m -> args[1].isBlank() || m.getName().equals(args[1])).sorted(Comparator.comparing(Method::getName)).toList();
      if (tests.isEmpty()) throw new IllegalArgumentException("No public instance test methods found: " + args[1]);
      for (Method method : tests) {
        String name = method.getName();
        event("testStarted", "name", name, "locationHint", "gosutest://" + suite + "/" + name, "captureStandardOutput", "true");
        long start = System.nanoTime();
        Throwable error = null;
        Object instance = null;
        // Encode output as service messages so test output cannot be mistaken for test events.
        System.setOut(new PrintStream(new TestOutput(name, "testStdOut"), true, java.nio.charset.StandardCharsets.UTF_8));
        System.setErr(new PrintStream(new TestOutput(name, "testStdErr"), true, java.nio.charset.StandardCharsets.UTF_8));
        try {
          instance = cls.getConstructor().newInstance();
          hook(cls, instance, "beforeTest");
          method.invoke(instance);
        } catch (Throwable ex) { error = unwrap(ex); }
        finally {
          if (instance != null) {
            try { hook(cls, instance, "afterTest"); }
            catch (Throwable ex) { if (error == null) error = unwrap(ex); else error.addSuppressed(unwrap(ex)); }
          }
          System.out.flush(); System.err.flush(); System.setOut(PROTOCOL); System.setErr(PROTOCOL);
        }
        if (error != null) { failures++; failed(name, error); }
        event("testFinished", "name", name, "duration", Long.toString((System.nanoTime() - start) / 1_000_000));
      }
    } catch (Throwable ex) {
      failures++;
      event("testStarted", "name", "Test configuration");
      failed("Test configuration", unwrap(ex));
      event("testFinished", "name", "Test configuration");
    } finally { event("testSuiteFinished", "name", suite); }
    System.exit(failures == 0 ? 0 : 1);
  }
  private static void hook(Class<?> cls, Object instance, String name) throws Exception {
    Method hook;
    try { hook = cls.getMethod(name); } catch (NoSuchMethodException ignored) { return; }
    if (Modifier.isStatic(hook.getModifiers()) || hook.getReturnType() != void.class) throw new IllegalArgumentException(name + " must be an instance void method");
    hook.invoke(instance);
  }
  private static void failed(String name, Throwable error) {
    StringWriter trace = new StringWriter(); error.printStackTrace(new PrintWriter(trace));
    event("testFailed", "name", name, "message", error.toString(), "details", trace.toString());
  }
  private static Throwable unwrap(Throwable error) {
    while (error instanceof InvocationTargetException && error.getCause() != null) error = error.getCause();
    return error;
  }
  public static String escape(String text) {
    return text.replace("|", "||").replace("'", "|'").replace("\n", "|n").replace("\r", "|r").replace("[", "|[").replace("]", "|]");
  }
  private static synchronized void event(String name, String... attributes) {
    StringBuilder out = new StringBuilder("##teamcity[").append(name);
    for (int i = 0; i < attributes.length; i += 2) out.append(' ').append(attributes[i]).append("='").append(escape(attributes[i + 1])).append('\'');
    PROTOCOL.println(out.append(']'));
  }
  private static final class TestOutput extends OutputStream {
    private final String name, event;
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    TestOutput(String name, String event) { this.name = name; this.event = event; }
    @Override public synchronized void write(int b) { buffer.write(b); if (b == '\n') flush(); }
    @Override public synchronized void flush() {
      if (buffer.size() == 0) return;
      event(event, "name", name, "out", buffer.toString(java.nio.charset.StandardCharsets.UTF_8)); buffer.reset();
    }
  }
}
