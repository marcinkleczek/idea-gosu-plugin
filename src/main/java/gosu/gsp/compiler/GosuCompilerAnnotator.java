package gosu.gsp.compiler;

import com.intellij.lang.annotation.*;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import gosu.gsp.psi.GosuFile;
import gosu.gsp.resolve.GosuSource;
import gosu.gsp.settings.*;
import org.jetbrains.annotations.NotNull;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/** Snapshot on the read thread, SDK parsing off the UI thread, annotations only for the same revision. */
public final class GosuCompilerAnnotator extends ExternalAnnotator<GosuCompilerAnnotator.Request, GosuCompilerAnnotator.Response> {
  public record Request(Project project, GosuCompilerRunner.Input input, long stamp) {}
  public record Response(Request request, GosuCompilerRunner.Result result) {}

  @Service(Service.Level.PROJECT)
  public static final class Queue {
    final ReentrantLock lock = new ReentrantLock();
  }

  @Override public Request collectInformation(@NotNull PsiFile file) {
    if (!(file instanceof GosuFile) || file.getVirtualFile() == null || DumbService.isDumb(file.getProject())
        || !file.getVirtualFile().isInLocalFileSystem()) return null;
    var settings = GosuProjectSettings.getInstance(file.getProject());
    if (!settings.isCompilerChecks() || settings.getSdkHome().isBlank()) return null;
    String javaHome = settings.getJdkHome();
    var roots = ProjectRootManager.getInstance(file.getProject());
    if (javaHome.isBlank() && roots.getProjectSdk() != null && roots.getProjectSdk().getHomePath() != null) javaHome = roots.getProjectSdk().getHomePath();
    List<String> classpath = new ArrayList<>(GosuClasspath.entries(settings.getClasspath(), file.getProject().getBasePath()));
    for (var root : roots.getContentSourceRoots()) if (!classpath.contains(root.getPath())) classpath.add(root.getPath());
    if (file.getVirtualFile().getParent() != null) classpath.add(file.getVirtualFile().getParent().getPath());
    var input = new GosuCompilerRunner.Input(file.getText(), file.getName(), GosuSource.packageName(file), settings.getSdkHome(), javaHome, List.copyOf(classpath));
    return new Request(file.getProject(), input, file.getModificationStamp());
  }

  @Override public Response doAnnotate(Request request) {
    if (request == null || request.project().isDisposed()) return null;
    var queue = request.project().getService(Queue.class);
    boolean acquired = false;
    try {
      while (!(acquired = queue.lock.tryLock(100, TimeUnit.MILLISECONDS))) ProgressManager.checkCanceled();
      ProgressManager.checkCanceled();
      if (request.project().isDisposed()) return null;
      return new Response(request, GosuCompilerRunner.check(request.input()));
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      return null;
    } finally { if (acquired) queue.lock.unlock(); }
  }

  @Override public void apply(@NotNull PsiFile file, Response response, @NotNull AnnotationHolder holder) {
    if (response == null || response.request().stamp() != file.getModificationStamp()) return;
    var settings = GosuProjectSettings.getInstance(file.getProject());
    if (!settings.isCompilerChecks()) return;
    Request current = collectInformation(file);
    if (current == null || !current.input().equals(response.request().input())) return;
    var document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
    if (document == null) return;
    if (response.result().failure() != null) {
      holder.newAnnotation(HighlightSeverity.WEAK_WARNING, response.result().failure()).range(new TextRange(0, Math.min(1, file.getTextLength()))).create();
      return;
    }
    for (var issue : response.result().diagnostics()) {
      if (issue.line() >= document.getLineCount()) continue;
      int start = Math.min(document.getLineStartOffset(issue.line()) + issue.column(), document.getLineEndOffset(issue.line()));
      int end = Math.min(start + 1, document.getTextLength());
      while (end < document.getLineEndOffset(issue.line()) && Character.isJavaIdentifierPart(document.getCharsSequence().charAt(end))) end++;
      holder.newAnnotation(issue.severity().equals("WARNING") ? HighlightSeverity.WARNING : HighlightSeverity.ERROR, issue.message())
          .range(new TextRange(start, end)).tooltip(GosuDiagnosticExplanation.tooltip(issue.message())).create();
    }
  }
}
