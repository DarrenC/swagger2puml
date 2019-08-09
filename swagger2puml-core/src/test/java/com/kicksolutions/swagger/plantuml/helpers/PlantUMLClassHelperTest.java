package com.kicksolutions.swagger.plantuml.helpers;

import com.kicksolutions.swagger.plantuml.vo.ClassDiagram;
import io.swagger.models.Swagger;
import io.swagger.parser.SwaggerParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class PlantUMLClassHelperTest {

  private PlantUMLClassHelper helper = new PlantUMLClassHelper(true);

  @Test
  @DisplayName("Check basic Class Diagram list creation")
  void processSwaggerModels() {
    // TODO - replace with a mock to allow specific testing
    String specFile = "src/test/resources/petstore/swagger.yaml";
    Swagger swagger = new SwaggerParser().read(new File(specFile).getAbsolutePath());

    List<ClassDiagram> classDiagrams = helper.processSwaggerModels(swagger);

    assertNotNull(classDiagrams, "Model should have at least one class");
  }
}