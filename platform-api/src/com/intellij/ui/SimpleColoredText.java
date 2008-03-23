/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package com.intellij.ui;

import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public class SimpleColoredText {
  private final ArrayList<String> myTexts;
  private final ArrayList<SimpleTextAttributes> myAttributes;
  private String myCachedToString = null;
  
  public SimpleColoredText() {
    myTexts = new ArrayList<String>(3);
    myAttributes = new ArrayList<SimpleTextAttributes>(3);
  }

  public void append(@NotNull String fragment, @NotNull SimpleTextAttributes attributes){
    myTexts.add(fragment);
    myCachedToString = null;
    myAttributes.add(attributes);
  }

  public void clear() {
    myTexts.clear();
    myCachedToString = null;
    myAttributes.clear();
  }
  
  public void appendToComponent(SimpleColoredComponent component) {
    int size = myTexts.size();
    for (int i=0; i < size; i++){
      String text = myTexts.get(i);
      SimpleTextAttributes attribute = myAttributes.get(i);
      component.append(text, attribute);
    }
  }

  public String toString() {
    if (myCachedToString == null) {
      final StringBuilder builder = StringBuilderSpinAllocator.alloc();
      try {
        for (String text : myTexts) {
          builder.append(text);
        }
        myCachedToString = builder.toString();
      }
      finally{
        StringBuilderSpinAllocator.dispose(builder);
      } 
        
    }
    return myCachedToString;
  }

  public ArrayList<String> getTexts() {
    return myTexts;
  }

  public ArrayList<SimpleTextAttributes> getAttributes() {
    return myAttributes;
  }
}
