package gosu.gsp.run;

import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.OpenFileHyperlinkInfo;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Collection;

/** Per-console state: a compiler header identifies the file of subsequent diagnostics. */
public final class GosuConsoleFilter implements Filter {
  private final Project project;
  private final String script;
  private final String workingDirectory;
  private String diagnosticFile;

  public GosuConsoleFilter(Project project, String script, String workingDirectory) {
    this.project = project;
    this.script = script;
    this.workingDirectory = workingDirectory;
  }

  @Override
  public Result applyFilter(@NotNull String line, int entireLength) {
    String header = GosuConsoleLocation.diagnosticFile(line);
    if (header != null) diagnosticFile = header;
    GosuConsoleLocation.Location location = GosuConsoleLocation.parse(line, diagnosticFile);
    if (location == null || project.isDisposed()) return null;
    VirtualFile file = ReadAction.compute(() -> findFile(location.file()));
    if (file == null) return null;
    int base = entireLength - line.length();
    return new Result(base + location.start(), base + location.end(),
        new OpenFileHyperlinkInfo(project, file, location.line(), location.column()));
  }

  private VirtualFile findFile(String path) {
    try {
      Path candidate = Path.of(path);
      if (candidate.isAbsolute()) return LocalFileSystem.getInstance().findFileByNioFile(candidate.normalize());
      Path scriptPath = Path.of(script).toAbsolutePath();
      if (candidate.getNameCount() == 1 && candidate.getFileName().equals(scriptPath.getFileName())) {
        VirtualFile scriptFile = LocalFileSystem.getInstance().findFileByNioFile(scriptPath);
        if (scriptFile != null) return scriptFile;
      }
      Path base = workingDirectory == null || workingDirectory.isBlank() ? scriptPath.getParent() : Path.of(workingDirectory);
      VirtualFile relative = base == null ? null : LocalFileSystem.getInstance().findFileByNioFile(base.resolve(candidate).normalize());
      if (relative != null) return relative;
      if (DumbService.isDumb(project)) return null;
      Collection<VirtualFile> matches = FilenameIndex.getVirtualFilesByName(candidate.getFileName().toString(), GlobalSearchScope.allScope(project));
      // Do not jump to an arbitrary file when multiple packages contain the same filename.
      if (matches.size() == 1) return matches.iterator().next();
      return null;
    } catch (InvalidPathException e) { return null; }
  }
}
