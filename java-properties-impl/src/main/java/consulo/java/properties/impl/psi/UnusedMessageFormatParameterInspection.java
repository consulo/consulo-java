/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package consulo.java.properties.impl.psi;

import com.intellij.java.analysis.impl.codeInspection.ex.BaseLocalInspectionTool;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.PropertiesLanguage;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.ide.impl.idea.codeInsight.daemon.impl.quickfix.RenameElementFix;
import consulo.language.Language;
import consulo.language.ast.ASTNode;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.scheme.InspectionManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.localize.LocalizeValue;
import consulo.properties.localize.PropertiesLocalize;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @since anna
 * @since 2005-09-07
 */
@ExtensionImpl
public class UnusedMessageFormatParameterInspection extends BaseLocalInspectionTool {
    public static final String REGEXP = "regexp";

    @Nullable
    @Override
    public Language getLanguage() {
        return PropertiesLanguage.INSTANCE;
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return PropertiesLocalize.unusedMessageFormatParameterDisplayName();
    }

    @Nonnull
    @Override
    public String getShortName() {
        return "UnusedMessageFormatParameter";
    }

    @Nullable
    @Override
    @RequiredReadAction
    public ProblemDescriptor[] checkFile(@Nonnull PsiFile file, @Nonnull InspectionManager manager, boolean isOnTheFly, Object state) {
        if (!(file instanceof PropertiesFile)) {
            return null;
        }
        PropertiesFile propertiesFile = (PropertiesFile) file;
        List<IProperty> properties = propertiesFile.getProperties();
        List<ProblemDescriptor> problemDescriptors = new ArrayList<>();
        for (IProperty property : properties) {
            String name = property.getName();
            if (name != null) {
                if (name.startsWith("log4j")) {
                    continue;
                }
                if (name.startsWith(REGEXP + ".") || name.endsWith("." + REGEXP)) {
                    continue;
                }
            }
            String value = property.getValue();
            Set<Integer> parameters = new HashSet<>();
            if (value != null) {
                int index = value.indexOf('{');
                while (index != -1) {
                    value = value.substring(index + 1);
                    int comma = value.indexOf(',');
                    int brace = value.indexOf('}');
                    if (brace == -1) {
                        break; //misformatted string
                    }
                    if (comma == -1) {
                        index = brace;
                    }
                    else {
                        index = Math.min(comma, brace);
                    }
                    try {
                        parameters.add(Integer.valueOf(value.substring(0, index)));
                    }
                    catch (NumberFormatException e) {
                        break;
                    }
                    index = value.indexOf('{');
                }
                for (Integer integer : parameters) {
                    for (int i = 0; i < integer; i++) {
                        if (!parameters.contains(i)) {
                            ASTNode[] nodes = property.getPsiElement().getNode().getChildren(null);
                            PsiElement valElement = nodes.length < 3 ? property.getPsiElement() : nodes[2].getPsi();
                            String propertyKey = property.getKey();
                            LocalQuickFix fix = isOnTheFly
                                ? new RenameElementFix(((Property) property), propertyKey == null ? REGEXP : propertyKey + "." + REGEXP)
                                : null;
                            problemDescriptors.add(
                                manager.newProblemDescriptor(PropertiesLocalize.unusedMessageFormatParameterProblemDescriptor(
                                        integer.toString(),
                                        Integer.toString(i)
                                    ))
                                    .range(valElement)
                                    .onTheFly(isOnTheFly)
                                    .withOptionalFix(fix)
                                    .create()
                            );
                            break;
                        }
                    }
                }
            }
        }
        return problemDescriptors.isEmpty() ? null : problemDescriptors.toArray(new ProblemDescriptor[problemDescriptors.size()]);
    }
}
