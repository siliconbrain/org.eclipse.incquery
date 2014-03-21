/*******************************************************************************
 * Copyright (c) 2004-2008 Gabor Bergmann and Daniel Varro
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Gabor Bergmann - initial API and implementation
 *******************************************************************************/

package org.eclipse.incquery.runtime.matchers.tuple;

import java.util.Arrays;

/**
 * Default tuple implementation
 * 
 * @author Gabor Bergmann
 */
public class FlatTuple extends Tuple {
    /**
     * Array of substituted values. DO NOT MODIFY! Use Constructor to build a new instance instead.
     */
    private final Object[] elements;

    /**
     * Creates a Tuple instance, fills it with the given array. @pre: no elements are null
     * 
     * @param elements
     *            array of substitution values
     */
    public FlatTuple(final Object... elements) {
        this.elements = elements;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.eclipse.incquery.runtime.matchers.tuple.Tuple#get(int)
     */
    @Override
    public Object get(final int index) {
        return elements[index];
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.eclipse.incquery.runtime.matchers.tuple.Tuple#getSize()
     */
    @Override
    public int getSize() {
        return elements.length;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.eclipse.incquery.runtime.matchers.tuple.Tuple#getElements()
     */
    @Override
    public Object[] getElements() {
        return elements;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.eclipse.incquery.runtime.matchers.tuple.Tuple#internalEquals(org.eclipse.incquery.runtime.matchers.tuple.
     * Tuple)
     */
    @Override
    protected boolean internalEquals(final Tuple other) {
        if (other instanceof FlatTuple) {
            return Arrays.equals(elements, ((FlatTuple) other).elements);
        } else
            return super.internalEquals(other);
    }

}
