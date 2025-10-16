package com.intellij.java.analysis.impl.codeInspection;

import consulo.configurable.ConfigurableBuilder;
import consulo.configurable.UnnamedConfigurable;
import consulo.java.analysis.impl.localize.JavaInspectionsLocalize;
import consulo.language.editor.inspection.InspectionToolState;
import consulo.util.xml.serializer.XmlSerializerUtil;
import jakarta.annotation.Nullable;

/**
 * @author VISTALL
 * @since 2025-10-16
 */
public class EnhancedSwitchMigrationInspectionState implements InspectionToolState<EnhancedSwitchMigrationInspectionState> {
    public boolean myWarnOnlyOnExpressionConversion = true;
    public int myMaxNumberStatementsForBranch = 2;

    @Nullable
    @Override
    public EnhancedSwitchMigrationInspectionState getState() {
        return this;
    }

    @Nullable
    @Override
    public UnnamedConfigurable createConfigurable() {
        return ConfigurableBuilder.newBuilder()
            .checkBox(
                JavaInspectionsLocalize.inspectionSwitchExpressionMigrationWarnOnlyOnExpression(),
                () -> myWarnOnlyOnExpressionConversion,
                b -> myWarnOnlyOnExpressionConversion =  b
            )
            // TODO support JavaInspectionsLocalize.inspectionSwitchExpressionMigrationExpressionMaxStatements()
            .buildUnnamed();
    }

    @Override
    public void loadState(EnhancedSwitchMigrationInspectionState state) {
        XmlSerializerUtil.copyBean(state, this);
    }
}
