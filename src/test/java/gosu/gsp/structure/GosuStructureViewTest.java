package gosu.gsp.structure;

import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.lang.LanguageStructureViewBuilder;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import gosu.gsp.psi.GosuDecls.FunctionDecl;

import java.util.Arrays;
import java.util.List;

/** Use the registered IDE extension so a broken plugin.xml cannot silently pass. */
public class GosuStructureViewTest extends BasePlatformTestCase {
  private StructureViewModel model() {
    var factory = LanguageStructureViewBuilder.INSTANCE.forLanguage(myFixture.getFile().getLanguage());
    assertInstanceOf(factory, GosuStructureViewFactory.class);
    var builder = LanguageStructureViewBuilder.INSTANCE.getStructureViewBuilder(myFixture.getFile());
    assertInstanceOf(builder, TreeBasedStructureViewBuilder.class);
    return ((TreeBasedStructureViewBuilder) builder).createStructureViewModel(myFixture.getEditor());
  }

  private static List<String> names(StructureViewTreeElement node) {
    return Arrays.stream(node.getChildren())
        .map(child -> ((StructureViewTreeElement) child).getPresentation().getPresentableText()).toList();
  }

  private static StructureViewTreeElement child(StructureViewTreeElement node, String name) {
    return Arrays.stream(node.getChildren()).map(StructureViewTreeElement.class::cast)
        .filter(e -> name.equals(e.getPresentation().getPresentableText())).findFirst().orElseThrow();
  }

  public void testScriptTreeAndCaretSelection() {
    myFixture.configureByText("lesson.gsp", """
        uses java.util.ArrayList
        var title : String = "Gosu"
        function greet(name : String) : String {
          var message : String = name
          <caret>print(message)
          return message
        }
        class Person {
          var age : int = 20
          function birthday() { age++ }
        }
        """);
    StructureViewModel model = model();
    try {
      var root = model.getRoot();
      assertEquals("lesson.gsp", root.getPresentation().getPresentableText());
      assertEquals(List.of("java.util.ArrayList", "title", "greet(name)", "Person"), names(root));
      assertEquals(List.of("name", "message"), names(child(root, "greet(name)")));
      assertEquals(List.of("age", "birthday()"), names(child(root, "Person")));
      assertEquals(": String", child(root, "title").getPresentation().getLocationString());
      assertInstanceOf(model.getCurrentEditorElement(), FunctionDecl.class);
      assertEquals("greet", ((FunctionDecl) model.getCurrentEditorElement()).getName());
      assertTrue(child(root, "Person").canNavigateToSource());
    } finally { Disposer.dispose(model); }
  }

  public void testClassAndEnhancementFilesUseTheirOwnTree() {
    for (String extension : List.of("gs", "gsx")) {
      String declaration = extension.equals("gs") ? "class Lesson" : "enhancement Lesson : String";
      myFixture.configureByText("Lesson." + extension,
          declaration + " { function example() {} }");
      StructureViewModel model = model();
      try {
        assertEquals("Lesson." + extension, model.getRoot().getPresentation().getPresentableText());
        assertEquals(List.of("Lesson"), names(model.getRoot()));
        assertEquals(List.of("example()"), names(child(model.getRoot(), "Lesson")));
      } finally { Disposer.dispose(model); }
    }
    myFixture.configureByText("other.gsp", "var other : String = \"another file\"");
    StructureViewModel other = model();
    try { assertEquals(List.of("other"), names(other.getRoot())); }
    finally { Disposer.dispose(other); }
  }

  public void testPropertyAccessorsHaveDistinctLabelsAndIcons() {
    myFixture.configureByText("Person.gs", """
        class Person {
          property get Name() : String { return "Ada" }
          property set Name(value : String) { print(value) }
          function greet() {}
        }
        """);
    StructureViewModel model = model();
    try {
      var person = child(model.getRoot(), "Person");
      assertEquals(List.of("Name [get]", "Name [set]", "greet()"), names(person));
      assertSame(com.intellij.icons.AllIcons.Nodes.PropertyRead, child(person, "Name [get]").getPresentation().getIcon(false));
      assertSame(com.intellij.icons.AllIcons.Nodes.PropertyWrite, child(person, "Name [set]").getPresentation().getIcon(false));
      assertSame(com.intellij.icons.AllIcons.Nodes.Method, child(person, "greet()").getPresentation().getIcon(false));
      assertTrue(child(person, "Name [get]").canNavigateToSource());
      assertTrue(child(person, "Name [set]").canNavigateToSource());
    } finally { Disposer.dispose(model); }
  }

  public void testTreeReflectsEditsAndEmptyFiles() {
    myFixture.configureByText("editable.gsp", "var oldName : String");
    StructureViewModel model = model();
    try {
      assertEquals(List.of("oldName"), names(model.getRoot()));
      var updates = new java.util.concurrent.atomic.AtomicInteger();
      model.addModelListener(updates::incrementAndGet);
      WriteCommandAction.runWriteCommandAction(getProject(), () -> {
        myFixture.getEditor().getDocument().setText("function added() {}\nvar newName : String");
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
      });
      assertTrue("Structure must be notified after a committed edit", updates.get() > 0);
      assertEquals(List.of("added()", "newName"), names(model.getRoot()));
      WriteCommandAction.runWriteCommandAction(getProject(), () -> {
        myFixture.getEditor().getDocument().setText("");
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
      });
      assertTrue(names(model.getRoot()).isEmpty());
    } finally { Disposer.dispose(model); }
  }
}
