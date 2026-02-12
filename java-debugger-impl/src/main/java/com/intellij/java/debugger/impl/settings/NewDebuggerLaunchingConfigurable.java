package com.intellij.java.debugger.impl.settings;

import com.intellij.java.debugger.localize.JavaDebuggerLocalize;
import consulo.configurable.Configurable;
import consulo.configurable.SimpleConfigurableByProperties;
import consulo.disposer.Disposable;
import consulo.java.language.localize.JavaLanguageLocalize;
import consulo.localize.LocalizeValue;
import consulo.ui.CheckBox;
import consulo.ui.ComboBox;
import consulo.ui.Component;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.layout.VerticalLayout;
import consulo.ui.util.LabeledBuilder;
import jakarta.annotation.Nonnull;

import java.util.function.Supplier;

/**
 * @author VISTALL
 * @since 2024-06-04
 */
public class NewDebuggerLaunchingConfigurable extends SimpleConfigurableByProperties implements Configurable {
    private final Supplier<DebuggerSettings> mySettingsSupplier;

    public NewDebuggerLaunchingConfigurable(Supplier<DebuggerSettings> settingsSupplier) {
        mySettingsSupplier = settingsSupplier;
    }

    @Nonnull
    @Override
    public String getId() {
        return "reference.idesettings.debugger.launching";
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return JavaLanguageLocalize.javaLanguageDisplayName();
    }

    @Nonnull
    @Override
    @RequiredUIAccess
    protected Component createLayout(@Nonnull PropertyBuilder propertyBuilder, @Nonnull Disposable disposable) {
        DebuggerSettings settings = mySettingsSupplier.get();

        VerticalLayout layout = VerticalLayout.create();
        ComboBox<Integer> transportBox = ComboBox.create(DebuggerSettings.SOCKET_TRANSPORT, DebuggerSettings.SHMEM_TRANSPORT);
        transportBox.setTextRenderer(value -> {
            if (value == null) {
                return LocalizeValue.empty();
            }

            return switch (value) {
                case DebuggerSettings.SOCKET_TRANSPORT -> JavaDebuggerLocalize.transportNameSocket();
                case DebuggerSettings.SHMEM_TRANSPORT -> JavaDebuggerLocalize.transportNameSharedMemory();
                default -> LocalizeValue.of(String.valueOf(value));
            };
        });
        layout.add(LabeledBuilder.sided(JavaDebuggerLocalize.labelDebuggerLaunchingConfigurableDebuggerTransport(), transportBox));
        propertyBuilder.add(transportBox, () -> settings.DEBUGGER_TRANSPORT, it -> settings.DEBUGGER_TRANSPORT = it);

        CheckBox forceClassicVMBox = CheckBox.create(JavaDebuggerLocalize.labelDebuggerLaunchingConfigurableForceClassicVm());
        layout.add(forceClassicVMBox);
        propertyBuilder.add(forceClassicVMBox, () -> settings.FORCE_CLASSIC_VM, it -> settings.FORCE_CLASSIC_VM = it);

        CheckBox disableJitBox = CheckBox.create(JavaDebuggerLocalize.labelDebuggerLaunchingConfigurableDisableJit());
        layout.add(disableJitBox);
        propertyBuilder.add(disableJitBox, () -> settings.DISABLE_JIT, it -> settings.DISABLE_JIT = it);

        return layout;
    }
}
