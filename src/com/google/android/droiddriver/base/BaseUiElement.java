/*
 * Copyright (C) 2013 DroidDriver committers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.droiddriver.base;

import android.graphics.Rect;

import com.google.android.droiddriver.UiElement;
import com.google.android.droiddriver.actions.Action;
import com.google.android.droiddriver.actions.EventUiElementActor;
import com.google.android.droiddriver.actions.UiElementActor;
import com.google.android.droiddriver.exceptions.DroidDriverException;
import com.google.android.droiddriver.finders.Attribute;
import com.google.android.droiddriver.finders.Predicate;
import com.google.android.droiddriver.finders.Predicates;
import com.google.android.droiddriver.scroll.Direction.PhysicalDirection;
import com.google.android.droiddriver.util.Logs;
import com.google.android.droiddriver.util.Strings;
import com.google.android.droiddriver.util.Strings.ToStringHelper;
import com.google.android.droiddriver.validators.Validator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

/**
 * Base UiElement that implements the common operations.
 *
 * @param <R> the type of the raw element this class wraps, for example, View or
 *        AccessibilityNodeInfo
 * @param <E> the type of the concrete subclass of BaseUiElement
 */
public abstract class BaseUiElement<R, E extends BaseUiElement<R, E>> implements UiElement {
  // These two attribute names are used for debugging only.
  // The two constants are used internally and must match to-uiautomator.xsl.
  public static final String ATTRIB_VISIBLE_BOUNDS = "VisibleBounds";
  public static final String ATTRIB_NOT_VISIBLE = "NotVisible";

  private UiElementActor uiElementActor = EventUiElementActor.INSTANCE;
  private Validator validator = null;

  @SuppressWarnings("unchecked")
  @Override
  public <T> T get(Attribute attribute) {
    return (T) getAttributes().get(attribute);
  }

  @Override
  public String getText() {
    return get(Attribute.TEXT);
  }

  @Override
  public String getContentDescription() {
    return get(Attribute.CONTENT_DESC);
  }

  @Override
  public String getClassName() {
    return get(Attribute.CLASS);
  }

  @Override
  public String getResourceId() {
    return get(Attribute.RESOURCE_ID);
  }

  @Override
  public String getPackageName() {
    return get(Attribute.PACKAGE);
  }

  @Override
  public boolean isCheckable() {
    return (Boolean) get(Attribute.CHECKABLE);
  }

  @Override
  public boolean isChecked() {
    return (Boolean) get(Attribute.CHECKED);
  }

  @Override
  public boolean isClickable() {
    return (Boolean) get(Attribute.CLICKABLE);
  }

  @Override
  public boolean isEnabled() {
    return (Boolean) get(Attribute.ENABLED);
  }

  @Override
  public boolean isFocusable() {
    return (Boolean) get(Attribute.FOCUSABLE);
  }

  @Override
  public boolean isFocused() {
    return (Boolean) get(Attribute.FOCUSED);
  }

  @Override
  public boolean isScrollable() {
    return (Boolean) get(Attribute.SCROLLABLE);
  }

  @Override
  public boolean isLongClickable() {
    return (Boolean) get(Attribute.LONG_CLICKABLE);
  }

  @Override
  public boolean isPassword() {
    return (Boolean) get(Attribute.PASSWORD);
  }

  @Override
  public boolean isSelected() {
    return (Boolean) get(Attribute.SELECTED);
  }

  @Override
  public Rect getBounds() {
    return get(Attribute.BOUNDS);
  }

  // TODO: expose these 3 methods in UiElement?
  public int getSelectionStart() {
    Integer value = get(Attribute.SELECTION_START);
    return value == null ? 0 : value;
  }

  public int getSelectionEnd() {
    Integer value = get(Attribute.SELECTION_END);
    return value == null ? 0 : value;
  }

  public boolean hasSelection() {
    final int selectionStart = getSelectionStart();
    final int selectionEnd = getSelectionEnd();

    return selectionStart >= 0 && selectionStart != selectionEnd;
  }

  @Override
  public boolean perform(Action action) {
    Logs.call(this, "perform", action);
    if (validator != null && validator.isApplicable(this, action)) {
      String failure = validator.validate(this, action);
      if (failure != null) {
        throw new DroidDriverException(toString() + " failed validation: " + failure);
      }
    }

    // timeoutMillis <= 0 means no need to wait
    if (action.getTimeoutMillis() <= 0) {
      return doPerform(action);
    }
    return performAndWait(action);
  }

  protected boolean doPerform(Action action) {
    return action.perform(this);
  }

  protected abstract void doPerformAndWait(FutureTask<Boolean> futureTask, long timeoutMillis);

  private boolean performAndWait(final Action action) {
    FutureTask<Boolean> futureTask = new FutureTask<Boolean>(new Callable<Boolean>() {
      @Override
      public Boolean call() {
        return doPerform(action);
      }
    });
    doPerformAndWait(futureTask, action.getTimeoutMillis());

    try {
      return futureTask.get();
    } catch (ExecutionException e) {
      Throwable cause = e.getCause();
      if (cause instanceof RuntimeException) {
        throw (RuntimeException) cause;
      }
      throw new DroidDriverException(cause);
    } catch (InterruptedException e) {
      throw new DroidDriverException(e);
    }
  }

  @Override
  public void setText(String text) {
    uiElementActor.setText(this, text);
  }

  @Override
  public void click() {
    uiElementActor.click(this);
  }

  @Override
  public void longClick() {
    uiElementActor.longClick(this);
  }

  @Override
  public void doubleClick() {
    uiElementActor.doubleClick(this);
  }

  @Override
  public void scroll(PhysicalDirection direction) {
    uiElementActor.scroll(this, direction);
  }

  protected abstract Map<Attribute, Object> getAttributes();

  protected abstract List<E> getChildren();

  @Override
  public List<E> getChildren(Predicate<? super UiElement> predicate) {
    List<E> children = getChildren();
    if (children == null) {
      return Collections.emptyList();
    }
    if (predicate == null || predicate.equals(Predicates.any())) {
      return children;
    }

    List<E> filteredChildren = new ArrayList<E>(children.size());
    for (E child : children) {
      if (predicate.apply(child)) {
        filteredChildren.add(child);
      }
    }
    return Collections.unmodifiableList(filteredChildren);
  }

  @Override
  public String toString() {
    ToStringHelper toStringHelper = Strings.toStringHelper(this);
    for (Map.Entry<Attribute, Object> entry : getAttributes().entrySet()) {
      addAttribute(toStringHelper, entry.getKey(), entry.getValue());
    }
    if (!isVisible()) {
      toStringHelper.addValue(ATTRIB_NOT_VISIBLE);
    } else if (!getVisibleBounds().equals(getBounds())) {
      toStringHelper.add(ATTRIB_VISIBLE_BOUNDS, getVisibleBounds().toShortString());
    }
    return toStringHelper.toString();
  }

  private static void addAttribute(ToStringHelper toStringHelper, Attribute attr, Object value) {
    if (value != null) {
      if (value instanceof Boolean) {
        if ((Boolean) value) {
          toStringHelper.addValue(attr.getName());
        }
      } else if (value instanceof Rect) {
        toStringHelper.add(attr.getName(), ((Rect) value).toShortString());
      } else {
        toStringHelper.add(attr.getName(), value);
      }
    }
  }

  /**
   * Gets the raw element used to create this UiElement. The attributes of this
   * UiElement are based on a snapshot of the raw element at construction time.
   * If the raw element is updated later, the attributes may not match.
   */
  // TODO: expose in UiElement?
  public abstract R getRawElement();

  public void setUiElementActor(UiElementActor uiElementActor) {
    this.uiElementActor = uiElementActor;
  }

  /**
   * Sets the validator to check when {@link #perform(Action)} is called.
   */
  public void setValidator(Validator validator) {
    this.validator = validator;
  }
}
