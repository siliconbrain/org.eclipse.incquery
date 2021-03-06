/*******************************************************************************
 * Copyright (c) 2010-2012, Mark Czotter, Istvan Rath and Daniel Varro
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Mark Czotter - initial API and implementation
 *******************************************************************************/

package org.eclipse.incquery.patternlanguage.emf.jvmmodel

import com.google.inject.Inject
import java.util.Collection
import java.util.HashSet
import java.util.Set
import org.eclipse.incquery.patternlanguage.emf.util.EMFJvmTypesBuilder
import org.eclipse.incquery.patternlanguage.emf.util.EMFPatternLanguageJvmModelInferrerUtil
import org.eclipse.incquery.patternlanguage.patternLanguage.Pattern
import org.eclipse.incquery.runtime.api.IMatchProcessor
import org.eclipse.incquery.runtime.matchers.tuple.Tuple
import org.eclipse.incquery.runtime.rete.misc.DeltaMonitor
import org.eclipse.xtext.common.types.JvmDeclaredType
import org.eclipse.xtext.common.types.JvmTypeReference
import org.eclipse.xtext.common.types.JvmVisibility
import org.eclipse.xtext.xbase.compiler.output.ITreeAppendable

class PatternMatcherClassMethodInferrer {

	@Inject extension JavadocInferrer
	@Inject extension EMFPatternLanguageJvmModelInferrerUtil
	@Inject extension EMFJvmTypesBuilder

	/**
   	 * Infers methods for Matcher class based on the input 'pattern'.
   	 */
   	def inferMethods(JvmDeclaredType type, Pattern pattern, JvmTypeReference matchClassReference) {
   		// Adding type-safe matcher calls
		// if the pattern not defines parameters, the Matcher class contains only the hasMatch method
		if (!pattern.parameters.isEmpty) {
			 type.members += pattern.toMethod("getAllMatches", pattern.newTypeRef(typeof(Collection), cloneWithProxies(matchClassReference))) [
   				documentation = pattern.javadocGetAllMatchesMethod.toString
   				for (parameter : pattern.parameters){
					parameters += parameter.toParameter(parameter.parameterName, parameter.calculateType)
   				}
   				body = [append('''
   					return rawGetAllMatches(new Object[]{«FOR p : pattern.parameters SEPARATOR ', '»«p.parameterName»«ENDFOR»});''')
   				]
   			]
   			type.members += pattern.toMethod("getOneArbitraryMatch", cloneWithProxies(matchClassReference)) [
   				documentation = pattern.javadocGetOneArbitraryMatchMethod.toString
   				for (parameter : pattern.parameters){
					parameters += parameter.toParameter(parameter.parameterName, parameter.calculateType)
   				}
   				body = [append('''
   					return rawGetOneArbitraryMatch(new Object[]{«FOR p : pattern.parameters SEPARATOR ', '»«p.parameterName»«ENDFOR»});''')
   				]
   			]
   			type.members += pattern.toMethod("hasMatch", pattern.newTypeRef(typeof(boolean))) [
   				documentation = pattern.javadocHasMatchMethod.toString
   				for (parameter : pattern.parameters){
					parameters += parameter.toParameter(parameter.parameterName, parameter.calculateType)
   				}
   				body = [append('''
   					return rawHasMatch(new Object[]{«FOR p : pattern.parameters SEPARATOR ', '»«p.parameterName»«ENDFOR»});''')
   				]
   			]
   			type.members += pattern.toMethod("countMatches", pattern.newTypeRef(typeof(int))) [
   				documentation = pattern.javadocCountMatchesMethod.toString
   				for (parameter : pattern.parameters){
					parameters += parameter.toParameter(parameter.parameterName, parameter.calculateType)
   				}
   				body = [append('''
   					return rawCountMatches(new Object[]{«FOR p : pattern.parameters SEPARATOR ', '»«p.parameterName»«ENDFOR»});''')
   				]
   			]
   			type.members += pattern.toMethod("forEachMatch", null) [
   				returnType = pattern.newTypeRef(Void::TYPE)
   				documentation = pattern.javadocForEachMatchMethod.toString
   				for (parameter : pattern.parameters){
					parameters += parameter.toParameter(parameter.parameterName, parameter.calculateType)
   				}
				parameters += pattern.toParameter("processor", pattern.newTypeRef(typeof (IMatchProcessor), cloneWithProxies(matchClassReference).wildCardSuper))
   				body = [append('''
   					rawForEachMatch(new Object[]{«FOR p : pattern.parameters SEPARATOR ', '»«p.parameterName»«ENDFOR»}, processor);''')
   				]
   			]
   			type.members += pattern.toMethod("forOneArbitraryMatch", pattern.newTypeRef(typeof(boolean))) [
   				documentation = pattern.javadocForOneArbitraryMatchMethod.toString
   				for (parameter : pattern.parameters){
					parameters += parameter.toParameter(parameter.parameterName, parameter.calculateType)
   				}
   				parameters += pattern.toParameter("processor", pattern.newTypeRef(typeof (IMatchProcessor), cloneWithProxies(matchClassReference).wildCardSuper))
   				body = [append('''
   					return rawForOneArbitraryMatch(new Object[]{«FOR p : pattern.parameters SEPARATOR ', '»«p.parameterName»«ENDFOR»}, processor);''')
   				]
   			]
   			type.members += pattern.toMethod("newFilteredDeltaMonitor", pattern.newTypeRef(typeof(DeltaMonitor), cloneWithProxies(matchClassReference))) [
   				documentation = pattern.javadocNewFilteredDeltaMonitorMethod.toString
    			parameters += pattern.toParameter("fillAtStart", pattern.newTypeRef(typeof (boolean)))
   				for (parameter : pattern.parameters){
					parameters += parameter.toParameter(parameter.parameterName, parameter.calculateType)
   				}
   				annotations += pattern.toAnnotation(typeof (Deprecated))
   				body = [append('''
   					return rawNewFilteredDeltaMonitor(fillAtStart, new Object[]{«FOR p : pattern.parameters SEPARATOR ', '»«p.parameterName»«ENDFOR»});''')
   				]
   			]
   			type.members += pattern.toMethod("newMatch", cloneWithProxies(matchClassReference)) [
    			documentation = pattern.javadocNewMatchMethod.toString
   				for (parameter : pattern.parameters){
					parameters += parameter.toParameter(parameter.parameterName, parameter.calculateType)
   				}
   				body = [append('''
   					return new ''')
	   				serialize(matchClassReference, pattern)
	   				append('''
	   				.«pattern.matchImmutableInnerClassName»(«FOR p : pattern.parameters SEPARATOR ', '»«p.parameterName»«ENDFOR»);
	   				''')
   				]
	   		]
   			for (variable : pattern.parameters){
   				val typeOfVariable = variable.calculateType
   				type.members += pattern.toMethod("rawAccumulateAllValuesOf"+variable.name, pattern.newTypeRef(typeof(Set), typeOfVariable)) [
	   				documentation = variable.javadocGetAllValuesOfMethod.toString
	   				parameters += pattern.toParameter("parameters", pattern.newTypeRef(typeof (Object)).addArrayTypeDimension)
   					visibility = JvmVisibility::PROTECTED
	   				body = [
						referClass(pattern, typeof(Set), typeOfVariable)
						append(''' results = new ''')
	   					referClass(pattern, typeof(HashSet), typeOfVariable)
	   					append('''
	   					();
	   					rawAccumulateAllValues(«variable.positionConstant», parameters, results);
	   					return results;''')
					]
	   			]
	   			type.members += pattern.toMethod("getAllValuesOf"+variable.name, pattern.newTypeRef(typeof(Set), typeOfVariable)) [
	   				documentation = variable.javadocGetAllValuesOfMethod.toString
	   				body = [append('''
	   					return rawAccumulateAllValuesOf«variable.name»(emptyArray());''')
	   				]
	   			]
	   			if(pattern.parameters.size > 1){
		   			type.members += pattern.toMethod("getAllValuesOf"+variable.name, pattern.newTypeRef(typeof(Set), typeOfVariable)) [
		   				documentation = variable.javadocGetAllValuesOfMethod.toString
		   				parameters += pattern.toParameter("partialMatch", cloneWithProxies(matchClassReference))
		   				body = [append('''
		   					return rawAccumulateAllValuesOf«variable.name»(partialMatch.toArray());''')
		   				]
		   			]
		   			type.members += pattern.toMethod("getAllValuesOf"+variable.name, pattern.newTypeRef(typeof(Set), typeOfVariable)) [
		   				documentation = variable.javadocGetAllValuesOfMethod.toString
		   				for (parameter : pattern.parameters){
		   					if(parameter != variable){
								parameters += parameter.toParameter(parameter.parameterName, parameter.calculateType)
			   				}
		   				}
		   				body = [
		   					append('''return rawAccumulateAllValuesOf«variable.name»(new Object[]{«FOR p : pattern.parameters SEPARATOR ', '»«
		   						if (p.parameterName == variable.parameterName) "null" else p.parameterName
		   						»«ENDFOR»});'''
		   					)
		   				]
		   			]
	   			}
   			}
		} else {
			type.members += pattern.toMethod("hasMatch", pattern.newTypeRef(typeof(boolean))) [
   				documentation = pattern.javadocHasMatchMethodNoParameter.toString
   				body = [append('''
   					return rawHasMatch(new Object[]{});''')
   				]
   			]
		}

		type.inferMatcherClassToMatchMethods(pattern, matchClassReference)
   	}

   	/**
   	 * Infers tupleToMatch, arrayToMatch methods for Matcher class based on the input 'pattern'.
   	 */
   	def inferMatcherClassToMatchMethods(JvmDeclaredType matcherClass, Pattern pattern, JvmTypeReference matchClassRef) {
	   	val tupleToMatchMethod = pattern.toMethod("tupleToMatch", cloneWithProxies(matchClassRef)) [
   			annotations += pattern.toAnnotation(typeof (Override))
   			visibility = JvmVisibility::PROTECTED
   			parameters += pattern.toParameter("t", pattern.newTypeRef(typeof (Tuple)))
   		]
   		val arrayToMatchMethod = pattern.toMethod("arrayToMatch", cloneWithProxies(matchClassRef)) [
   			annotations += pattern.toAnnotation(typeof (Override))
   			visibility = JvmVisibility::PROTECTED
   			parameters += pattern.toParameter("match", pattern.newTypeRef(typeof (Object)).addArrayTypeDimension)
   		]
   		val arrayToMatchMutableMethod = pattern.toMethod("arrayToMatchMutable", cloneWithProxies(matchClassRef)) [
   			annotations += pattern.toAnnotation(typeof (Override))
   			visibility = JvmVisibility::PROTECTED
   			parameters += pattern.toParameter("match", pattern.newTypeRef(typeof (Object)).addArrayTypeDimension)
   		]
   		tupleToMatchMethod.setBody([it | pattern.inferTupleToMatchMethodBody(it)])
   		arrayToMatchMethod.setBody([it | pattern.inferArrayToMatchMethodBody(it)])
   		arrayToMatchMutableMethod.setBody([it | pattern.inferArrayToMatchMutableMethodBody(it)])
   		matcherClass.members += tupleToMatchMethod
   		matcherClass.members += arrayToMatchMethod
   		matcherClass.members += arrayToMatchMutableMethod
   	}

  	/**
  	 * Infers the tupleToMatch method body.
  	 */
  	def inferTupleToMatchMethodBody(Pattern pattern, ITreeAppendable appendable) {
   		appendable.append('''
   			try {
   			  return new «pattern.matchClassName».«pattern.matchImmutableInnerClassName»(«FOR p : pattern.parameters SEPARATOR ', '»(«p.calculateType.qualifiedName») t.get(«p.positionConstant»)«ENDFOR»);
   			} catch(ClassCastException e) {''')
   		appendable.increaseIndentation
   		inferErrorLogging("Element(s) in tuple not properly typed!", "e", appendable)
   		appendable.decreaseIndentation
   		appendable.newLine
   		appendable.append('''
   			  return null;
   			}
   		''')
  	}

  	/**
  	 * Infers the arrayToMatch method body.
  	 */
  	def inferArrayToMatchMethodBody(Pattern pattern, ITreeAppendable appendable) {
  		appendable.append('''
   			try {
   			  return new «pattern.matchClassName».«pattern.matchImmutableInnerClassName»(«FOR p : pattern.parameters SEPARATOR ', '»(«p.calculateType.qualifiedName») match[«p.positionConstant»]«ENDFOR»);
   			} catch(ClassCastException e) {''')
   		appendable.increaseIndentation
   		inferErrorLogging("Element(s) in array not properly typed!", "e", appendable)
   		appendable.decreaseIndentation
   		appendable.newLine
   		appendable.append('''
   			  return null;
   			}
   		''')
  	}
  	/**
  	 * Infers the arrayToMatch method body.
  	 */
  	def inferArrayToMatchMutableMethodBody(Pattern pattern, ITreeAppendable appendable) {
  		appendable.append('''
   			try {
   			  return new «pattern.matchClassName».«pattern.matchMutableInnerClassName»(«FOR p : pattern.parameters SEPARATOR ', '»(«p.calculateType.qualifiedName») match[«p.positionConstant»]«ENDFOR»);
   			} catch(ClassCastException e) {''')
   		appendable.increaseIndentation
   		inferErrorLogging("Element(s) in array not properly typed!", "e", appendable)
   		appendable.decreaseIndentation
   		appendable.newLine
   		appendable.append('''
   			  return null;
   			}
   		''')
  	}

  	/**
  	 * Infers the appropriate logging based on the parameters.
  	 *
  	 */
  	def inferErrorLogging(String message, String exceptionName,  ITreeAppendable appendable) {
  		appendable.newLine
  		if(exceptionName == null){
	  		appendable.append('''LOGGER.error("«message»");''')
  		} else {
  			appendable.append('''LOGGER.error("«message»",«exceptionName»);''')
  		}
	}

}