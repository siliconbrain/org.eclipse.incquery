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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;

/**
 * @author Gabor Bergmann
 * 
 */
public abstract class Tuple implements Iterable<Object> {

    /**
     * Caches precalculated hash value
     */
    private Integer cachedHash;

    /**
     * @return number of elements
     */
    public abstract int getSize();

    /**
     * @pre: 0 <= index < getSize()
     * 
     * @return the element at the specified index
     */
    public abstract Object get(int index);

    /**
     * Hash calculation. Overrides should keep semantics.
     */
    protected int calcHash() {
        return calcHash(this);
    }

    /**
     * @return the array containing all elements of this Tuple
     */
    public Object[] getElements() {
        return Iterables.toArray(this, Object.class);
    }

    /**
     * @return the set containing all distinct elements of this Tuple, cast as type T
     */
    @SuppressWarnings("unchecked")
    public <T> Set<T> getDistinctElements() {
        Set<T> result = new HashSet<T>();
        for (Object object : this) {
            result.add((T) object);
        }
        return result;
    }

    protected static int calcHash(final Iterable<Object> objects) {
        return calcHash(objects, 1);
    }

    protected static int calcHash(final Iterable<Object> objects, final int partialResult) {
        final int PRIME = 31;
        int result = partialResult;
        for (Object element : objects) {
            result *= PRIME;
            if (element != null)
                result += element.hashCode();
        }
        return result;
    }

    /**
     * Calculates an inverted index of the elements of this pattern. For each element, the index of the (last)
     * occurrence is calculated.
     * 
     * @return the inverted index mapping each element of this pattern to its index in the array
     */
    public Map<Object, Integer> invertIndex() {
        Map<Object, Integer> result = new HashMap<Object, Integer>();
        for (int i = 0; i < getSize(); i++)
            result.put(get(i), i);
        return result;
    }

    /**
     * Calculates an inverted index of the elements of this pattern. For each element, the index of all of its
     * occurrences is calculated.
     * 
     * @return the inverted index mapping each element of this pattern to its index in the array
     */
    public Map<Object, List<Integer>> invertIndexWithMupliplicity() {
        Map<Object, List<Integer>> result = new HashMap<Object, List<Integer>>();
        for (int i = 0; i < getSize(); i++) {
            Object value = get(i);
            List<Integer> indices = result.get(value);
            if (indices == null) {
                indices = new ArrayList<Integer>();
                result.put(value, indices);
            }
            indices.add(i);
        }
        return result;
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

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(final Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (!(obj instanceof Tuple))
            return false;
        final Tuple other = (Tuple) obj;
        if (hashCode() != other.hashCode())
            return false;
        return internalEquals(other);
    }

    protected boolean internalEquals(final Tuple other) {
        if (getSize() != other.getSize())
            return false;
        for (int i = 0; i < getSize(); ++i) {
            Object ours = get(i);
            Object theirs = other.get(i);

            if (ours == null) {
                if (theirs != null)
                    return false;
            } else {
                if (!ours.equals(theirs))
                    return false;
            }
        }
        return true;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        if (cachedHash == null) {
            cachedHash = calcHash();
        }
        return cachedHash.intValue();
    }

    @Override
    public String toString() {
        StringBuilder s = new StringBuilder();
        s.append("T(");
        for (Object o : this) {
            s.append(o == null ? "null" : o.toString());
            s.append(';');
        }
        s.append(')');
        return s.toString();
    }

    /**
     * @param obsolete
     * @param replacement
     * @return
     */
    public Tuple replaceAll(final Object obsolete, final Object replacement) {
        return new FlatTuple(Iterables.transform(this, new Function<Object, Object>(){
            @Override
            public Object apply(Object arg0) {
                return obsolete.equals(arg0) ? replacement : arg0;
            }}));
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Iterable#iterator()
     */
    @Override
    public Iterator<Object> iterator() {
        return new TupleIterator(this);
    }

    /**
     * Iterator for {@link Tuple}s
     * 
     * @author Adam Dudas
     * 
     */
    public class TupleIterator implements Iterator<Object> {

        private int currentIndex;
        private final Tuple tuple;

        /**
         * Create a new iterator for the specified tuple
         */
        public TupleIterator(final Tuple tuple) {
            this.tuple = tuple;
            this.currentIndex = -1;
        }

        /*
         * (non-Javadoc)
         * 
         * @see java.util.Iterator#hasNext()
         */
        @Override
        public boolean hasNext() {
            return currentIndex + 1 < tuple.getSize();
        }

        /*
         * (non-Javadoc)
         * 
         * @see java.util.Iterator#next()
         */
        @Override
        public Object next() {
            return tuple.get(++currentIndex);
        }

        /*
         * (non-Javadoc)
         * 
         * @see java.util.Iterator#remove()
         */
        @Override
        public void remove() {
            throw new UnsupportedOperationException("Tuples are immutable.");
        }
    }
}
