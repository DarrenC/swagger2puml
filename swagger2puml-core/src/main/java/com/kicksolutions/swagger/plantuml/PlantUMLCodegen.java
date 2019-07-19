/**
 * 
 */
package com.kicksolutions.swagger.plantuml;

import com.kicksolutions.swagger.plantuml.vo.*;
import io.swagger.models.*;
import io.swagger.models.parameters.*;
import io.swagger.models.properties.*;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

import static com.kicksolutions.swagger.plantuml.FormatUtility.toTitleCase;

public class PlantUMLCodegen {

	private static final Logger LOGGER = Logger.getLogger(PlantUMLCodegen.class.getName());
	public static final String TITLE = "title";
	public static final String VERSION = "version";
	public static final String CLASS_DIAGRAMS = "classDiagrams";
	public static final String INTERFACE_DIAGRAMS = "interfaceDiagrams";
	public static final String ENTITY_RELATIONS = "entityRelations";

	private boolean generateDefinitionModelOnly;
	private boolean includeCardinality;
	private Swagger swagger;
	private File targetLocation;
	private static final String CARDINALITY_ONE_TO_MANY = "1..*";
	private static final String CARDINALITY_NONE_TO_MANY = "0..*";
	private static final String CARDINALITY_ONE_TO_ONE = "1..1";
	private static final String CARDINALITY_NONE_TO_ONE = "0..1";

	public PlantUMLCodegen(Swagger swagger, File targetLocation, boolean generateDefinitionModelOnly,
			boolean includeCardinality) {
		this.swagger = swagger;
		this.targetLocation = targetLocation;
		this.generateDefinitionModelOnly = generateDefinitionModelOnly;
		this.includeCardinality = includeCardinality;
	}

	/**
	 * generate a PlantUML File based on this classes Swagger property
	 *
	 * @return filepath to the PlantUML file as a String
	 * @throws IOException - If there is an error writing the file
	 * @throws IllegalAccessException - if there is an issue generating the file information
	 */
	public String generatePlantUmlFile(Swagger swagger) throws IOException, IllegalAccessException {
		LOGGER.entering(LOGGER.getName(), "generatePlantUmlFile");

		Map<String, Object> plantUmlObjectModelMap = convertSwaggerToPlantUmlObjectModelMap(swagger);

		MustacheUtility mustacheUtility = new MustacheUtility();
		String plantUmlFilePath = mustacheUtility.createPlantUmlFile(targetLocation, plantUmlObjectModelMap);

		LOGGER.exiting(LOGGER.getName(), "generatePlantUmlFile");
		return plantUmlFilePath;
	}

	public Map<String, Object> convertSwaggerToPlantUmlObjectModelMap(Swagger swagger) {
		LOGGER.entering(LOGGER.getName(), "convertSwaggerToPlantUmlObjectModelMap");

		Map<String, Object> additionalProperties = new TreeMap<>();

		additionalProperties.put(TITLE, swagger.getInfo().getTitle());
		additionalProperties.put(VERSION, swagger.getInfo().getVersion());

		List<ClassDiagram> classDiagrams = processSwaggerModels(swagger);
		additionalProperties.put(CLASS_DIAGRAMS, classDiagrams);

		List<InterfaceDiagram> interfaceDiagrams = new ArrayList<>();
		
		if (!generateDefinitionModelOnly) {
			interfaceDiagrams.addAll(processSwaggerPaths(swagger));
			additionalProperties.put(INTERFACE_DIAGRAMS, interfaceDiagrams);
		}
		
		additionalProperties.put(ENTITY_RELATIONS, getRelations(classDiagrams, interfaceDiagrams));

		LOGGER.exiting(LOGGER.getName(), "convertSwaggerToPlantUmlObjectModelMap");

		return additionalProperties;
	}

	private List<ClassRelation> getRelations(List<ClassDiagram> classDiagrams,List<InterfaceDiagram> interfaceDiagrams){
		List<ClassRelation> relations = new ArrayList<>();
		relations.addAll(getAllModelRelations(classDiagrams));
		relations.addAll(getAllInterfacesRelations(interfaceDiagrams));
		
		return filterUnique(relations,false);
	}

	private List<ClassRelation> getAllModelRelations(List<ClassDiagram> classDiagrams){
		List<ClassRelation> modelRelations = new ArrayList<>();
		
		for(ClassDiagram classDiagram: classDiagrams){
			 List<ClassRelation> classRelations = classDiagram.getChildClass();
			 
			 for(ClassRelation classRelation: classRelations){
				 classRelation.setSourceClass(classDiagram.getClassName());
				 modelRelations.add(classRelation);
			 }
		}		
		
		return modelRelations;
	}

	private List<ClassRelation> getAllInterfacesRelations(List<InterfaceDiagram> interfaceDiagrams){
		List<ClassRelation> modelRelations = new ArrayList<>();
		
		for(InterfaceDiagram classDiagram: interfaceDiagrams){
			 List<ClassRelation> classRelations = classDiagram.getChildClass();
			 
			 for(ClassRelation classRelation: classRelations){
				classRelation.setSourceClass(classDiagram.getInterfaceName());
				modelRelations.add(classRelation);
			 }
		}		
		
		return modelRelations;
	}

	private List<InterfaceDiagram> processSwaggerPaths(Swagger swagger) {
		LOGGER.entering(LOGGER.getName(), "processSwaggerPaths");
		List<InterfaceDiagram> interfaceDiagrams = new ArrayList<>();
		Map<String, Path> paths = swagger.getPaths();

		for (Map.Entry<String, Path> entry : paths.entrySet()) {
			Path pathObject = entry.getValue();

			LOGGER.info("Processing Path --> " + entry.getKey());

			List<Operation> operations = pathObject.getOperations();
			String uri = entry.getKey();

			for (Operation operation : operations) {
				interfaceDiagrams.add(getInterfaceDiagram(operation, uri));
			}
		}

		LOGGER.exiting(LOGGER.getName(), "processSwaggerPaths");
		return interfaceDiagrams;
	}

	private InterfaceDiagram getInterfaceDiagram(Operation operation, String uri) {
		LOGGER.entering(LOGGER.getName(), "getInterfaceDiagram");

		InterfaceDiagram interfaceDiagram = new InterfaceDiagram();
		String interfaceName = getInterfaceName(operation.getTags(), operation, uri);
		List<String> errorClassNames = getErrorClassNames(operation);
		interfaceDiagram.setInterfaceName(interfaceName);
		interfaceDiagram.setErrorClasses(errorClassNames);
		interfaceDiagram.setMethods(getInterfaceMethods(operation));
		interfaceDiagram.setChildClass(getInterfaceRelations(operation,errorClassNames));

		LOGGER.exiting(LOGGER.getName(), "getInterfaceDiagram");
		return interfaceDiagram;
	}

	private List<ClassRelation> getInterfaceRelations(Operation operation, List<String> errorClassNames) {
		List<ClassRelation> relations = new ArrayList<>();
		relations.addAll(getInterfaceRelatedResponses(operation));
		relations.addAll(getInterfaceRelatedInputs(operation));
		for(String errorClassName : errorClassNames){
			relations.add(getErrorClass(errorClassName));
		}
		
		return filterUnique(relations,true);
	}

	private List<ClassRelation> filterUnique(List<ClassRelation> relations,boolean compareTargetOnly){
		List<ClassRelation> uniqueList = new ArrayList<>();
		
		for(ClassRelation relation: relations){			
			if(!isTargetClassInMap(relation, uniqueList,compareTargetOnly)){
				uniqueList.add(relation);
			}
		}
		
		return uniqueList;
	}

	private boolean isTargetClassInMap(ClassRelation sourceRelation, List<ClassRelation> relatedResponses,
																		 boolean considerTargetOnly) {
		for (ClassRelation relation : relatedResponses) {
			
			if(considerTargetOnly){
				if(StringUtils.isNotEmpty(relation.getTargetClass()) && StringUtils.isNotEmpty(sourceRelation.getTargetClass())
						&& relation.getTargetClass().equalsIgnoreCase(sourceRelation.getTargetClass())){
					return true;
				}
			}
			else{
				if(StringUtils.isNotEmpty(relation.getSourceClass()) 
						&& StringUtils.isNotEmpty(sourceRelation.getSourceClass()) 
						&& StringUtils.isNotEmpty(relation.getTargetClass())
						&& StringUtils.isNotEmpty(sourceRelation.getTargetClass())
						&& relation.getSourceClass().equalsIgnoreCase(sourceRelation.getSourceClass()) 
						&& relation.getTargetClass().equalsIgnoreCase(sourceRelation.getTargetClass())){
					
					return true;
				}
			}
		}

		return false;
	}

	private ClassRelation getErrorClass(String errorClassName){
		ClassRelation classRelation = new ClassRelation();
		classRelation.setTargetClass(errorClassName);
		classRelation.setComposition(false);
		classRelation.setExtension(true);
				
		return classRelation;
	}


	private List<ClassRelation> getInterfaceRelatedInputs(Operation operation) {
		List<ClassRelation> relatedResponses = new ArrayList<>();
		List<Parameter> parameters = operation.getParameters();

		for (Parameter parameter : parameters) {
			if (parameter instanceof BodyParameter) {
				Model bodyParameter = ((BodyParameter) parameter).getSchema();

				if (bodyParameter instanceof RefModel) {

					ClassRelation classRelation = new ClassRelation();
					classRelation.setTargetClass(((RefModel) bodyParameter).getSimpleRef());
					classRelation.setComposition(false);
					classRelation.setExtension(true);

					relatedResponses.add(classRelation);
				} else if (bodyParameter instanceof ArrayModel) {
					Property propertyObject = ((ArrayModel) bodyParameter).getItems();

					if (propertyObject instanceof RefProperty) {
						ClassRelation classRelation = new ClassRelation();
						classRelation.setTargetClass(((RefProperty) propertyObject).getSimpleRef());
						classRelation.setComposition(false);
						classRelation.setExtension(true);

						relatedResponses.add(classRelation);
					}
				}
			}
		}

		return relatedResponses;
	}

	private List<ClassRelation> getInterfaceRelatedResponses(Operation operation) {
		List<ClassRelation> relatedResponses = new ArrayList<>();
		Map<String,Response> responses = operation.getResponses();
		
		for (Map.Entry<String, Response> responsesEntry : responses.entrySet()){			
			String responseCode = responsesEntry.getKey();
			
			if (!(responseCode.equalsIgnoreCase("default") || Integer.parseInt(responseCode) >= 300)) {
				Property responseProperty = responsesEntry.getValue().getSchema();
				
				if(responseProperty instanceof RefProperty){
					ClassRelation relation = new ClassRelation();
					relation.setTargetClass(((RefProperty)responseProperty).getSimpleRef());
					relation.setComposition(false);
					relation.setExtension(true);
					
					relatedResponses.add(relation);
				}
				else if (responseProperty instanceof ArrayProperty){
					ArrayProperty arrayObject =  (ArrayProperty)responseProperty;
					Property arrayResponseProperty = arrayObject.getItems();
					
					if(arrayResponseProperty instanceof RefProperty){
						ClassRelation relation = new ClassRelation();
						relation.setTargetClass(((RefProperty)arrayResponseProperty).getSimpleRef());
						relation.setComposition(false);
						relation.setExtension(true);
						
						relatedResponses.add(relation);
					}
				}
			}
			
		}
		
		return relatedResponses;
	}

	private List<MethodDefinitions> getInterfaceMethods(Operation operation) {
		List<MethodDefinitions> interfaceMethods = new ArrayList<>();
		MethodDefinitions methodDefinitions = new MethodDefinitions();
		methodDefinitions.setMethodDefinition(new StringBuilder().append(operation.getOperationId()).append("(")
				.append(getMethodParameters(operation)).append(")").toString());
		methodDefinitions.setReturnType(getInterfaceReturnType(operation));

		interfaceMethods.add(methodDefinitions);

		return interfaceMethods;
	}

	private String getMethodParameters(Operation operation) {
		String methodParameter = "";
		List<Parameter> parameters = operation.getParameters();

		for (Parameter parameter : parameters) {
			if (StringUtils.isNotEmpty(methodParameter)) {
				methodParameter = new StringBuilder().append(methodParameter).append(",").toString();
			}

			if (parameter instanceof PathParameter) {
				methodParameter = new StringBuilder().append(methodParameter)
						.append(toTitleCase(((PathParameter) parameter).getType())).append(" ")
						.append(((PathParameter) parameter).getName()).toString();
			} else if (parameter instanceof QueryParameter) {
				Property queryParameterProperty = ((QueryParameter) parameter).getItems();

				if (queryParameterProperty instanceof RefProperty) {
					methodParameter = new StringBuilder().append(methodParameter)
							.append(toTitleCase(((RefProperty) queryParameterProperty).getSimpleRef())).append("[] ")
							.append(((BodyParameter) parameter).getName()).toString();
				} else if (queryParameterProperty instanceof StringProperty) {
					methodParameter = new StringBuilder().append(methodParameter)
							.append(toTitleCase(((StringProperty) queryParameterProperty).getType())).append("[] ")
							.append(((QueryParameter) parameter).getName()).toString();
				} else {
					methodParameter = new StringBuilder().append(methodParameter)
							.append(toTitleCase(((QueryParameter) parameter).getType())).append(" ")
							.append(((QueryParameter) parameter).getName()).toString();
				}
			} else if (parameter instanceof BodyParameter) {
				Model bodyParameter = ((BodyParameter) parameter).getSchema();

				if (bodyParameter instanceof RefModel) {
					methodParameter = methodParameter +
							toTitleCase(((RefModel) bodyParameter).getSimpleRef()) + " " +
							((BodyParameter) parameter).getName();
				} else if (bodyParameter instanceof ArrayModel) {
					Property propertyObject = ((ArrayModel) bodyParameter).getItems();

					if (propertyObject instanceof RefProperty) {
						methodParameter = methodParameter +
								toTitleCase(((RefProperty) propertyObject).getSimpleRef()) + "[] " +
								((BodyParameter) parameter).getName();
					}
				}
			} else if (parameter instanceof FormParameter) {
				methodParameter = methodParameter +
						toTitleCase(((FormParameter) parameter).getType()) +
						" " + ((FormParameter) parameter).getName();
			}
		}

		return methodParameter;
	}

	private String getInterfaceReturnType(Operation operation) {
		String returnType = "void";

		Map<String, Response> responses = operation.getResponses();
		for (Map.Entry<String, Response> responsesEntry : responses.entrySet()) {
			String responseCode = responsesEntry.getKey();

			if (!(responseCode.equalsIgnoreCase("default") || Integer.parseInt(responseCode) >= 300)) {
				Property responseProperty = responsesEntry.getValue().getSchema();

				if (responseProperty instanceof RefProperty) {
					returnType = ((RefProperty) responseProperty).getSimpleRef();
				} else if (responseProperty instanceof ArrayProperty) {
					Property arrayResponseProperty = ((ArrayProperty) responseProperty).getItems();
					if (arrayResponseProperty instanceof RefProperty) {
						returnType = ((RefProperty) arrayResponseProperty).getSimpleRef() + "[]";
					}
				} else if (responseProperty instanceof ObjectProperty) {
					returnType = toTitleCase(operation.getOperationId()) + "Generated";
				}
			}
		}

		return returnType;
	}

	private String getErrorClassName(Operation operation) {
		StringBuilder errorClass = new StringBuilder();
		Map<String, Response> responses = operation.getResponses();
		for (Map.Entry<String, Response> responsesEntry : responses.entrySet()) {
			String responseCode = responsesEntry.getKey();

			if (responseCode.equalsIgnoreCase("default") || Integer.parseInt(responseCode) >= 300) {
				Property responseProperty = responsesEntry.getValue().getSchema();

				if (responseProperty instanceof RefProperty) {
					String errorClassName = ((RefProperty) responseProperty).getSimpleRef();
					if (!errorClass.toString().contains(errorClassName)) {
						if (StringUtils.isNotEmpty(errorClass)) {
							errorClass.append(",");
						}
						errorClass.append(errorClassName);
					}
				}
			}
		}

		return errorClass.toString();
	}

	private List<String> getErrorClassNames(Operation operation) {
		List<String> errorClasses = new ArrayList<>();
		Map<String, Response> responses = operation.getResponses();

		for (Map.Entry<String, Response> responsesEntry : responses.entrySet()) {
			String responseCode = responsesEntry.getKey();

			if (responseCode.equalsIgnoreCase("default") || Integer.parseInt(responseCode) >= 300) {
				Property responseProperty = responsesEntry.getValue().getSchema();

				if (responseProperty instanceof RefProperty) {
					String errorClassName = ((RefProperty) responseProperty).getSimpleRef();
					if (!errorClasses.contains(errorClassName)) {
							errorClasses.add(errorClassName);
					}
				}
			}
		}

		return errorClasses;
	}

	private String getInterfaceName(List<String> tags, Operation operation, String uri) {
		String interfaceName;

		if (!tags.isEmpty()) {
			interfaceName = toTitleCase(tags.get(0).replaceAll(" ", ""));
		}else if (StringUtils.isNotEmpty(operation.getOperationId())) {
			interfaceName = toTitleCase(operation.getOperationId());
		}else {
			interfaceName = toTitleCase(uri.replaceAll("{", "").replaceAll("}", "").replaceAll("\\", ""));
		}

		return interfaceName + "Api";
	}

	private List<ClassDiagram> processSwaggerModels(Swagger swagger) {
		LOGGER.entering(LOGGER.getName(), "processSwaggerModels");

		List<ClassDiagram> classDiagrams = new ArrayList<>();
		Map<String, Model> modelsMap = swagger.getDefinitions();

		for (Map.Entry<String, Model> models : modelsMap.entrySet()) {
			String className = models.getKey();
			Model modelObject = models.getValue();

			LOGGER.info("Processing Model " + className);

			String superClass = getSuperClass(modelObject);
			List<ClassMembers> classMembers = getClassMembers(modelObject, modelsMap);

			classDiagrams.add(new ClassDiagram(className, modelObject.getDescription(), classMembers,
					getChildClasses(classMembers, superClass), isModelClass(modelObject), superClass));
		}

		LOGGER.exiting(LOGGER.getName(), "processSwaggerModels");

		return classDiagrams;
	}

	private boolean isModelClass(Model model) {
		LOGGER.entering(LOGGER.getName(), "isModelClass");

		boolean isModelClass = true;

		if (model instanceof ModelImpl) {
			List<String> enumValues = ((ModelImpl) model).getEnum();

			if (enumValues != null && !enumValues.isEmpty()) {
				isModelClass = false;
			}
		}

		LOGGER.exiting(LOGGER.getName(), "isModelClass");

		return isModelClass;
	}

	private String getSuperClass(Model model) {
		LOGGER.entering(LOGGER.getName(), "getSuperClass");

		String superClass = null;

		if (model instanceof ArrayModel) {
			ArrayModel arrayModel = (ArrayModel) model;
			Property propertyObject = arrayModel.getItems();

			if (propertyObject instanceof RefProperty) {
				superClass = "ArrayList["+ ((RefProperty) propertyObject).getSimpleRef() + "]";
			}
		} else if (model instanceof ModelImpl) {
			Property addProperty = ((ModelImpl) model).getAdditionalProperties();

			if (addProperty instanceof RefProperty) {
				superClass = "Map[" + ((RefProperty) addProperty).getSimpleRef() + "]";
			}
		}

		LOGGER.exiting(LOGGER.getName(), "getSuperClass");

		return superClass;
	}

	private List<ClassRelation> getChildClasses(List<ClassMembers> classMembers, String superClass) {
		LOGGER.entering(LOGGER.getName(), "getChildClasses");

		List<ClassRelation> childClasses = new ArrayList<>();

		for (ClassMembers member : classMembers) {

			boolean alreadyExists = false;

			for (ClassRelation classRelation : childClasses) {

				if (classRelation.getTargetClass().equalsIgnoreCase(member.getClassName())) {
					alreadyExists = true;
				}
			}

			if (!alreadyExists && member.getClassName() != null && member.getClassName().trim().length() > 0) {
				if (StringUtils.isNotEmpty(superClass)) {
					childClasses.add(new ClassRelation(member.getClassName(), true, false, member.getCardinality(),null));
				} else {
					childClasses.add(new ClassRelation(member.getClassName(), false, true, member.getCardinality(),null));
				}
			}
		}

		LOGGER.exiting(LOGGER.getName(), "getChildClasses");

		return childClasses;
	}

	private List<ClassMembers> getClassMembers(Model modelObject, Map<String, Model> modelsMap) {
		LOGGER.entering(LOGGER.getName(), "getClassMembers");

		List<ClassMembers> classMembers = new ArrayList<>();

		if (modelObject instanceof ModelImpl) {
			classMembers = getClassMembers((ModelImpl) modelObject, modelsMap);
		} else if (modelObject instanceof ComposedModel) {
			classMembers = getClassMembers((ComposedModel) modelObject, modelsMap);
		} else if (modelObject instanceof ArrayModel) {
			classMembers = getClassMembers((ArrayModel) modelObject, modelsMap);
		}

		LOGGER.exiting(LOGGER.getName(), "getClassMembers");
		return classMembers;
	}

	private List<ClassMembers> getClassMembers(ArrayModel arrayModel, Map<String, Model> modelsMap) {
		LOGGER.entering(LOGGER.getName(), "getClassMembers-ArrayModel");

		List<ClassMembers> classMembers = new ArrayList<>();

		Property propertyObject = arrayModel.getItems();

		if (propertyObject instanceof RefProperty) {
			classMembers.add(getRefClassMembers((RefProperty) propertyObject));
		}

		LOGGER.exiting(LOGGER.getName(), "getClassMembers-ArrayModel");
		return classMembers;
	}

	private List<ClassMembers> getClassMembers(ComposedModel composedModel, Map<String, Model> modelsMap) {
      return getClassMembers(composedModel, modelsMap, new HashSet<>());
	}

  /**
   * New Overloaded getClassMembers Implementation to handle deeply nested class hierarchies
   * @param composedModel
   * @param modelsMap
   * @param visited
   * @return
   */
	private List<ClassMembers> getClassMembers(ComposedModel composedModel, Map<String, Model> modelsMap, Set<Model> visited) {
	  LOGGER.entering(LOGGER.getName(), "getClassMembers-ComposedModel-DeepNest");

		List<ClassMembers> classMembers = new ArrayList<>();
		Map<String, Property> childProperties = new HashMap<>();

		if (null != composedModel.getChild()) {
			childProperties = composedModel.getChild().getProperties();
		}

		List<ClassMembers> ancestorMembers;

		List<Model> allOf = composedModel.getAllOf();
		for (Model currentModel : allOf) {

			if (currentModel instanceof RefModel) {
				RefModel refModel = (RefModel) currentModel;
				// This line throws an NPE when encountering deeply nested class hierarchies because it assumes any child
        // classes are RefModel and not ComposedModel
//				childProperties.putAll(modelsMap.get(refModel.getSimpleRef()).getProperties());

        Model parentRefModel = modelsMap.get(refModel.getSimpleRef());

        if (parentRefModel.getProperties() != null) {
          childProperties.putAll(parentRefModel.getProperties());
        }

				classMembers = convertModelPropertiesToClassMembers(childProperties,
						modelsMap.get(refModel.getSimpleRef()), modelsMap);

        // If the parent model also has AllOf references -- meaning it's a child of some other superclass
        // then we need to recurse to get the grandparent's properties and add them to our current classes
        // derived property list
        if (parentRefModel instanceof ComposedModel) {
          ComposedModel parentRefComposedModel = (ComposedModel) parentRefModel;
          // Use visited to mark which classes we've processed -- this is just to avoid
          // an infinite loop in case there's a circular reference in the class hierarchy.
          if (!visited.contains(parentRefComposedModel)) {
            ancestorMembers = getClassMembers(parentRefComposedModel, modelsMap, visited);
            classMembers.addAll(ancestorMembers);
          }
        }
			}
		}

		visited.add(composedModel);
		LOGGER.exiting(LOGGER.getName(), "getClassMembers-ComposedModel-DeepNest");
		return classMembers;
  }

	private List<ClassMembers> getClassMembers(ModelImpl model, Map<String, Model> modelsMap) {
		LOGGER.entering(LOGGER.getName(), "getClassMembers-ModelImpl");

		List<ClassMembers> classMembers = new ArrayList<>();

		Map<String, Property> modelMembers = model.getProperties();
		if (modelMembers != null && !modelMembers.isEmpty()) {
			classMembers.addAll(convertModelPropertiesToClassMembers(modelMembers, model, modelsMap));
		} else {
			Property modelAdditionalProps = model.getAdditionalProperties();

			if (modelAdditionalProps instanceof RefProperty) {
				classMembers.add(getRefClassMembers((RefProperty) modelAdditionalProps));
			}

			if (modelAdditionalProps == null) {
				List<String> enumValues = model.getEnum();

				if (enumValues != null && !enumValues.isEmpty()) {
					classMembers.addAll(getEnum(enumValues));
				}
			}
		}

		LOGGER.exiting(LOGGER.getName(), "getClassMembers-ModelImpl");

		return classMembers;
	}

	private ClassMembers getRefClassMembers(RefProperty refProperty) {
		LOGGER.entering(LOGGER.getName(), "getRefClassMembers");
		ClassMembers classMember = new ClassMembers();
		classMember.setClassName(refProperty.getSimpleRef());
		classMember.setName(" ");

		if (includeCardinality) {
			classMember.setCardinality(CARDINALITY_NONE_TO_MANY);
		}

		LOGGER.exiting(LOGGER.getName(), "getRefClassMembers");
		return classMember;
	}

	private List<ClassMembers> getEnum(List<String> enumValues) {
		LOGGER.entering(LOGGER.getName(), "getEnum");

		List<ClassMembers> classMembers = new ArrayList<>();

		if (enumValues != null && !enumValues.isEmpty()) {
			for (String enumValue : enumValues) {
				ClassMembers classMember = new ClassMembers();
				classMember.setName(enumValue);
				classMembers.add(classMember);
			}
		}

		LOGGER.exiting(LOGGER.getName(), "getEnum");
		return classMembers;
	}

	private List<ClassMembers> convertModelPropertiesToClassMembers(Map<String, Property> modelMembers,
			Model modelObject, Map<String, Model> models) {
		LOGGER.entering(LOGGER.getName(), "convertModelPropertiesToClassMembers");

		List<ClassMembers> classMembers = new ArrayList<>();

		for (Map.Entry<String, Property> modelMapObject : modelMembers.entrySet()) {
			String variablName = modelMapObject.getKey();

			ClassMembers classMemberObject = new ClassMembers();
			Property property = modelMembers.get(variablName);

			if (property instanceof ArrayProperty) {
				classMemberObject = getClassMember((ArrayProperty) property, modelObject, models, variablName);
			} else if (property instanceof RefProperty) {
				classMemberObject = getClassMember((RefProperty) property, models, modelObject, variablName);
			} else {
				classMemberObject.setDataType(
						getDataType(property.getFormat() != null ? property.getFormat() : property.getType(), false));
				classMemberObject.setName(variablName);
			}

			classMembers.add(classMemberObject);
		}

		LOGGER.exiting(LOGGER.getName(), "convertModelPropertiesToClassMembers");
		return classMembers;
	}

	private ClassMembers getClassMember(ArrayProperty property, Model modelObject, Map<String, Model> models,
			String variablName) {
		LOGGER.entering(LOGGER.getName(), "getClassMember-ArrayProperty");

		ClassMembers classMemberObject = new ClassMembers();
		Property propObject = property.getItems();

		if (propObject instanceof RefProperty) {
			classMemberObject = getClassMember((RefProperty) propObject, models, modelObject, variablName);
		} else if (propObject instanceof StringProperty) {
			classMemberObject = getClassMember((StringProperty) propObject, variablName);
		}

		LOGGER.exiting(LOGGER.getName(), "getClassMember-ArrayProperty");
		return classMemberObject;
	}

	private ClassMembers getClassMember(StringProperty stringProperty, String variablName) {
		LOGGER.entering(LOGGER.getName(), "getClassMember-StringProperty");

		ClassMembers classMemberObject = new ClassMembers();
		classMemberObject.setDataType(getDataType(stringProperty.getType(), true));
		classMemberObject.setName(variablName);

		LOGGER.exiting(LOGGER.getName(), "getClassMember-StringProperty");
		return classMemberObject;
	}

	private ClassMembers getClassMember(RefProperty refProperty, Map<String, Model> models, Model modelObject,
			String variableName) {
		LOGGER.entering(LOGGER.getName(), "getClassMember-RefProperty");

		ClassMembers classMemberObject = new ClassMembers();
		classMemberObject.setDataType(getDataType(refProperty.getSimpleRef(), true));
		classMemberObject.setName(variableName);

		if (models.containsKey(refProperty.getSimpleRef())) {
			classMemberObject.setClassName(refProperty.getSimpleRef());
		}

		if (includeCardinality && StringUtils.isNotEmpty(variableName) && modelObject != null) {
			if (isRequiredProperty(modelObject, variableName)) {
				classMemberObject.setCardinality(CARDINALITY_ONE_TO_MANY);
			} else {
				classMemberObject.setCardinality(CARDINALITY_NONE_TO_MANY);
			}
		}

		LOGGER.exiting(LOGGER.getName(), "getClassMember-RefProperty");
		return classMemberObject;
	}

	private boolean isRequiredProperty(Model modelObject, String propertyName) {
		boolean isRequiredProperty = false;
		LOGGER.entering(LOGGER.getName(), "isRequiredProperty");

		if (modelObject != null) {
			if (modelObject instanceof ModelImpl) {
				List<String> requiredProperties = ((ModelImpl) modelObject).getRequired();
				if (requiredProperties != null && !requiredProperties.isEmpty()) {
					isRequiredProperty = requiredProperties.contains(propertyName);
				}
			}
		}

		LOGGER.exiting(LOGGER.getName(), "isRequiredProperty");
		return isRequiredProperty;
	}

	private String getDataType(String className, boolean isArray) {
		if (isArray) {
			return toTitleCase(className) + "[]";
		}

		return toTitleCase(className);
	}
}