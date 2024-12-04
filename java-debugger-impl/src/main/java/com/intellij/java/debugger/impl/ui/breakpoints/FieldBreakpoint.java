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

package com.intellij.java.debugger.impl.ui.breakpoints;

import com.intellij.java.debugger.DebuggerBundle;
import com.intellij.java.debugger.SourcePosition;
import com.intellij.java.debugger.engine.evaluation.EvaluateException;
import com.intellij.java.debugger.engine.jdi.VirtualMachineProxy;
import com.intellij.java.debugger.impl.DebuggerManagerEx;
import com.intellij.java.debugger.impl.PositionUtil;
import com.intellij.java.debugger.impl.breakpoints.properties.JavaFieldBreakpointProperties;
import com.intellij.java.debugger.impl.engine.DebugProcessImpl;
import com.intellij.java.debugger.impl.engine.SuspendContextImpl;
import com.intellij.java.debugger.impl.engine.requests.RequestManagerImpl;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiModifier;
import consulo.application.ApplicationManager;
import consulo.application.util.function.Processor;
import consulo.document.Document;
import consulo.execution.debug.XDebuggerUtil;
import consulo.execution.debug.breakpoint.XBreakpoint;
import consulo.execution.debug.icon.ExecutionDebugIconGroup;
import consulo.internal.com.sun.jdi.*;
import consulo.internal.com.sun.jdi.event.AccessWatchpointEvent;
import consulo.internal.com.sun.jdi.event.LocatableEvent;
import consulo.internal.com.sun.jdi.event.ModificationWatchpointEvent;
import consulo.internal.com.sun.jdi.request.AccessWatchpointRequest;
import consulo.internal.com.sun.jdi.request.ModificationWatchpointRequest;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.image.Image;
import consulo.util.dataholder.Key;
import consulo.util.lang.CharArrayUtil;
import consulo.util.xml.serializer.InvalidDataException;
import consulo.util.xml.serializer.JDOMExternalizerUtil;
import jakarta.annotation.Nonnull;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.util.function.Supplier;


/*
 * Class FieldBreakpoint
 * @author Jeka
 */
public class FieldBreakpoint extends BreakpointWithHighlighter<JavaFieldBreakpointProperties> {
    private static final Logger LOG = Logger.getInstance(FieldBreakpoint.class);
    private boolean myIsStatic;

    @NonNls
    public static final Key<FieldBreakpoint> CATEGORY = BreakpointCategory.lookup("field_breakpoints");

    protected FieldBreakpoint(Project project, XBreakpoint breakpoint) {
        super(project, breakpoint);
    }

    private FieldBreakpoint(Project project, @Nonnull String fieldName, XBreakpoint breakpoint) {
        super(project, breakpoint);
        setFieldName(fieldName);
    }

    public boolean isStatic() {
        return myIsStatic;
    }

    public String getFieldName() {
        return getProperties().myFieldName;
    }

    @Override
    protected Image getDisabledIcon(boolean isMuted) {
        final Breakpoint master = DebuggerManagerEx.getInstanceEx(myProject).getBreakpointManager().findMasterBreakpoint(this);
        if (isMuted) {
            return master == null ? ExecutionDebugIconGroup.breakpointBreakpointfieldmuteddisabled() : ExecutionDebugIconGroup.breakpointBreakpointfieldmuteddependent();
        }
        else {
            return master == null ? ExecutionDebugIconGroup.breakpointBreakpointfielddisabled() : ExecutionDebugIconGroup.breakpointBreakpointfielddependent();
        }
    }

    @Override
    protected Image getSetIcon(boolean isMuted) {
        return isMuted ? ExecutionDebugIconGroup.breakpointBreakpointfieldmuted() : ExecutionDebugIconGroup.breakpointBreakpointfield();
    }

    @Override
    protected Image getInvalidIcon(boolean isMuted) {
        return isMuted ? ExecutionDebugIconGroup.breakpointBreakpointfieldmuted() : ExecutionDebugIconGroup.breakpointBreakpointfield();
    }

    @Override
    protected Image getVerifiedIcon(boolean isMuted) {
        return isMuted ? ExecutionDebugIconGroup.breakpointBreakpointfieldmuted() : ExecutionDebugIconGroup.breakpointBreakpointfieldvalid();
    }

    @Override
    protected Image getVerifiedWarningsIcon(boolean isMuted) {
        return isMuted ? ExecutionDebugIconGroup.breakpointBreakpointfieldmuted() : ExecutionDebugIconGroup.breakpointBreakpointfield();
    }

    @Override
    public Key<FieldBreakpoint> getCategory() {
        return CATEGORY;
    }

    public PsiField getPsiField() {
        final SourcePosition sourcePosition = getSourcePosition();
        final PsiField field = ApplicationManager.getApplication().runReadAction((Supplier<PsiField>) () -> {
            final PsiClass psiClass = getPsiClassAt(sourcePosition);
            return psiClass != null ? psiClass.findFieldByName(getFieldName(), true) : null;
        });
        if (field != null) {
            return field;
        }
        return PositionUtil.getPsiElementAt(getProject(), PsiField.class, sourcePosition);
    }

    @Override
    protected void reload(PsiFile psiFile) {
        super.reload(psiFile);
        PsiField field = PositionUtil.getPsiElementAt(getProject(), PsiField.class, getSourcePosition());
        if (field != null) {
            setFieldName(field.getName());
            PsiClass psiClass = field.getContainingClass();
            if (psiClass != null) {
                getProperties().myClassName = psiClass.getQualifiedName();
            }
            myIsStatic = field.hasModifierProperty(PsiModifier.STATIC);
        }
        if (myIsStatic) {
            setInstanceFiltersEnabled(false);
        }
    }

    //@Override
    //public boolean moveTo(@NotNull SourcePosition position) {
    //  final PsiField field = PositionUtil.getPsiElementAt(getProject(), PsiField.class, position);
    //  return field != null && super.moveTo(SourcePosition.createFromElement(field));
    //}

    @Override
    protected ObjectReference getThisObject(SuspendContextImpl context, LocatableEvent event) throws EvaluateException {
        if (event instanceof ModificationWatchpointEvent) {
            ModificationWatchpointEvent modificationEvent = (ModificationWatchpointEvent) event;
            ObjectReference reference = modificationEvent.object();
            if (reference != null) {  // non-static
                return reference;
            }
        }
        else if (event instanceof AccessWatchpointEvent) {
            AccessWatchpointEvent accessEvent = (AccessWatchpointEvent) event;
            ObjectReference reference = accessEvent.object();
            if (reference != null) { // non-static
                return reference;
            }
        }

        return super.getThisObject(context, event);
    }

    @Override
    public void createRequestForPreparedClass(DebugProcessImpl debugProcess, ReferenceType refType) {
        VirtualMachineProxy vm = debugProcess.getVirtualMachineProxy();
        try {
            Field field = refType.fieldByName(getFieldName());
            if (field == null) {
                debugProcess.getRequestsManager().setInvalid(this, DebuggerBundle.message("error.invalid.breakpoint.missing.field.in.class",
                    getFieldName(), refType.name()));
                return;
            }
            RequestManagerImpl manager = debugProcess.getRequestsManager();
            if (isWatchModification() && vm.canWatchFieldModification()) {
                ModificationWatchpointRequest request = manager.createModificationWatchpointRequest(this, field);
                debugProcess.getRequestsManager().enableRequest(request);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Modification request added");
                }
            }
            if (isWatchAccess() && vm.canWatchFieldAccess()) {
                AccessWatchpointRequest request = manager.createAccessWatchpointRequest(this, field);
                debugProcess.getRequestsManager().enableRequest(request);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Access request added field = " + field.name() + "; refType = " + refType.name());
                }
            }
        }
        catch (ObjectCollectedException ex) {
            LOG.debug(ex);
        }
        catch (Exception ex) {
            LOG.debug(ex);
        }
    }

    @Override
    public String getEventMessage(final LocatableEvent event) {
        final Location location = event.location();
        final String locationQName = location.declaringType().name() + "." + location.method().name();
        String locationFileName;
        try {
            locationFileName = location.sourceName();
        }
        catch (AbsentInformationException e) {
            locationFileName = getFileName();
        }
        final int locationLine = location.lineNumber();

        if (event instanceof ModificationWatchpointEvent) {
            final ModificationWatchpointEvent modificationEvent = (ModificationWatchpointEvent) event;
            final ObjectReference object = modificationEvent.object();
            final Field field = modificationEvent.field();
            if (object != null) {
                return DebuggerBundle.message("status.field.watchpoint.reached.modification", field.declaringType().name(), field.name(),
                    modificationEvent.valueCurrent(), modificationEvent.valueToBe(), locationQName, locationFileName, locationLine,
                    object.uniqueID());
            }
            return DebuggerBundle.message("status.static.field.watchpoint.reached.modification", field.declaringType().name(), field.name(),
                modificationEvent.valueCurrent(), modificationEvent.valueToBe(), locationQName, locationFileName, locationLine);
        }
        if (event instanceof AccessWatchpointEvent) {
            AccessWatchpointEvent accessEvent = (AccessWatchpointEvent) event;
            final ObjectReference object = accessEvent.object();
            final Field field = accessEvent.field();
            if (object != null) {
                return DebuggerBundle.message("status.field.watchpoint.reached.access", field.declaringType().name(), field.name(), locationQName,
                    locationFileName, locationLine, object.uniqueID());
            }
            return DebuggerBundle.message("status.static.field.watchpoint.reached.access", field.declaringType().name(), field.name(),
                locationQName, locationFileName, locationLine);
        }
        return null;
    }

    @Override
    public String getDisplayName() {
        if (!isValid()) {
            return DebuggerBundle.message("status.breakpoint.invalid");
        }
        final String className = getClassName();
        return className != null && !className.isEmpty() ? className + "." + getFieldName() : getFieldName();
    }

    public static FieldBreakpoint create(@Nonnull Project project, String fieldName, XBreakpoint xBreakpoint) {
        FieldBreakpoint breakpoint = new FieldBreakpoint(project, fieldName, xBreakpoint);
        return (FieldBreakpoint) breakpoint.init();
    }

    //@Override
    //public boolean canMoveTo(final SourcePosition position) {
    //  return super.canMoveTo(position) && PositionUtil.getPsiElementAt(getProject(), PsiField.class, position) != null;
    //}

    @Override
    public boolean isValid() {
        return super.isValid() && getPsiField() != null;
    }

    @Override
    public boolean isAt(@Nonnull Document document, int offset) {
        PsiField field = findField(myProject, document, offset);
        return field == getPsiField();
    }

    //protected static FieldBreakpoint create(@NotNull Project project, @NotNull Field field, ObjectReference object, XBreakpoint xBreakpoint) {
    //  String fieldName = field.name();
    //  int line = 0;
    //  Document document = null;
    //  try {
    //    List locations = field.declaringType().allLineLocations();
    //    if(!locations.isEmpty()) {
    //      Location location = (Location)locations.get(0);
    //      line = location.lineNumber();
    //      VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(location.sourcePath());
    //      if(file != null) {
    //        PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
    //        if(psiFile != null) {
    //          document = PsiDocumentManager.getInstance(project).getDocument(psiFile);
    //        }
    //      }
    //    }
    //  }
    //  catch (AbsentInformationException e) {
    //    LOG.debug(e);
    //  }
    //  catch (InternalError e) {
    //    LOG.debug(e);
    //  }
    //
    //  if(document == null) return null;
    //
    //  FieldBreakpoint fieldBreakpoint = new FieldBreakpoint(project, createHighlighter(project, document, line), fieldName, xBreakpoint);
    //  if (!fieldBreakpoint.isStatic()) {
    //    fieldBreakpoint.addInstanceFilter(object.uniqueID());
    //  }
    //  return (FieldBreakpoint)fieldBreakpoint.init();
    //}

    public static PsiField findField(Project project, Document document, int offset) {
        PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(document);
        if (file == null) {
            return null;
        }
        offset = CharArrayUtil.shiftForward(document.getCharsSequence(), offset, " \t");
        PsiElement element = file.findElementAt(offset);
        if (element == null) {
            return null;
        }
        PsiField field = PsiTreeUtil.getParentOfType(element, PsiField.class, false);
        int line = document.getLineNumber(offset);
        if (field == null) {
            final PsiField[] fld = {null};
            XDebuggerUtil.getInstance().iterateLine(project, document, line, new Processor<PsiElement>() {
                @Override
                public boolean process(PsiElement element) {
                    PsiField field = PsiTreeUtil.getParentOfType(element, PsiField.class, false);
                    if (field != null) {
                        fld[0] = field;
                        return false;
                    }
                    return true;
                }
            });
            field = fld[0];
        }

        return field;
    }

    @Override
    public void readExternal(@Nonnull Element breakpointNode) throws InvalidDataException {
        super.readExternal(breakpointNode);
        //noinspection HardCodedStringLiteral
        setFieldName(breakpointNode.getAttributeValue("field_name"));
        if (getFieldName() == null) {
            throw new InvalidDataException("No field name for field breakpoint");
        }
        try {
            getProperties().WATCH_MODIFICATION = Boolean.valueOf(JDOMExternalizerUtil.readField(breakpointNode, "WATCH_MODIFICATION"));
        }
        catch (Exception e) {
        }
        try {
            getProperties().WATCH_ACCESS = Boolean.valueOf(JDOMExternalizerUtil.readField(breakpointNode, "WATCH_ACCESS"));
        }
        catch (Exception e) {
        }
    }
    //
    //@Override
    //@SuppressWarnings({"HardCodedStringLiteral"})
    //public void writeExternal(@NotNull Element parentNode) throws WriteExternalException {
    //  super.writeExternal(parentNode);
    //  parentNode.setAttribute("field_name", getFieldName());
    //}

    @Override
    public PsiElement getEvaluationElement() {
        return getPsiClass();
    }

    private boolean isWatchModification() {
        return getProperties().WATCH_MODIFICATION;
    }

    private boolean isWatchAccess() {
        return getProperties().WATCH_ACCESS;
    }

    void setFieldName(String fieldName) {
        getProperties().myFieldName = fieldName;
    }
}
