package com.kicksolutions.swagger.plantuml;

import com.kicksolutions.swagger.plantuml.helpers.PlantUMLClassHelper;
import com.kicksolutions.swagger.plantuml.helpers.PlantUMLInterfaceDiagramHelper;
import com.kicksolutions.swagger.plantuml.helpers.PlantUMLRelationHelper;
import com.kicksolutions.swagger.plantuml.vo.ClassDiagram;
import com.kicksolutions.swagger.plantuml.vo.ClassRelation;
import com.kicksolutions.swagger.plantuml.vo.InterfaceDiagram;
import io.swagger.models.Swagger;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;

public class PlantUMLCodegen {

  private static final Logger LOGGER = Logger.getLogger(PlantUMLCodegen.class.getName());
  static final String TITLE = "title";
  static final String VERSION = "version";
  static final String CLASS_DIAGRAMS = "classDiagrams";
  static final String INTERFACE_DIAGRAMS = "interfaceDiagrams";
  static final String ENTITY_RELATIONS = "entityRelations";

  private boolean generateDefinitionModelOnly;
  private boolean includeCardinality;
  private Swagger swagger;
  private File targetLocation;

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
   * @throws IOException            - If there is an error writing the file
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

    // First refactoring point - use PlantUMLClassHelper
    PlantUMLClassHelper plantUMLClassHelper = new PlantUMLClassHelper(this.includeCardinality);
    List<ClassDiagram> classDiagrams = plantUMLClassHelper.processSwaggerModels(swagger);
    additionalProperties.put(CLASS_DIAGRAMS, classDiagrams);

    List<InterfaceDiagram> interfaceDiagrams = new ArrayList<>();

    if (!generateDefinitionModelOnly) {
      PlantUMLInterfaceDiagramHelper plantUMLInterfaceDiagramHelper = new PlantUMLInterfaceDiagramHelper();
      interfaceDiagrams.addAll(plantUMLInterfaceDiagramHelper.processSwaggerPaths(swagger));
      additionalProperties.put(INTERFACE_DIAGRAMS, interfaceDiagrams);
    }

    PlantUMLRelationHelper plantUMLRelationHelper = new PlantUMLRelationHelper();
    // TODO - Test class for this part
    additionalProperties.put(ENTITY_RELATIONS, plantUMLRelationHelper.getRelations(classDiagrams, interfaceDiagrams));

    LOGGER.exiting(LOGGER.getName(), "convertSwaggerToPlantUmlObjectModelMap");

    return additionalProperties;
  }

}