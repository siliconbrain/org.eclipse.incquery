/*******************************************************************************
 * Copyright (c) 2010-2013, Andras Okros, Istvan Rath and Daniel Varro
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Andras Okros - initial API and implementation
 *******************************************************************************/
package org.eclipse.incquery.tooling.ui.patternregistry.handlers;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.xtext.ui.resource.IResourceSetProvider;

import com.google.inject.Inject;

public class AddPatternHandler extends AbstractHandler {

    @Inject
    private IResourceSetProvider resourceSetProvider;

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        try {
            IFile file = (IFile) HandlerUtil.getActiveEditorInput(event).getAdapter(IFile.class);
            if (file != null) {
                RegisterHandlersUtil.registerSingleFile(file, resourceSetProvider);
            }
        } catch (Exception exception) {
            throw new ExecutionException("Error loading eiq file.", exception);
        }

        return null;
    }

}
