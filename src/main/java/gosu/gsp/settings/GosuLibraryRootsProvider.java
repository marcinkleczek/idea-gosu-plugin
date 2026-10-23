package gosu.gsp.settings;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider;
import com.intellij.openapi.roots.SyntheticLibrary;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.*;

/** Makes Gosu SDK and script classpath libraries available to IntelliJ's Java index. */
public final class GosuLibraryRootsProvider extends AdditionalLibraryRootsProvider {
  @Override
  public Collection<SyntheticLibrary> getAdditionalProjectLibraries(Project project) {
    GosuProjectSettings settings = GosuProjectSettings.getInstance(project);
    List<String> paths = new ArrayList<>(GosuClasspath.entries(settings.getClasspath(), project.getBasePath()));
    paths.addAll(GosuClasspath.sdkJars(settings.getSdkHome()));
    List<VirtualFile> sources = new ArrayList<>(), binaries = new ArrayList<>();
    for (String path : paths) {
      VirtualFile file = LocalFileSystem.getInstance().findFileByPath(path);
      if (file == null) continue;
      if (file.isDirectory()) {
        sources.add(file);
        binaries.add(file);
      } else {
        VirtualFile root = JarFileSystem.getInstance().getJarRootForLocalFile(file);
        if (root != null) { binaries.add(root); sources.add(root); }
      }
    }
    return binaries.isEmpty() ? List.of()
        : List.of(SyntheticLibrary.newImmutableLibrary(sources, binaries, Set.of(), null));
  }

  @Override
  public Collection<VirtualFile> getRootsToWatch(Project project) {
    List<VirtualFile> roots = new ArrayList<>();
    for (SyntheticLibrary library : getAdditionalProjectLibraries(project)) roots.addAll(library.getAllRoots());
    return roots;
  }
}
