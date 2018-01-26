// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.rename;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.move.moveClassesOrPackages.MoveDirectoryWithClassesProcessor;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * @author yole
 */
public class RenamePsiPackageProcessor extends RenamePsiElementProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.rename.RenamePsiPackageProcessor");

  public boolean canProcessElement(@NotNull final PsiElement element) {
    return element instanceof PsiPackage;
  }

  @NotNull
  @Override
  public RenameDialog createRenameDialog(@NotNull final Project project, @NotNull final PsiElement element, PsiElement nameSuggestionContext, Editor editor) {

    return new RenameDialog(project, element, nameSuggestionContext, editor) {
      @Override
      protected void createNewNameComponent() {
        super.createNewNameComponent();
        final String qualifiedName = ((PsiPackage)element).getQualifiedName();
        final String packageName = StringUtil.getPackageName(qualifiedName);
        preselectExtension(packageName.isEmpty() ? 0 : packageName.length() + 1, qualifiedName.length());
      }

      @Override
      public String[] getSuggestedNames() {
        return new String[]{((PsiPackage)element).getQualifiedName()};
      }

      @Override
      public String getNewName() {
        final PsiPackage psiPackage = (PsiPackage)element;
        final String oldName = psiPackage.getQualifiedName();
        final String newName = super.getNewName();
        if (!Comparing.strEqual(StringUtil.getPackageName(oldName), StringUtil.getPackageName(newName))) {
          return newName;
        }
        return StringUtil.getShortName(newName);
      }

      protected void doAction() {
        final PsiPackage psiPackage = (PsiPackage)element;
        final String oldName = psiPackage.getQualifiedName();
        final String newName = super.getNewName();
        if (!Comparing.strEqual(StringUtil.getPackageName(oldName), StringUtil.getPackageName(newName))) {
          invokeRefactoring(createRenameMoveProcessor(newName, psiPackage, isSearchInComments(), isSearchInNonJavaFiles()));
        } else {
          super.doAction();
        }
      }
    };
  }

  public static MoveDirectoryWithClassesProcessor createRenameMoveProcessor(final String newName,
                                                                            final PsiPackage psiPackage,
                                                                            final boolean searchInComments,
                                                                            final boolean searchInNonJavaFiles) {
    final Project project = psiPackage.getProject();
    final ProjectFileIndex index = ProjectRootManager.getInstance(project).getFileIndex();
    final PsiDirectory[] directories = psiPackage.getDirectories();

    return new MoveDirectoryWithClassesProcessor(project, directories, null, searchInComments,
                                                 searchInNonJavaFiles, false, null) {
      @Override
      public TargetDirectoryWrapper getTargetDirectory(final PsiDirectory dir) {
        final VirtualFile vFile = dir.getVirtualFile();
        final VirtualFile sourceRoot = index.getSourceRootForFile(vFile);
        LOG.assertTrue(sourceRoot != null, vFile.getPath());
        return new TargetDirectoryWrapper(dir.getManager().findDirectory(sourceRoot), newName.replaceAll("\\.", "\\/"));
      }

      @Override
      protected String getTargetName() {
        return newName;
      }

      @NotNull
      @Override
      protected String getCommandName() {
        return "Rename package";
      }
    };
  }

  public void renameElement(@NotNull final PsiElement element,
                            @NotNull final String newName,
                            @NotNull final UsageInfo[] usages,
                            @Nullable RefactoringElementListener listener) throws IncorrectOperationException {
    final PsiPackage psiPackage = (PsiPackage)element;
    final String shortName = StringUtil.getShortName(newName);
    psiPackage.handleQualifiedNameChange(PsiUtilCore.getQualifiedNameAfterRename(psiPackage.getQualifiedName(), shortName));
    RenameUtil.doRenameGenericNamedElement(element, shortName, usages, listener);
  }

  public String getQualifiedNameAfterRename(@NotNull final PsiElement element, @NotNull final String newName, final boolean nonJava) {
    return getPackageQualifiedNameAfterRename((PsiPackage)element, newName, nonJava);
  }

  public static String getPackageQualifiedNameAfterRename(final PsiPackage element, final String newName, final boolean nonJava) {
    if (nonJava) {
      String qName = element.getQualifiedName();
      int index = qName.lastIndexOf('.');
      return index < 0 ? newName : qName.substring(0, index + 1) + newName;
    }
    else {
      return newName;
    }
  }

  @Override
  public void findExistingNameConflicts(@NotNull PsiElement element, @NotNull String newName, @NotNull MultiMap<PsiElement,String> conflicts) {
    final PsiPackage aPackage = (PsiPackage)element;
    final Project project = element.getProject();
    final String qualifiedNameAfterRename = getPackageQualifiedNameAfterRename(aPackage, newName, true);
    final PsiClass psiClass = JavaPsiFacade.getInstance(project).findClass(qualifiedNameAfterRename, GlobalSearchScope.allScope(project));
    if (psiClass != null) {
      conflicts.putValue(psiClass, "Class with qualified name \'" + qualifiedNameAfterRename + "\'  already exist");
    }
  }

  public void prepareRenaming(@NotNull final PsiElement element, @NotNull final String newName, @NotNull final Map<PsiElement, String> allRenames) {
    preparePackageRenaming((PsiPackage)element, newName, allRenames);
  }

  public static void preparePackageRenaming(PsiPackage psiPackage, final String newName, Map<PsiElement, String> allRenames) {
    final String newDirectoryName = StringUtil.getShortName(newName);
    final PsiDirectory[] directories = psiPackage.getDirectories();
    for (PsiDirectory directory : directories) {
      if (!JavaDirectoryService.getInstance().isSourceRoot(directory)) {
        allRenames.put(directory, newDirectoryName);
      }
    }
  }

  @Nullable
  public Runnable getPostRenameCallback(@NotNull final PsiElement element, @NotNull final String newName, @NotNull final RefactoringElementListener listener) {
    final Project project = element.getProject();
    final PsiPackage psiPackage = (PsiPackage)element;
    final String newQualifiedName = PsiUtilCore.getQualifiedNameAfterRename(psiPackage.getQualifiedName(), newName);
    return () -> {
      final PsiPackage aPackage = JavaPsiFacade.getInstance(project).findPackage(newQualifiedName);
      if (aPackage == null) {
        return; //rename failed e.g. when the dir is used by another app
      }
      listener.elementRenamed(aPackage);
    };
  }

  @Nullable
  @NonNls
  public String getHelpID(final PsiElement element) {
    return HelpID.RENAME_PACKAGE;
  }

  public boolean isToSearchInComments(@NotNull final PsiElement psiElement) {
    return JavaRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_PACKAGE;
  }

  public void setToSearchInComments(@NotNull final PsiElement element, final boolean enabled) {
    JavaRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_PACKAGE = enabled;
  }

  public boolean isToSearchForTextOccurrences(@NotNull final PsiElement element) {
    return JavaRefactoringSettings.getInstance().RENAME_SEARCH_FOR_TEXT_FOR_PACKAGE;
  }

  public void setToSearchForTextOccurrences(@NotNull final PsiElement element, final boolean enabled) {
    JavaRefactoringSettings.getInstance().RENAME_SEARCH_FOR_TEXT_FOR_PACKAGE = enabled;
  }
}
