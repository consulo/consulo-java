package consulo.java.compiler.impl.javaCompiler.ui;

import com.intellij.java.compiler.impl.javaCompiler.javac.JavacCompilerConfiguration;
import com.intellij.java.compiler.impl.javaCompiler.javac.JpsJavaCompilerOptions;
import com.intellij.openapi.options.Configurable;
import com.intellij.util.execution.ParametersListUtil;
import consulo.disposer.Disposable;
import consulo.localize.LocalizeValue;
import consulo.options.SimpleConfigurableByProperties;
import consulo.ui.CheckBox;
import consulo.ui.Component;
import consulo.ui.IntBox;
import consulo.ui.TextBoxWithExpandAction;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.layout.VerticalLayout;
import consulo.ui.util.LabeledBuilder;
import jakarta.inject.Provider;

import javax.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 01/12/2021
 */
public class JavacConfigurable extends SimpleConfigurableByProperties implements Configurable
{
	private final Provider<JavacCompilerConfiguration> myJavacCompilerConfigurationProvider;

	public JavacConfigurable(Provider<JavacCompilerConfiguration> javacCompilerConfigurationProvider)
	{
		myJavacCompilerConfigurationProvider = javacCompilerConfigurationProvider;
	}

	@RequiredUIAccess
	@Nonnull
	@Override
	protected Component createLayout(@Nonnull PropertyBuilder propertyBuilder, @Nonnull Disposable disposable)
	{
		JavacCompilerConfiguration configuration = myJavacCompilerConfigurationProvider.get();

		JpsJavaCompilerOptions state = configuration.getState();

		VerticalLayout verticalLayout = VerticalLayout.create();

		CheckBox generateDebugInfo = CheckBox.create(LocalizeValue.localizeTODO("Generate debugging info"));
		verticalLayout.add(generateDebugInfo);
		propertyBuilder.add(generateDebugInfo, () -> state.DEBUGGING_INFO, (v) -> state.DEBUGGING_INFO = v);

		CheckBox reportDeprecated = CheckBox.create(LocalizeValue.localizeTODO("Report use of deprecated features"));
		verticalLayout.add(reportDeprecated);
		propertyBuilder.add(reportDeprecated, () -> state.DEPRECATION, (v) -> state.DEPRECATION = v);

		CheckBox generateNoWarning = CheckBox.create(LocalizeValue.localizeTODO("Generate no warnings"));
		verticalLayout.add(generateNoWarning);
		propertyBuilder.add(generateNoWarning, () -> state.GENERATE_NO_WARNINGS, (v) -> state.GENERATE_NO_WARNINGS = v);

		TextBoxWithExpandAction additionalArguments = TextBoxWithExpandAction.create(null, "Edit Arguments", ParametersListUtil.DEFAULT_LINE_PARSER, ParametersListUtil.DEFAULT_LINE_JOINER);
		propertyBuilder.add(additionalArguments, () -> state.ADDITIONAL_OPTIONS_STRING, (v) -> state.ADDITIONAL_OPTIONS_STRING = v);

		verticalLayout.add(LabeledBuilder.filled(LocalizeValue.localizeTODO("Additional command line parameters:"), additionalArguments));

		IntBox memoryTextBox = IntBox.create();
		verticalLayout.add(LabeledBuilder.sided(LocalizeValue.localizeTODO("Maximum heap size (MB):"), memoryTextBox));
		propertyBuilder.add(memoryTextBox, () -> state.MAXIMUM_HEAP_SIZE, (v) -> state.MAXIMUM_HEAP_SIZE = v);
		return verticalLayout;
	}
}
