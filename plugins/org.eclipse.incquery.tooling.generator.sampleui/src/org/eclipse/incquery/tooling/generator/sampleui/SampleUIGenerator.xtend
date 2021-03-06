/*******************************************************************************
 * Copyright (c) 2010-2012, Zoltan Ujhelyi, Mark Czotter, Istvan Rath and Daniel Varro
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Zoltan Ujhelyi, Mark Czotter - initial API and implementation
 *******************************************************************************/

package org.eclipse.incquery.tooling.generator.sampleui

import com.google.inject.Inject
import org.eclipse.core.runtime.Path
import org.eclipse.incquery.patternlanguage.emf.util.EMFPatternLanguageJvmModelInferrerUtil
import org.eclipse.incquery.patternlanguage.patternLanguage.Pattern
import org.eclipse.incquery.patternlanguage.patternLanguage.StringValue
import org.eclipse.incquery.tooling.core.generator.ExtensionGenerator
import org.eclipse.incquery.tooling.core.generator.fragments.IGenerationFragment
import org.eclipse.xtext.generator.IFileSystemAccess
import org.eclipse.xtext.xbase.lib.Pair

import static extension org.eclipse.incquery.patternlanguage.helper.CorePatternLanguageHelper.*

class SampleUIGenerator implements IGenerationFragment {

	@Inject extension EMFPatternLanguageJvmModelInferrerUtil
	private static val String ECLIPSE_UI_COMMANDS_EXTENSION_POINT = "org.eclipse.ui.commands"
	private static val String ECLIPSE_UI_HANDLERS_EXTENSION_POINT = "org.eclipse.ui.handlers"
	private static val String ECLIPSE_UI_MENUS_EXTENSION_POINT = "org.eclipse.ui.menus"
	public static val String UI_COMMANDS_PREFIX = "generated.incquery.command."
	public static val String UI_HANDLERS_PREFIX = "generated.incquery.handler."
	public static val String UI_MENUS_PREFIX = "generated.incquery.menu."
	@Inject extension ExtensionGenerator exGen = new ExtensionGenerator

	override generateFiles(Pattern pattern, IFileSystemAccess fsa) {
		fsa.generateFile(pattern.handlerClassJavaFile, pattern.patternHandler)
	}

	override cleanUp(Pattern pattern, IFileSystemAccess fsa) {
		fsa.deleteFile(pattern.handlerClassJavaFile)
	}

	override removeExtension(Pattern pattern) {
		newArrayList(
			Pair::of(pattern.commandExtensionId, ECLIPSE_UI_COMMANDS_EXTENSION_POINT),
			Pair::of(pattern.handlerExtensionId, ECLIPSE_UI_HANDLERS_EXTENSION_POINT),
			Pair::of(pattern.menuExtensionId, ECLIPSE_UI_MENUS_EXTENSION_POINT)
		)
	}

	override getRemovableExtensions() {
		newArrayList(
			Pair::of(UI_COMMANDS_PREFIX, ECLIPSE_UI_COMMANDS_EXTENSION_POINT),
			Pair::of(UI_HANDLERS_PREFIX, ECLIPSE_UI_HANDLERS_EXTENSION_POINT),
			Pair::of(UI_MENUS_PREFIX, ECLIPSE_UI_MENUS_EXTENSION_POINT)
		)
	}

	override getProjectDependencies() {
		newArrayList(
			"org.eclipse.core.runtime",
			"org.eclipse.ui",
		 	"org.eclipse.emf.ecore",
		 	"org.eclipse.pde.core",
		 	"org.eclipse.core.resources",
		 	"org.eclipse.incquery.runtime",
		 	"org.eclipse.incquery.tooling.ui")
	}

	override getProjectPostfix() {
		"ui"
	}

	override extensionContribution(Pattern pattern) {
		val menuContribution = pattern.menuContribution
		newArrayList(
		contribExtension(pattern.commandExtensionId, ECLIPSE_UI_COMMANDS_EXTENSION_POINT) [
			contribElement(it, "command") [
				contribAttribute(it, "id", pattern.commandId)
				contribAttribute(it, "name", "Get All Matches for " + pattern.fullyQualifiedName)
				contribAttribute(it, "categoryId", "org.eclipse.incquery.tooling.category")
			]
		],
		contribExtension(pattern.handlerExtensionId, ECLIPSE_UI_HANDLERS_EXTENSION_POINT) [
			contribElement(it, "handler") [
				contribAttribute(it, "commandId", pattern.commandId)
				contribAttribute(it, "class", pattern.handlerClassName)
			]
		],
		menuContribution
		)
	}

	def menuContribution(Pattern pattern) {
		val fileExtension = pattern.handlerFileExtension
		if (fileExtension.nullOrEmpty) {
			throw new IllegalArgumentException("FileExtension must be defined for Handler annotation in pattern: " + pattern.fullyQualifiedName);
		}
		contribExtension(pattern.menuExtensionId, ECLIPSE_UI_MENUS_EXTENSION_POINT) [
			contribElement(it, "menuContribution") [
				contribAttribute(it, "locationURI", "popup:org.eclipse.ui.popup.any")
				contribElement(it, "menu") [
					contribAttribute(it, "label", "EMF-IncQuery")
					contribElement(it, "command") [
						contribAttribute(it, "commandId", pattern.commandId)
						contribAttribute(it, "style", "push")
						contribElement(it, "visibleWhen") [
							contribAttribute(it, "checkEnabled", "false")
							contribElement(it, "with") [
								contribAttribute(it, "variable", "selection")
								contribElement(it, "iterate") [
									contribAttribute(it, "ifEmpty", "false")
									contribElement(it, "adapt") [
										contribAttribute(it, "type", "org.eclipse.core.resources.IFile")
										contribElement(it, "test") [
											contribAttribute(it, "property", "org.eclipse.core.resources.name")
											contribAttribute(it, "value", String::format("*.%s", fileExtension))
										]
									]
								]
							]
						]
					]
				]
			]
		]
	}

	def handlerFileExtension(Pattern pattern) {
		for (annotation : pattern.annotations) {
			if ("Handler".equals(annotation.name)) {
				for (parameter : annotation.parameters) {
					if ("fileExtension".equals(parameter.name)) {
						if (parameter.value instanceof StringValue) {
							return (parameter.value as StringValue).value
						}
					}
				}
			}
		}
		return null
	}

	def handlerClassName(Pattern pattern) {
		String::format("%s.handlers.%sHandler", pattern.packageName, pattern.realPatternName.toFirstUpper)
	}

	def handlerClassPath(Pattern pattern) {
		String::format("%s/handlers/%sHandler", pattern.packagePath, pattern.realPatternName.toFirstUpper)
	}

	def handlerClassJavaFile(Pattern pattern) {
		pattern.handlerClassPath + ".java"
	}

	def handlerExtensionId(Pattern pattern) {
		UI_HANDLERS_PREFIX + pattern.getFullyQualifiedName + "Handler"
	}
	def commandExtensionId(Pattern pattern) {
		UI_COMMANDS_PREFIX + pattern.getFullyQualifiedName + "Command"
	}
	def menuExtensionId(Pattern pattern) {
		UI_MENUS_PREFIX + pattern.getFullyQualifiedName + "MenuContribution"
	}
	def commandId(Pattern pattern) {
		pattern.getFullyQualifiedName + "CommandId"
	}

	def patternHandler(Pattern pattern) '''
		package «pattern.packageName».handlers;

		import java.util.Collection;

		import org.eclipse.core.commands.AbstractHandler;
		import org.eclipse.core.commands.ExecutionEvent;
		import org.eclipse.core.commands.ExecutionException;
		import org.eclipse.emf.ecore.resource.Resource;
		import org.eclipse.emf.ecore.resource.ResourceSet;
		import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
		import org.eclipse.core.resources.IFile;
		import org.eclipse.emf.common.notify.Notifier;
		import org.eclipse.emf.common.util.URI;

		import org.eclipse.jface.dialogs.MessageDialog;
		import org.eclipse.jface.viewers.IStructuredSelection;
		import org.eclipse.swt.widgets.Display;
		import org.eclipse.ui.handlers.HandlerUtil;
		import org.eclipse.incquery.tooling.ui.dialog.SampleUIDialogCreator;
		import org.eclipse.incquery.runtime.exception.IncQueryException;
		import org.eclipse.incquery.runtime.api.IncQueryEngine;

		import «pattern.packageName + "." + pattern.matcherClassName»;
		import «pattern.packageName + "." + pattern.matchClassName»;

		public class «pattern.name.toFirstUpper + "Handler"» extends AbstractHandler {

			@Override
			public Object execute(ExecutionEvent event) throws ExecutionException {
				//returns the selected element
				IStructuredSelection selection = (IStructuredSelection) HandlerUtil.getCurrentSelection(event);
				Object firstElement = selection.getFirstElement();
				//the filter is set in the command declaration no need for type checking
				IFile file = (IFile)firstElement;

				//Loads the resource
				ResourceSet resourceSet = new ResourceSetImpl();
				URI fileURI = URI.createPlatformResourceURI(file.getFullPath()
						.toString(), false);
				Resource resource = resourceSet.getResource(fileURI, true);

				«pattern.matcherClassName» matcher;
				try{
					matcher = «pattern.matcherClassName».querySpecification().getMatcher(resource /* or resourceSet */);
				} catch (IncQueryException ex) {
					throw new ExecutionException("Error creating pattern matcher", ex);
				}
				SampleUIDialogCreator.createDialog(matcher).open();
«««				String matches = getMatches(resource);
«««				//prints the match set to a dialog window
«««				MessageDialog.openInformation(Display.getCurrent().getActiveShell(), "Match set of the \"«pattern.name»\" pattern",
«««						matches);
				return null;
			}

«««			/**
«««			* Returns the match set of the «pattern.name» pattern on the input EMF resource
«««			* @param emfRoot the container of the EMF model on which the pattern matching is invoked
«««			* @return The serialized form of the match set
«««			*/
«««			private String getMatches(Notifier emfRoot){
«««				//the match set will be serialized into a string builder
«««				StringBuilder builder = new StringBuilder();
«««
«««				if(emfRoot != null) {
«««					//get all matches of the pattern
«««					«pattern.matcherClassName» matcher = «pattern.matcherClassName».querySpecification().getMatcher(IncQueryEngine.on(emfRoot));
«««					Collection<«pattern.matchClassName»> matches = matcher.getAllMatches();
«««					//serializes the current match into the string builder
«««					if(matches.size() > 0)
«««						for(«pattern.matchClassName» match: matches) {
«««							builder.append(match.toString());
«««					 		builder.append("\n");
«««						}
«««					else
«««						builder.append("The «pattern.name» pattern has an empty match set.");
«««				}
«««				//returns the match set in a serialized form
«««				return builder.toString();
«««			}
		}
	'''

	override getAdditionalBinIncludes() {
		return newArrayList(new Path("plugin.xml"))
	}

}