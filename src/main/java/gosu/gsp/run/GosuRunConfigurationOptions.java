package gosu.gsp.run;

import com.intellij.execution.configurations.LocatableRunConfigurationOptions;
import com.intellij.util.xmlb.annotations.OptionTag;

/** Persisted Run Configuration state (new serialization). */
public final class GosuRunConfigurationOptions extends LocatableRunConfigurationOptions {
  @OptionTag("SCRIPT_PATH")
  public String scriptPath = "";

  @OptionTag("GOSU_EXE")
  public String gosuExecutable = "";

  @OptionTag("PROGRAM_ARGS")
  public String programArgs = "";

  @OptionTag("WORK_DIR")
  public String workingDir = "";
}
