/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.util;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.PsiElementProcessor;

import java.util.ArrayList;

public class PsiTreeUtil {
  private static final Key INDEX = Key.create("PsiTreeUtil.copyElements.INDEX");
  private static final Key MARKER = Key.create("PsiTreeUtil.copyElements.INDEX");

  public static boolean isAncestor(PsiElement ancestor, PsiElement element, boolean strict) {
    PsiElement parent = strict ? element.getParent() : element;
    while (true) {
      if (parent == null) return false;
      if (parent.equals(ancestor)) return true;
      parent = parent.getParent();
    }
  }

  public static PsiElement findCommonParent(PsiElement element1, PsiElement element2) {
    // optimization
    if(element1 == element2) return element1;
    final PsiFile containingFile = element1.getContainingFile();
    final PsiElement topLevel = containingFile == element2.getContainingFile() ? containingFile : null;

    ArrayList parents1 = getParents(element1, topLevel);
    ArrayList parents2 = getParents(element2, topLevel);
    int size = Math.min(parents1.size(), parents2.size());
    PsiElement parent = topLevel;
    for (int i = 1; i <= size; i++) {
      PsiElement parent1 = (PsiElement) parents1.get(parents1.size() - i);
      PsiElement parent2 = (PsiElement) parents2.get(parents2.size() - i);
      if (!parent1.equals(parent2)) break;
      parent = parent1;
    }
    return parent;
  }

  private static ArrayList getParents(PsiElement element, PsiElement topLevel) {
    ArrayList parents = new ArrayList();
    PsiElement parent = element;
    while (parent != topLevel && parent != null) {
      parents.add(parent);
      parent = parent.getParent();
    }
    return parents;
  }

  public static <ChildType extends PsiElement> ChildType getChildOfType(PsiElement element, Class<ChildType> aClass) {
    for(PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()){
      if (aClass.isInstance(child)) return (ChildType)child;
    }
    return null;
  }

  public static <ChildType extends PsiElement> ChildType getNextSiblingOfType(PsiElement sibling, Class<ChildType> aClass) {
    for(PsiElement child = sibling.getNextSibling(); child != null; child = child.getNextSibling()){
      if (aClass.isInstance(child)) return (ChildType)child;
    }
    return null;
  }

  public static <T extends PsiElement> T getPrevSiblingOfType(PsiElement sibling, Class<T> aClass) {
    for(PsiElement child = sibling.getPrevSibling(); child != null; child = child.getPrevSibling()){
      if (aClass.isInstance(child)) return (T)child;
    }
    return null;
  }

  public static <ParentType extends PsiElement> ParentType getParentOfType(PsiElement element, Class<ParentType> aClass) {
    return getParentOfType(element, aClass, true);
  }

  public static <ContextType extends PsiElement> ContextType getContextOfType(PsiElement element, Class<ContextType> aClass, boolean strict) {
    if (element == null) return null;
    if (strict) {
      element = element.getContext();
    }

    while (element != null && !aClass.isInstance(element)) {
      element = element.getContext();
    }

    return (ContextType)element;
  }

  public static <ParentType extends PsiElement> ParentType getParentOfType(PsiElement element, Class<ParentType> aClass, boolean strict) {
    if (element == null) return null;
    if (strict) {
      element = element.getParent();
    }

    while (element != null && !aClass.isInstance(element)) {
      element = element.getParent();
    }

    return (ParentType)element;
  }

  public static PsiElement skipSiblingsForward (PsiElement element, Class[] elementClasses) {
    if (element == null) return null;
    NextSibling:
    for (PsiElement e = element.getNextSibling(); e != null; e = e.getNextSibling()) {
      for (Class aClass : elementClasses) {
        if (aClass.isInstance(e)) continue NextSibling;
      }
      return e;
    }
    return null;
  }

  public static PsiElement skipSiblingsBackward (PsiElement element, Class[] elementClasses) {
    if (element == null) return null;
    NextSibling:
    for (PsiElement e = element.getPrevSibling(); e != null; e = e.getPrevSibling()) {
      for (Class aClass : elementClasses) {
        if (aClass.isInstance(e)) continue NextSibling;
      }
      return e;
    }
    return null;
  }

  public static PsiElement getParentOfType(PsiElement element, Class[] classes) {
    return getParentOfType(element, classes, true);
  }

  public static PsiElement getParentOfType(PsiElement element, Class[] classes, boolean strict) {
    if (strict) {
      element = element.getParent();
    }

    while (element != null) {
      for (Class aClass : classes) {
        if (aClass.isInstance(element)) return element;
      }
      element = element.getParent();
    }

    return element;
  }

  public static PsiElement[] collectElements(PsiElement element, PsiElementFilter filter) {
    PsiElementProcessor.CollectFilteredElements processor = new PsiElementProcessor.CollectFilteredElements(filter);
    processElements(element, processor);
    return processor.toArray();
  }

  public static boolean processElements(PsiElement element, PsiElementProcessor processor) {
    if (element == null) return true;
    if (!processor.execute(element)) return false;
    for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (!processElements(child, processor)) return false;
    }

    return true;
  }

  public static PsiElement[] copyElements(PsiElement[] elements) {
    ArrayList roots = new ArrayList();
    for (int i = 0; i < elements.length; i++) {
      PsiElement rootCandidate = elements[i];
      boolean failed = false;
      for (int j = 0; j < elements.length; j++) {
        PsiElement element = elements[j];
        if (i != j && isAncestor(element,  rootCandidate, true)) {
          failed = true;
          break;
        }
      }
      if (!failed) {
        roots.add(rootCandidate);
      }
    }
    for (int i = 0; i < elements.length; i++) {
      PsiElement element = elements[i];
      element.putCopyableUserData(INDEX, new Integer(i));
    }
    PsiElement[] newRoots = new PsiElement[roots.size()];
    for (int i = 0; i < roots.size(); i++) {
      PsiElement root = (PsiElement) roots.get(i);
      newRoots[i] = root.copy();
    }

    final PsiElement[] result = new PsiElement[elements.length];
    for (PsiElement newRoot : newRoots) {
      decodeIndices(newRoot, result);
    }
    return result;
  }

  private static void decodeIndices(PsiElement element, PsiElement[] result) {
    final Object data = element.getCopyableUserData(INDEX);
    if (data != null) {
      element.putCopyableUserData(INDEX, null);
      int index = ((Integer) data).intValue();
      result[index] = element;
    }
    PsiElement child = element.getFirstChild();
    while (child != null) {
      decodeIndices(child, result);
      child = child.getNextSibling();
    }
  }

  public static void mark(PsiElement element, Object marker) {
    element.putCopyableUserData(MARKER, marker);
  }

  public static PsiElement releaseMark(PsiElement root, Object marker) {
    if (marker.equals(root.getCopyableUserData(MARKER))) {
      root.putCopyableUserData(MARKER, null);
      return root;
    } else {
      PsiElement child = root.getFirstChild();
      while (child != null) {
        final PsiElement result = releaseMark(child, marker);
        if (result != null) return result;
        child = child.getNextSibling();
      }
      return null;
    }
  }

  public static <T extends PsiElement> T findElementOfClassAtOffset (PsiFile file, int offset, Class<T> clazz, boolean strictStart) {
    final PsiFile[] psiRoots = file.getPsiRoots();
    T result = null;
    for (PsiFile root : psiRoots) {
      final PsiElement elementAt = root.findElementAt(offset);
      if (elementAt != null) {
        final T parent = getParentOfType(elementAt, clazz);
        if (parent != null) {
          final TextRange range = parent.getTextRange();
          if (!strictStart || range.getStartOffset() == offset) {
            if (result == null || result.getTextRange().getEndOffset() > range.getEndOffset()) {
              result = parent;
            }
          }
        }
      }
    }

    return result;
  }

  /**
   * @return maximal element of specified Class starting at startOffset exactly and ending not farther than endOffset
   */
  public static <T extends PsiElement> T findElementOfClassAtRange (PsiFile file, int startOffset, int endOffset, Class<T> clazz) {
    final PsiFile[] psiRoots = file.getPsiRoots();
    T result = null;
    for (PsiFile root : psiRoots) {
      PsiElement elementAt = root.findElementAt(startOffset);
      T run = getParentOfType(elementAt, clazz, false);
      T prev = run;
      while (run != null && run.getTextRange().getStartOffset() == startOffset &&
             run.getTextRange().getEndOffset() <= endOffset) {
        prev = run;
        run = getParentOfType(run, clazz);
      }

      if (prev == null) continue;
      final int elementStartOffset = prev.getTextRange().getStartOffset();
      final int elementEndOffset = prev.getTextRange().getEndOffset();
      if (elementStartOffset != startOffset || elementEndOffset > endOffset) continue;

      if (result == null || result.getTextRange().getEndOffset() < elementEndOffset) {
        result = prev;
      }
    }

    return result;
  }
}
