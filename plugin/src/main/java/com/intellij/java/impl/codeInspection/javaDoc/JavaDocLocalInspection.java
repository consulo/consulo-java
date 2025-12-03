/*
 * Copyright (c) 2005 Jet Brains. All Rights Reserved.
 */
package com.intellij.java.impl.codeInspection.javaDoc;

import com.intellij.java.analysis.codeInspection.reference.RefJavaUtil;
import com.intellij.java.analysis.impl.codeInspection.ex.BaseLocalInspectionTool;
import com.intellij.java.impl.codeInsight.CodeInsightUtil;
import com.intellij.java.language.impl.psi.impl.source.javadoc.PsiDocParamRef;
import com.intellij.java.language.psi.PsiElementFactory;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.javadoc.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.intellij.java.language.psi.util.PropertyUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.codeEditor.ScrollType;
import consulo.component.extension.ExtensionPoint;
import consulo.fileEditor.FileEditorManager;
import consulo.java.analysis.impl.localize.JavaQuickFixLocalize;
import consulo.language.ast.ASTNode;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.localize.InspectionLocalize;
import consulo.language.editor.inspection.scheme.InspectionManager;
import consulo.language.editor.inspection.scheme.InspectionProfile;
import consulo.language.editor.inspection.scheme.InspectionProfileManager;
import consulo.language.editor.inspection.scheme.InspectionProjectProfileManager;
import consulo.language.psi.*;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.ex.Gray;
import consulo.ui.ex.awt.*;
import consulo.ui.ex.awt.event.DocumentAdapter;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.Comparing;
import consulo.util.xml.serializer.DefaultJDOMExternalizer;
import consulo.util.xml.serializer.InvalidDataException;
import consulo.util.xml.serializer.JDOMExternalizable;
import consulo.util.xml.serializer.WriteExternalException;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jdom.Element;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import java.awt.*;
import java.util.List;
import java.util.*;

@ExtensionImpl
public class JavaDocLocalInspection extends BaseLocalInspectionTool {
    private static final String NONE = "none";
    private static final String PUBLIC = "public";
    private static final String PROTECTED = "protected";
    private static final String PACKAGE_LOCAL = "package";
    private static final String PRIVATE = "private";
    private static final Set<String> ourUniqueTags = new HashSet<>();
    public static final String SHORT_NAME = "JavaDoc";

    static {
        ourUniqueTags.add("return");
        ourUniqueTags.add("deprecated");
        ourUniqueTags.add("serial");
        ourUniqueTags.add("serialData");
    }

    private static final String IGNORE_ACCESSORS_ATTR_NAME = "IGNORE_ACCESSORS";

    public static class Options implements JDOMExternalizable {
        public String ACCESS_JAVADOC_REQUIRED_FOR = NONE;
        public String REQUIRED_TAGS = "";

        public Options() {
        }

        public Options(String ACCESS_JAVADOC_REQUIRED_FOR, String REQUIRED_TAGS) {
            this.ACCESS_JAVADOC_REQUIRED_FOR = ACCESS_JAVADOC_REQUIRED_FOR;
            this.REQUIRED_TAGS = REQUIRED_TAGS;
        }

        @Override
        public void readExternal(Element element) throws InvalidDataException {
            DefaultJDOMExternalizer.readExternal(this, element);
        }

        @Override
        public void writeExternal(Element element) throws WriteExternalException {
            DefaultJDOMExternalizer.writeExternal(this, element);
        }
    }

    public Options TOP_LEVEL_CLASS_OPTIONS = new Options("none", "");
    public Options INNER_CLASS_OPTIONS = new Options("none", "");
    public Options METHOD_OPTIONS = new Options("none", "@return@param@throws or @exception");
    public Options FIELD_OPTIONS = new Options("none", "");
    public boolean IGNORE_DEPRECATED = false;
    public boolean IGNORE_JAVADOC_PERIOD = true;
    public boolean IGNORE_DUPLICATED_THROWS = false;
    public boolean IGNORE_POINT_TO_ITSELF = false;
    public String myAdditionalJavadocTags = "";

    private boolean myIgnoreEmptyDescriptions = false;
    private boolean myIgnoreSimpleAccessors = false;

    public void setIgnoreSimpleAccessors(boolean ignoreSimpleAccessors) {
        myIgnoreSimpleAccessors = ignoreSimpleAccessors;
    }

    private static final Logger LOG = Logger.getInstance(JavaDocLocalInspection.class);

    private class OptionsPanel extends JPanel {
        private JPanel createOptionsPanel(String[] modifiers, String[] tags, Options options) {
            JPanel pane = new JPanel(new GridLayout(1, tags == null ? 1 : 2));

            pane.add(createScopePanel(modifiers, options));
            if (tags != null) {
                pane.add(createTagsPanel(tags, options));
            }

            pane.validate();

            return pane;
        }

        private JPanel createTagsPanel(String[] tags, Options options) {
            JPanel panel = new JPanel(new GridBagLayout());
            panel.setBorder(BorderFactory.createCompoundBorder(
                IdeBorderFactory.createTitledBorder(InspectionLocalize.inspectionJavadocRequiredTagsOptionTitle().get(), true),
                BorderFactory.createEmptyBorder(0, 3, 3, 3)
            ));

            GridBagConstraints gc = new GridBagConstraints();
            gc.weightx = 1;
            gc.weighty = 0;
            gc.fill = GridBagConstraints.HORIZONTAL;
            gc.anchor = GridBagConstraints.NORTHWEST;


            for (int i = 0; i < tags.length; i++) {
                JCheckBox box = new JCheckBox(tags[i]);
                gc.gridy = i;
                if (i == tags.length - 1) {
                    gc.weighty = 1;
                }
                panel.add(box, gc);
                box.setSelected(isTagRequired(options, tags[i]));
                box.addChangeListener(new MyChangeListener(box, options, tags[i]));
            }

            return panel;
        }

        private class MyChangeListener implements ChangeListener {
            private final JCheckBox myCheckBox;
            private final Options myOptions;
            private final String myTagName;

            public MyChangeListener(JCheckBox checkBox, Options options, String tagName) {
                myCheckBox = checkBox;
                myOptions = options;
                myTagName = tagName;
            }

            @Override
            public void stateChanged(ChangeEvent e) {
                if (myCheckBox.isSelected()) {
                    if (!isTagRequired(myOptions, myTagName)) {
                        myOptions.REQUIRED_TAGS += myTagName;
                    }
                }
                else {
                    myOptions.REQUIRED_TAGS = myOptions.REQUIRED_TAGS.replaceAll(myTagName, "");
                }
            }
        }

        private JPanel createScopePanel(String[] modifiers, Options options) {
            JPanel panel = new JPanel(new BorderLayout());
            panel.setBorder(BorderFactory.createCompoundBorder(
                IdeBorderFactory.createTitledBorder(InspectionLocalize.inspectionScopeForTitle().get(), true),
                BorderFactory.createEmptyBorder(0, 3, 3, 3)
            ));

            Hashtable<Integer, JLabel> sliderLabels = new Hashtable<>();
            for (int i = 0; i < modifiers.length; i++) {
                sliderLabels.put(i + 1, new JLabel(modifiers[i]));
            }

            JSlider slider = new JSlider(SwingConstants.VERTICAL, 1, modifiers.length, 1);

            slider.setLabelTable(sliderLabels);
            slider.putClientProperty(UIUtil.JSLIDER_ISFILLED, Boolean.TRUE);
            slider.setPreferredSize(new Dimension(80, 50));
            slider.setPaintLabels(true);
            slider.setSnapToTicks(true);
            slider.addChangeListener(e -> {
                int value = slider.getValue();
                options.ACCESS_JAVADOC_REQUIRED_FOR = modifiers[value - 1];
                for (Integer key : sliderLabels.keySet()) {
                    sliderLabels.get(key).setForeground(key <= value ? Color.black : Gray._100);
                }
            });

            Color fore = Color.black;
            for (int i = 0; i < modifiers.length; i++) {
                sliderLabels.get(i + 1).setForeground(fore);

                if (modifiers[i].equals(options.ACCESS_JAVADOC_REQUIRED_FOR)) {
                    slider.setValue(i + 1);
                    fore = Gray._100;
                }
            }

            panel.add(slider, BorderLayout.WEST);

            return panel;
        }

        public OptionsPanel() {
            super(new GridBagLayout());
            GridBagConstraints gc = new GridBagConstraints(
                0, GridBagConstraints.RELATIVE, 2, 1, 1, 0, GridBagConstraints.NORTH, GridBagConstraints.HORIZONTAL,
                JBUI.emptyInsets(), 0, 0
            );
            add(createAdditionalJavadocTagsPanel(), gc);
            JTabbedPane tabs = new JBTabbedPane(SwingConstants.BOTTOM);
            String[] tags = new String[]{"@author", "@version", "@since", "@param"};
            tabs.add(
                InspectionLocalize.inspectionJavadocOptionTabTitle().get(),
                createOptionsPanel(new String[]{NONE, PUBLIC, PACKAGE_LOCAL}, tags, TOP_LEVEL_CLASS_OPTIONS)
            );
            tags = new String[]{"@return", "@param", InspectionLocalize.inspectionJavadocThrowsOrExceptionOption().get()};
            tabs.add(
                InspectionLocalize.inspectionJavadocOptionTabTitleMethod().get(),
                createOptionsPanel(new String[]{NONE, PUBLIC, PROTECTED, PACKAGE_LOCAL, PRIVATE}, tags, METHOD_OPTIONS)
            );
            tabs.add(
                InspectionLocalize.inspectionJavadocOptionTabTitleField().get(),
                createOptionsPanel(new String[]{NONE, PUBLIC, PROTECTED, PACKAGE_LOCAL, PRIVATE}, null, FIELD_OPTIONS)
            );
            tabs.add(
                InspectionLocalize.inspectionJavadocOptionTabTitleInnerClass().get(),
                createOptionsPanel(new String[]{NONE, PUBLIC, PROTECTED, PACKAGE_LOCAL, PRIVATE}, null, INNER_CLASS_OPTIONS)
            );
            add(tabs, gc);

            JCheckBox checkBox = new JCheckBox(InspectionLocalize.inspectionJavadocOptionIgnoreDeprecated().get(), IGNORE_DEPRECATED);
            checkBox.addActionListener(e -> IGNORE_DEPRECATED = checkBox.isSelected());
            gc.gridwidth = 1;
            add(checkBox, gc);
            JCheckBox periodCheckBox = new JCheckBox(
                InspectionLocalize.inspectionJavadocOptionIgnorePeriod().get(),
                IGNORE_JAVADOC_PERIOD
            );
            periodCheckBox.addActionListener(e -> IGNORE_JAVADOC_PERIOD = periodCheckBox.isSelected());
            add(periodCheckBox, gc);

            JCheckBox ignoreDuplicateThrowsCheckBox = new JCheckBox("Ignore duplicate throws tag", IGNORE_DUPLICATED_THROWS);
            ignoreDuplicateThrowsCheckBox.addActionListener(e -> IGNORE_DUPLICATED_THROWS = ignoreDuplicateThrowsCheckBox.isSelected());
            add(ignoreDuplicateThrowsCheckBox, gc);

            JCheckBox ignorePointToItselfCheckBox = new JCheckBox("Ignore javadoc pointing to itself", IGNORE_POINT_TO_ITSELF);
            ignorePointToItselfCheckBox.addActionListener(e -> IGNORE_POINT_TO_ITSELF = ignorePointToItselfCheckBox.isSelected());
            add(ignorePointToItselfCheckBox, gc);
            JCheckBox ignoreSimpleAccessorsCheckBox = new JCheckBox("Ignore simple property accessors", myIgnoreSimpleAccessors);
            ignoreSimpleAccessorsCheckBox.addActionListener(e -> myIgnoreSimpleAccessors = ignoreSimpleAccessorsCheckBox.isSelected());
            add(ignoreSimpleAccessorsCheckBox, gc);
        }

        public FieldPanel createAdditionalJavadocTagsPanel() {
            FieldPanel additionalTagsPanel = new FieldPanel(
                InspectionLocalize.inspectionJavadocLabelText().get(),
                InspectionLocalize.inspectionJavadocDialogTitle().get(),
                null,
                null
            );
            additionalTagsPanel.setPreferredSize(new Dimension(150, additionalTagsPanel.getPreferredSize().height));
            additionalTagsPanel.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
                @Override
                protected void textChanged(DocumentEvent e) {
                    Document document = e.getDocument();
                    try {
                        String text = document.getText(0, document.getLength());
                        if (text != null) {
                            myAdditionalJavadocTags = text.trim();
                        }
                    }
                    catch (BadLocationException e1) {
                        LOG.error(e1);
                    }
                }
            });
            additionalTagsPanel.setText(myAdditionalJavadocTags);
            return additionalTagsPanel;
        }
    }

    @Override
    public JComponent createOptionsPanel() {
        return new OptionsPanel();
    }

    @Override
    public void writeSettings(@Nonnull Element node) throws WriteExternalException {
        super.writeSettings(node);
        if (myIgnoreSimpleAccessors) {
            Element option = new Element(IGNORE_ACCESSORS_ATTR_NAME);
            option.setAttribute("value", String.valueOf(true));
            node.addContent(option);
        }
    }

    @Override
    public void readSettings(@Nonnull Element node) throws InvalidDataException {
        super.readSettings(node);
        Element ignoreAccessorsTag = node.getChild(IGNORE_ACCESSORS_ATTR_NAME);
        if (ignoreAccessorsTag != null) {
            myIgnoreSimpleAccessors = Boolean.parseBoolean(ignoreAccessorsTag.getAttributeValue("value"));
        }
    }

    private static class AddMissingTagFix implements LocalQuickFix {
        private final String myTag;
        private final String myValue;

        public AddMissingTagFix(String tag, String value) {
            myTag = tag;
            myValue = value;
        }

        public AddMissingTagFix(String tag) {
            this(tag, "");
        }

        @Override
        @Nonnull
        public LocalizeValue getName() {
            return InspectionLocalize.inspectionJavadocProblemAddTag(myTag, myValue);
        }

        @Override
        @RequiredWriteAction
        public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
            PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
            try {
                PsiDocCommentOwner owner = PsiTreeUtil.getParentOfType(descriptor.getEndElement(), PsiDocCommentOwner.class);
                if (owner != null) {
                    if (!CodeInsightUtil.preparePsiElementsForWrite(owner)) {
                        return;
                    }
                    PsiDocComment docComment = owner.getDocComment();
                    PsiDocTag tag = factory.createDocTagFromText("@" + myTag + " " + myValue);
                    if (docComment != null) {
                        PsiElement addedTag;
                        PsiElement anchor = getAnchor(descriptor);
                        if (anchor != null) {
                            addedTag = docComment.addBefore(tag, anchor);
                        }
                        else {
                            addedTag = docComment.add(tag);
                        }
                        moveCaretTo(addedTag);
                    }
                }
            }
            catch (IncorrectOperationException e) {
                LOG.error(e);
            }
        }

        @Nullable
        protected PsiElement getAnchor(ProblemDescriptor descriptor) {
            return null;
        }

        @RequiredReadAction
        private static void moveCaretTo(PsiElement newCaretPosition) {
            Project project = newCaretPosition.getProject();
            PsiFile psiFile = newCaretPosition.getContainingFile();
            Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
            if (editor != null && IJSwingUtilities.hasFocus(editor.getComponent())) {
                PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
                if (file == psiFile) {
                    editor.getCaretModel().moveToOffset(newCaretPosition.getTextRange().getEndOffset());
                    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
                }
            }
        }
    }

    @Override
    @Nullable
    @RequiredReadAction
    public ProblemDescriptor[] checkClass(
        @Nonnull PsiClass psiClass,
        @Nonnull InspectionManager manager,
        boolean isOnTheFly,
        Object state
    ) {
        if (psiClass instanceof PsiAnonymousClass) {
            return null;
        }
        // if (psiClass instanceof JspClass) return null;
        if (psiClass instanceof PsiTypeParameter) {
            return null;
        }
        if (IGNORE_DEPRECATED && psiClass.isDeprecated()) {
            return null;
        }
        PsiDocComment docComment = psiClass.getDocComment();
        PsiIdentifier nameIdentifier = psiClass.getNameIdentifier();
        PsiElement elementToHighlight = nameIdentifier != null ? nameIdentifier : psiClass;
        boolean required = isJavaDocRequired(psiClass);
        if (docComment == null) {
            if (!required) {
                return null;
            }
            return new ProblemDescriptor[]{
                manager.newProblemDescriptor(InspectionLocalize.inspectionJavadocProblemDescriptor())
                    .range(elementToHighlight)
                    .onTheFly(isOnTheFly)
                    .create()
            };
        }

        PsiDocTag[] tags = docComment.getTags();
        String[] tagsToCheck = {"author", "version", "since"};
        LocalizeValue[] absentDescriptions = {
            InspectionLocalize.inspectionJavadocProblemMissingAuthorDescription(),
            InspectionLocalize.inspectionJavadocProblemMissingVersionDescription(),
            InspectionLocalize.inspectionJavadocProblemMissingSinceDescription()
        };
        List<ProblemDescriptor> problems = new ArrayList<>(2);
        if (required) {
            boolean[] isTagRequired = new boolean[tagsToCheck.length];
            boolean[] isTagPresent = new boolean[tagsToCheck.length];

            boolean someTagsAreRequired = false;
            for (int i = 0; i < tagsToCheck.length; i++) {
                String tag = tagsToCheck[i];
                someTagsAreRequired |= isTagRequired[i] = isTagRequired(psiClass, tag);
            }

            if (someTagsAreRequired) {
                for (PsiDocTag tag : tags) {
                    String tagName = tag.getName();
                    for (int i = 0; i < tagsToCheck.length; i++) {
                        String tagToCheck = tagsToCheck[i];
                        if (tagToCheck.equals(tagName)) {
                            isTagPresent[i] = true;
                        }
                    }
                }
            }

            for (int i = 0; i < tagsToCheck.length; i++) {
                String tagToCheck = tagsToCheck[i];
                if (isTagRequired[i] && !isTagPresent[i]) {
                    problems.add(createMissingTagDescriptor(elementToHighlight, tagToCheck, manager, isOnTheFly));
                }
            }
        }

        List<ProblemDescriptor> tagProblems = getTagValuesProblems(psiClass, tags, manager, isOnTheFly);
        if (tagProblems != null) {
            problems.addAll(tagProblems);
        }
        checkForPeriodInDoc(docComment, problems, manager, isOnTheFly);
        checkInlineTags(manager, problems, docComment.getDescriptionElements(),
            JavadocManager.SERVICE.getInstance(docComment.getProject()), isOnTheFly
        );
        checkForBadCharacters(docComment, problems, manager, isOnTheFly);
        for (PsiDocTag tag : tags) {
            for (int i = 0; i < tagsToCheck.length; i++) {
                String tagToCheck = tagsToCheck[i];
                if (tagToCheck.equals(tag.getName()) && extractTagDescription(tag).length() == 0) {
                    problems.add(
                        manager.newProblemDescriptor(absentDescriptions[i])
                            .range(elementToHighlight)
                            .onTheFly(isOnTheFly)
                            .create()
                    );
                }
            }
        }

        checkDuplicateTags(tags, problems, manager, isOnTheFly);

        if (required && isTagRequired(psiClass, "param") && psiClass.hasTypeParameters() && nameIdentifier != null) {
            List<PsiTypeParameter> absentParameters = null;
            PsiTypeParameter[] typeParameters = psiClass.getTypeParameters();
            for (PsiTypeParameter typeParameter : typeParameters) {
                if (!isFound(tags, typeParameter)) {
                    if (absentParameters == null) {
                        absentParameters = new ArrayList<>(1);
                    }
                    absentParameters.add(typeParameter);
                }
            }
            if (absentParameters != null) {
                for (PsiTypeParameter psiTypeParameter : absentParameters) {
                    problems.add(createMissingParamTagDescriptor(nameIdentifier, psiTypeParameter, manager, isOnTheFly));
                }
            }
        }

        return problems.isEmpty()
            ? null
            : problems.toArray(new ProblemDescriptor[problems.size()]);
    }

    @RequiredReadAction
    private static ProblemDescriptor createMissingParamTagDescriptor(
        PsiIdentifier nameIdentifier,
        PsiTypeParameter psiTypeParameter,
        InspectionManager manager,
        boolean isOnTheFly
    ) {
        return manager.newProblemDescriptor(InspectionLocalize.inspectionJavadocProblemMissingTag("<code>@param</code>"))
            .range(nameIdentifier)
            .onTheFly(isOnTheFly)
            .withFix(new AddMissingTagFix("param", "<" + psiTypeParameter.getName() + ">"))
            .create();
    }

    @Override
    @Nullable
    @RequiredReadAction
    public ProblemDescriptor[] checkField(
        @Nonnull PsiField psiField,
        @Nonnull InspectionManager manager,
        boolean isOnTheFly,
        Object state
    ) {
        if (IGNORE_DEPRECATED && (psiField.isDeprecated() || psiField.getContainingClass().isDeprecated())) {
            return null;
        }

        PsiDocComment docComment = psiField.getDocComment();
        if (docComment == null) {
            if (!isJavaDocRequired(psiField)) {
                return null;
            }
            return new ProblemDescriptor[]{
                manager.newProblemDescriptor(InspectionLocalize.inspectionJavadocProblemDescriptor())
                    .range(psiField.getNameIdentifier())
                    .onTheFly(isOnTheFly)
                    .create()
            };
        }

        List<ProblemDescriptor> problems = new ArrayList<>(2);
        List<ProblemDescriptor> tagProblems = getTagValuesProblems(psiField, docComment.getTags(), manager, isOnTheFly);
        if (tagProblems != null) {
            problems.addAll(tagProblems);
        }
        checkInlineTags(
            manager,
            problems,
            docComment.getDescriptionElements(),
            JavadocManager.SERVICE.getInstance(docComment.getProject()),
            isOnTheFly
        );
        checkForPeriodInDoc(docComment, problems, manager, isOnTheFly);
        checkDuplicateTags(docComment.getTags(), problems, manager, isOnTheFly);
        checkForBadCharacters(docComment, problems, manager, isOnTheFly);
        return problems.isEmpty()
            ? null
            : problems.toArray(new ProblemDescriptor[problems.size()]);
    }

    @Override
    @Nullable
    @RequiredReadAction
    public ProblemDescriptor[] checkMethod(
        @Nonnull PsiMethod psiMethod,
        @Nonnull InspectionManager manager,
        boolean isOnTheFly,
        Object state
    ) {
        //if (psiMethod instanceof JspHolderMethod) return null;
        if (IGNORE_DEPRECATED && (psiMethod.isDeprecated() || psiMethod.getContainingClass().isDeprecated())) {
            return null;
        }
        if (myIgnoreSimpleAccessors && PropertyUtil.isSimplePropertyAccessor(psiMethod)) {
            return null;
        }
        PsiDocComment docComment = psiMethod.getDocComment();
        PsiMethod[] superMethods = psiMethod.findSuperMethods();
        boolean required = isJavaDocRequired(psiMethod);
        if (docComment == null) {
            if (required) {
                if (superMethods.length > 0) {
                    return null;
                }
                ExtensionPoint<JavaDocNotNecessaryFilter> filters =
                    psiMethod.getApplication().getExtensionPoint(JavaDocNotNecessaryFilter.class);
                if (filters.anyMatchSafe(filter -> filter.isJavaDocNotNecessary(psiMethod))) {
                    return null;
                }
                PsiIdentifier nameIdentifier = psiMethod.getNameIdentifier();
                if (nameIdentifier != null) {
                    return new ProblemDescriptor[]{
                        manager.newProblemDescriptor(InspectionLocalize.inspectionJavadocProblemDescriptor())
                            .range(nameIdentifier)
                            .onTheFly(isOnTheFly)
                            .create()
                    };
                }
            }
            return null;
        }

        PsiElement[] descriptionElements = docComment.getDescriptionElements();
        for (PsiElement descriptionElement : descriptionElements) {
            if (descriptionElement instanceof PsiInlineDocTag inlineDocTag
                && "inheritDoc".equals(inlineDocTag.getName())) {
                return null;
            }
        }

        List<ProblemDescriptor> problems = new ArrayList<>(2);

        checkInlineTags(
            manager,
            problems,
            descriptionElements,
            JavadocManager.SERVICE.getInstance(docComment.getProject()),
            isOnTheFly
        );

        PsiDocTag tagByName = docComment.findTagByName("inheritDoc");
        if (tagByName != null) {
            String tagName = tagByName.getName();
            JavadocTagInfo tagInfo = JavadocManager.SERVICE.getInstance(tagByName.getProject()).getTagInfo(tagName);
            if (tagInfo != null && tagInfo.isValidInContext(psiMethod)) {
                return null;
            }
        }

        PsiDocTag[] tags = docComment.getTags();

        boolean isReturnRequired = false;
        boolean isReturnAbsent = true;
        if (superMethods.length == 0 && !psiMethod.isConstructor() && !PsiType.VOID.equals(psiMethod.getReturnType()) && isTagRequired(
            psiMethod,
            "return"
        )) {
            isReturnRequired = true;
            for (PsiDocTag tag : tags) {
                if ("return".equals(tag.getName())) {
                    isReturnAbsent = false;
                    break;
                }
            }
        }

        List<PsiParameter> absentParameters = null;
        if (required && superMethods.length == 0 && isTagRequired(psiMethod, "param")) {
            PsiParameter[] params = psiMethod.getParameterList().getParameters();
            for (PsiParameter param : params) {
                if (!isFound(tags, param)) {
                    if (absentParameters == null) {
                        absentParameters = new ArrayList<>(2);
                    }
                    absentParameters.add(param);
                }
            }
        }

        if (required && isReturnRequired && isReturnAbsent) {
            PsiIdentifier psiIdentifier = psiMethod.getNameIdentifier();
            if (psiIdentifier != null) {
                problems.add(createMissingTagDescriptor(psiIdentifier, "return", manager, isOnTheFly));
            }
        }

        if (absentParameters != null) {
            for (PsiParameter psiParameter : absentParameters) {
                PsiIdentifier nameIdentifier = psiMethod.getNameIdentifier();
                if (nameIdentifier != null) {
                    problems.add(createMissingParamTagDescriptor(nameIdentifier, psiParameter, manager, isOnTheFly));
                }
            }
        }

        if (!myIgnoreEmptyDescriptions) {
            for (PsiDocTag tag : tags) {
                if ("param".equals(tag.getName())) {
                    PsiElement[] dataElements = tag.getDataElements();
                    PsiDocTagValue valueElement = tag.getValueElement();
                    boolean hasProblemsWithTag = dataElements.length < 2;
                    if (!hasProblemsWithTag) {
                        StringBuilder buf = new StringBuilder();
                        for (PsiElement element : dataElements) {
                            if (element != valueElement) {
                                buf.append(element.getText());
                            }
                        }
                        hasProblemsWithTag = buf.toString().trim().length() == 0;
                    }
                    if (hasProblemsWithTag && valueElement != null) {
                        problems.add(
                            manager.newProblemDescriptor(InspectionLocalize.inspectionJavadocMethodProblemMissingTagDescription(
                                    "<code>@param " + valueElement.getText() + "</code>"
                                ))
                                .range(valueElement)
                                .onTheFly(isOnTheFly)
                                .create()
                        );
                    }
                }
            }
        }

        if (required && superMethods.length == 0 && isTagRequired(psiMethod, "@throws")
            && psiMethod.getThrowsList().getReferencedTypes().length > 0) {
            Map<PsiClassType, PsiClass> declaredExceptions = new HashMap<>();
            PsiClassType[] classTypes = psiMethod.getThrowsList().getReferencedTypes();
            for (PsiClassType classType : classTypes) {
                PsiClass psiClass = classType.resolve();
                if (psiClass != null) {
                    declaredExceptions.put(classType, psiClass);
                }
            }
            processThrowsTags(tags, declaredExceptions, manager, problems, isOnTheFly);
            if (!declaredExceptions.isEmpty()) {
                for (PsiClassType declaredException : declaredExceptions.keySet()) {
                    problems.add(createMissingThrowsTagDescriptor(psiMethod, manager, declaredException, isOnTheFly));
                }
            }
        }

        List<ProblemDescriptor> tagProblems = getTagValuesProblems(psiMethod, tags, manager, isOnTheFly);
        if (tagProblems != null) {
            problems.addAll(tagProblems);
        }

        checkForPeriodInDoc(docComment, problems, manager, isOnTheFly);
        checkForBadCharacters(docComment, problems, manager, isOnTheFly);
        for (PsiDocTag tag : tags) {
            if ("param".equals(tag.getName())) {
                if (extractTagDescription(tag).length() == 0) {
                    PsiDocTagValue value = tag.getValueElement();
                    if (value instanceof PsiDocParamRef paramRef) {
                        for (PsiParameter param : psiMethod.getParameterList().getParameters()) {
                            if (paramRef.getReference().isReferenceTo(param)) {
                                problems.add(
                                    manager.newProblemDescriptor(InspectionLocalize.inspectionJavadocMethodProblemDescriptor(
                                            "<code>@param</code>",
                                            "<code>" + param.getName() + "</code>"
                                        ))
                                        .range(value)
                                        .onTheFly(isOnTheFly)
                                        .create()
                                );
                            }
                        }
                    }
                }
            }
            else if ("return".equals(tag.getName()) && !myIgnoreEmptyDescriptions && extractTagDescription(tag).length() == 0) {
                problems.add(
                    manager.newProblemDescriptor(InspectionLocalize.inspectionJavadocMethodProblemMissingTagDescription(
                            "<code>@return</code>"
                        ))
                        .range(tag.getNameElement())
                        .onTheFly(isOnTheFly)
                        .create()
                );
            }
        }

        checkDuplicateTags(tags, problems, manager, isOnTheFly);

        return problems.isEmpty()
            ? null
            : problems.toArray(new ProblemDescriptor[problems.size()]);
    }

    @RequiredReadAction
    public static boolean isFound(PsiDocTag[] tags, PsiElement param) {
        for (PsiDocTag tag : tags) {
            if ("param".equals(tag.getName()) && tag.getValueElement() instanceof PsiDocParamRef paramRef) {
                PsiReference psiReference = paramRef.getReference();
                if (psiReference != null && psiReference.isReferenceTo(param)) {
                    return true;
                }
            }
        }
        return false;
    }

    @RequiredReadAction
    private void processThrowsTags(
        PsiDocTag[] tags,
        Map<PsiClassType, PsiClass> declaredExceptions,
        InspectionManager manager,
        @Nonnull List<ProblemDescriptor> problems,
        boolean isOnTheFly
    ) {
        for (PsiDocTag tag : tags) {
            if ("throws".equals(tag.getName()) || "exception".equals(tag.getName())) {
                PsiDocTagValue value = tag.getValueElement();
                if (value == null) {
                    continue;
                }
                PsiElement firstChild = value.getFirstChild();
                if (firstChild != null
                    && firstChild.getFirstChild() instanceof PsiJavaCodeReferenceElement ref
                    && ref.resolve() instanceof PsiClass exceptionClass) {
                    for (Iterator<PsiClassType> it = declaredExceptions.keySet().iterator(); it.hasNext(); ) {
                        PsiClassType classType = it.next();
                        PsiClass psiClass = declaredExceptions.get(classType);
                        if (InheritanceUtil.isInheritorOrSelf(exceptionClass, psiClass, true)) {
                            if (!myIgnoreEmptyDescriptions && extractThrowsTagDescription(tag).length() == 0) {
                                problems.add(
                                    manager.newProblemDescriptor(InspectionLocalize.inspectionJavadocMethodProblemMissingTagDescription(
                                            "<code>" + tag.getName() + "</code>"
                                        ))
                                        .range(tag.getNameElement())
                                        .onTheFly(isOnTheFly)
                                        .create());
                            }
                            it.remove();
                        }
                    }
                }
            }
        }
    }

    @Nullable
    @RequiredReadAction
    private static ProblemDescriptor createMissingThrowsTagDescriptor(
        PsiMethod method,
        InspectionManager manager,
        PsiClassType exceptionClassType,
        boolean isOnTheFly
    ) {
        String tag = "throws";
        LocalizeValue message =
            InspectionLocalize.inspectionJavadocProblemMissingTag("<code>@" + tag + "</code> " + exceptionClassType.getCanonicalText());
        String firstDeclaredException = exceptionClassType.getCanonicalText();
        PsiIdentifier nameIdentifier = method.getNameIdentifier();
        if (nameIdentifier == null) {
            return null;
        }
        return manager.newProblemDescriptor(message)
            .range(nameIdentifier)
            .onTheFly(isOnTheFly)
            .withFix(new AddMissingTagFix(tag, firstDeclaredException))
            .create();
    }

    @RequiredReadAction
    private static ProblemDescriptor createMissingTagDescriptor(
        PsiElement elementToHighlight,
        String tag,
        InspectionManager manager,
        boolean isOnTheFly
    ) {
        return manager.newProblemDescriptor(InspectionLocalize.inspectionJavadocProblemMissingTag("<code>@" + tag + "</code>"))
            .range(elementToHighlight)
            .onTheFly(isOnTheFly)
            .withFix(new AddMissingTagFix(tag))
            .create();
    }

    @RequiredReadAction
    private static ProblemDescriptor createMissingParamTagDescriptor(
        PsiElement elementToHighlight,
        PsiParameter param,
        InspectionManager manager,
        boolean isOnTheFly
    ) {
        return manager.newProblemDescriptor(InspectionLocalize.inspectionJavadocMethodProblemMissingParamTag(
                "<code>@param</code>",
                "<code>" + param.getName() + "</code>"
            ))
            .range(elementToHighlight)
            .onTheFly(isOnTheFly)
            .withFix(new AddMissingParamTagFix(param.getName()))
            .create();
    }

    private static class AddMissingParamTagFix extends AddMissingTagFix {
        private final String myName;

        public AddMissingParamTagFix(String name) {
            super("param", name);
            myName = name;
        }

        @Override
        @Nonnull
        public LocalizeValue getName() {
            return InspectionLocalize.inspectionJavadocProblemAddParamTag(myName);
        }

        @Override
        @Nullable
        @RequiredReadAction
        protected PsiElement getAnchor(ProblemDescriptor descriptor) {
            if (!(descriptor.getPsiElement() instanceof PsiElement element && element.getParent() instanceof PsiMethod method)) {
                return null;
            }
            PsiParameter[] parameters = method.getParameterList().getParameters();
            PsiParameter myParam = ContainerUtil.find(parameters, psiParameter -> myName.equals(psiParameter.getName()));
            if (myParam == null) {
                return null;
            }

            PsiMethod psiMethod = PsiTreeUtil.getParentOfType(myParam, PsiMethod.class);
            LOG.assertTrue(psiMethod != null);
            PsiDocComment docComment = psiMethod.getDocComment();
            LOG.assertTrue(docComment != null);
            PsiDocTag[] tags = docComment.findTagsByName("param");
            if (tags.length == 0) { //insert as first tag or append to description
                tags = docComment.getTags();
                if (tags.length == 0) {
                    return null;
                }
                return tags[0];
            }

            PsiParameter nextParam = PsiTreeUtil.getNextSiblingOfType(myParam, PsiParameter.class);
            while (nextParam != null) {
                for (PsiDocTag tag : tags) {
                    if (matches(nextParam, tag)) {
                        return tag;
                    }
                }
                nextParam = PsiTreeUtil.getNextSiblingOfType(nextParam, PsiParameter.class);
            }

            PsiParameter prevParam = PsiTreeUtil.getPrevSiblingOfType(myParam, PsiParameter.class);
            while (prevParam != null) {
                for (PsiDocTag tag : tags) {
                    if (matches(prevParam, tag)) {
                        return PsiTreeUtil.getNextSiblingOfType(tag, PsiDocTag.class);
                    }
                }
                prevParam = PsiTreeUtil.getPrevSiblingOfType(prevParam, PsiParameter.class);
            }

            return null;
        }

        @RequiredReadAction
        private static boolean matches(PsiParameter param, PsiDocTag tag) {
            PsiDocTagValue valueElement = tag.getValueElement();
            return valueElement != null && valueElement.getText().trim().startsWith(param.getName());
        }
    }

    @RequiredReadAction
    private static String extractTagDescription(PsiDocTag tag) {
        StringBuilder buf = new StringBuilder();
        PsiElement[] children = tag.getChildren();
        for (PsiElement child : children) {
            if (child instanceof PsiDocToken token) {
                if (token.getTokenType() == JavaDocTokenType.DOC_COMMENT_DATA) {
                    buf.append(token.getText());
                }
            }
            else if (child instanceof PsiDocTagValue) {
                buf.append(child.getText());
            }
            else if (child instanceof PsiInlineDocTag) {
                buf.append(child.getText());
            }
        }

        String s = buf.toString();
        return s.trim();
    }

    @RequiredReadAction
    private static String extractThrowsTagDescription(PsiDocTag tag) {
        StringBuilder buf = new StringBuilder();
        PsiElement[] children = tag.getChildren();
        for (PsiElement child : children) {
            if (child instanceof PsiDocToken token && token.getTokenType() == JavaDocTokenType.DOC_COMMENT_DATA) {
                buf.append(token.getText());
            }
        }

        return buf.toString().trim();
    }

    private void checkForBadCharacters(
        PsiDocComment docComment,
        final List<ProblemDescriptor> problems,
        final InspectionManager manager, final boolean onTheFly
    ) {
        docComment.accept(new PsiRecursiveElementVisitor() {
            @Override
            @RequiredReadAction
            public void visitElement(PsiElement element) {
                super.visitElement(element);
                ASTNode node = element.getNode();
                if (node != null && node.getElementType() == JavaDocTokenType.DOC_COMMENT_BAD_CHARACTER) {
                    problems.add(
                        manager.newProblemDescriptor(LocalizeValue.localizeTODO("Illegal character"))
                            .range(element)
                            .onTheFly(onTheFly)
                            .create()
                    );
                }
            }
        });
    }

    @RequiredReadAction
    private void checkForPeriodInDoc(
        PsiDocComment docComment,
        List<ProblemDescriptor> problems,
        InspectionManager manager,
        boolean onTheFly
    ) {
        if (IGNORE_JAVADOC_PERIOD) {
            return;
        }
        PsiDocTag[] tags = docComment.getTags();
        int dotIndex = docComment.getText().indexOf('.');
        int tagOffset = 0;
        if (dotIndex >= 0) {      //need to find first valid tag
            PsiDocCommentOwner owner = PsiTreeUtil.getParentOfType(docComment, PsiDocCommentOwner.class);
            for (PsiDocTag tag : tags) {
                String tagName = tag.getName();
                JavadocTagInfo tagInfo = JavadocManager.SERVICE.getInstance(tag.getProject()).getTagInfo(tagName);
                if (tagInfo != null && tagInfo.isValidInContext(owner) && !tagInfo.isInline()) {
                    tagOffset = tag.getTextOffset();
                    break;
                }
            }
        }

        if (dotIndex == -1 || tagOffset > 0 && dotIndex + docComment.getTextOffset() > tagOffset) {
            problems.add(
                manager.newProblemDescriptor(InspectionLocalize.inspectionJavadocProblemDescriptor1())
                    .range(docComment.getFirstChild())
                    .onTheFly(onTheFly)
                    .create()
            );
        }
    }

    @Nullable
    @RequiredReadAction
    private List<ProblemDescriptor> getTagValuesProblems(
        PsiDocCommentOwner context,
        PsiDocTag[] tags,
        InspectionManager inspectionManager,
        boolean isOnTheFly
    ) {
        List<ProblemDescriptor> problems = new ArrayList<>(2);
        for (PsiDocTag tag : tags) {
            JavadocManager manager = JavadocManager.SERVICE.getInstance(tag.getProject());
            String tagName = tag.getName();
            JavadocTagInfo tagInfo = manager.getTagInfo(tagName);

            if ((tagInfo == null || !tagInfo.isValidInContext(context))
                && checkTagInfo(inspectionManager, tagInfo, tag, isOnTheFly, problems)) {
                continue;
            }

            PsiDocTagValue value = tag.getValueElement();
            JavadocTagInfo info = manager.getTagInfo(tagName);
            if (info != null && !info.isValidInContext(context)) {
                continue;
            }
            String message = info == null ? null : info.checkTagValue(value);

            PsiReference reference = value != null ? value.getReference() : null;
            if (message == null && reference != null) {
                PsiElement element = reference.resolve();
                if (element == null) {
                    int textOffset = value.getTextOffset();

                    if (textOffset == value.getTextRange().getEndOffset()) {
                        problems.add(
                            inspectionManager.newProblemDescriptor(InspectionLocalize.inspectionJavadocProblemNameExpected())
                                .range(tag)
                                .afterEndOfLine(true)
                                .onTheFly(isOnTheFly)
                                .create()
                        );
                    }
                }
            }

            if (message != null) {
                PsiDocTagValue valueElement = tag.getValueElement();
                if (valueElement == null) {
                    problems.add(
                        inspectionManager.newProblemDescriptor(InspectionLocalize.inspectionJavadocMethodProblemMissingTagDescription(
                                "<code>" + tag.getName() + "</code>"
                            ))
                            .range(tag)
                            .afterEndOfLine(true)
                            .onTheFly(isOnTheFly)
                            .create()
                    );
                }
                else {
                    problems.add(
                        inspectionManager.newProblemDescriptor(LocalizeValue.localizeTODO(message))
                            .range(valueElement)
                            .onTheFly(isOnTheFly)
                            .create()
                    );
                }
            }
            checkInlineTags(inspectionManager, problems, tag.getDataElements(), manager, isOnTheFly);
        }

        return problems.isEmpty() ? null : problems;
    }

    @RequiredReadAction
    private boolean checkTagInfo(
        InspectionManager inspectionManager,
        JavadocTagInfo tagInfo,
        PsiDocTag tag,
        boolean isOnTheFly,
        List<ProblemDescriptor> problems
    ) {
        String tagName = tag.getName();
        StringTokenizer tokenizer = new StringTokenizer(myAdditionalJavadocTags, ", ");
        while (tokenizer.hasMoreTokens()) {
            if (Comparing.strEqual(tagName, tokenizer.nextToken())) {
                return true;
            }
        }

        PsiElement nameElement = tag.getNameElement();
        if (nameElement != null) {
            problems.add(
                inspectionManager.newProblemDescriptor(
                        tagInfo == null
                            ? InspectionLocalize.inspectionJavadocProblemWrongTag("<code>" + tagName + "</code>")
                            : InspectionLocalize.inspectionJavadocProblemDisallowedTag("<code>" + tagName + "</code>")
                    )
                    .range(nameElement)
                    .onTheFly(isOnTheFly)
                    .withFix(new AddUnknownTagToCustoms(tag.getName()))
                    .create()
            );
        }
        return false;
    }

    @RequiredReadAction
    private void checkInlineTags(
        InspectionManager inspectionManager,
        List<ProblemDescriptor> problems,
        PsiElement[] dataElements,
        JavadocManager manager,
        boolean isOnTheFly
    ) {
        for (PsiElement dataElement : dataElements) {
            if (dataElement instanceof PsiInlineDocTag inlineDocTag) {
                PsiElement nameElement = inlineDocTag.getNameElement();
                if (manager.getTagInfo(inlineDocTag.getName()) == null) {
                    checkTagInfo(inspectionManager, null, inlineDocTag, isOnTheFly, problems);
                }
                if (!IGNORE_POINT_TO_ITSELF) {
                    PsiDocTagValue value = inlineDocTag.getValueElement();
                    if (value != null) {
                        PsiReference reference = value.getReference();
                        if (reference != null) {
                            PsiElement ref = reference.resolve();
                            if (ref != null && PsiTreeUtil.getParentOfType(inlineDocTag, PsiDocCommentOwner.class)
                                == PsiTreeUtil.getParentOfType(ref, PsiDocCommentOwner.class, false)
                                && nameElement != null) {
                                problems.add(
                                    inspectionManager.newProblemDescriptor(InspectionLocalize.inspectionJavadocProblemPointingToItself())
                                        .range(nameElement)
                                        .onTheFly(isOnTheFly)
                                        .create()
                                );
                            }
                        }
                    }
                }
            }
        }
    }

    @SuppressWarnings({"SimplifiableIfStatement"})
    private boolean isTagRequired(PsiElement context, String tag) {
        if (context instanceof PsiClass) {
            if (PsiTreeUtil.getParentOfType(context, PsiClass.class) != null) {
                return isTagRequired(INNER_CLASS_OPTIONS, tag);
            }

            return isTagRequired(TOP_LEVEL_CLASS_OPTIONS, tag);
        }

        if (context instanceof PsiMethod) {
            return isTagRequired(METHOD_OPTIONS, tag);
        }

        if (context instanceof PsiField) {
            return isTagRequired(FIELD_OPTIONS, tag);
        }

        return false;
    }

    private static boolean isTagRequired(Options options, String tag) {
        return options.REQUIRED_TAGS.contains(tag);
    }

    private boolean isJavaDocRequired(PsiModifierListOwner psiElement) {
        RefJavaUtil refUtil = RefJavaUtil.getInstance();
        int actualAccess = getAccessNumber(refUtil.getAccessModifier(psiElement));
        if (psiElement instanceof PsiClass psiClass) {
            if (PsiTreeUtil.getParentOfType(psiClass, PsiClass.class) != null) {
                return actualAccess <= getAccessNumber(INNER_CLASS_OPTIONS.ACCESS_JAVADOC_REQUIRED_FOR);
            }

            return actualAccess <= getAccessNumber(TOP_LEVEL_CLASS_OPTIONS.ACCESS_JAVADOC_REQUIRED_FOR);
        }

        if (psiElement instanceof PsiMethod) {
            psiElement = PsiTreeUtil.getParentOfType(psiElement, PsiClass.class);
            while (psiElement != null) {
                actualAccess = Math.max(actualAccess, getAccessNumber(refUtil.getAccessModifier(psiElement)));
                psiElement = PsiTreeUtil.getParentOfType(psiElement, PsiClass.class);
            }

            return actualAccess <= getAccessNumber(METHOD_OPTIONS.ACCESS_JAVADOC_REQUIRED_FOR);
        }

        if (psiElement instanceof PsiField) {
            psiElement = PsiTreeUtil.getParentOfType(psiElement, PsiClass.class);
            while (psiElement != null) {
                actualAccess = Math.max(actualAccess, getAccessNumber(refUtil.getAccessModifier(psiElement)));
                psiElement = PsiTreeUtil.getParentOfType(psiElement, PsiClass.class);
            }

            return actualAccess <= getAccessNumber(FIELD_OPTIONS.ACCESS_JAVADOC_REQUIRED_FOR);
        }

        return false;
    }

    @RequiredReadAction
    private void checkDuplicateTags(
        PsiDocTag[] tags,
        List<ProblemDescriptor> problems,
        InspectionManager manager,
        boolean isOnTheFly
    ) {
        Set<String> documentedParamNames = null;
        Set<String> documentedExceptions = null;
        Set<String> uniqueTags = null;
        for (PsiDocTag tag : tags) {
            if ("param".equals(tag.getName())) {
                if (tag.getValueElement() instanceof PsiDocParamRef paramRef) {
                    PsiReference reference = paramRef.getReference();
                    if (reference != null) {
                        String paramName = reference.getCanonicalText();
                        if (documentedParamNames == null) {
                            documentedParamNames = new HashSet<>();
                        }
                        if (documentedParamNames.contains(paramName)) {
                            problems.add(
                                manager.newProblemDescriptor(InspectionLocalize.inspectionJavadocProblemDuplicateParam(paramName))
                                    .range(tag.getNameElement())
                                    .onTheFly(isOnTheFly)
                                    .create()
                            );
                        }
                        documentedParamNames.add(paramName);
                    }
                }
            }
            else if (!IGNORE_DUPLICATED_THROWS && ("throws".equals(tag.getName()) || "exception".equals(tag.getName()))) {
                PsiDocTagValue value = tag.getValueElement();
                if (value != null) {
                    PsiElement firstChild = value.getFirstChild();
                    if (firstChild != null
                        && firstChild.getFirstChild() instanceof PsiJavaCodeReferenceElement refElement
                        && refElement.resolve() instanceof PsiClass psiClass) {
                        String fqName = psiClass.getQualifiedName();
                        if (documentedExceptions == null) {
                            documentedExceptions = new HashSet<>();
                        }
                        if (documentedExceptions.contains(fqName)) {
                            problems.add(
                                manager.newProblemDescriptor(InspectionLocalize.inspectionJavadocProblemDuplicateThrows(fqName))
                                    .range(tag.getNameElement())
                                    .onTheFly(isOnTheFly)
                                    .create()
                            );
                        }
                        documentedExceptions.add(fqName);
                    }
                }
            }
            else if (JavaDocLocalInspection.ourUniqueTags.contains(tag.getName())) {
                if (uniqueTags == null) {
                    uniqueTags = new HashSet<>();
                }
                if (uniqueTags.contains(tag.getName())) {
                    problems.add(
                        manager.newProblemDescriptor(InspectionLocalize.inspectionJavadocProblemDuplicateTag(tag.getName()))
                            .range(tag.getNameElement())
                            .onTheFly(isOnTheFly)
                            .create()
                    );
                }
                uniqueTags.add(tag.getName());
            }
        }
    }

    private static int getAccessNumber(String accessModifier) {
        if (accessModifier.startsWith("none")) {
            return 0;
        }
        if (accessModifier.startsWith("public")) {
            return 1;
        }
        if (accessModifier.startsWith("protected")) {
            return 2;
        }
        if (accessModifier.startsWith("package")) {
            return 3;
        }
        if (accessModifier.startsWith("private")) {
            return 4;
        }

        return 5;
    }

    @Override
    @Nonnull
    public LocalizeValue getDisplayName() {
        return InspectionLocalize.inspectionJavadocDisplayName();
    }

    @Override
    @Nonnull
    public LocalizeValue getGroupDisplayName() {
        return InspectionLocalize.groupNamesJavadocIssues();
    }

    @Override
    @Nonnull
    public String getShortName() {
        return SHORT_NAME;
    }

    public void setIgnoreEmptyDescriptions(boolean ignoreEmptyDescriptions) {
        myIgnoreEmptyDescriptions = ignoreEmptyDescriptions;
    }

    private class AddUnknownTagToCustoms implements LocalQuickFix {
        private final String myTag;

        public AddUnknownTagToCustoms(String tag) {
            myTag = tag;
        }

        @Override
        @Nonnull
        public LocalizeValue getName() {
            return JavaQuickFixLocalize.addDoctagToCustomTags(myTag);
        }

        @Override
        public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
            if (myTag == null) {
                return;
            }
            if (myAdditionalJavadocTags.length() > 0) {
                myAdditionalJavadocTags += "," + myTag;
            }
            else {
                myAdditionalJavadocTags = myTag;
            }
            InspectionProfile inspectionProfile =
                InspectionProjectProfileManager.getInstance(project).getInspectionProfile();
            //correct save settings
            InspectionProfileManager.getInstance().fireProfileChanged(inspectionProfile);
            //TODO lesya
            /*
            try {
                inspectionProfile.save();
            }
            catch (IOException e) {
                Messages.showErrorDialog(project, e.getMessage(), CommonBundle.getErrorTitle());
            }
            */
        }
    }
}
