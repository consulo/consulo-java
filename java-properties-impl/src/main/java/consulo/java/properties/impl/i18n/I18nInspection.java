/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

/**
 * @author cdr
 */
package consulo.java.properties.impl.i18n;

import com.intellij.java.analysis.codeInspection.BatchSuppressManager;
import com.intellij.java.analysis.impl.codeInsight.intention.AddAnnotationFix;
import com.intellij.java.analysis.impl.codeInspection.ex.BaseLocalInspectionTool;
import com.intellij.java.analysis.refactoring.IntroduceConstantHandler;
import com.intellij.java.analysis.refactoring.JavaRefactoringActionHandlerFactory;
import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.codeInsight.AnnotationUtil;
import com.intellij.java.language.impl.refactoring.util.RefactoringChangeUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.MethodSignature;
import com.intellij.java.language.psi.util.MethodSignatureUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.util.TreeClassChooser;
import com.intellij.java.language.util.TreeClassChooserFactory;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.document.Document;
import consulo.java.analysis.impl.util.JavaI18nUtil;
import consulo.java.language.module.util.JavaClassNames;
import consulo.language.Language;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.localize.InspectionLocalize;
import consulo.language.editor.inspection.scheme.InspectionManager;
import consulo.language.editor.intention.SuppressIntentionAction;
import consulo.language.editor.localize.CodeInsightLocalize;
import consulo.language.editor.refactoring.RefactoringBundle;
import consulo.language.psi.*;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.project.ProjectManager;
import consulo.ui.ex.awt.AddDeleteListPanel;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.FieldPanel;
import consulo.ui.ex.awt.ScrollPaneFactory;
import consulo.ui.ex.awt.event.DocumentAdapter;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.Comparing;
import consulo.util.lang.StringUtil;
import consulo.util.xml.serializer.InvalidDataException;
import consulo.util.xml.serializer.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ExtensionImpl
public class I18nInspection extends BaseLocalInspectionTool {
  public boolean ignoreForAssertStatements = true;
  public boolean ignoreForExceptionConstructors = true;
  @NonNls
  public String ignoreForSpecifiedExceptionConstructors = "";
  public boolean ignoreForJUnitAsserts = true;
  public boolean ignoreForClassReferences = true;
  public boolean ignoreForPropertyKeyReferences = true;
  public boolean ignoreForNonAlpha = true;
  public boolean ignoreAssignedToConstants = false;
  public boolean ignoreToString = false;
  @NonNls
  public String nonNlsCommentPattern = "NON-NLS";
  private boolean ignoreForEnumConstants = false;

  private static final LocalQuickFix I18N_QUICK_FIX = new I18nizeQuickFix();
  private static final I18nizeConcatenationQuickFix I18N_CONCATENATION_QUICK_FIX = new I18nizeConcatenationQuickFix();

  @Nullable
  private Pattern myCachedNonNlsPattern;
  @NonNls
  private static final String TO_STRING = "toString";

  public I18nInspection() {
    cacheNonNlsCommentPattern();
  }

  @Override
  public SuppressIntentionAction[] getSuppressActions(PsiElement element) {
    SuppressIntentionAction[] actions = {};
    if (myCachedNonNlsPattern != null) {
      actions = new SuppressIntentionAction[]{new SuppressByCommentOutAction(nonNlsCommentPattern)};
    }
    return ArrayUtil.mergeArrays(actions, super.getSuppressActions(element));
  }

  private static final String SKIP_FOR_ENUM = "ignoreForEnumConstant";

  @Override
  public void writeSettings(@Nonnull Element node) throws WriteExternalException {
    super.writeSettings(node);
    if (ignoreForEnumConstants) {
      final Element e = new Element("option");
      e.setAttribute("name", SKIP_FOR_ENUM);
      e.setAttribute("value", Boolean.toString(ignoreForEnumConstants));
      node.addContent(e);
    }
  }

  @Override
  public void readSettings(@Nonnull Element node) throws InvalidDataException {
    super.readSettings(node);
    for (Object o : node.getChildren()) {
      if (o instanceof Element && Comparing.strEqual(node.getAttributeValue("name"), SKIP_FOR_ENUM)) {
        final String ignoreForConstantsAttr = node.getAttributeValue("value");
        if (ignoreForConstantsAttr != null) {
          ignoreForEnumConstants = Boolean.parseBoolean(ignoreForConstantsAttr);
        }
        break;
      }
    }
    cacheNonNlsCommentPattern();
  }

  @Override
  @Nonnull
  public String getGroupDisplayName() {
    return InspectionLocalize.groupNamesInternationalizationIssues().get();
  }

  @Override
  @Nonnull
  public String getDisplayName() {
    return CodeInsightLocalize.inspectionI18nDisplayName().get();
  }

  @Nullable
  @Override
  public Language getLanguage() {
    return JavaLanguage.INSTANCE;
  }

  @Override
  @Nonnull
  public String getShortName() {
    return "HardCodedStringLiteral";
  }

  @Override
  public JComponent createOptionsPanel() {
    final GridBagLayout layout = new GridBagLayout();
    final JPanel panel = new JPanel(layout);
    final JCheckBox assertStatementsCheckbox =
      new JCheckBox(CodeInsightLocalize.inspectionI18nOptionIgnoreAssert().get(), ignoreForAssertStatements);
    assertStatementsCheckbox.addChangeListener(e -> ignoreForAssertStatements = assertStatementsCheckbox.isSelected());
    final JCheckBox exceptionConstructorCheck =
      new JCheckBox(
        CodeInsightLocalize.inspectionI18nOptionIgnoreForExceptionConstructorArguments().get(),
        ignoreForExceptionConstructors
      );
    exceptionConstructorCheck.addChangeListener(e -> ignoreForExceptionConstructors = exceptionConstructorCheck.isSelected());

    final JTextField specifiedExceptions = new JTextField(ignoreForSpecifiedExceptionConstructors);
    specifiedExceptions.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        ignoreForSpecifiedExceptionConstructors = specifiedExceptions.getText();
      }
    });

    final JCheckBox junitAssertCheckbox = new JCheckBox(
      CodeInsightLocalize.inspectionI18nOptionIgnoreForJunitAssertArguments().get(),
      ignoreForJUnitAsserts
    );
    junitAssertCheckbox.addChangeListener(e -> ignoreForJUnitAsserts = junitAssertCheckbox.isSelected());
    final JCheckBox classRef =
      new JCheckBox(CodeInsightLocalize.inspectionI18nOptionIgnoreQualifiedClassNames().get(), ignoreForClassReferences);
    classRef.addChangeListener(e -> ignoreForClassReferences = classRef.isSelected());
    final JCheckBox propertyRef =
      new JCheckBox(CodeInsightLocalize.inspectionI18nOptionIgnorePropertyKeys().get(), ignoreForPropertyKeyReferences);
    propertyRef.addChangeListener(e -> ignoreForPropertyKeyReferences = propertyRef.isSelected());
    final JCheckBox nonAlpha =
      new JCheckBox(CodeInsightLocalize.inspectionI18nOptionIgnoreNonalphanumerics().get(), ignoreForNonAlpha);
    nonAlpha.addChangeListener(e -> ignoreForNonAlpha = nonAlpha.isSelected());
    final JCheckBox assignedToConstants =
      new JCheckBox(CodeInsightLocalize.inspectionI18nOptionIgnoreAssignedToConstants().get(), ignoreAssignedToConstants);
    assignedToConstants.addChangeListener(e -> ignoreAssignedToConstants = assignedToConstants.isSelected());
    final JCheckBox chkToString = new JCheckBox(CodeInsightLocalize.inspectionI18nOptionIgnoreTostring().get(), ignoreToString);
    chkToString.addChangeListener(e -> ignoreToString = chkToString.isSelected());

    final JCheckBox ignoreEnumConstants = new JCheckBox("Ignore enum constants", ignoreForEnumConstants);
    ignoreEnumConstants.addChangeListener(e -> ignoreForEnumConstants = ignoreEnumConstants.isSelected());

    final GridBagConstraints gc = new GridBagConstraints();
    gc.fill = GridBagConstraints.HORIZONTAL;
    gc.insets.bottom = 2;

    gc.gridx = GridBagConstraints.REMAINDER;
    gc.gridy = 0;
    gc.weightx = 1;
    gc.weighty = 0;
    panel.add(assertStatementsCheckbox, gc);

    gc.gridy++;
    panel.add(junitAssertCheckbox, gc);

    gc.gridy++;
    panel.add(exceptionConstructorCheck, gc);

    gc.gridy++;
    final Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
    panel.add(
      new FieldPanel(
        specifiedExceptions,
        null,
        CodeInsightLocalize.inspectionI18nOptionIgnoreForSpecifiedExceptionConstructorArguments().get(),
        openProjects.length == 0 ? null : e -> createIgnoreExceptionsConfigurationDialog(openProjects[0], specifiedExceptions).show(),
        null
      ),
      gc
    );

    gc.gridy++;
    panel.add(classRef, gc);

    gc.gridy++;
    panel.add(propertyRef, gc);

    gc.gridy++;
    panel.add(assignedToConstants, gc);

    gc.gridy++;
    panel.add(chkToString, gc);

    gc.gridy++;
    panel.add(nonAlpha, gc);

    gc.gridy++;
    panel.add(ignoreEnumConstants, gc);

    gc.gridy++;
    gc.anchor = GridBagConstraints.NORTHWEST;
    gc.weighty = 1;
    final JTextField text = new JTextField(nonNlsCommentPattern);
    final FieldPanel nonNlsCommentPatternComponent =
      new FieldPanel(
        text,
        CodeInsightLocalize.inspectionI18nOptionIgnoreCommentPattern().get(),
        CodeInsightLocalize.inspectionI18nOptionIgnoreCommentTitle().get(),
        null,
        () -> {
          nonNlsCommentPattern = text.getText();
          cacheNonNlsCommentPattern();
        }
      );
    panel.add(nonNlsCommentPatternComponent, gc);

    final JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(panel);
    scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
    scrollPane.setBorder(null);
    scrollPane.setPreferredSize(new Dimension(
      panel.getPreferredSize().width + scrollPane.getVerticalScrollBar().getPreferredSize().width,
      panel.getPreferredSize().height + scrollPane.getHorizontalScrollBar().getPreferredSize().height
    ));
    return scrollPane;
  }

  @SuppressWarnings({"NonStaticInitializer"})
  private DialogWrapper createIgnoreExceptionsConfigurationDialog(final Project project, final JTextField specifiedExceptions) {
    return new DialogWrapper(true) {
      private AddDeleteListPanel myPanel;

      {
        setTitle(CodeInsightLocalize.inspectionI18nOptionIgnoreForSpecifiedExceptionConstructorArguments());
        init();
      }

      @Override
      protected JComponent createCenterPanel() {
        final String[] ignored = ignoreForSpecifiedExceptionConstructors.split(",");
        final List<String> initialList = new ArrayList<>();
        if (ignored != null) {
          for (String e : ignored) {
            if (e.length() > 0) initialList.add(e);
          }
        }
        myPanel = new AddDeleteListPanel<>(null, initialList) {
          @Override
          protected String findItemToAdd() {
            final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
            TreeClassChooser chooser = TreeClassChooserFactory.getInstance(project).
              createInheritanceClassChooser(
                CodeInsightLocalize.inspectionI18nOptionIgnoreForSpecifiedExceptionConstructorArguments().get(),
                scope,
                JavaPsiFacade.getInstance(project).findClass("java.lang.Throwable", scope),
                true,
                true,
                null
              );
            chooser.showDialog();
            PsiClass selectedClass = chooser.getSelected();
            return selectedClass != null ? selectedClass.getQualifiedName() : null;
          }
        };
        return myPanel;
      }

      @Override
      protected void doOKAction() {
        StringBuilder buf = new StringBuilder();
        final Object[] exceptions = myPanel.getListItems();
        for (Object exception : exceptions) {
          buf.append(",").append(exception);
        }
        specifiedExceptions.setText(buf.length() > 0 ? buf.substring(1) : buf.toString());
        super.doOKAction();
      }
    };
  }

  @Override
  @Nullable
  public ProblemDescriptor[] checkMethod(@Nonnull PsiMethod method, @Nonnull InspectionManager manager, boolean isOnTheFly, Object state) {
    PsiClass containingClass = method.getContainingClass();
    if (containingClass == null || isClassNonNls(containingClass)) {
      return null;
    }
    final PsiCodeBlock body = method.getBody();
    if (body != null) {
      return checkElement(body, manager, isOnTheFly);
    }
    return null;
  }

  @Override
  @Nullable
  public ProblemDescriptor[] checkClass(@Nonnull PsiClass aClass, @Nonnull InspectionManager manager, boolean isOnTheFly, Object state) {
    if (isClassNonNls(aClass)) {
      return null;
    }
    final PsiClassInitializer[] initializers = aClass.getInitializers();
    List<ProblemDescriptor> result = new ArrayList<>();
    for (PsiClassInitializer initializer : initializers) {
      final ProblemDescriptor[] descriptors = checkElement(initializer, manager, isOnTheFly);
      if (descriptors != null) {
        ContainerUtil.addAll(result, descriptors);
      }
    }

    return result.isEmpty() ? null : result.toArray(new ProblemDescriptor[result.size()]);
  }

  @Override
  @Nullable
  public ProblemDescriptor[] checkField(@Nonnull PsiField field, @Nonnull InspectionManager manager, boolean isOnTheFly, Object state) {
    PsiClass containingClass = field.getContainingClass();
    if (containingClass == null || isClassNonNls(containingClass)) {
      return null;
    }
    if (AnnotationUtil.isAnnotated(field, AnnotationUtil.NON_NLS, false, false)) {
      return null;
    }
    final PsiExpression initializer = field.getInitializer();
    if (initializer != null) return checkElement(initializer, manager, isOnTheFly);

    if (field instanceof PsiEnumConstant enumConstant) {
      return checkElement(enumConstant.getArgumentList(), manager, isOnTheFly);
    }
    return null;
  }

  private ProblemDescriptor[] checkElement(final PsiElement element, InspectionManager manager, boolean isOnTheFly) {
    StringI18nVisitor visitor = new StringI18nVisitor(manager, isOnTheFly);
    element.accept(visitor);
    List<ProblemDescriptor> problems = visitor.getProblems();
    return problems.isEmpty() ? null : problems.toArray(new ProblemDescriptor[problems.size()]);
  }

  private static LocalQuickFix createIntroduceConstantFix() {
    return new LocalQuickFix() {
      @Override
      @Nonnull
      public String getName() {
        return RefactoringBundle.message("introduce.constant.title");
      }

      @Override
      public void applyFix(@Nonnull final Project project, @Nonnull final ProblemDescriptor descriptor) {
        //do it later because it is invoked from write action
        project.getApplication().invokeLater(
          () -> {
            PsiElement element = descriptor.getPsiElement();
            if (!(element instanceof PsiExpression)) return;

            PsiExpression[] expressions = {(PsiExpression)element};

            IntroduceConstantHandler handler = JavaRefactoringActionHandlerFactory.getInstance().createIntroduceConstantHandler();

            handler.invoke(project, expressions);
          },
          project.getDisposed()
        );
      }

      @Override
      @Nonnull
      public String getFamilyName() {
        return getName();
      }
    };
  }

  private class StringI18nVisitor extends JavaRecursiveElementVisitor {
    private final List<ProblemDescriptor> myProblems = new ArrayList<>();
    private final InspectionManager myManager;
    private final boolean myOnTheFly;

    public StringI18nVisitor(final InspectionManager manager, boolean onTheFly) {
      myManager = manager;
      myOnTheFly = onTheFly;
    }

    @Override
    public void visitAnonymousClass(PsiAnonymousClass aClass) {
      final PsiExpressionList argumentList = aClass.getArgumentList();
      if (argumentList != null) {
        argumentList.accept(this);
      }
    }

    @Override
    public void visitClass(@Nonnull PsiClass aClass) {
    }

    @Override
    public void visitField(@Nonnull PsiField field) {
    }

    @Override
    public void visitMethod(@Nonnull PsiMethod method) {
    }

    @Override
    public void visitLiteralExpression(PsiLiteralExpression expression) {
      Object value = expression.getValue();
      if (!(value instanceof String)) return;
      String stringValue = (String)value;
      if (stringValue.trim().length() == 0) {
        return;
      }

      Set<PsiModifierListOwner> nonNlsTargets = new HashSet<>();
      if (canBeI18ned(myManager.getProject(), expression, stringValue, nonNlsTargets)) {
        PsiField parentField = PsiTreeUtil.getParentOfType(expression, PsiField.class);
        if (parentField != null) {
          nonNlsTargets.add(parentField);
        }

        final LocalizeValue description = CodeInsightLocalize.inspectionI18nMessageGeneralWithValue("#ref");

        List<LocalQuickFix> fixes = new ArrayList<>();
        if (I18nizeConcatenationQuickFix.getEnclosingLiteralConcatenation(expression) != null) {
          fixes.add(I18N_CONCATENATION_QUICK_FIX);
        }
        fixes.add(I18N_QUICK_FIX);

        if (!isNotConstantFieldInitializer(expression)) {
          fixes.add(createIntroduceConstantFix());
        }

        final Project project = expression.getManager().getProject();
        final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
        if (PsiUtil.isLanguageLevel5OrHigher(expression)) {
          for (PsiModifierListOwner element : nonNlsTargets) {
            if (!AnnotationUtil.isAnnotated(element, AnnotationUtil.NLS, true, false)) {
              if (!element.getManager().isInProject(element) || facade.findClass(AnnotationUtil.NON_NLS,
                                                                                 element.getResolveScope()) != null) {
                fixes.add(new AddAnnotationFix(AnnotationUtil.NON_NLS, element));
              }
            }
          }
        }

        LocalQuickFix[] farr = fixes.toArray(new LocalQuickFix[fixes.size()]);
        final ProblemDescriptor problem =
          myManager.createProblemDescriptor(expression, description.get(), myOnTheFly, farr, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
        myProblems.add(problem);
      }
    }

    private boolean isNotConstantFieldInitializer(final PsiExpression expression) {
      PsiField parentField = expression.getParent() instanceof PsiField field ? field : null;
      return parentField != null && expression == parentField.getInitializer() &&
        parentField.hasModifierProperty(PsiModifier.FINAL) &&
        parentField.hasModifierProperty(PsiModifier.STATIC);
    }


    @Override
    public void visitAnnotation(PsiAnnotation annotation) {
      //prevent from @SuppressWarnings
      if (!BatchSuppressManager.SUPPRESS_INSPECTIONS_ANNOTATION_NAME.equals(annotation.getQualifiedName())) {
        super.visitAnnotation(annotation);
      }
    }

    public List<ProblemDescriptor> getProblems() {
      return myProblems;
    }
  }

  @RequiredReadAction
  private boolean canBeI18ned(
    @Nonnull Project project,
    @Nonnull PsiLiteralExpression expression,
    @Nonnull String value,
    @Nonnull Set<PsiModifierListOwner> nonNlsTargets
  ) {
    if (ignoreForNonAlpha && !StringUtil.containsAlphaCharacters(value)) {
      return false;
    }

    if (JavaI18nUtil.isPassedToAnnotatedParam(project, expression, AnnotationUtil.NON_NLS, new HashMap<>(), nonNlsTargets)) {
      return false;
    }

    if (isInNonNlsCall(project, expression, nonNlsTargets)) {
      return false;
    }

    if (isInNonNlsEquals(expression, nonNlsTargets)) {
      return false;
    }

    if (isPassedToNonNlsVariable(project, expression, nonNlsTargets)) {
      return false;
    }

    if (JavaI18nUtil.mustBePropertyKey(project, expression, new HashMap<>())) {
      return false;
    }

    if (isReturnedFromNonNlsMethod(expression, nonNlsTargets)) {
      return false;
    }
    if (ignoreForAssertStatements && isArgOfAssertStatement(expression)) {
      return false;
    }
    if (ignoreForExceptionConstructors && isArgOfExceptionConstructor(expression)) {
      return false;
    }
    if (ignoreForEnumConstants && isArgOfEnumConstant(expression)) {
      return false;
    }
    if (!ignoreForExceptionConstructors && isArgOfSpecifiedExceptionConstructor(expression,
                                                                                ignoreForSpecifiedExceptionConstructors.split(","))) {
      return false;
    }
    if (ignoreForJUnitAsserts && isArgOfJUnitAssertion(expression)) {
      return false;
    }
    if (ignoreForClassReferences && isClassRef(expression, value)) {
      return false;
    }
    if (ignoreForPropertyKeyReferences && JavaPropertiesUtil.isPropertyRef(expression, value, null)) {
      return false;
    }
    if (ignoreToString && isToString(expression)) {
      return false;
    }

    Pattern pattern = myCachedNonNlsPattern;
    if (pattern != null) {
      PsiFile file = expression.getContainingFile();
      Document document = PsiDocumentManager.getInstance(project).getDocument(file);
      int line = document.getLineNumber(expression.getTextRange().getStartOffset());
      int lineStartOffset = document.getLineStartOffset(line);
      CharSequence lineText = document.getCharsSequence().subSequence(lineStartOffset, document.getLineEndOffset(line));

      Matcher matcher = pattern.matcher(lineText);
      int start = 0;
      while (matcher.find(start)) {
        start = matcher.start();
        PsiElement element = file.findElementAt(lineStartOffset + start);
        if (PsiTreeUtil.getParentOfType(element, PsiComment.class, false) != null) return false;
        if (start == lineText.length() - 1) break;
        start++;
      }
    }

    return true;
  }

  private boolean isArgOfEnumConstant(PsiLiteralExpression expression) {
    final PsiElement parent = PsiTreeUtil.getParentOfType(expression, PsiExpressionList.class, PsiClass.class);
    if (!(parent instanceof PsiExpressionList)) {
      return false;
    }
    final PsiElement grandparent = parent.getParent();
    return grandparent instanceof PsiEnumConstant;
  }

  public void cacheNonNlsCommentPattern() {
    myCachedNonNlsPattern = nonNlsCommentPattern.trim().length() == 0 ? null : Pattern.compile(nonNlsCommentPattern);
  }

  private static boolean isClassRef(final PsiLiteralExpression expression, String value) {
    if (StringUtil.startsWithChar(value, '#')) {
      value = value.substring(1); // A favor for JetBrains team to catch common Logger usage practice.
    }

    return JavaPsiFacade.getInstance(expression.getProject()).findClass(value, GlobalSearchScope.allScope(expression.getProject())) != null;
  }

  @Override
  public boolean isEnabledByDefault() {
    return false;
  }

  private static boolean isClassNonNls(@Nonnull PsiClass clazz) {
    final PsiDirectory directory = clazz.getContainingFile().getContainingDirectory();
    return directory != null && isPackageNonNls(JavaDirectoryService.getInstance().getPackage(directory));
  }

  @RequiredReadAction
  public static boolean isPackageNonNls(final PsiJavaPackage psiPackage) {
    if (psiPackage == null || psiPackage.getName() == null) {
      return false;
    }
    final PsiModifierList pkgModifierList = psiPackage.getAnnotationList();
    return pkgModifierList != null && pkgModifierList.findAnnotation(AnnotationUtil.NON_NLS) != null
      || isPackageNonNls(psiPackage.getParentPackage());
  }

  @RequiredReadAction
  private boolean isPassedToNonNlsVariable(
    @Nonnull Project project,
    @Nonnull PsiLiteralExpression expression,
    final Set<PsiModifierListOwner> nonNlsTargets
  ) {
    PsiExpression toplevel = JavaI18nUtil.getToplevelExpression(project, expression);
    PsiVariable var = null;
    if (toplevel instanceof PsiAssignmentExpression assignmentExpression) {
      PsiExpression lExpression = assignmentExpression.getLExpression();
      while (lExpression instanceof PsiArrayAccessExpression arrayAccessExpression) {
        lExpression = arrayAccessExpression.getArrayExpression();
      }
      if (lExpression instanceof PsiReferenceExpression referenceExpression) {
        final PsiElement resolved = referenceExpression.resolve();
        if (resolved instanceof PsiVariable variable) var = variable;
      }
    }

    if (var == null) {
      PsiElement parent = toplevel.getParent();
      if (parent instanceof PsiVariable variable && toplevel.equals(variable.getInitializer())) {
        var = variable;
      }
    }

    if (var != null) {
      if (annotatedAsNonNls(var)) {
        return true;
      }
      if (ignoreAssignedToConstants &&
        var.hasModifierProperty(PsiModifier.STATIC) &&
        var.hasModifierProperty(PsiModifier.FINAL)) {
        return true;
      }
      nonNlsTargets.add(var);
    }
    return false;
  }

  private static boolean annotatedAsNonNls(final PsiModifierListOwner parent) {
    if (parent instanceof PsiParameter parameter) {
      final PsiElement declarationScope = parameter.getDeclarationScope();
      if (declarationScope instanceof PsiMethod method) {
        final int index = method.getParameterList().getParameterIndex(parameter);
        return JavaI18nUtil.isMethodParameterAnnotatedWith(method, index, null, AnnotationUtil.NON_NLS, null, null);
      }
    }
    return AnnotationUtil.isAnnotated(parent, AnnotationUtil.NON_NLS, false, false);
  }

  private static boolean isInNonNlsEquals(PsiExpression expression, final Set<PsiModifierListOwner> nonNlsTargets) {
    if (!(expression.getParent().getParent() instanceof PsiMethodCallExpression)) {
      return false;
    }
    final PsiMethodCallExpression call = (PsiMethodCallExpression)expression.getParent().getParent();
    final PsiReferenceExpression methodExpression = call.getMethodExpression();
    final PsiExpression qualifier = methodExpression.getQualifierExpression();
    if (qualifier != expression) {
      return false;
    }
    if (!"equals".equals(methodExpression.getReferenceName())) {
      return false;
    }
    final PsiElement resolved = methodExpression.resolve();
    if (!(resolved instanceof PsiMethod)) {
      return false;
    }
    PsiType objectType = PsiType.getJavaLangObject(resolved.getManager(), resolved.getResolveScope());
    MethodSignature equalsSignature = MethodSignatureUtil.createMethodSignature(
      "equals",
      new PsiType[]{objectType},
      PsiTypeParameter.EMPTY_ARRAY,
      PsiSubstitutor.EMPTY
    );
    if (!equalsSignature.equals(((PsiMethod)resolved).getSignature(PsiSubstitutor.EMPTY))) {
      return false;
    }
    final PsiExpression[] expressions = call.getArgumentList().getExpressions();
    if (expressions.length != 1) {
      return false;
    }
    final PsiExpression arg = expressions[0];
    PsiReferenceExpression ref = null;
    if (arg instanceof PsiReferenceExpression referenceExpression) {
      ref = referenceExpression;
    }
    else if (arg instanceof PsiMethodCallExpression methodCallExpression) ref = methodCallExpression.getMethodExpression();
    if (ref != null) {
      final PsiElement resolvedEntity = ref.resolve();
      if (resolvedEntity instanceof PsiModifierListOwner modifierListOwner) {
        if (annotatedAsNonNls(modifierListOwner)) {
          return true;
        }
        nonNlsTargets.add(modifierListOwner);
      }
    }
    return false;
  }

  private static boolean isInNonNlsCall(
    @Nonnull Project project,
    @Nonnull PsiExpression expression,
    final Set<PsiModifierListOwner> nonNlsTargets
  ) {
    expression = JavaI18nUtil.getToplevelExpression(project, expression);
    final PsiElement parent = expression.getParent();
    if (parent instanceof PsiExpressionList) {
      final PsiElement grParent = parent.getParent();
      if (grParent instanceof PsiMethodCallExpression methodCallExpression) {
        return isNonNlsCall(methodCallExpression, nonNlsTargets);
      }
      else if (grParent instanceof PsiNewExpression) {
        final PsiElement parentOfNew = grParent.getParent();
        if (parentOfNew instanceof PsiLocalVariable newVariable) {
          if (annotatedAsNonNls(newVariable)) {
            return true;
          }
          nonNlsTargets.add(newVariable);
          return false;
        }
        else if (parentOfNew instanceof PsiAssignmentExpression assignmentExpression) {
          final PsiExpression lExpression = assignmentExpression.getLExpression();
          if (lExpression instanceof PsiReferenceExpression referenceExpression) {
            final PsiElement resolved = referenceExpression.resolve();
            if (resolved instanceof PsiModifierListOwner modifierListOwner) {
              if (annotatedAsNonNls(modifierListOwner)) {
                return true;
              }
              nonNlsTargets.add(modifierListOwner);
              return false;
            }
          }
        }
      }
    }

    return false;
  }

  private static boolean isNonNlsCall(PsiMethodCallExpression grParent, Set<PsiModifierListOwner> nonNlsTargets) {
    final PsiReferenceExpression methodExpression = grParent.getMethodExpression();
    final PsiExpression qualifier = methodExpression.getQualifierExpression();
    if (qualifier instanceof PsiReferenceExpression referenceExpression) {
      final PsiElement resolved = referenceExpression.resolve();
      if (resolved instanceof PsiModifierListOwner modifierListOwner) {
        if (annotatedAsNonNls(modifierListOwner)) {
          return true;
        }
        nonNlsTargets.add(modifierListOwner);
        return false;
      }
    }
    else if (qualifier instanceof PsiMethodCallExpression methodCallExpression) {
      final PsiType type = qualifier.getType();
      if (type != null && type.equals(methodExpression.getType())) {
        return isNonNlsCall(methodCallExpression, nonNlsTargets);
      }
    }
    return false;
  }

  private static boolean isReturnedFromNonNlsMethod(final PsiLiteralExpression expression, final Set<PsiModifierListOwner> nonNlsTargets) {
    PsiElement parent = expression.getParent();
    PsiMethod method;
    if (parent instanceof PsiNameValuePair nameValuePair) {
      method = AnnotationUtil.getAnnotationMethod(nameValuePair);
    }
    else {
      final PsiElement returnStmt = PsiTreeUtil.getParentOfType(expression, PsiReturnStatement.class, PsiMethodCallExpression.class);
      if (returnStmt == null || !(returnStmt instanceof PsiReturnStatement)) {
        return false;
      }
      method = PsiTreeUtil.getParentOfType(expression, PsiMethod.class);
    }
    if (method == null) return false;

    if (AnnotationUtil.isAnnotated(method, AnnotationUtil.NON_NLS, true, false)) {
      return true;
    }
    nonNlsTargets.add(method);
    return false;
  }

  private static boolean isToString(final PsiLiteralExpression expression) {
    final PsiMethod method = PsiTreeUtil.getParentOfType(expression, PsiMethod.class);
    if (method == null) return false;
    final PsiType returnType = method.getReturnType();
    return TO_STRING.equals(method.getName())
      && method.getParameterList().getParametersCount() == 0
      && returnType != null
      && "java.lang.String".equals(returnType.getCanonicalText());
  }

  private static boolean isArgOfJUnitAssertion(PsiExpression expression) {
    final PsiElement parent = expression.getParent();
    if (!(parent instanceof PsiExpressionList)) {
      return false;
    }
    final PsiElement grandparent = parent.getParent();
    if (!(grandparent instanceof PsiMethodCallExpression)) {
      return false;
    }
    final PsiMethodCallExpression call = (PsiMethodCallExpression)grandparent;
    final PsiReferenceExpression methodExpression = call.getMethodExpression();
    @NonNls final String methodName = methodExpression.getReferenceName();
    if (methodName == null) {
      return false;
    }

    if (!methodName.startsWith("assert") && !methodName.equals("fail")) {
      return false;
    }
    final PsiMethod method = call.resolveMethod();
    if (method == null) {
      return false;
    }
    final PsiClass containingClass = method.getContainingClass();
    if (containingClass == null) {
      return false;
    }
    final Project project = expression.getProject();
    final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
    final PsiClass junitAssert = JavaPsiFacade.getInstance(project).findClass("junit.framework.Assert", scope);
    return junitAssert != null && !containingClass.isInheritor(junitAssert, true);
  }

  private static boolean isArgOfExceptionConstructor(PsiExpression expression) {
    final PsiElement parent = PsiTreeUtil.getParentOfType(expression, PsiExpressionList.class, PsiClass.class);
    if (!(parent instanceof PsiExpressionList)) {
      return false;
    }
    final PsiElement grandparent = parent.getParent();
    final PsiClass aClass;
    if (RefactoringChangeUtil.isSuperOrThisMethodCall(grandparent)) {
      final PsiMethod method = ((PsiMethodCallExpression)grandparent).resolveMethod();
      if (method != null) {
        aClass = method.getContainingClass();
      }
      else {
        return false;
      }
    }
    else {
      if (!(grandparent instanceof PsiNewExpression)) {
        return false;
      }
      final PsiJavaCodeReferenceElement reference = ((PsiNewExpression)grandparent).getClassReference();
      if (reference == null) {
        return false;
      }
      final PsiElement referent = reference.resolve();
      if (!(referent instanceof PsiClass)) {
        return false;
      }

      aClass = (PsiClass)referent;
    }
    final Project project = expression.getProject();
    final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
    final PsiClass throwable = JavaPsiFacade.getInstance(project).findClass(JavaClassNames.JAVA_LANG_THROWABLE, scope);
    return throwable != null && aClass.isInheritor(throwable, true);
  }

  private static boolean isArgOfSpecifiedExceptionConstructor(PsiExpression expression, String[] specifiedExceptions) {
    if (specifiedExceptions.length == 0) return false;

    final PsiElement parent = PsiTreeUtil.getParentOfType(expression, PsiExpressionList.class, PsiClass.class);
    if (!(parent instanceof PsiExpressionList)) {
      return false;
    }
    final PsiElement grandparent = parent.getParent();
    if (!(grandparent instanceof PsiNewExpression)) {
      return false;
    }
    final PsiJavaCodeReferenceElement reference =
      ((PsiNewExpression)grandparent).getClassReference();
    if (reference == null) {
      return false;
    }
    final PsiElement referent = reference.resolve();
    if (!(referent instanceof PsiClass)) {
      return false;
    }
    final PsiClass aClass = (PsiClass)referent;

    for (String specifiedException : specifiedExceptions) {
      if (specifiedException.equals(aClass.getQualifiedName())) return true;

    }

    return false;
  }

  private static boolean isArgOfAssertStatement(PsiExpression expression) {
    return PsiTreeUtil.getParentOfType(expression, PsiAssertStatement.class, PsiClass.class) instanceof PsiAssertStatement;
  }
}
