package gosu.gsp.settings;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

@Service(Service.Level.PROJECT)
@State(name = "GosuProjectSettings", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public final class GosuProjectSettings implements PersistentStateComponent<GosuProjectSettings.State> {
  public static final class State {
    public String sdkHome = "";
    public String jdkHome = "";
    public String classpath = "";
    public boolean compilerChecks = true;
  }

  private State state = new State();

  public static GosuProjectSettings getInstance(Project project) {
    return project.getService(GosuProjectSettings.class);
  }

  @Override
  public @NotNull State getState() { return state; }

  @Override
  public void loadState(@NotNull State state) { this.state = state; }

  public String getSdkHome() { return state.sdkHome; }

  public String getJdkHome() { return state.jdkHome; }
  public void setJdkHome(String home) { state.jdkHome = home; }
  public boolean isCompilerChecks() { return state.compilerChecks; }
  public void setCompilerChecks(boolean enabled) { state.compilerChecks = enabled; }
  public String getClasspath() { return state.classpath; }
  public void setClasspath(String classpath) { state.classpath = classpath; }

  public void setSdkHome(String sdkHome) { state.sdkHome = sdkHome; }
}
