package gosu.gsp.testing;
import com.intellij.execution.configurations.LocatableRunConfigurationOptions;
import com.intellij.util.xmlb.annotations.OptionTag;
public final class GosuTestOptions extends LocatableRunConfigurationOptions {
  @OptionTag("CLASS") public String className = "";
  @OptionTag("METHOD") public String method = "";
  @OptionTag("SOURCE_ROOT") public String sourceRoot = "";
}
