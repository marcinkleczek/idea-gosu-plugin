package gosu.gsp.resolve;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.*;
import gosu.gsp.psi.GosuFile;
import gosu.gsp.run.GosuConsoleFilter;
import com.intellij.execution.filters.OpenFileHyperlinkInfo;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/** Real editor/PSI/index tests, not a text-only imitation of completion. */
public class GosuInsightTest extends LightJavaCodeInsightFixtureTestCase {
  private static final com.intellij.testFramework.LightProjectDescriptor DESCRIPTOR =
      new com.intellij.testFramework.LightProjectDescriptor() {
        @Override public String getModuleTypeId() { return "JAVA_MODULE"; }
        @Override public com.intellij.openapi.projectRoots.Sdk getSdk() {
          return com.intellij.openapi.projectRoots.JavaSdk.getInstance()
              .createJdk("Gosu test JDK", System.getProperty("gosu.test.jdk"), false);
        }
      };

  @Override protected com.intellij.testFramework.LightProjectDescriptor getProjectDescriptor() { return DESCRIPTOR; }

  private Set<String> complete(String source) {
    myFixture.configureByText("learn.gsp", source);
    assertInstanceOf(myFixture.getFile(), GosuFile.class);
    LookupElement[] variants = myFixture.completeBasic();
    return variants == null ? Set.of() : Arrays.stream(variants).map(LookupElement::getLookupString).collect(Collectors.toSet());
  }

  private PsiElement target(String source) {
    myFixture.configureByText("learn.gsp", source);
    PsiReference ref = myFixture.getFile().findReferenceAt(myFixture.getCaretOffset());
    assertNotNull("No reference at caret", ref);
    return ref.resolve();
  }

  private String extractVariable(String source, String name) {
    myFixture.configureByText("extract.gsp", source);
    var selection = myFixture.getEditor().getSelectionModel();
    return gosu.gsp.refactor.GosuExtractVariable.transform(myFixture.getFile(), selection.getSelectionStart(), selection.getSelectionEnd(), name);
  }

  public void testExtractVariablePreservesTypeAndEvaluationSite() {
    assertEquals("var value : String = \"Ada\".toUpperCase()\nvar name : String = value\nprint(name)",
        extractVariable("var name : String = <selection>\"Ada\".toUpperCase()</selection>\nprint(name)", "value"));
    assertEquals("function label() : String {\n  var value : String = \"Ada\"\n  return value\n}",
        extractVariable("function label() : String {\n  return <selection>\"Ada\"</selection>\n}", "value"));
    assertEquals("for (x in {1, 2}) {\n  var value = x + 1\n  var y = value\n}",
        extractVariable("for (x in {1, 2}) {\n  var y = <selection>x + 1</selection>\n}", "value"));
  }

  public void testExtractVariableRejectsUnsafeSelectionsAndNames() {
    for (String source : java.util.List.of(
        "var x = true and <selection>check()</selection>",
        "if (true) var x = <selection>check()</selection>",
        "class C {\n var x = <selection>check()</selection>\n}",
        "var x = <selection>\"part</selection>ial\"",
        "var x = <selection>\\ -> 1</selection>")) {
      myFixture.configureByText("extract.gsp", source);
      var selection = myFixture.getEditor().getSelectionModel();
      assertNull(source, gosu.gsp.refactor.GosuExtractVariable.selection(myFixture.getFile(), selection.getSelectionStart(), selection.getSelectionEnd()));
    }
    myFixture.configureByText("extract.gsp", "var value = 1");
    assertFalse(gosu.gsp.refactor.GosuExtractVariable.validName(myFixture.getFile(), "value"));
    assertFalse(gosu.gsp.refactor.GosuExtractVariable.validName(myFixture.getFile(), "class"));
    assertNotNull(new gosu.gsp.editor.GosuRefactoringSupportProvider().getIntroduceVariableHandler());
  }

  private void assertGosuCompiles(String source) {
    String sdk = System.getProperty("gosu.test.sdk");
    if (sdk == null || sdk.isBlank()) return;
    var result = gosu.gsp.compiler.GosuCompilerRunner.check(new gosu.gsp.compiler.GosuCompilerRunner.Input(
        source, source.startsWith("class Example") ? "Example.gs" : "Extracted.gsp", "", sdk, System.getProperty("gosu.test.jdk"), java.util.List.of()));
    assertNull(result.failure());
    assertTrue(result.diagnostics().toString(), result.diagnostics().stream().noneMatch(d -> d.severity().equals("ERROR")));
  }

  private String extractMethod(String source) {
    myFixture.configureByText("extract.gsp", source);
    var selection = myFixture.getEditor().getSelectionModel();
    return gosu.gsp.refactor.GosuExtractMethod.transform(myFixture.getFile(), selection.getSelectionStart(), selection.getSelectionEnd(), "compute");
  }

  public void testExtractMethodExpressionParametersAndStaticContext() {
    String result = extractMethod("class Example {\n  static function label(name : String) : String {\n    return <selection>name.toUpperCase()</selection>\n  }\n}");
    assertTrue(result, result.contains("return compute(name)"));
    assertTrue(result, result.contains("private static function compute(nameInput : java.lang.String) : String"));
    assertTrue(result, result.contains("return nameInput.toUpperCase()"));
    assertGosuCompiles(result);
    assertNotNull(new gosu.gsp.editor.GosuRefactoringSupportProvider().getExtractMethodHandler());
  }

  public void testExtractMethodStatementsAndSingleOutput() {
    String result = extractMethod("var name = \"Ada\"\n<selection>var upper = name.toUpperCase()\nprint(upper)</selection>\nprint(upper)");
    assertTrue(result, result.startsWith("var name = \"Ada\"\nvar upper : java.lang.String = compute(name)\nprint(upper)"));
    assertTrue(result, result.contains("function compute(nameInput : java.lang.String) : java.lang.String"));
    assertTrue(result, result.contains("return upperLocal"));
    assertGosuCompiles(result);
    String noOutput = extractMethod("var name = \"Ada\"\n<selection>print(name)</selection>");
    assertTrue(noOutput, noOutput.contains("function compute(nameInput : java.lang.String) : void"));
  }

  public void testExtractMethodRejectsControlFlowMutationsAndMultipleOutputs() {
    for (String source : java.util.List.of(
        "var x = 1\n<selection>x = 2</selection>",
        "var x = 1\n<selection>x++</selection>",
        "while (true) {\n<selection>break</selection>\n}",
        "<selection>var x = 1\nvar y = 2</selection>\nprint(x)\nprint(y)",
        "var name = \"Ada\"\nvar label : String = <selection>\"${name}\"</selection>",
        "var value = \"value\" + 1\n<selection>print(value)</selection>",
        "<selection>print(unknown)</selection>")) {
      myFixture.configureByText("extract.gsp", source);
      var selection = myFixture.getEditor().getSelectionModel();
      assertNull(source, gosu.gsp.refactor.GosuExtractMethod.plan(myFixture.getFile(), selection.getSelectionStart(), selection.getSelectionEnd()));
    }
  }

  public void testNullSafeIntentionAndSdkCompilation() {
    myFixture.configureByText("nulls.gsp", "var name : String = null\nvar upper = name.to<caret>UpperCase()");
    var action = new gosu.gsp.intentions.GosuNullSafeIntention();
    PsiElement element = myFixture.getFile().findElementAt(myFixture.getCaretOffset());
    assertTrue(action.isAvailable(getProject(), myFixture.getEditor(), element));
    com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(getProject(), () ->
        action.invoke(getProject(), myFixture.getEditor(), element));
    myFixture.checkResult("var name : String = null\nvar upper = name?.toUpperCase()");
    assertGosuCompiles(myFixture.getFile().getText());
    for (String source : java.util.List.of("var name : String = null\nname?.to<caret>UpperCase()", "String.val<caret>ueOf(1)", "var n = 1\nn.to<caret>String()")) {
      myFixture.configureByText("nulls.gsp", source);
      assertFalse(source, action.isAvailable(getProject(), myFixture.getEditor(), myFixture.getFile().findElementAt(myFixture.getCaretOffset())));
    }
  }

  public void testNullInspectionOnlyReportsDefiniteImmediateNull() {
    myFixture.enableInspections(new gosu.gsp.inspection.GosuNullDereferenceInspection());
    myFixture.configureByText("nulls.gsp", "var name : String = null\nname.toUpperCase()");
    assertTrue(myFixture.doHighlighting().stream().anyMatch(h -> h.getDescription() != null && h.getDescription().contains("The receiver is null")));
    for (String source : java.util.List.of(
        "var name : String = null\nname = \"Ada\"\nname.toUpperCase()",
        "var name : String = null\nif (name != null) { name.toUpperCase() }",
        "var name : String = null\nname?.toUpperCase()",
        "var name = \"Ada\"\nname.toUpperCase()")) {
      myFixture.configureByText("nulls.gsp", source);
      assertFalse(source, myFixture.doHighlighting().stream().anyMatch(h -> h.getDescription() != null && h.getDescription().contains("The receiver is null")));
    }
  }

  public void testNullFallbackKeepsExpressionGroupingAndRejectsWrites() {
    myFixture.configureByText("nulls.gsp", "var name : String = null\nprint(na<caret>me.toUpperCase())");
    PsiElement element = myFixture.getFile().findElementAt(myFixture.getCaretOffset());
    var ref = gosu.gsp.intentions.GosuNullSupport.fallbackTarget(element);
    assertNotNull(ref);
    String replacement = gosu.gsp.intentions.GosuNullSupport.fallback(element, "\"Gość\"");
    assertEquals("(name ?: \"Gość\")", replacement);
    String source = myFixture.getFile().getText();
    assertGosuCompiles(source.substring(0, ref.getTextOffset()) + replacement + source.substring(ref.getTextRange().getEndOffset()));
    for (String input : java.util.List.of("", "foo)", "\"x\" // comment", "a; b", "x = 1"))
      assertFalse(input, gosu.gsp.intentions.GosuNullSupport.validFallback(input));
    myFixture.configureByText("nulls.gsp", "var name : String = null\nna<caret>me = \"Ada\"");
    assertNull(gosu.gsp.intentions.GosuNullSupport.fallbackTarget(myFixture.getFile().findElementAt(myFixture.getCaretOffset())));
  }

  public void testCallHierarchyAcrossFilesAndOverloads() {
    var library = myFixture.addFileToProject("calls/Worker.gs", "package calls\nclass Worker { static function work(text : String) : String { return text.toUpperCase() } }");
    myFixture.addFileToProject("calls/Caller.gsp", "uses calls.Worker\nWorker.work(\"Ada\")");
    var fn = com.intellij.psi.util.PsiTreeUtil.findChildOfType(library, gosu.gsp.psi.GosuDecls.FunctionDecl.class);
    assertTrue(gosu.gsp.hierarchy.GosuCalls.related(fn, true).stream().anyMatch(e -> e instanceof PsiFile && e.getContainingFile().getName().equals("Caller.gsp")));
    assertTrue(gosu.gsp.hierarchy.GosuCalls.related(fn, false).stream().anyMatch(e -> e instanceof PsiMethod m && m.getName().equals("toUpperCase")));
    myFixture.configureByText("calls.gsp", "function run(x : String) {}\nfunction run(x : int) {}\nrun(1)");
    var refs = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(myFixture.getFile(), gosu.gsp.psi.GosuRefExpr.class);
    var call = refs.stream().filter(r -> r.getText().equals("run")).findFirst().orElseThrow();
    assertTrue(gosu.gsp.hierarchy.GosuCalls.target(call).getText().contains("int"));
    var browser = new gosu.gsp.hierarchy.GosuCallHierarchyBrowser(fn);
    com.intellij.openapi.util.Disposer.dispose(browser);
  }

  private Set<String> smart(String source) {
    myFixture.configureByText("smart.gsp", source);
    var variants = myFixture.complete(com.intellij.codeInsight.completion.CompletionType.SMART);
    return variants == null ? Set.of() : Arrays.stream(variants).map(LookupElement::getLookupString).collect(Collectors.toSet());
  }
  public void testSmartCompletionUsesExpectedTypes() {
    var values = smart("var first = \"Ada\"\nvar second = \"Bob\"\nvar count = 3\nvar result : String = <caret>");
    assertTrue(values.toString(), values.containsAll(Set.of("first", "second")));
    assertFalse(values.contains("count"));
    values = smart("function consume(text : String) {}\nvar text = \"Ada\"\nvar count = 3\nconsume(<caret>)");
    assertTrue(values.toString(), values.contains("text"));
    assertFalse(values.contains("count"));
    values = smart("function result() : String {\nvar text = \"Ada\"\nvar number = 3\nreturn <caret>\n}");
    assertTrue(values.toString(), values.contains("text"));
    assertFalse(values.contains("number"));
  }
  public void testSmartCompletionNamedArguments() {
    var values = smart("function consume(text : String, count : int) {}\nvar text = \"Ada\"\nvar count = 3\nconsume(:count = <caret>)");
    assertTrue(values.toString(), values.contains("count"));
    assertFalse(values.contains("text"));
  }
  public void testSmartCompletionAssignableJavaTypesAndMembers() {
    var values = smart("uses java.util.ArrayList\nuses java.util.List\nvar names = new ArrayList<String>()\nvar other = new ArrayList<String>()\nvar count = 1\nvar target : List<String> = <caret>");
    assertTrue(values.toString(), values.contains("names"));
    assertFalse(values.contains("count"));
    values = smart("var text = \"Ada\"\nvar target : String = text.<caret>");
    assertTrue(values.toString(), values.contains("toUpperCase"));
    assertFalse(values.contains("length"));
  }

  public void testChangeSignatureUpdatesProjectCallsAndParameterReferences() {
    var file = myFixture.addFileToProject("signature/Worker.gs", "package signature\nclass Worker { static function greet(name : String, count : int) : String { return name } }");
    var caller = myFixture.addFileToProject("signature/Caller.gsp", "uses signature.Worker\nprint(Worker.greet(\"Ada\", 2))\nprint(Worker.greet(:count = 3, :name = \"Bob\"))");
    var fn = com.intellij.psi.util.PsiTreeUtil.findChildOfType(file, gosu.gsp.psi.GosuDecls.FunctionDecl.class);
    var plan = gosu.gsp.refactor.GosuChangeSignature.plan(fn, java.util.List.of(
        new gosu.gsp.refactor.GosuChangeSignature.Parameter("count", "times", "int", ""),
        new gosu.gsp.refactor.GosuChangeSignature.Parameter("name", "text", "String", ""),
        new gosu.gsp.refactor.GosuChangeSignature.Parameter("", "suffix", "String", "\"!\"")));
    assertTrue(plan.results().get(file), plan.results().get(file).contains("greet(times : int, text : String, suffix : String) : String { return text }"));
    assertTrue(plan.results().get(caller), plan.results().get(caller).contains("greet(2, \"Ada\", \"!\")"));
    assertTrue(plan.results().get(caller), plan.results().get(caller).contains("greet(3, \"Bob\", \"!\")"));
    plan.apply("Change signature test");
    assertTrue(caller.getText(), caller.getText().contains("greet(2, \"Ada\", \"!\")"));
  }
  public void testChangeSignatureRefusesUsedRemovedParametersAndSideEffects() {
    myFixture.configureByText("signature.gsp", "function greet(name : String) : String { return name }\ngreet(\"Ada\")");
    var fn = com.intellij.psi.util.PsiTreeUtil.findChildOfType(myFixture.getFile(), gosu.gsp.psi.GosuDecls.FunctionDecl.class);
    try { gosu.gsp.refactor.GosuChangeSignature.plan(fn, java.util.List.of()); fail("Used parameter must not be removed"); } catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("is used")); }
    myFixture.configureByText("signature.gsp", "function first() : String { return \"Ada\" }\nfunction greet(name : String, count : int) {}\ngreet(first(), 1)");
    fn = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(myFixture.getFile(), gosu.gsp.psi.GosuDecls.FunctionDecl.class).stream().filter(f -> f.getName().equals("greet")).findFirst().orElseThrow();
    try { gosu.gsp.refactor.GosuChangeSignature.plan(fn, java.util.List.of(new gosu.gsp.refactor.GosuChangeSignature.Parameter("count", "count", "int", ""), new gosu.gsp.refactor.GosuChangeSignature.Parameter("name", "name", "String", ""))); fail("Must preserve evaluation order"); } catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("side effects")); }
  }

  private void assertGosuProjectCompiles(String entry, java.util.Map<String, String> sources) {
    String sdk = System.getProperty("gosu.test.sdk");
    if (sdk == null || sdk.isBlank()) return;
    java.nio.file.Path root = null;
    try {
      root = java.nio.file.Files.createTempDirectory("gosu-refactoring-");
      for (var source : sources.entrySet()) {
        var path = root.resolve(source.getKey());
        java.nio.file.Files.createDirectories(path.getParent());
        java.nio.file.Files.writeString(path, source.getValue());
      }
      var result = gosu.gsp.compiler.GosuCompilerRunner.check(new gosu.gsp.compiler.GosuCompilerRunner.Input(
          sources.get(entry), entry, "", sdk, System.getProperty("gosu.test.jdk"), java.util.List.of(root.toString())));
      assertNull(result.failure());
      assertTrue(result.diagnostics().toString(), result.diagnostics().stream().noneMatch(d -> d.severity().equals("ERROR")));
    } catch (java.io.IOException failure) { throw new RuntimeException(failure); }
    finally {
      if (root != null) try (var paths = java.nio.file.Files.walk(root)) {
        for (var path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) java.nio.file.Files.deleteIfExists(path);
      } catch (java.io.IOException failure) { throw new RuntimeException(failure); }
    }
  }

  public void testMoveClassUpdatesPackageImportsAndQualifiedReferences() {
    var source = myFixture.addFileToProject("before/Person.gs", "package before\nclass Person { function name() : String { return \"Ada\" } }");
    var caller = myFixture.addFileToProject("client/Use.gsp", "uses before.Person\nvar p = new Person()\nvar q = new before.Person()");
    var peer = myFixture.addFileToProject("before/Peer.gs", "package before\nclass Peer { var person : Person }");
    var cls = com.intellij.psi.util.PsiTreeUtil.findChildOfType(source, gosu.gsp.psi.GosuDecls.ClassDecl.class);
    var plan = gosu.gsp.refactor.GosuMove.classPlan(cls, "after.model");
    assertTrue(plan.edits().results().get(source).contains("package after.model"));
    assertTrue(plan.edits().results().get(caller).contains("uses after.model.Person"));
    assertTrue(plan.edits().results().get(caller).contains("new after.model.Person()"));
    assertTrue(plan.edits().results().get(peer).contains("uses after.model.Person"));
    plan.apply();
    assertNotNull(myFixture.findFileInTempDir("after/model/Person.gs"));
    assertNull(myFixture.findFileInTempDir("before/Person.gs"));
    var moved = PsiManager.getInstance(getProject()).findFile(myFixture.findFileInTempDir("after/model/Person.gs"));
    assertGosuProjectCompiles("Run.gsp", java.util.Map.of("after/model/Person.gs", moved.getText(), "before/Peer.gs", peer.getText(), "Run.gsp", caller.getText()));
  }
  public void testMoveStaticMethodUpdatesCallsAndChecksDependencies() {
    var source = myFixture.addFileToProject("moving/Source.gs", "package moving\nclass Source { static function upper(text : String) : String { return text.toUpperCase() } }");
    var destination = myFixture.addFileToProject("moving/Destination.gs", "package moving\nclass Destination {} ");
    var caller = myFixture.addFileToProject("moving/Caller.gsp", "uses moving.Source\nprint(Source.upper(\"Ada\"))");
    var fn = com.intellij.psi.util.PsiTreeUtil.findChildOfType(source, gosu.gsp.psi.GosuDecls.FunctionDecl.class);
    var cls = com.intellij.psi.util.PsiTreeUtil.findChildOfType(destination, gosu.gsp.psi.GosuDecls.ClassDecl.class);
    var plan = gosu.gsp.refactor.GosuMove.methodPlan(fn, cls);
    assertTrue(plan.results().get(destination).contains("public static function upper"));
    assertTrue(plan.results().get(caller).contains("moving.Destination.upper(\"Ada\")"));
    assertFalse(plan.results().get(source).contains("function upper"));
    plan.apply("Move test");
    var member = com.intellij.psi.util.PsiTreeUtil.findChildOfType(destination, gosu.gsp.psi.GosuDecls.FunctionDecl.class);
    assertNotNull(member);
    assertGosuProjectCompiles("Run.gsp", java.util.Map.of("moving/Source.gs", source.getText(), "moving/Destination.gs", destination.getText(), "Run.gsp", caller.getText()));
  }
  public void testMoveRejectsConflictsAndInstanceMethods() {
    var source = myFixture.addFileToProject("moveguard/Source.gs", "package moveguard\nclass Source { function work() {} }");
    var destination = myFixture.addFileToProject("moveguard/Destination.gs", "package moveguard\nclass Destination {}");
    var fn = com.intellij.psi.util.PsiTreeUtil.findChildOfType(source, gosu.gsp.psi.GosuDecls.FunctionDecl.class);
    var cls = com.intellij.psi.util.PsiTreeUtil.findChildOfType(destination, gosu.gsp.psi.GosuDecls.ClassDecl.class);
    try { gosu.gsp.refactor.GosuMove.methodPlan(fn, cls); fail("Instance method must not silently become static"); } catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("static")); }
    myFixture.addFileToProject("occupied/Source.gs", "package occupied\nclass Source {}");
    var sourceClass = com.intellij.psi.util.PsiTreeUtil.findChildOfType(source, gosu.gsp.psi.GosuDecls.ClassDecl.class);
    try { gosu.gsp.refactor.GosuMove.classPlan(sourceClass, "occupied"); fail("Must not overwrite class"); } catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("already exists")); }
  }

  private java.util.List<gosu.gsp.inspection.GosuDataFlow.Finding> flow(String source) {
    myFixture.configureByText("flow.gsp", source);
    return gosu.gsp.inspection.GosuDataFlow.analyze(myFixture.getFile());
  }
  public void testDataFlowJoinsBranchesAndTracksAssignments() {
    var findings = flow("function use(flag : boolean) {\nvar name : String = null\nif (flag) {\nname = \"Ada\"\n}\nname.toUpperCase()\n}");
    assertTrue(findings.toString(), findings.stream().anyMatch(f -> f.kind() == gosu.gsp.inspection.GosuDataFlow.Kind.MAYBE_NULL_ACCESS));
    findings = flow("var name : String = null\nvar copy = name\ncopy.toUpperCase()");
    assertTrue(findings.toString(), findings.stream().anyMatch(f -> f.kind() == gosu.gsp.inspection.GosuDataFlow.Kind.NULL_ACCESS));
    findings = flow("var name : String = null\nname = \"Ada\"\nname.toUpperCase()");
    assertTrue(findings.toString(), findings.isEmpty());
  }
  public void testDataFlowUnderstandsGuardsAndFallback() {
    var findings = flow("function use(name : String) {\nif (name == null) {\nreturn\n}\nname.toUpperCase()\n}");
    assertTrue(findings.toString(), findings.isEmpty());
    findings = flow("function use(name : String) {\nif (name != null) {\nname.toUpperCase()\n} else {\nvar copy = name ?: \"Gość\"\ncopy.toUpperCase()\n}\n}");
    assertTrue(findings.toString(), findings.isEmpty());
    findings = flow("function use(name : String) {\nif (name == null) {\nname.toUpperCase()\n}\n}");
    assertTrue(findings.toString(), findings.stream().anyMatch(f -> f.kind() == gosu.gsp.inspection.GosuDataFlow.Kind.NULL_ACCESS));
  }
  public void testDataFlowReportsConstantConditionsAndUnreachableCode() {
    var findings = flow("function use() {\nvar name = \"Ada\"\nif (name != null) {\nreturn\n}\nprint(\"unreachable\")\n}");
    assertTrue(findings.toString(), findings.stream().anyMatch(f -> f.kind() == gosu.gsp.inspection.GosuDataFlow.Kind.CONSTANT_CONDITION));
    assertTrue(findings.toString(), findings.stream().anyMatch(f -> f.kind() == gosu.gsp.inspection.GosuDataFlow.Kind.UNREACHABLE));
  }
  public void testDataFlowDiscardsUnknownEffectsAndUnsupportedControlFlow() {
    for (String source : java.util.List.of(
        "var name : String = null\nunknownCall()\nname.toUpperCase()",
        "var name : String = null\nprint(updateName(), name.toUpperCase())",
        "var name : String = null\nvar ignored = other.Value\nname.toUpperCase()",
        "var name : String = null\nvar ignored = other.Value + name.toUpperCase()",
        "function use(flag : boolean) {\nvar name = \"Ada\"\nif (flag)\nname = null\nname.toUpperCase()\n}",
        "function use(flag : boolean) {\nvar name = \"Ada\"\nif (flag) { name = null } else if (flag) { name = null }\nname.toUpperCase()\n}",
        "var name : String = null\nwhile (unknownCondition()) { name = \"Ada\" }\nname.toUpperCase()",
        "var name : String = null\nvar value = name != null and name.toUpperCase() == \"ADA\"")) {
      var findings = flow(source);
      assertFalse(source + findings, findings.stream().anyMatch(f -> f.kind() == gosu.gsp.inspection.GosuDataFlow.Kind.NULL_ACCESS || f.kind() == gosu.gsp.inspection.GosuDataFlow.Kind.MAYBE_NULL_ACCESS));
    }
  }

  public void testImportedGosuClassAndMembers() {
    PsiFile person = myFixture.addFileToProject("demo/Person.gs", "package demo\nclass Person {\n property get Name(): String { return \"Ada\" }\n function greet(): String { return Name }\n private function secret() {}\n static function create(): Person { return new Person() }\n}");
    assertInstanceOf(person, GosuFile.class);
    Set<String> names = complete("uses demo.Person\nvar p = new Person()\np.<caret>");
    assertTrue(names.toString(), names.containsAll(Set.of("Name", "greet")));
    assertFalse(names.contains("secret"));
    assertFalse(names.contains("create"));
    assertFalse(names.contains("class"));
    PsiElement resolved = target("uses demo.Person\nvar p = new Person()\np.gr<caret>eet()");
    assertNotNull(resolved);
    assertEquals("Person.gs", resolved.getContainingFile().getName());
    assertEquals("greet", ((PsiNamedElement) resolved).getName());
    assertNotNull(target("uses demo.Per<caret>son\nvar p = new Person()"));
    assertNotNull(target("var p = new demo.Per<caret>son()"));
  }

  public void testJavaMembersLiteralsAndMethodChains() {
    myFixture.addClass("package demo; public class Text { public String value() { return null; } public static Text make() { return null; } private void hidden() {} }");
    Set<String> names = complete("uses demo.Text\nvar t : Text\nt.<caret>");
    assertTrue(names.contains("value"));
    assertFalse(names.contains("make"));
    assertFalse(names.contains("hidden"));
    assertTrue(complete("var text = \"hello\"\ntext.<caret>").contains("substring"));
    assertTrue(complete("\"hello\".<caret>").contains("length"));
    assertTrue(complete("uses demo.Text\nText.make().value().<caret>").contains("substring"));
    assertNotNull(target("uses demo.Text\nvar t = new Text()\nt.val<caret>ue()"));
  }

  public void testJavaGenericsAndProperties() {
    myFixture.addClass("package demo; public class Box<T> { public T getValue() { return null; } }");
    assertTrue(complete("uses demo.Box\nvar box : Box<String>\nbox.Value.<caret>").contains("substring"));
    assertTrue(complete("uses demo.Box\nvar box = new Box<String>()\nbox.getValue().<caret>").contains("substring"));
    assertTrue(complete("uses demo.Box\nfunction show(box : Box<String>) { box.Value.<caret> }").contains("substring"));
  }

  public void testPackagesAndAmbiguousImports() {
    myFixture.addFileToProject("a/Person.gs", "package a\nclass Person { function fromA() {} }");
    myFixture.addFileToProject("b/Person.gs", "package b\nclass Person { function fromB() {} }");
    assertTrue(complete("uses a.Person\nvar p : Person\np.<caret>").contains("fromA"));
    assertFalse(complete("uses a.Person\nvar p : Person\np.<caret>").contains("fromB"));
    assertTrue(complete("uses b.*\nvar p : Person\np.<caret>").contains("fromB"));
    assertTrue(complete("package a\nvar p : Person\np.<caret>").contains("fromA"));
    assertNull(target("uses a.*\nuses b.*\nvar p : Per<caret>son"));
  }

  public void testInheritanceShadowingAndUnknownReceiver() {
    myFixture.addFileToProject("Base.gs", "class Base { function inherited(): String { return \"base\" } }");
    myFixture.addFileToProject("Child.gs", "class Child extends Base { function own() {} }");
    assertTrue(complete("var c = new Child()\nc.<caret>").containsAll(Set.of("own", "inherited")));
    assertTrue(complete("var c = new Child()\nc.inherited().<caret>").contains("substring"));
    assertTrue(complete("var text = \"outer\"\nfunction f() { if (true) { var text = new Child() } text.<caret> }").contains("substring"));
    assertTrue(complete("unknown.<caret>").isEmpty());
    assertTrue(complete("var cyclic = cyclic\ncyclic.<caret>").isEmpty());
  }

  public void testQualifiedTypesAndNullSafeAccess() {
    myFixture.addClass("package demo; public class Text { public String value() { return null; } }");
    assertTrue(complete("var text : demo.Text\ntext?.<caret>").contains("value"));
    assertTrue(complete("function f(text : demo.Text) { text.<caret> }").contains("value"));
  }

  public void testExternalJarClasspathAndRunCommand() throws Exception {
    java.nio.file.Path temp = java.nio.file.Files.createTempDirectory("gosu classpath ");
    var settings = gosu.gsp.settings.GosuProjectSettings.getInstance(getProject());
    try {
      java.nio.file.Path source = temp.resolve("External.java");
      java.nio.file.Files.writeString(source, "package learning; public class External { public String lesson() { return \"ok\"; } }");
      String javac = java.nio.file.Path.of(System.getProperty("gosu.test.jdk"), "bin",
          com.intellij.openapi.util.SystemInfo.isWindows ? "javac.exe" : "javac").toString();
      Process compiler = new ProcessBuilder(javac, "--release", "17", "-d", temp.toString(), source.toString())
          .redirectErrorStream(true).start();
      String diagnostics = new String(compiler.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
      assertEquals(diagnostics, 0, compiler.waitFor());
      java.nio.file.Path jar = temp.resolve("learning library.jar");
      try (var out = new java.util.jar.JarOutputStream(java.nio.file.Files.newOutputStream(jar))) {
        out.putNextEntry(new java.util.jar.JarEntry("learning/External.class"));
        out.write(java.nio.file.Files.readAllBytes(temp.resolve("learning/External.class")));
        out.closeEntry();
      }
      com.intellij.openapi.vfs.LocalFileSystem.getInstance().refreshAndFindFileByNioFile(jar);
      com.intellij.openapi.application.WriteAction.run(() ->
          com.intellij.openapi.roots.ex.ProjectRootManagerEx.getInstanceEx(getProject()).makeRootsChange(() -> {
            settings.setClasspath(jar.toString());
            settings.setJdkHome(System.getProperty("gosu.test.jdk"));
          }, false, true));
      assertTrue(complete("uses learning.External\nvar e = new External()\ne.<caret>").contains("lesson"));
      assertTrue(complete("uses learning.External\nvar e = new External()\ne.lesson().<caret>").contains("substring"));
      assertNotNull(target("uses learning.External\nvar e = new External()\ne.les<caret>son()"));
      var configuration = new gosu.gsp.run.GosuRunConfiguration(getProject(), new gosu.gsp.run.GosuRunConfigurationType());
      configuration.setScriptPath(temp.resolve("learn.gsp").toString());
      configuration.setProgramArgs("first \"two words\"");
      var command = configuration.createCommandLine();
      assertEquals(java.util.List.of("-classpath", jar.toString(), temp.resolve("learn.gsp").toString(), "first", "two words"),
          command.getParametersList().getList());
      assertEquals(System.getProperty("gosu.test.jdk"), command.getEnvironment().get("JAVA_HOME"));
      assertTrue(command.getEnvironment().get("JAVACMD").contains("bin/java"));
    } finally {
      com.intellij.openapi.application.WriteAction.run(() ->
          com.intellij.openapi.roots.ex.ProjectRootManagerEx.getInstanceEx(getProject()).makeRootsChange(() -> {
            settings.setClasspath("");
            settings.setJdkHome("");
          }, false, true));
      com.intellij.openapi.util.io.FileUtil.delete(temp.toFile());
    }
  }

  public void testCompletionInsertionAndNoSuggestionsInComments() {
    myFixture.configureByText("learn.gsp", "var text = \"hello\"\ntext.subst<caret>");
    myFixture.completeBasic();
    if (myFixture.getLookup() != null) myFixture.finishLookup('\n');
    assertTrue(myFixture.getFile().getText(), myFixture.getFile().getText().contains("text.substring()"));
    assertTrue(complete("// text.<caret>").isEmpty());
  }

  public void testEnhancementsCompletionNavigationAndImportVisibility() {
    myFixture.addFileToProject("learning/TextExtras.gsx", """
        package learning
        /** Learning helpers. */
        enhancement TextExtras : String {
          /** Returns a loud greeting. */
          function shout(times : int) : String { return this.toUpperCase() }
          property get Letters() : int { return this.length() }
        }
        """);
    assertTrue(complete("var text = \"hi\"\ntext.<caret>").contains("shout"));
    assertTrue(complete("uses learning.TextExtras\nvar text = \"hi\"\ntext.<caret>").containsAll(Set.of("shout", "Letters")));
    assertTrue(complete("uses learning.*\nvar text = \"hi\"\ntext.shout(2).<caret>").contains("substring"));
    PsiElement fn = target("uses learning.TextExtras\nvar text = \"hi\"\ntext.sh<caret>out(2)");
    assertNotNull(fn);
    assertEquals("TextExtras.gsx", fn.getContainingFile().getName());
    String doc = new gosu.gsp.completion.GosuDocumentationProvider().generateDoc(fn, null);
    assertTrue(doc, doc.contains("Returns a loud greeting"));
    myFixture.configureByText("learn.gsp", "uses learning.TextExtras\nvar text = \"hi\"\ntext.shout(2)");
    Runnable optimize = new gosu.gsp.editor.GosuImportOptimizer().processFile(myFixture.getFile());
    com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(getProject(), optimize);
    assertTrue(myFixture.getFile().getText().contains("uses learning.TextExtras"));
  }

  public void testGenericEnhancementResultAndSpecializedApplicability() {
    myFixture.addClass("package demo; public class Box<T> { public T getValue() { return null; } }");
    myFixture.addFileToProject("demo/BoxExtras.gsx", "package demo\nuses demo.Box\nenhancement BoxExtras<T> : Box<T> { function firstValue() : T { return this.Value } }");
    myFixture.addFileToProject("demo/StringBoxExtras.gsx", "package demo\nuses demo.Box\nenhancement StringBoxExtras : Box<String> { function textOnly() : String { return this.Value } }");
    assertTrue(complete("uses demo.*\nvar box = new Box<String>()\nbox.firstValue().<caret>").contains("substring"));
    assertTrue(complete("uses demo.*\nvar box = new Box<String>()\nbox.<caret>").contains("textOnly"));
    assertFalse(complete("uses demo.*\nvar box = new Box<Integer>()\nbox.<caret>").contains("textOnly"));
  }

  public void testCompletionAutoImportAndConflicts() {
    myFixture.addClass("package lessons; public class LearningWidget { public LearningWidget() {} }");
    myFixture.configureByText("learn.gsp", "// Keep this header\nvar w = new LearningWid<caret>()");
    myFixture.completeBasic();
    if (myFixture.getLookup() != null) myFixture.finishLookup('\n');
    assertTrue(myFixture.getFile().getText(), myFixture.getFile().getText().contains("uses lessons.LearningWidget"));
    assertTrue(myFixture.getFile().getText().startsWith("// Keep this header\n"));
    myFixture.configureByText("learn.gsp", "uses first.Widget\nvar value : Widget");
    int offset = myFixture.getFile().getText().lastIndexOf("Widget");
    com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(getProject(), () ->
        gosu.gsp.completion.GosuAutoImports.insert(myFixture.getFile(), offset, offset + 6, "second.Widget"));
    assertEquals("uses first.Widget\nvar value : second.Widget", myFixture.getFile().getText());
  }

  public void testAltEnterImportChoices() {
    myFixture.addFileToProject("learning/Example.gs", "package learning\nclass Example {}");
    myFixture.enableInspections(new gosu.gsp.inspection.GosuMissingImportInspection());
    myFixture.configureByText("learn.gsp", "var example = new Exa<caret>mple()");
    myFixture.launchAction(myFixture.findSingleIntention("Import learning.Example"));
    assertTrue(myFixture.getFile().getText(), myFixture.getFile().getText().startsWith("uses learning.Example\n"));
  }

  public void testInferredTypeLabels() {
    myFixture.configureByText("learn.gsp", "uses java.util.ArrayList\nvar names = new ArrayList<String>()\nvar count = 42\nvar explicit : String\nvar unknown = missing");
    var variables = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(myFixture.getFile(), gosu.gsp.psi.GosuDecls.VarDecl.class);
    java.util.Map<String,String> labels = new java.util.HashMap<>();
    for (var variable : variables) labels.put(variable.getName(), gosu.gsp.completion.GosuTypeInlayHintsProvider.inferredLabel(variable));
    assertEquals("ArrayList<String>", labels.get("names"));
    assertEquals("int", labels.get("count"));
    assertEquals("", labels.get("explicit"));
    assertEquals("", labels.get("unknown"));
  }

  public void testOverloadedParameterInfoAndJavaDocumentation() {
    myFixture.addClass("""
        package lessons;
        public class Methods {
          /** Explains a message. @param text the message */
          public String explain(String text) { return text; }
          public String explain(String text, int count) { return text; }
        }
        """);
    myFixture.configureByText("learn.gsp", "uses lessons.Methods\nvar m = new Methods()\nm.explain(\"comma, inside\", <caret>)");
    var call = gosu.gsp.completion.GosuCalls.at(myFixture.getFile(), myFixture.getCaretOffset());
    assertNotNull(call);
    assertEquals(1, call.parameterIndex());
    assertEquals(2, call.signatures().size());
    assertTrue(call.signatures().stream().anyMatch(c -> c.text().contains("count: int")));
    String doc = new gosu.gsp.completion.GosuDocumentationProvider().generateDoc(call.signatures().getFirst().target(), call.reference());
    assertNotNull(doc);
    assertTrue(doc, doc.contains("explain"));
    myFixture.configureByText("learn.gsp", "function f(a : String, b : String) {}\nf(\"a,b\", f(\"x\", \"y\"), <caret>)");
    assertEquals(2, gosu.gsp.completion.GosuCalls.at(myFixture.getFile(), myFixture.getCaretOffset()).parameterIndex());
  }

  public void testSdkEnhancementsAreIndexedAndNavigable() {
    String sdk = System.getProperty("gosu.test.sdk");
    if (sdk == null) return;
    var settings = gosu.gsp.settings.GosuProjectSettings.getInstance(getProject());
    try {
      com.intellij.openapi.vfs.LocalFileSystem.getInstance().refreshAndFindFileByPath(sdk);
      com.intellij.openapi.application.WriteAction.run(() ->
          com.intellij.openapi.roots.ex.ProjectRootManagerEx.getInstanceEx(getProject()).makeRootsChange(() -> settings.setSdkHome(sdk), false, true));
      assertTrue(complete("var text = \"hello\"\ntext.<caret>").contains("capitalize"));
      var target = target("var text = \"hello\"\ntext.capi<caret>talize()");
      assertNotNull(target);
      assertEquals("CoreStringEnhancement.gsx", target.getContainingFile().getName());
    } finally {
      com.intellij.openapi.application.WriteAction.run(() ->
          com.intellij.openapi.roots.ex.ProjectRootManagerEx.getInstanceEx(getProject()).makeRootsChange(() -> settings.setSdkHome(""), false, true));
    }
  }

  public void testConsoleHyperlinks() {
    PsiFile script = myFixture.addFileToProject("scripts/failure.gsp", "print(1)\nthrow new RuntimeException()\n");
    String path = script.getVirtualFile().getPath();
    GosuConsoleFilter filter = new GosuConsoleFilter(getProject(), path, script.getVirtualFile().getParent().getPath());
    String header = "gw.lang.parser.exceptions.ParseResultsException: failure.gsp\n";
    assertNull(filter.applyFilter(header, header.length()));
    String diagnostic = "Bad type [line:2 col:7] in\n";
    var result = filter.applyFilter(diagnostic, header.length() + diagnostic.length());
    assertNotNull(result);
    var link = (OpenFileHyperlinkInfo) result.getFirstHyperlinkInfo();
    assertEquals(script.getVirtualFile(), link.getDescriptor().getFile());
    var document = PsiDocumentManager.getInstance(getProject()).getDocument(script);
    assertNotNull(document);
    assertEquals(document.getLineStartOffset(1) + 6, link.getDescriptor().getOffset());
    String stack = "\tat _nopackage_.failure.evaluate(failure.gsp:2)\n";
    assertNotNull(filter.applyFilter(stack, stack.length()));
    assertNull(filter.applyFilter("at java.lang.Thread.run(Thread.java:1)\n", 50));
  }

  public void testOverloadSelectionAndGenericFactories() {
    myFixture.addClass("package demo; public class Factory { public static String pick(String s) { return s; } public static Integer pick(int n) { return n; } public static <T> T identity(T value) { return value; } }");
    assertTrue(complete("uses demo.Factory\nFactory.pick(\"text\").<caret>").contains("substring"));
    assertTrue(complete("uses demo.Factory\nFactory.pick(12).<caret>").contains("intValue"));
    assertFalse(complete("uses demo.Factory\nFactory.pick(12).<caret>").contains("substring"));
    assertTrue(complete("uses demo.Factory\nFactory.identity(\"hello\").<caret>").contains("substring"));
    var factoryNames = complete("uses java.util.Collections\nCollections.singletonList(\"hello\").get(0).<caret>");
    assertTrue(factoryNames.toString(), factoryNames.contains("substring"));
    var method = target("uses demo.Factory\nFactory.pi<caret>ck(\"hello\")");
    assertInstanceOf(method, PsiMethod.class);
    assertEquals("java.lang.String", ((PsiMethod)method).getParameterList().getParameters()[0].getType().getCanonicalText());
  }

  public void testCollectionElementsAndNestedGenerics() {
    assertTrue(complete("var names = {\"Ada\", \"Ola\"}\nnames[0].<caret>").contains("substring"));
    assertTrue(complete("uses java.util.List\nvar names : List<String>\nfor (name in names) { name.<caret> }").contains("substring"));
    myFixture.configureByText("learn.gsp", "uses java.util.List\nvar nested : List<List<String>>");
    var nested = com.intellij.psi.util.PsiTreeUtil.findChildOfType(myFixture.getFile(), gosu.gsp.psi.GosuDecls.VarDecl.class);
    assertEquals("List<List<String>>", GosuMembers.typeLabel(GosuMembers.infer(nested)));
    var nestedNames = complete("uses java.util.List\nvar nested : List<List<String>>\nnested.get(0).get(0).<caret>");
    assertTrue(nestedNames.toString(), nestedNames.contains("substring"));
    myFixture.addFileToProject("demo/Container.gs", "package demo\nclass Container<T> { function getValue() : T { return null } }");
    assertTrue(complete("uses demo.Container\nvar box : Container<String>\nbox.getValue().<caret>").contains("substring"));
    myFixture.addFileToProject("demo/Strings.gs", "package demo\nclass Strings extends Container<String> {}");
    assertTrue(complete("uses demo.Strings\nvar box : Strings\nbox.getValue().<caret>").contains("substring"));
    assertNotNull(target("uses java.util.List\nvar names : List<String>\nnames.ge<caret>t(0)"));
  }

  public void testBlockParameterTypesAndScope() {
    assertTrue(complete("var block = \\ text : String -> text.<caret>").contains("substring"));
    assertTrue(complete("uses java.util.List\nvar names : List<String>\nnames.forEach(\\ text -> text.<caret>)").contains("substring"));
    assertTrue(complete("var block = \\ text : String -> text.length()\ntext.<caret>").isEmpty());
  }

  public void testImplementMethodsAndAvoidDuplicates() {
    myFixture.configureByText("Task.gs", "class Task implements Runnable { <caret> }");
    var action = myFixture.findSingleIntention("Implement all missing methods");
    myFixture.launchAction(action);
    String generated = myFixture.getFile().getText();
    assertTrue(generated, generated.contains("override function run() : void"));
    assertTrue(generated.contains("UnsupportedOperationException"));
    var cls = com.intellij.psi.util.PsiTreeUtil.findChildOfType(myFixture.getFile(), gosu.gsp.psi.GosuDecls.ClassDecl.class);
    assertFalse(gosu.gsp.intentions.GosuOverrideMethods.candidates(cls).stream().anyMatch(c -> c.key().equals("run()")));
    if (System.getProperty("gosu.test.sdk") != null) {
      var result = gosu.gsp.compiler.GosuCompilerRunner.check(new gosu.gsp.compiler.GosuCompilerRunner.Input(
          generated, "Task.gs", "", System.getProperty("gosu.test.sdk"), System.getProperty("gosu.test.jdk"), java.util.List.of()));
      assertNull(result.failure());
      assertTrue(result.diagnostics().toString(), result.diagnostics().isEmpty());
    }
    myFixture.configureByText("Names.gs", "uses java.util.Comparator\nclass Names implements Comparator<String> { <caret> }");
    cls = com.intellij.psi.util.PsiTreeUtil.findChildOfType(myFixture.getFile(), gosu.gsp.psi.GosuDecls.ClassDecl.class);
    var candidates = gosu.gsp.intentions.GosuOverrideMethods.candidates(cls);
    assertTrue(candidates.toString(), candidates.stream().anyMatch(c -> c.source().contains("java.lang.String") && c.source().contains("compare(")));
  }

  public void testAltEnterTypeFunctionAndProperty() {
    myFixture.configureByText("learn.gsp", "var mess<caret>age = \"hello\"");
    myFixture.launchAction(myFixture.findSingleIntention("Add explicit variable type"));
    assertTrue(myFixture.getFile().getText().contains("message : java.lang.String"));
    myFixture.configureByText("learn.gsp", "gre<caret>et(\"Ada\", 2)");
    myFixture.launchAction(myFixture.findSingleIntention("Create function from usage"));
    assertTrue(myFixture.getFile().getText(), myFixture.getFile().getText().contains("function greet(arg1 : java.lang.String, arg2 : int)"));
    myFixture.configureByText("Person.gs", "class Person { private var _na<caret>me : String }");
    myFixture.launchAction(myFixture.findSingleIntention("Generate property getter / setter"));
    String text = myFixture.getFile().getText();
    assertTrue(text, text.contains("property get Name() : java.lang.String"));
    assertTrue(text, text.contains("property set Name(value : java.lang.String)"));
  }

  public void testTestGutterAndResultNavigation() {
    PsiFile file = myFixture.addFileToProject("learning/LessonTest.gs", "package learning\nclass LessonTest { function testPass() {} function helper() {} }");
    var cls = com.intellij.psi.util.PsiTreeUtil.findChildOfType(file, gosu.gsp.psi.GosuDecls.ClassDecl.class);
    assertNotNull(gosu.gsp.testing.GosuTests.testClass(cls));
    var marker = new gosu.gsp.testing.GosuTestLineMarker();
    assertNotNull(marker.getInfo(cls.getNameIdentifier()));
    var methods = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(cls, gosu.gsp.psi.GosuDecls.FunctionDecl.class);
    for (var fn : methods) assertEquals(fn.getName().equals("testPass"), marker.getInfo(fn.getNameIdentifier()) != null);
    var locations = new gosu.gsp.testing.GosuTestLocator().getLocation("gosutest", "learning.LessonTest/testPass", getProject(), com.intellij.psi.search.GlobalSearchScope.allScope(getProject()));
    assertEquals(1, locations.size());
    assertEquals("testPass", ((PsiNamedElement)locations.getFirst().getPsiElement()).getName());
  }

  public void testConfigurationReportExplainsInvalidPaths() {
    String report = gosu.gsp.settings.GosuConfigurationReport.describe(getProject(), "/missing/gosu", "/missing/jdk", "/missing/library.jar");
    assertTrue(report, report.contains("ERROR:"));
    assertTrue(report, report.contains("/missing/library.jar"));
    assertTrue(report, report.contains("Project JDK:"));
    assertTrue(report, report.contains("No program was executed"));
  }

  public void testInheritanceNavigationOverloadsAndJavaInterfaces() {
    var base = myFixture.addFileToProject("nav/Base.gs", "package nav\nclass Base { function greet(x : String) : String { return x } function greet(x : int) : int { return x } }");
    var child = myFixture.addFileToProject("nav/Child.gs", "package nav\nclass Child extends Base { override function greet(x : String) : String { return x } }");
    var baseClass = com.intellij.psi.util.PsiTreeUtil.findChildOfType(base, gosu.gsp.psi.GosuDecls.ClassDecl.class);
    var childClass = com.intellij.psi.util.PsiTreeUtil.findChildOfType(child, gosu.gsp.psi.GosuDecls.ClassDecl.class);
    var method = com.intellij.psi.util.PsiTreeUtil.findChildOfType(child, gosu.gsp.psi.GosuDecls.FunctionDecl.class);
    assertEquals(java.util.List.of(baseClass), gosu.gsp.hierarchy.GosuHierarchy.parents(childClass));
    assertEquals(java.util.List.of(childClass), gosu.gsp.hierarchy.GosuHierarchy.children(baseClass));
    var supers = gosu.gsp.hierarchy.GosuHierarchy.superMembers(method);
    assertEquals(1, supers.size());
    assertTrue(supers.getFirst().getText(), supers.getFirst().getText().contains("x : String"));
    var definitions = com.intellij.psi.search.searches.DefinitionsScopedSearch.search(supers.getFirst()).findAll();
    assertTrue(definitions.toString(), definitions.contains(method));
    var tree = gosu.gsp.hierarchy.GosuTypeHierarchyBrowser.structure(baseClass, false);
    assertEquals(1, tree.getChildElements(tree.getRootElement()).length);
    var provider = new gosu.gsp.hierarchy.GosuHierarchyProvider();
    var browser = provider.createHierarchyBrowser(childClass);
    try { assertNotNull(browser.getComponent()); }
    finally { com.intellij.openapi.util.Disposer.dispose((com.intellij.openapi.Disposable)browser); }
    var markers = new java.util.ArrayList<com.intellij.codeInsight.daemon.LineMarkerInfo<?>>();
    new gosu.gsp.hierarchy.GosuInheritanceMarkers().collectSlowLineMarkers(java.util.List.of(method.getNameIdentifier()), markers);
    assertFalse(markers.isEmpty());

    var task = myFixture.addFileToProject("nav/Task.gs", "package nav\nclass Task implements Runnable { override function run() {} }");
    var run = com.intellij.psi.util.PsiTreeUtil.findChildOfType(task, gosu.gsp.psi.GosuDecls.FunctionDecl.class);
    assertEquals("run", ((PsiMethod)gosu.gsp.hierarchy.GosuHierarchy.superMembers(run).getFirst()).getName());
    var runnable = JavaPsiFacade.getInstance(getProject()).findClass("java.lang.Runnable", com.intellij.psi.search.GlobalSearchScope.allScope(getProject()));
    assertTrue(com.intellij.psi.search.searches.DefinitionsScopedSearch.search(runnable).findAll().stream().anyMatch(e -> e.getContainingFile() == task));
  }

  public void testHierarchyGenericSubstitutionPropertiesAndCycles() {
    myFixture.addClass("package nav; public interface Value<T> { T transform(T input); String getName(); void setName(String name); }");
    var file = myFixture.addFileToProject("nav/Named.gs", "package nav\nclass Named implements Value<String> { override function transform(input : String) : String { return input } override property get Name() : String { return \"x\" } override property set Name(name : String) {} }");
    var fn = com.intellij.psi.util.PsiTreeUtil.findChildOfType(file, gosu.gsp.psi.GosuDecls.FunctionDecl.class);
    assertEquals(1, gosu.gsp.hierarchy.GosuHierarchy.superMembers(fn).size());
    for (var prop : com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(file, gosu.gsp.psi.GosuDecls.VarDecl.class))
      assertEquals(1, gosu.gsp.hierarchy.GosuHierarchy.superMembers(prop).size());
    myFixture.addFileToProject("nav/A.gs", "package nav\nclass A extends B {}");
    var b = myFixture.addFileToProject("nav/B.gs", "package nav\nclass B extends A {}");
    var cls = com.intellij.psi.util.PsiTreeUtil.findChildOfType(b, gosu.gsp.psi.GosuDecls.ClassDecl.class);
    assertEquals(1, gosu.gsp.hierarchy.GosuHierarchy.descendants(cls).size());
  }

  public void testGosuInitializerCompletionAndInsertion() {
    myFixture.addFileToProject("forms/Person.gs", "package forms\nclass Person { property get ReadOnly() : String { return \"x\" } property get Name() : String { return \"x\" } property set Name(value : String) {} property set Age(value : int) {} private property set Secret(value : String) {} }");
    var names = complete("uses forms.Person\nvar p = new Person() { :<caret> }");
    assertTrue(names.toString(), names.containsAll(Set.of("Name", "Age")));
    assertFalse(names.contains("ReadOnly"));
    assertFalse(names.contains("Secret"));
    assertFalse(complete("uses forms.Person\nvar p = new Person() { :Name = \"Ada\", :<caret> }").contains("Name"));
    myFixture.configureByText("learn.gsp", "uses forms.Person\nvar p = new Person() { :Na<caret> }");
    myFixture.completeBasic();
    if (myFixture.getLookup() != null) myFixture.finishLookup('\n');
    assertTrue(myFixture.getFile().getText(), myFixture.getFile().getText().contains(":Name = "));
    assertFalse(myFixture.getFile().getText().contains("::"));
    myFixture.addClass("package forms; public class Bean { public void setName(String value) {} public String getReadOnly() { return null; } public final int LOCKED = 1; }");
    assertTrue(complete("uses forms.Bean\nvar p = new Bean() { :<caret> }").contains("Name"));
    assertFalse(complete("uses forms.Bean\nvar p = new Bean() { :<caret> }").contains("ReadOnly"));
  }

  public void testGosuNamedArgumentsAndEnhancementImportInsertion() {
    var names = complete("function greet(name : String, count : int) {}\ngreet(:<caret>)");
    assertTrue(names.toString(), names.containsAll(Set.of("name", "count")));
    assertFalse(complete("function greet(name : String, count : int) {}\ngreet(:name = \"Ada\", :<caret>)").contains("name"));
    myFixture.configureByText("learn.gsp", "function greet(name : String, count : int) {}\ngreet(:co<caret>)");
    myFixture.completeBasic();
    if (myFixture.getLookup() != null) myFixture.finishLookup('\n');
    assertTrue(myFixture.getFile().getText(), myFixture.getFile().getText().contains(":count = "));
    myFixture.addFileToProject("extras/TextHelpers.gsx", "package extras\nenhancement TextHelpers : String { function lessonGreeting() : String { return this } }");
    myFixture.configureByText("learn.gsp", "\"hello\".lessonGree<caret>");
    myFixture.completeBasic();
    if (myFixture.getLookup() != null) myFixture.finishLookup('\n');
    String text = myFixture.getFile().getText();
    assertTrue(text, text.contains("uses extras.TextHelpers"));
    assertTrue(text, text.contains("lessonGreeting()"));
    assertNotNull(target(text.replace("lessonGreeting()", "lessonGree<caret>ting()")));
  }

  public void testShortSyntaxDocumentationAndSymbolDocumentation() {
    var provider = new gosu.gsp.completion.GosuDocumentationProvider();
    for (String source : java.util.List.of("us<caret>es java.lang.String", "var name : String\nname?<caret>.toUpperCase()", "var name : String\nvar value = name ?<caret>: \"none\"", "prop<caret>erty get Name() : String { return \"x\" }", "var twice = \\<caret> n : int -> n * 2")) {
      myFixture.configureByText("learn.gsp", source);
      var element = provider.getCustomDocumentationElement(myFixture.getEditor(), myFixture.getFile(), null, myFixture.getCaretOffset());
      assertNotNull(source, element);
      String doc = provider.generateDoc(element, null);
      assertNotNull(source, doc);
      assertTrue(doc, doc.contains("<pre>"));
    }
    myFixture.configureByText("learn.gsp", "uses java.util.List\nvar names : List<String>\nnames.ge<caret>t(0)");
    var element = provider.getCustomDocumentationElement(myFixture.getEditor(), myFixture.getFile(), null, myFixture.getCaretOffset());
    assertInstanceOf(element, PsiMethod.class);
  }

  public void testMethodOriginInCompletionAndDocumentation() {
    myFixture.addFileToProject("origin/Base.gs", "package origin\nclass Base { function hello() : String { return \"hi\" } }");
    myFixture.addFileToProject("origin/Child.gs", "package origin\nclass Child extends Base {} ");
    myFixture.configureByText("learn.gsp", "uses origin.Child\nvar child : Child\nchild.<caret>");
    var items = myFixture.completeBasic();
    var hello = java.util.Arrays.stream(items).filter(e -> e.getLookupString().equals("hello")).findFirst().orElseThrow();
    var presentation = new com.intellij.codeInsight.lookup.LookupElementPresentation();
    hello.renderElement(presentation);
    assertTrue(presentation.getTailText(), presentation.getTailText().contains("inherited from origin.Base"));
    myFixture.configureByText("learn.gsp", "uses origin.Child\nvar child : Child\nchild.he<caret>llo()");
    var reference = myFixture.getFile().findReferenceAt(myFixture.getCaretOffset());
    String doc = new gosu.gsp.completion.GosuDocumentationProvider().generateDoc(reference.resolve(), reference.getElement());
    assertTrue(doc, doc.contains("Origin:") && doc.contains("inherited from origin.Base"));
    PsiElement javaMethod = target("\"hello\".sub<caret>string(1)");
    doc = new gosu.gsp.completion.GosuDocumentationProvider().generateDoc(javaMethod, null);
    assertTrue(doc, doc.contains("Java method") && doc.contains("java.lang.String"));
    var enhancement = myFixture.addFileToProject("origin/TextExtras.gsx", "package origin\nenhancement TextExtras : String { function originExample() : String { return this } }");
    var fn = com.intellij.psi.util.PsiTreeUtil.findChildOfType(enhancement, gosu.gsp.psi.GosuDecls.FunctionDecl.class);
    doc = new gosu.gsp.completion.GosuDocumentationProvider().generateDoc(fn, null);
    assertTrue(doc, doc.contains("Added by the enhancement origin.TextExtras") && doc.contains("for the type String"));
  }

  public void testTypeExplanationFollowsGenericReturnAndVariableSource() {
    myFixture.configureByText("learn.gsp", "uses java.util.List\nvar names : List<String>\nvar first = names.get(0)\nvar copy = first");
    var declarations = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(myFixture.getFile(), gosu.gsp.psi.GosuDecls.VarDecl.class).stream().toList();
    String explanation = gosu.gsp.completion.GosuTypeExplanation.explain(declarations.get(1)).text();
    assertTrue(explanation, explanation.contains("Type: String") && explanation.contains("List<String>") && explanation.contains("get(...)"));
    assertTrue(explanation, explanation.contains("Generic parameters"));
    String copy = gosu.gsp.completion.GosuTypeExplanation.explain(declarations.get(2)).text();
    assertTrue(copy, copy.contains("first") && copy.contains("List<String>"));
    String doc = new gosu.gsp.completion.GosuDocumentationProvider().generateDoc(declarations.get(1), null);
    assertTrue(doc, doc.contains("Where does the type come from?") && doc.contains("List&lt;String&gt;"));
  }

  public void testTypeExplanationExplicitLiteralUnknownAndIntention() {
    myFixture.configureByText("learn.gsp", "var explicit : String\nvar text = \"<hello>\"\nvar unknown = missing\nvar cyclic = cyclic\ntex<caret>t");
    var declarations = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(myFixture.getFile(), gosu.gsp.psi.GosuDecls.VarDecl.class).stream().toList();
    assertTrue(gosu.gsp.completion.GosuTypeExplanation.explain(declarations.getFirst()).text().contains("declared explicitly"));
    assertTrue(gosu.gsp.completion.GosuTypeExplanation.explain(declarations.get(1)).html().contains("&lt;hello&gt;"));
    assertTrue(gosu.gsp.completion.GosuTypeExplanation.explain(declarations.get(2)).text().contains("Type unknown"));
    assertTrue(gosu.gsp.completion.GosuTypeExplanation.explain(declarations.get(3)).text().contains("Type unknown"));
    assertNotNull(myFixture.findSingleIntention("Explain where the type comes from"));
    assertEquals(declarations.get(1), gosu.gsp.completion.GosuTypeExplanation.declaration(myFixture.getFile().findElementAt(myFixture.getCaretOffset())));
  }
}
