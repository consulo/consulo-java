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

/*
 * Class StaticDescriptorImpl
 * @author Jeka
 */
package com.intellij.java.debugger.impl.ui.impl.watch;

import com.intellij.java.debugger.engine.evaluation.EvaluateException;
import com.intellij.java.debugger.impl.engine.DebuggerManagerThreadImpl;
import com.intellij.java.debugger.impl.engine.evaluation.EvaluationContextImpl;
import com.intellij.java.debugger.impl.settings.NodeRendererSettings;
import com.intellij.java.debugger.impl.ui.tree.StaticDescriptor;
import com.intellij.java.debugger.impl.ui.tree.render.ClassRenderer;
import com.intellij.java.debugger.impl.ui.tree.render.DescriptorLabelListener;
import consulo.internal.com.sun.jdi.Field;
import consulo.internal.com.sun.jdi.ReferenceType;
import consulo.localize.LocalizeValue;

public class StaticDescriptorImpl extends NodeDescriptorImpl implements StaticDescriptor {

    private final ReferenceType myType;
    private final boolean myHasStaticFields;

    public StaticDescriptorImpl(ReferenceType refType) {
        myType = refType;

        boolean hasStaticFields = false;
        for (Field field : myType.allFields()) {
            if (field.isStatic()) {
                hasStaticFields = true;
                break;
            }
        }
        myHasStaticFields = hasStaticFields;
    }

    @Override
    public ReferenceType getType() {
        return myType;
    }

    @Override
    public String getName() {
        //noinspection HardCodedStringLiteral
        return "static";
    }

    @Override
    public boolean isExpandable() {
        return myHasStaticFields;
    }

    @Override
    public void setContext(EvaluationContextImpl context) {
    }

    @Override
    protected LocalizeValue calcRepresentation(EvaluationContextImpl context, DescriptorLabelListener descriptorLabelListener) throws EvaluateException {
        DebuggerManagerThreadImpl.assertIsManagerThread();
        final ClassRenderer classRenderer = NodeRendererSettings.getInstance().getClassRenderer();
        return LocalizeValue.of(getName() + " = " + classRenderer.renderTypeName(myType.name()));
    }
}