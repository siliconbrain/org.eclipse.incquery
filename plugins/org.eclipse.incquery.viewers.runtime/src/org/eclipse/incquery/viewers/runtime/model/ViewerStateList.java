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
package org.eclipse.incquery.viewers.runtime.model;

import java.util.Collection;

import org.eclipse.core.databinding.observable.IObservableCollection;
import org.eclipse.core.databinding.observable.list.IListChangeListener;
import org.eclipse.core.databinding.observable.list.IObservableList;
import org.eclipse.core.databinding.observable.list.ListChangeEvent;
import org.eclipse.core.databinding.observable.list.ListDiff;
import org.eclipse.core.databinding.observable.list.ListDiffEntry;
import org.eclipse.incquery.viewers.runtime.model.listeners.IViewerStateListener;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;

/**
 * An {@link IObservableList}-based implementation of {@link ViewerState}.
 * 
 * @author Zoltan Ujhelyi
 * 
 */
public class ViewerStateList extends ViewerState {


	public ViewerStateList(ViewerDataModel model, ViewerDataFilter filter,
			Collection<ViewerStateFeature> features) {
		this.model = model;
		initializeViewerState(model, filter, features);
	}
	
	private IObservableList itemList;
	private IObservableList edgeList;

	private IObservableList containmentList;

	
	private Multimap<Object, Item> initializeItemMap() {
	    return new ItemMap();
	}

	private IListChangeListener itemListener = new IListChangeListener() {

		@Override
		public void handleListChange(ListChangeEvent event) {
			ListDiff diff = event.diff;
			for (ListDiffEntry entry : diff.getDifferences()) {
				Item item = (Item) entry.getElement();
				if (entry.isAddition()) {
					for (Object listener : stateListeners.getListeners()) {
						((IViewerStateListener) listener).itemAppeared(item);
						item.getLabel().addChangeListener(labelChangeListener);
					}
					for (Edge edge : edgeDelayer.removeDelayedEdgesForItem(item)) {
                        handleEdgeAddition(edge);
                    }
				} else {
					for (Object listener : stateListeners.getListeners()) {
						item.getLabel().removeChangeListener(labelChangeListener);
						((IViewerStateListener) listener).itemDisappeared(item);
					}
				}
			}
		}
	};

	private IListChangeListener edgeListener = new IListChangeListener() {

		@Override
		public void handleListChange(ListChangeEvent event) {
			ListDiff diff = event.diff;
			for (ListDiffEntry entry : diff.getDifferences()) {
				Edge edge = (Edge) entry.getElement();
				boolean existingSource = itemMap.containsValue(edge.getSource());
                boolean existingTarget = itemMap.containsValue(edge.getTarget());
                if (existingSource && existingTarget) {
                    if (entry.isAddition()) {
                        handleEdgeAddition(edge);
                    } else {
                        for (Object listener : stateListeners.getListeners()) {
                            edge.getLabel().removeChangeListener(labelChangeListener);
                            ((IViewerStateListener) listener).edgeDisappeared(edge);
                        }
                    }
                } else {
                    handleEdgeDelay(entry, edge, existingSource, existingTarget);
                }
			}
		}
	};

	private void handleEdgeDelay(ListDiffEntry entry, Edge edge, boolean existingSource, boolean existingTarget) {
        if (entry.isAddition()) {
            if (!existingSource) {
                edgeDelayer.delayEdgeForNonExistingSource(edge);
            }
            if (!existingTarget) {
                edgeDelayer.delayEdgeForNonExistingTarget(edge);
            }
        } else {
            if (edge instanceof Containment) {
                childrenMap.remove(edge.getSource(), edge.getTarget());
                parentMap.remove(edge.getTarget());
            }
            if (!existingSource) {
                edgeDelayer.removeDelayedEdgeForNonExistingSource(edge);
            }
            if (!existingTarget) {
                edgeDelayer.removeDelayedEdgeForNonExistingTarget(edge);
            }
        }
    }

    private void handleEdgeAddition(Edge edge) {
        if (edge instanceof Containment) {
            containmentAppeared((Containment) edge);
        } else {
            for (Object listener : stateListeners.getListeners()) {
                ((IViewerStateListener) listener).edgeAppeared(edge);
                edge.getLabel().addChangeListener(labelChangeListener);
            }
        }
    }
    
	private IListChangeListener containmentListener = new IListChangeListener() {

		@Override
		public void handleListChange(ListChangeEvent event) {
			ListDiff diff = event.diff;
			for (ListDiffEntry entry : diff.getDifferences()) {
				Containment edge = (Containment) entry.getElement();
				boolean existingSource = itemMap.containsValue(edge.getSource());
                boolean existingTarget = itemMap.containsValue(edge.getTarget());
                if (existingSource && existingTarget) {
                    if (entry.isAddition()) {
                        containmentAppeared(edge);
                    } else {
                        containmentDisappeared(edge);
                    }
                } else {
                    handleEdgeDelay(entry, edge, existingSource, existingTarget);
                }
			}

		}
	};



	private void initializeViewerState(ViewerDataModel model,
			ViewerDataFilter filter, Collection<ViewerStateFeature> features) {
		itemMap = initializeItemMap();
		initializeItemList(model.initializeObservableItemList(filter, itemMap));
		for (ViewerStateFeature feature : features) {
			switch (feature) {
			case EDGE:
				initializeEdgeList(model.initializeObservableEdgeList(filter,
						itemMap));
				break;
			case CONTAINMENT:
				initializeContainmentList(model
						.initializeObservableContainmentList(filter, itemMap));
				break;
			}
		}
	}

	/*
	 * Item management
	 */
	/**
	 * Returns the item stored in this Viewer State
	 * 
	 * @return
	 */
	private IObservableList getItemList() {
		return itemList;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.incquery.viewers.runtime.model.ViewerState#getItems()
	 */
	@Override
	public IObservableCollection getItems() {
		return getItemList();
	}

	private void initializeItemList(IObservableList itemList) {
		if (this.itemList != null) {
			removeItemListener(this.itemList);
			for (Object _item : this.itemList) {
				Item item = (Item) _item;
				item.getLabel().removeChangeListener(labelChangeListener);
			}
		}
		this.itemList = itemList;
		addItemListener(itemList);
		for (Object _item : itemList) {
			Item item = (Item) _item;
			item.getLabel().addChangeListener(labelChangeListener);
		}
	}

	private void addItemListener(IObservableList _itemList) {
		_itemList.addListChangeListener(itemListener);
	}

	private void removeItemListener(IObservableList _oldItemList) {
		_oldItemList.removeListChangeListener(itemListener);
	}

	/*
	 * Edge management
	 */

	/**
	 * Returns the edges stored in this Viewer State
	 * 
	 * @return
	 */
	private IObservableList getEdgeList() {
		return edgeList;
	}
	
	@Override
	public IObservableCollection getEdges() {
		return getEdgeList();
	};

	private void initializeEdgeList(IObservableList edgeList) {
		if (this.edgeList != null) {
			removeEdgeListener(this.edgeList);
			for (Object _edge : edgeList) {
				Edge edge = (Edge) _edge;
				edge.getLabel().removeChangeListener(labelChangeListener);
			}
		}
		this.edgeList = edgeList;
		addEdgeListener(edgeList);
		for (Object _edge : edgeList) {
			Edge edge = (Edge) _edge;
			edge.getLabel().addChangeListener(labelChangeListener);
		}
	}

	private void addEdgeListener(IObservableList edgeList) {
		edgeList.addListChangeListener(edgeListener);
	}

	private void removeEdgeListener(IObservableList oldEdgeList) {
		oldEdgeList.removeListChangeListener(edgeListener);
	}

	/*
	 * Containment management
	 */

	/**
	 * Returns the containments stored in this Viewer State
	 * 
	 * @return
	 */
	private IObservableList getContainmentList() {
		return containmentList;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.incquery.viewers.runtime.model.ViewerState#getContainments()
	 */
	@Override
	public IObservableCollection getContainments() {
		return getContainmentList();
	}
	
	private void initializeContainmentList(IObservableList containmentList) {
		if (this.containmentList != null) {
			removeContainmentListener(this.containmentList);
		}
		this.containmentList = containmentList;
		childrenMap = HashMultimap.create();
		parentMap = Maps.newHashMap();
		for (Object obj : containmentList) {
			Containment containment = (Containment) obj;
			containmentAppeared(containment);
		}
		addContainmentListener(containmentList);
	}

	private void containmentAppeared(Containment containment) {
		childrenMap.put(containment.getSource(), containment.getTarget());
		parentMap.put(containment.getTarget(), containment.getSource());
		for (Object listener : stateListeners.getListeners()) {
			((IViewerStateListener) listener).containmentAppeared(containment);
		}
	}

	private void containmentDisappeared(Containment containment) {
		childrenMap.remove(containment.getSource(), containment.getTarget());
		parentMap.remove(containment.getTarget());
		for (Object listener : stateListeners.getListeners()) {
			((IViewerStateListener) listener)
					.containmentDisappeared(containment);
		}
	}

	private void addContainmentListener(IObservableList oldContainmentList) {
		oldContainmentList.addListChangeListener(containmentListener);
	}

	private void removeContainmentListener(IObservableList oldContainmentList) {
		oldContainmentList.removeListChangeListener(containmentListener);
	}
	
	
}

