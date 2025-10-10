/*
 * Copyright 2011 Bas Leijdekkers
 *
 * Licensed under the Apache License, Version 2.0 (the "License"));
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
package com.siyeh.ig;

import consulo.language.editor.inspection.localize.InspectionLocalize;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

import java.util.HashMap;
import java.util.Map;

public class GroupDisplayNameUtil {
  private static final Map<String, LocalizeValue> packageGroupDisplayNameMap = new HashMap<>();

  static {
    packageGroupDisplayNameMap.put("abstraction", InspectionLocalize.groupNamesAbstractionIssues());
    packageGroupDisplayNameMap.put("assignment", InspectionLocalize.groupNamesAssignmentIssues());
    packageGroupDisplayNameMap.put("bitwise", InspectionLocalize.groupNamesBitwiseOperationIssues());
    packageGroupDisplayNameMap.put("bugs", InspectionLocalize.groupNamesProbableBugs());
    packageGroupDisplayNameMap.put("classlayout", InspectionLocalize.groupNamesClassStructure());
    packageGroupDisplayNameMap.put("classmetrics", InspectionLocalize.groupNamesClassMetrics());
    packageGroupDisplayNameMap.put("cloneable", InspectionLocalize.groupNamesCloningIssues());
    packageGroupDisplayNameMap.put("controlflow", InspectionLocalize.groupNamesControlFlowIssues());
    packageGroupDisplayNameMap.put("dataflow", InspectionLocalize.groupNamesDataFlowIssues());
    packageGroupDisplayNameMap.put("dependency", InspectionLocalize.groupNamesDependencyIssues());
    packageGroupDisplayNameMap.put("encapsulation", InspectionLocalize.groupNamesEncapsulationIssues());
    packageGroupDisplayNameMap.put("errorhandling", InspectionLocalize.groupNamesErrorHandling());
    packageGroupDisplayNameMap.put("finalization", InspectionLocalize.groupNamesFinalizationIssues());
    packageGroupDisplayNameMap.put("imports", InspectionLocalize.groupNamesImports());
    packageGroupDisplayNameMap.put("inheritance", InspectionLocalize.groupNamesInheritanceIssues());
    packageGroupDisplayNameMap.put("initialization", InspectionLocalize.groupNamesInitializationIssues());
    packageGroupDisplayNameMap.put("internationalization", InspectionLocalize.groupNamesInternationalizationIssues());
    packageGroupDisplayNameMap.put("j2me", InspectionLocalize.groupNamesJ2meIssues());
    packageGroupDisplayNameMap.put("javabeans", InspectionLocalize.groupNamesJavabeansIssues());
    packageGroupDisplayNameMap.put("javadoc", InspectionLocalize.groupNamesJavadocIssues());
    packageGroupDisplayNameMap.put("jdk", InspectionLocalize.groupNamesJavaLanguageLevelIssues());
    packageGroupDisplayNameMap.put("migration", InspectionLocalize.groupNamesLanguageLevelSpecificIssuesAndMigrationAids());
    packageGroupDisplayNameMap.put("junit", InspectionLocalize.groupNamesJunitIssues());
    packageGroupDisplayNameMap.put("logging", InspectionLocalize.groupNamesLoggingIssues());
    packageGroupDisplayNameMap.put("maturity", InspectionLocalize.groupNamesCodeMaturityIssues());
    packageGroupDisplayNameMap.put("memory", InspectionLocalize.groupNamesMemoryIssues());
    packageGroupDisplayNameMap.put("methodmetrics", InspectionLocalize.groupNamesMethodMetrics());
    packageGroupDisplayNameMap.put("modularization", InspectionLocalize.groupNamesModularizationIssues());
    packageGroupDisplayNameMap.put("naming", InspectionLocalize.groupNamesNamingConventions());
    packageGroupDisplayNameMap.put("numeric", InspectionLocalize.groupNamesNumericIssues());
    packageGroupDisplayNameMap.put("packaging", InspectionLocalize.groupNamesPackagingIssues());
    packageGroupDisplayNameMap.put("performance", InspectionLocalize.groupNamesPerformanceIssues());
    packageGroupDisplayNameMap.put("portability", InspectionLocalize.groupNamesPortabilityIssues());
    packageGroupDisplayNameMap.put("redundancy", InspectionLocalize.groupNamesDeclarationRedundancy());
    packageGroupDisplayNameMap.put("resources", InspectionLocalize.groupNamesResourceManagementIssues());
    packageGroupDisplayNameMap.put("security", InspectionLocalize.groupNamesSecurityIssues());
    packageGroupDisplayNameMap.put("serialization", InspectionLocalize.groupNamesSerializationIssues());
    packageGroupDisplayNameMap.put("style", InspectionLocalize.groupNamesCodeStyleIssues());
    packageGroupDisplayNameMap.put("threading", InspectionLocalize.groupNamesThreadingIssues());
    packageGroupDisplayNameMap.put("visibility", InspectionLocalize.groupNamesVisibilityIssues());
  }

  private GroupDisplayNameUtil() {
  }

  @Nonnull
  public static LocalizeValue getGroupDisplayName(Class<?> aClass) {
    final Package thisPackage = aClass.getPackage();
    assert thisPackage != null : "need package to determine group display name";
    final String name = thisPackage.getName();
    assert name != null :
      "inspection has default package, group display name cannot be determined";
    final int index = name.lastIndexOf('.');
    final String key = name.substring(index + 1);
    final LocalizeValue groupDisplayName = packageGroupDisplayNameMap.get(key);
    assert groupDisplayName != null : "No display name found for " + key;
    return groupDisplayName;
  }
}
