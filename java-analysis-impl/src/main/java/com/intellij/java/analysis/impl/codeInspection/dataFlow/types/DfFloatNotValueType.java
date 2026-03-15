// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.types;

import com.intellij.java.language.psi.PsiKeyword;
import consulo.java.analysis.localize.JavaAnalysisLocalize;
import consulo.util.lang.StringUtil;

import java.util.HashSet;
import java.util.Set;

class DfFloatNotValueType extends DfAntiConstantType<Float> implements DfFloatType {
    DfFloatNotValueType(Set<Float> values) {
        super(values);
    }

    @Override
    public boolean isSuperType(DfType other) {
        if (other == DfTypes.BOTTOM || other.equals(this)) {
            return true;
        }
        if (other instanceof DfFloatNotValueType floatNotValueType) {
            return floatNotValueType.myNotValues.containsAll(myNotValues);
        }
        if (other instanceof DfFloatConstantType floatConstantType) {
            return !myNotValues.contains(floatConstantType.getValue());
        }
        return false;
    }

    @Override
    public DfType join(DfType other) {
        if (isSuperType(other)) {
            return this;
        }
        if (other.isSuperType(this)) {
            return other;
        }
        if (other instanceof DfFloatNotValueType floatNotValueType) {
            Set<Float> notValues = new HashSet<>(myNotValues);
            notValues.retainAll(floatNotValueType.myNotValues);
            return notValues.isEmpty() ? DfTypes.FLOAT : new DfFloatNotValueType(notValues);
        }
        return DfTypes.TOP;
    }

    @Override
    public DfType meet(DfType other) {
        if (isSuperType(other)) {
            return other;
        }
        if (other.isSuperType(this)) {
            return this;
        }
        if (other instanceof DfFloatConstantType floatConstantType && myNotValues.contains(floatConstantType.getValue())) {
            return DfTypes.BOTTOM;
        }
        if (other instanceof DfFloatNotValueType floatNotValueType) {
            Set<Float> notValues = new HashSet<>(myNotValues);
            notValues.addAll(floatNotValueType.myNotValues);
            return new DfFloatNotValueType(notValues);
        }
        return DfTypes.BOTTOM;
    }

    @Override
    public String toString() {
        return JavaAnalysisLocalize.typePresentationExceptValues(PsiKeyword.FLOAT, StringUtil.join(myNotValues, ", ")).get();
    }
}
