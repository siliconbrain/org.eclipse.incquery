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
 * 
 * Tuple that inherits another tuple on the left.
 * 
 * @author Gabor Bergmann
 * 
 */
public class LeftInheritanceTuple extends Tuple {
    /**
     * This object contains the same elements as the ancestor on the first inheritedIndex positions
     */
    private final Tuple ancestor;

    /**
     * Array of substituted values to the right from ancestor's values. DO NOT MODIFY! Use Constructor to build a new
     * instance instead.
     */
    private final Object[] localElements;

    /**
     * Creates a Tuple instance, lets it inherit from an ancestor, extends it with a given array. @pre: no elements are
     * null
     * 
     * @param elements
     *            array of substitution values
     */
    public LeftInheritanceTuple(final Tuple ancestor, final Object... localElements) {
        this.ancestor = ancestor;
        this.localElements = localElements;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.eclipse.incquery.runtime.matchers.tuple.Tuple#get(int)
     */
    @Override
    public Object get(final int index) {
        return (index < ancestor.getSize()) ? ancestor.get(index) : localElements[index - ancestor.getSize()];
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.eclipse.incquery.runtime.matchers.tuple.Tuple#getSize()
     */
    @Override
    public int getSize() {
        return ancestor.getSize() + localElements.length;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.eclipse.incquery.runtime.matchers.tuple.Tuple#calcHash()
     */
    @Override
    protected int calcHash() {
        // Optimized hash calculation
        return calcHash(Arrays.asList(localElements), ancestor.hashCode());
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
        // Optimized equals calculation (prediction: true, since hash values match)
        if (other instanceof LeftInheritanceTuple) {
            LeftInheritanceTuple lit = (LeftInheritanceTuple) other;
            if (this.ancestor.equals(lit.ancestor))
                return Arrays.equals(this.localElements, lit.localElements);
        }
        return super.internalEquals(other);
    }

    // public int compareTo(Object arg0) {
    // Tuple other = (Tuple) arg0;
    //
    // int retVal = cachedHash - other.cachedHash;
    // if (retVal==0) retVal = elements.length - other.elements.length;
    // for (int i=0; retVal==0 && i<elements.length; ++i)
    // {
    // if (elements[i] == null && other.elements[i] != null) retVal = -1;
    // else if (other.elements[i] == null) retVal = 1;
    // else retVal = elements[i].compareTo(other.elements[i]);
    // }
    // return retVal;
    // }

}
