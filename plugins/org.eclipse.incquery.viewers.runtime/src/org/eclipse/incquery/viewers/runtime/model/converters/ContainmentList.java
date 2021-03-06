/*******************************************************************************
 * Copyright (c) 2010-2013, Zoltan Ujhelyi, Istvan Rath and Daniel Varro
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Zoltan Ujhelyi - initial API and implementation
 *******************************************************************************/
package org.eclipse.incquery.viewers.runtime.model.converters;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.databinding.observable.list.ComputedList;
import org.eclipse.core.databinding.observable.list.IObservableList;
import org.eclipse.incquery.runtime.api.IPatternMatch;
import org.eclipse.incquery.runtime.matchers.psystem.annotations.PAnnotation;
import org.eclipse.incquery.runtime.matchers.psystem.annotations.ParameterReference;
import org.eclipse.incquery.viewers.runtime.model.Containment;
import org.eclipse.incquery.viewers.runtime.model.Edge;
import org.eclipse.incquery.viewers.runtime.model.Item;

import com.google.common.base.Preconditions;
import com.google.common.collect.Multimap;

/**
 * A converter from {@link IPatternMatch} matches to displayable {@link Edge} objects.
 * 
 * @author Zoltan Ujhelyi
 * 
 */
public class ContainmentList extends ComputedList {
    private String containerParameterName;
    private String destParameterName;
    private Multimap<Object, Item> itemMap;
    private IObservableList patternMatchList;

    public ContainmentList(PAnnotation itemAnnotation, Multimap<Object, Item> itemMap2, IObservableList patternMatchList) {
        Preconditions.checkArgument(Containment.ANNOTATION_ID.equals(itemAnnotation.getName()),
                "The converter should be initialized using a " + Edge.ANNOTATION_ID + " annotation.");
        this.itemMap = itemMap2;

        containerParameterName = ((ParameterReference)itemAnnotation.getFirstValue("container")).getName();
        destParameterName = ((ParameterReference)itemAnnotation.getFirstValue("item")).getName();
        this.patternMatchList = patternMatchList;
    }

    @Override
    public List<Containment> calculate() {
        List<Containment> edgeList = new ArrayList<Containment>();
        for (Object _match : patternMatchList) {
            
            IPatternMatch match = (IPatternMatch) _match;

            Object sourceValue = match.get(containerParameterName);
            Object destValue = match.get(destParameterName);

            for (Object _sourceItem : itemMap.get(sourceValue)) {
                Item sourceItem = (Item) _sourceItem;
                for (Object _destItem : itemMap.get(destValue)) {
                    Item destItem = (Item) _destItem;
                    Containment edge = new Containment(sourceItem, destItem, match);
                    edgeList.add(edge);
                }
            }
        }
        return edgeList;
    }
}