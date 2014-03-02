/*******************************************************************************
 * Copyright (c) 2010-2013, Adam Dudas, Istvan Rath and Daniel Varro
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Adam Dudas - initial API and implementation
 *******************************************************************************/
package org.eclipse.incquery.runtime.base.api;

import java.util.Set;

import com.google.common.collect.ImmutableSet;

/**
 * @author Adam Dudas
 *
 */
public class FunctionalDependency<A> {
    private ImmutableSet<A> determinant;
    private A dependent;
    
    public Set<A> getDeterminant() {
        return determinant;
    }
    
    public A getDependent() {
        return dependent;
    }
    
    public FunctionalDependency(Set<A> determinant, A dependent){
        this.determinant = ImmutableSet.copyOf(determinant);
        this.dependent = dependent;
    }
    
    public boolean equals(FunctionalDependency<A> other) {
        return other != null && (determinant.equals(other.determinant) && dependent.equals(other.dependent));
    }
    
    public boolean isTrivial() {
        return determinant.contains(dependent);
    }
}
