/*
 * Copyright 2013-2026 consulo.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package consulo.java.execution.impl.fragment;

import com.intellij.java.execution.CommonJavaRunConfigurationParameters;
import com.intellij.java.execution.ShortenCommandLine;
import com.intellij.java.execution.impl.ConfigurationWithCommandLineShortener;
import com.intellij.java.execution.impl.ui.UnifiedShortenCommandLineModeCombo;
import consulo.annotation.component.ExtensionImpl;
import consulo.disposer.Disposable;
import consulo.execution.configuration.LegacyRunConfigurationConverter;
import consulo.execution.configuration.ModuleBasedConfiguration;
import consulo.execution.configuration.RunConfiguration;
import consulo.execution.configuration.fragment.RunConfigurationFragment;
import consulo.execution.configuration.fragment.RunConfigurationFragmentEditor;
import consulo.execution.configuration.fragment.RunConfigurationFragmentLayout;
import consulo.execution.configuration.fragment.StandardRunConfigurationFragmentOrder;
import consulo.java.execution.configurations.fragment.ShortenCommandLineFragmentState;
import consulo.java.execution.localize.JavaExecutionLocalize;
import consulo.localize.LocalizeValue;
import consulo.module.Module;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import org.jspecify.annotations.Nullable;

/**
 * How the command line of a Java run configuration is shortened; the modes offered depend on its JRE.
 *
 * @author VISTALL
 * @since 2026-10-24
 */
@ExtensionImpl
public class ShortenCommandLineRunConfigurationFragment implements RunConfigurationFragment<RunConfiguration> {
    @Override
    public String getId() {
        return "shorten.command.line";
    }

    @Override
    public LocalizeValue getName() {
        return JavaExecutionLocalize.runConfigurationFragmentShortenCommandLine();
    }

    @Override
    public LocalizeValue getGroup() {
        return JavaExecutionLocalize.runConfigurationFragmentGroupJava();
    }

    @Override
    public int getOrder() {
        return StandardRunConfigurationFragmentOrder.MODULE + 10;
    }

    @Override
    public boolean isApplicable(RunConfiguration configuration) {
        return configuration instanceof ConfigurationWithCommandLineShortener;
    }

    @Override
    public boolean isVisibleByDefault(RunConfiguration target) {
        return ((ConfigurationWithCommandLineShortener) target).getShortenCommandLine() != null;
    }

    @Override
    public void applyHidden(RunConfiguration target) {
        ((ConfigurationWithCommandLineShortener) target).setShortenCommandLine(null);
    }

    @Override
    public Class<ShortenCommandLineFragmentState> getStateClass() {
        return ShortenCommandLineFragmentState.class;
    }

    @Override
    public void convertLegacyState(RunConfiguration configuration, LegacyRunConfigurationConverter converter) {
        ShortenCommandLine mode = ShortenCommandLine.readShortenClasspathMethod(converter.getElement());
        if (mode != null) {
            converter.addFragment(new ShortenCommandLineFragmentState(mode));
        }
    }

    @RequiredUIAccess
    @Override
    public RunConfigurationFragmentEditor<RunConfiguration> createEditor(Project project, Disposable parent) {
        UnifiedShortenCommandLineModeCombo combo = new UnifiedShortenCommandLineModeCombo(project);

        return new RunConfigurationFragmentEditor<>() {
            @RequiredUIAccess
            @Override
            public void addRows(RunConfigurationFragmentLayout layout) {
                layout.addLabeled(JavaExecutionLocalize.applicationConfigurationShortenCommandLineLabel(), combo.getComponent());
            }

            @RequiredUIAccess
            @Override
            public void reset(RunConfiguration target) {
                combo.refresh(getJdkRoot(target));
                combo.setSelectedItem(((ConfigurationWithCommandLineShortener) target).getShortenCommandLine());
            }

            @RequiredUIAccess
            @Override
            public void apply(RunConfiguration target) {
                ((ConfigurationWithCommandLineShortener) target).setShortenCommandLine(combo.getSelectedItem());
            }
        };
    }

    private static @Nullable String getJdkRoot(RunConfiguration configuration) {
        Module module = configuration instanceof ModuleBasedConfiguration<?> moduleBased
            ? moduleBased.getConfigurationModule().getModule()
            : null;
        if (configuration instanceof CommonJavaRunConfigurationParameters parameters) {
            return UnifiedShortenCommandLineModeCombo.getJdkRoot(
                parameters.isAlternativeJrePathEnabled(),
                parameters.getAlternativeJrePath(),
                module
            );
        }
        return UnifiedShortenCommandLineModeCombo.getJdkRoot(false, null, module);
    }
}
