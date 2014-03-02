/*******************************************************************************
 * Copyright (c) 2010-2012, Abel Hegedus, Istvan Rath and Daniel Varro
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Abel Hegedus - initial API and implementation
 *******************************************************************************/
package org.eclipse.incquery.querybasedfeatures.runtime.util.validation;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.incquery.patternlanguage.annotations.IPatternAnnotationAdditionalValidator;
import org.eclipse.incquery.patternlanguage.emf.eMFPatternLanguage.EClassifierConstraint;
import org.eclipse.incquery.patternlanguage.emf.types.IEMFTypeProvider;
import org.eclipse.incquery.patternlanguage.helper.CorePatternLanguageHelper;
import org.eclipse.incquery.patternlanguage.patternLanguage.AggregatedValue;
import org.eclipse.incquery.patternlanguage.patternLanguage.Annotation;
import org.eclipse.incquery.patternlanguage.patternLanguage.BoolValue;
import org.eclipse.incquery.patternlanguage.patternLanguage.CheckConstraint;
import org.eclipse.incquery.patternlanguage.patternLanguage.CompareConstraint;
import org.eclipse.incquery.patternlanguage.patternLanguage.CompareFeature;
import org.eclipse.incquery.patternlanguage.patternLanguage.Constraint;
import org.eclipse.incquery.patternlanguage.patternLanguage.PathExpressionConstraint;
import org.eclipse.incquery.patternlanguage.patternLanguage.Pattern;
import org.eclipse.incquery.patternlanguage.patternLanguage.PatternBody;
import org.eclipse.incquery.patternlanguage.patternLanguage.PatternCall;
import org.eclipse.incquery.patternlanguage.patternLanguage.PatternCompositionConstraint;
import org.eclipse.incquery.patternlanguage.patternLanguage.PatternLanguagePackage;
import org.eclipse.incquery.patternlanguage.patternLanguage.StringValue;
import org.eclipse.incquery.patternlanguage.patternLanguage.ValueReference;
import org.eclipse.incquery.patternlanguage.patternLanguage.Variable;
import org.eclipse.incquery.patternlanguage.patternLanguage.VariableValue;
import org.eclipse.incquery.patternlanguage.validation.IIssueCallback;
import org.eclipse.incquery.querybasedfeatures.runtime.QueryBasedFeatureKind;
import org.eclipse.incquery.runtime.base.api.FunctionalDependency;
import org.eclipse.incquery.runtime.base.api.FunctionalDependencyHelper;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.inject.Inject;

/**
 * @author Abel Hegedus
 * 
 */
public class QueryBasedFeaturePatternValidator implements IPatternAnnotationAdditionalValidator {

    private static final String VALIDATOR_BASE_CODE = "org.eclipse.incquery.querybasedfeatures.";
    public static final String GENERAL_ISSUE_CODE = VALIDATOR_BASE_CODE + "general";
    public static final String METAMODEL_ISSUE_CODE = VALIDATOR_BASE_CODE + "faulty_metamodel";
    public static final String PATTERN_ISSUE_CODE = VALIDATOR_BASE_CODE + "faulty_pattern";
    public static final String ANNOTATION_ISSUE_CODE = VALIDATOR_BASE_CODE + "faulty_annotation";

    @Inject
    private IEMFTypeProvider typeProvider;

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.eclipse.incquery.patternlanguage.annotations.IPatternAnnotationAdditionalValidator#executeAdditionalValidation
     * (org.eclipse.incquery.patternlanguage.patternLanguage.Annotation,
     * org.eclipse.incquery.patternlanguage.validation.IIssueCallback)
     */
    @Override
    public void executeAdditionalValidation(Annotation annotation, IIssueCallback validator) {
        Pattern pattern = (Pattern) annotation.eContainer();

        // 1. at least two parameters
        if (pattern.getParameters().size() < 2) {
            validator.error("Query-based feature pattern must have at least two parameters.", pattern,
                    PatternLanguagePackage.Literals.PATTERN__PARAMETERS, PATTERN_ISSUE_CODE);
            return;
        }
        // 2. first parameter or "source" (if set) is EClassifier -> Source
        Variable source = null;
        ValueReference ref = CorePatternLanguageHelper.getFirstAnnotationParameter(annotation, "source");
        if (ref == null) {
            source = pattern.getParameters().get(0);
        } else if (ref instanceof VariableValue) {
            source = CorePatternLanguageHelper.getParameterByName(pattern, ((VariableValue) ref).getValue().getVar());
            if (pattern.getParameters().get(0).equals(source)) {
                validator.warning("The 'source' parameter is not needed if it is the first pattern parameter.", ref,
                        PatternLanguagePackage.Literals.VARIABLE_VALUE__VALUE, ANNOTATION_ISSUE_CODE);
            }
        }
        EClassifier sourceClassifier = null;
        if (source != null) {
            sourceClassifier = typeProvider.getClassifierForVariable(source);
        }
        if (sourceClassifier == null || !(sourceClassifier instanceof EClass)) {
            validator.error("The 'source' parameter must be EClass.", source,
                    PatternLanguagePackage.Literals.VARIABLE__TYPE, PATTERN_ISSUE_CODE);
            return;
        }
        EClass sourceClass = (EClass) sourceClassifier;

        // 3. pattern name or "feature" is a feature of Source
        String featureName = null;
        EObject contextForFeature = null;
        EStructuralFeature contextESFForFeature = null;
        ref = CorePatternLanguageHelper.getFirstAnnotationParameter(annotation, "feature");
        if (ref == null) {
            featureName = pattern.getName();
            contextForFeature = pattern;
            contextESFForFeature = PatternLanguagePackage.Literals.PATTERN__NAME;
        } else if (ref instanceof StringValue) {
            featureName = ((StringValue) ref).getValue();
            contextForFeature = ref;
            contextESFForFeature = PatternLanguagePackage.Literals.STRING_VALUE__VALUE;
        }
        if (featureName == null || featureName.isEmpty()) {
            validator.error("The 'feature' parameter must not be empty.", ref,
                    PatternLanguagePackage.Literals.STRING_VALUE__VALUE, ANNOTATION_ISSUE_CODE);
            return;
        }
        EStructuralFeature feature = null;
        for (EStructuralFeature f : sourceClass.getEStructuralFeatures()) {
            if (featureName.equals(f.getName())) {
                feature = f;
                break;
            }
        }
        if (feature == null) {
            validator.error(String.format("Cannot find feature %s of EClass %s.", featureName, sourceClass.getName()),
                    contextForFeature, contextESFForFeature, ANNOTATION_ISSUE_CODE);
            return;
        } else {
            if (feature instanceof EReference) {
                boolean featureError = false;
                if (!feature.isDerived()) {
                    validator.error(String.format("Feature %s is not derived.", featureName), contextForFeature,
                            contextESFForFeature, METAMODEL_ISSUE_CODE);
                    featureError = true;
                }
                if (!feature.isTransient()) {
                    validator.error(String.format("Feature %s is not transient.", featureName), contextForFeature,
                            contextESFForFeature, METAMODEL_ISSUE_CODE);
                    featureError = true;
                }
                if (!feature.isVolatile()) {
                    validator.error(String.format("Feature %s is not volatile.", featureName), contextForFeature,
                            contextESFForFeature, METAMODEL_ISSUE_CODE);
                    featureError = true;
                }
                if (featureError) {
                    return;
                }
                if (feature.isChangeable()) {
                    validator.warning(
                            String.format("Feature %s is changeable, make sure to implement setter.", featureName),
                            contextForFeature, contextESFForFeature, METAMODEL_ISSUE_CODE);
                }
            }
        }
        EClassifier classifier = feature.getEGenericType().getEClassifier();
        if (classifier == null) {
            validator.error(String.format("Feature %s has no type information set in the metamodel", featureName),
                    contextForFeature, contextESFForFeature, METAMODEL_ISSUE_CODE);
            return;
        }
        // 4. second parameter or "target" (if set) is compatible(?) with feature type -> Target
        Variable target = null;
        ref = CorePatternLanguageHelper.getFirstAnnotationParameter(annotation, "target");
        if (ref == null) {
            target = pattern.getParameters().get(1);
        } else if (ref instanceof VariableValue) {
            target = CorePatternLanguageHelper.getParameterByName(pattern, ((VariableValue) ref).getValue().getVar());
            if (pattern.getParameters().get(1).equals(target)) {
                validator.warning("The 'target' parameter is not needed if it is the second pattern parameter.", ref,
                        PatternLanguagePackage.Literals.VARIABLE_VALUE__VALUE, ANNOTATION_ISSUE_CODE);
            }
        }
        EClassifier targetClassifier = typeProvider.getClassifierForVariable(target);
        if (targetClassifier == null) {
            validator.warning("Cannot find target EClassifier", target, PatternLanguagePackage.Literals.VARIABLE__TYPE,
                    PATTERN_ISSUE_CODE);
        }

        // 5. "kind" (if set) is valid enum value
        QueryBasedFeatureKind kind = null;
        ref = CorePatternLanguageHelper.getFirstAnnotationParameter(annotation, "kind");
        if (ref instanceof StringValue) {
            String kindStr = ((StringValue) ref).getValue();
            if (QueryBasedFeatureKind.getStringValue(QueryBasedFeatureKind.SINGLE_REFERENCE).equals(kindStr)) {
                if (feature.getUpperBound() != 1) {
                    validator.error(
                            String.format("Upper bound of feature %s should be 1 for single 'kind'.", featureName),
                            ref, PatternLanguagePackage.Literals.STRING_VALUE__VALUE, METAMODEL_ISSUE_CODE);
                    return;
                }
                kind = QueryBasedFeatureKind.SINGLE_REFERENCE;
            } else if (QueryBasedFeatureKind.getStringValue(QueryBasedFeatureKind.MANY_REFERENCE).equals(kindStr)) {
                if (feature.getUpperBound() != -1 && feature.getUpperBound() < 2) {
                    validator.error(String.format(
                            "Upper bound of feature %s should be -1 or larger than 1 for many 'kind'.", featureName),
                            ref, PatternLanguagePackage.Literals.STRING_VALUE__VALUE, METAMODEL_ISSUE_CODE);
                    return;
                }
                kind = QueryBasedFeatureKind.MANY_REFERENCE;
            } else if (QueryBasedFeatureKind.getStringValue(QueryBasedFeatureKind.COUNTER).equals(kindStr)
                    || QueryBasedFeatureKind.getStringValue(QueryBasedFeatureKind.SUM).equals(kindStr)) {
                if (!classifier.equals(EcorePackage.Literals.EINT)) {
                    validator.error(
                            String.format("Type of feature %s should be EInt for %s 'kind'.", featureName, kindStr),
                            ref, PatternLanguagePackage.Literals.STRING_VALUE__VALUE, METAMODEL_ISSUE_CODE);
                    return;
                }
                kind = QueryBasedFeatureKind.COUNTER;
            } else if (QueryBasedFeatureKind.getStringValue(QueryBasedFeatureKind.ITERATION).equals(kindStr)) {
                validator.warning("Don't forget to subclass QueryBasedFeature for iteration 'kind'.", ref,
                        PatternLanguagePackage.Literals.STRING_VALUE__VALUE, ANNOTATION_ISSUE_CODE);
                kind = QueryBasedFeatureKind.ITERATION;
            }

            // Check for mismatch between specified and calculated kind
            if (kind != null) {
                if (isParameterKindIncorrect(pattern, target, kind)) {
                    validator.error("Specified kind is incorrect.", ref,
                            PatternLanguagePackage.Literals.STRING_VALUE__VALUE, ANNOTATION_ISSUE_CODE);
                }
            }

        }

        if (!classifier.equals(targetClassifier)
                && (kind == QueryBasedFeatureKind.SINGLE_REFERENCE || kind == QueryBasedFeatureKind.MANY_REFERENCE)) {
            validator.warning(String.format("The 'target' parameter type %s is not equal to actual feature type %s.",
                    featureName, sourceClass.getName()), target, PatternLanguagePackage.Literals.VARIABLE__TYPE,
                    PATTERN_ISSUE_CODE);
        }

        // 6. keepCache (if set) is correct for the kind
        ref = CorePatternLanguageHelper.getFirstAnnotationParameter(annotation, "keepCache");
        if (ref instanceof BoolValue) {
            boolean keepCache = ((BoolValue) ref).isValue();
            if (keepCache == false) {
                switch (kind) {
                case SINGLE_REFERENCE:
                case MANY_REFERENCE:
                    // OK
                    break;
                default:
                    validator.error("Cacheless behavior only available for single and many kinds.", ref,
                            PatternLanguagePackage.Literals.STRING_VALUE__VALUE, ANNOTATION_ISSUE_CODE);
                    break;
                }
            }
        }
    }

    private final static boolean isParameterKindIncorrect(Pattern pattern, Variable parameter,
            QueryBasedFeatureKind kind) {
        if (!pattern.getParameters().contains(parameter))
            throw new IllegalArgumentException("The specified variable is not a parameter of the specified pattern.");

        for (PatternBody body : pattern.getBodies()) {
            if (isVariableKindIncorrect(pattern, body, parameter, kind))
                return true;
        }

        return false;
    }

    private final static boolean isVariableKindIncorrect(Pattern pattern, PatternBody body, Variable variable,
            QueryBasedFeatureKind kind) {
        for (Constraint constraint : body.getConstraints()) {
            if (isVariableKindIncorrect(pattern, body, constraint, variable, kind))
                return true;
        }
        return false;
    }

    private final static boolean isVariableKindIncorrect(Pattern pattern, PatternBody body, Constraint constraint,
            Variable variable, QueryBasedFeatureKind kind) {
        if (constraint instanceof CheckConstraint) {

        }

        if (constraint instanceof CompareConstraint) {
            return isVariableKindIncorrect(pattern, body, (CompareConstraint) constraint, variable, kind);
        }

        if (constraint instanceof EClassifierConstraint) {

        }

        if (constraint instanceof PathExpressionConstraint) {
            return isVariableKindIncorrect((PathExpressionConstraint) constraint, variable, kind);
        }

        if (constraint instanceof PatternCompositionConstraint) {
            return isVariableKindIncorrect(pattern, (PatternCompositionConstraint) constraint, variable, kind);
        }

        return false;
    }

    private final static boolean isVariableKindIncorrect(Pattern pattern, PatternBody body,
            CompareConstraint constraint, Variable variable, QueryBasedFeatureKind kind) {
        if (constraint.getFeature() == CompareFeature.EQUALITY) {
            if (constraint.getLeftOperand() instanceof VariableValue
                    && ((VariableValue) constraint.getLeftOperand()).getValue().getVariable().equals(variable)) {
                return isValueReferenceIncorrectKind(pattern, body, variable, kind, constraint.getRightOperand());
            }
            if (constraint.getRightOperand() instanceof VariableValue
                    && ((VariableValue) constraint.getRightOperand()).getValue().getVariable().equals(variable)) {
                return isValueReferenceIncorrectKind(pattern, body, variable, kind, constraint.getLeftOperand());
            }
        }

        return false;
    }

    private final static boolean isVariableKindIncorrect(PathExpressionConstraint constraint, Variable variable,
            QueryBasedFeatureKind kind) {
        if ((kind == QueryBasedFeatureKind.COUNTER || kind == QueryBasedFeatureKind.SUM)
                && containsReferenceToVariable(constraint, variable)) {
            return true;
        }

        return false;
    }

    private final static boolean containsReferenceToVariable(PathExpressionConstraint constraint, Variable variable) {
        TreeIterator<EObject> it = constraint.eAllContents();
        while (it.hasNext()) {
            if (it.next().equals(variable))
                return true;
        }

        return false;
    }

    private final static boolean isVariableKindIncorrect(Pattern pattern, PatternCompositionConstraint constraint,
            Variable variable, QueryBasedFeatureKind kind) {
        PatternCall call = constraint.getCall();
        EList<ValueReference> callParameters = call.getParameters();
        for (int i = 0; i < callParameters.size(); ++i) {
            if (callParameters.get(i) instanceof VariableValue
                    && ((VariableValue) callParameters.get(i)).getValue().getVariable().equals(variable)) {
                return isParameterKindIncorrect(call.getPatternRef(), call.getPatternRef().getParameters().get(i), kind);
            }
        }

        return false;
    }

    private final static boolean isValueReferenceIncorrectKind(Pattern pattern, PatternBody body, Variable variable,
            QueryBasedFeatureKind kind, ValueReference reference) {
        if (reference instanceof AggregatedValue
                && !(kind == QueryBasedFeatureKind.COUNTER || kind == QueryBasedFeatureKind.SUM)) {
            return true;
        }

        return false;
    }

    private final static Set<FunctionalDependency<Variable>> getParameterDependencies(Pattern pattern) {
        return FunctionalDependencyHelper.project(getDependencies(pattern), Sets.newHashSet(pattern.getParameters()));
    }

    private final static Set<FunctionalDependency<Variable>> getDependencies(Pattern pattern) {
        Set<FunctionalDependency<Variable>> result = null;

        for (PatternBody body : pattern.getBodies()) {
            Set<FunctionalDependency<Variable>> dependencies = getDependencies(body);

            if (result == null) {
                result = dependencies;
            } else {
                result.addAll(dependencies);
            }
        }

        return result;
    }

    private final static Set<FunctionalDependency<Variable>> getDependencies(PatternBody body) {
        Set<FunctionalDependency<Variable>> result = null;

        for (Constraint constraint : body.getConstraints()) {
            Set<FunctionalDependency<Variable>> dependencies = getDependencies(constraint);

            if (result == null) {
                result = dependencies;
            } else {
                result.addAll(dependencies);
            }
        }

        return result;
    }

    private final static Set<FunctionalDependency<Variable>> getDependencies(Constraint constraint) {
        Set<FunctionalDependency<Variable>> dependencies = new HashSet<FunctionalDependency<Variable>>();

        if (constraint instanceof CompareConstraint) {
            CompareConstraint compareConstraint = (CompareConstraint) constraint;
            if (compareConstraint.getFeature() == CompareFeature.EQUALITY) {
                ValueReference leftOp = compareConstraint.getLeftOperand();
                ValueReference rightOp = compareConstraint.getRightOperand();
                Variable leftVar = asVariable(leftOp);
                Variable rightVar = asVariable(rightOp);
                Set<Variable> leftVarSet = asVariableSet(leftOp);
                Set<Variable> rightVarSet = asVariableSet(rightOp);

                if (rightVar != null)
                    dependencies.add(new FunctionalDependency<Variable>(leftVarSet, rightVar));
                if (leftVar != null)
                    dependencies.add(new FunctionalDependency<Variable>(rightVarSet, leftVar));
            }
        } else if (constraint instanceof PathExpressionConstraint) {
            PathExpressionConstraint pathExprConstraint = (PathExpressionConstraint) constraint;
            Variable src = pathExprConstraint.getHead().getSrc().getVariable();
            Variable dst = asVariable(pathExprConstraint.getHead().getDst());

            if (dst != null)
                dependencies.add(new FunctionalDependency<Variable>(asVariableSet(src), dst));

        } else if (constraint instanceof PatternCompositionConstraint) {
            PatternCompositionConstraint compositionConstraint = (PatternCompositionConstraint) constraint;
            PatternCall call = compositionConstraint.getCall();
            Pattern callee = call.getPatternRef();

            Set<FunctionalDependency<Variable>> calleeDependencies = getParameterDependencies(callee);
            Map<Variable, ValueReference> parameterTranslationTable = getTranslationTable(callee.getParameters(),
                    call.getParameters());

            dependencies.addAll(translate(calleeDependencies, parameterTranslationTable));
        }

        return dependencies;
    }

    private final static Variable asVariable(ValueReference valueRef) {
        return valueRef instanceof VariableValue ? ((VariableValue) valueRef).getValue().getVariable() : null;
    }

    private final static Set<Variable> asVariableSet(ValueReference valueRef) {
        if (valueRef instanceof VariableValue) {
            return asVariableSet(asVariable(valueRef));
        } else if (valueRef instanceof AggregatedValue) {
            AggregatedValue aggrValue = (AggregatedValue) valueRef;
            Set<Variable> result = Sets.newHashSet();
            for (ValueReference param : aggrValue.getCall().getParameters()) {
                result.addAll(asVariableSet(param));
            }
            return result;
        } else {
            return Sets.newHashSet();
        }
    }

    private final static Set<Variable> asVariableSet(Variable var) {
        return var == null ? Sets.<Variable> newHashSet() : Sets.newHashSet(var);
    }

    private final static Map<Variable, ValueReference> getTranslationTable(EList<Variable> calleeParams,
            EList<ValueReference> callParams) {
        if (calleeParams.size() != callParams.size()) {
            return null; // TODO: throw some exception
        }

        Map<Variable, ValueReference> table = Maps.newHashMap();

        for (int i = 0; i < calleeParams.size(); ++i) {
            table.put(calleeParams.get(i), callParams.get(i));
        }

        return table;
    }

    private final static Set<Variable> translateVarSet(Set<Variable> variableSet,
            Map<Variable, ValueReference> translationTable) {
        Set<Variable> result = Sets.newHashSet();

        for (Variable var : variableSet) {
            Variable localVar = asVariable(translationTable.get(var));

            if (localVar != null)
                result.add(localVar);
        }

        return result;
    }

    private final static Set<FunctionalDependency<Variable>> translate(
            Set<FunctionalDependency<Variable>> dependencies, Map<Variable, ValueReference> translationTable) {
        Set<FunctionalDependency<Variable>> result = Sets.newHashSet();

        for (FunctionalDependency<Variable> dependency : dependencies) {
            Variable localDependent = asVariable(translationTable.get(dependency.getDependent()));

            if (localDependent != null) {
                Set<Variable> localDeterminant = translateVarSet(dependency.getDeterminant(), translationTable);
                result.add(new FunctionalDependency<Variable>(localDeterminant, localDependent));
            }
        }

        return result;
    }

}
