/*
 * Copyright (C) 2018 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.dialer.inject;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

/**
 * Annotates a type that should be included in Dialer Root Component. Typically, annotated types are
 * HasComponent interfaces.
 *
 * <p>An example:
 *
 * <pre>
 * <code>
 * {@literal @}dagger.Subcomponent
 * public abstract class SimulatorComponent {
 *   public static SimulatorComponent get(Context context) {
 *      return ((HasComponent)((HasRootComponent) context.getApplicationContext()).component())
 *         .simulatorComponent();
 *   }
 *   {@literal @}IncludeInDialerRoot
 *   public interface HasComponent {
 *      SimulatorComponent simulatorComponent();
 *  }
 * }
 * </code>
 * </pre>
 */
@Target(ElementType.TYPE)
public @interface IncludeInDialerRoot {
  Class<?>[] modules() default {};
}
