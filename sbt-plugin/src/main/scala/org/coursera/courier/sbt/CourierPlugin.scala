/*
 Copyright 2015 Coursera Inc.

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */

package org.coursera.courier.sbt

import org.coursera.courier.generator.ScalaDataTemplateGenerator

import sbt._
import Keys._
import java.io.File.pathSeparator
import scala.collection.JavaConverters._
import org.slf4j.impl.StaticLoggerBinder

/**
 * Based on the SBT Plugin from Sleipnir by Dmitriy Yefremov, which is in turn based on
 * the rest.li-sbt-plugin project.
 *
 * @author Dmitriy Yefremov
 * @author Dean Thompson
 * @author Jim Brikman
 * @author Joe Betz
 */
object CourierPlugin extends Plugin {

  val courierGenerator = taskKey[Seq[File]]("Generates Scala bindings for .pdsc files")

  val courierSourceDirectory = settingKey[File](
    "Directory with .pdsc files used to generate Scala bindings")

  val courierDestinationDirectory = settingKey[File]("Directory with the generated bindings")

  val courierPrefix = settingKey[Option[String]](
    "Namespace prefix used for generated Scala classes")

  val courierCacheSources = taskKey[File]("Caches .pdsc sources")

  /**
   * Settings that need to be added to the project to enable generation of Scala bindings for PDSC
   * files located in the project.
   */
  val courierSettings: Seq[Def.Setting[_]] = Seq(

    courierPrefix := Some("scala"),

    courierSourceDirectory := sourceDirectory.value / "main" / "pegasus",

    courierDestinationDirectory := target.value / s"scala-${scalaBinaryVersion.value}" / "courier",

    courierCacheSources := streams.value.cacheDirectory / "pdsc.sources",

    courierGenerator in Compile := {
      // Allow use of slf4j logging inside the generator code that we call into later
      StaticLoggerBinder.sbtLogger = streams.value.log

      val log = streams.value.log
      val src = courierSourceDirectory.value
      val dst = courierDestinationDirectory.value
      val namespacePrefix = courierPrefix.value
      // adds in .pdscs from projects that this project .dependsOn
      val resolverPathFiles = Seq(src.getAbsolutePath) ++
        (managedClasspath in Compile).value.map(_.data.getAbsolutePath) ++
        (internalDependencyClasspath in Compile).value.map(_.data.getAbsolutePath)
      val resolverPath = resolverPathFiles.mkString(pathSeparator)

      val cacheFileSources = courierCacheSources.value
      val sourceFiles = (src ** "*.pdsc").get
      val previousScalaFiles = (dst ** "*.scala").get
      val s = streams.value

      val (anyFilesChanged, cacheSourceFiles) = {
        prepareCacheUpdate(cacheFileSources, sourceFiles, s)
      }

      log.debug("Detected changed files: " + anyFilesChanged)
      if (anyFilesChanged) {
        log.info("Generating Scala bindings for PDSC...")
        log.info("Courier resolver path: " + resolverPath)
        log.info("Courier source path: " + src)
        log.info("Courier destination path: " + dst)
        val result = ScalaDataTemplateGenerator.run(
          resolverPath = resolverPath,
          defaultPackage = namespacePrefix.getOrElse(""),
          generateImported = false,
          targetDirectoryPath = dst.absolutePath,
          sources = sourceFiles.map(_.absolutePath).toArray)

        // NOTE: Deleting stale files does not work properly with courier activated on two different
        // projects.
        //val staleFiles = previousScalaFiles.sorted.diff(generatedFiles.sorted)
        //log.info("Not deleting stale files " + staleFiles.mkString(", "))
        //IO.delete(staleFiles)
        cacheSourceFiles()
        result.getModifiedFiles.asScala.toSeq
      } else {
        previousScalaFiles
      }
    },

    sourceGenerators in Compile <+= (courierGenerator in Compile),

    unmanagedSourceDirectories in Compile += courierDestinationDirectory.value,

    managedSourceDirectories in Compile += courierDestinationDirectory.value,

    cleanFiles += courierDestinationDirectory.value,

    libraryDependencies += {
      val version = CourierPlugin.getClass.getPackage.getImplementationVersion
      "org.coursera.courier" % s"courier-generator_${scalaBinaryVersion.value}" % version
    }

  )

  /**
   * Returns an indication of whether `sourceFiles` and their modify dates differ from what is
   * recorded in `cacheFile`, plus a function that can be called to write `sourceFiles` and their
   * modify dates to `cacheFile`.
   */
  def prepareCacheUpdate(cacheFile: File, sourceFiles: Seq[File],
                         streams: std.TaskStreams[_]): (Boolean, () => Unit) = {
    val fileToModifiedMap = sourceFiles.map(f => f -> FileInfo.lastModified(f)).toMap

    val (_, previousFileToModifiedMap) = Sync.readInfo(cacheFile)(FileInfo.lastModified.format)
    //we only care about the source files here
    val relation = Seq.fill(sourceFiles.size)(file(".")).zip(sourceFiles)

    streams.log.debug(
      s"${fileToModifiedMap.size} <- current VS previous -> ${previousFileToModifiedMap.size}")
    val anyFilesChanged = !cacheFile.exists || (previousFileToModifiedMap != fileToModifiedMap)
    def updateCache() {
      Sync.writeInfo(cacheFile, Relation.empty[File, File] ++ relation.toMap,
        sourceFiles.map(f => f -> FileInfo.lastModified(f)).toMap)(FileInfo.lastModified.format)
    }
    (anyFilesChanged, updateCache)
  }

  /**
   * Generates settings that place the artifact generated by `packagingTaskKey` in the specified
   * `ivyConfig`, while also suffixing the artifact name with "-" and the `ivyConfig`.
   */
  def restliArtifactSettings(
      packagingTaskKey : TaskKey[File])(ivyConfig : String): Seq[Def.Setting[_]] = {
    val config = Configurations.config(ivyConfig)

    Seq(
      (artifact in packagingTaskKey) <<= (artifact in packagingTaskKey) { artifact =>
        artifact.copy(name = artifact.name + "-" + ivyConfig,
          configurations = artifact.configurations ++ Seq(config))
      },
      ivyConfigurations += config
    )
  }

  /**
   * Finds descendants of `dir` matching `globExpr` and maps them to paths relative to `dir`.
   */
  def mappings(dir : File, globExpr : String): Seq[(File, String)] = {
    val filter = GlobFilter(globExpr)
    Seq(dir).flatMap(d => Path.allSubpaths(d).filter{ case (f, id) => filter.accept(f) } )
  }

  //package
  val packageDataModel = taskKey[File]("Produces a data model jar containing only pdsc files")

  // Returns settings that can be applied to a project to cause it to package the Pegasus artifacts.
  def pegasusArtifacts: Seq[Def.Setting[_]] = {
    def packageDataModelMappings: Def.Initialize[Task[Seq[(File, String)]]] =
      courierSourceDirectory.map { (dir) =>
        mappings(dir, "*.pdsc")
      }

    // The resulting settings create the two packaging tasks, put their artifacts in specific
    // Ivy configs, and add their artifacts to the project.

    val defaultConfig = config("default").extend(Runtime).describedAs(
      "Configuration for default artifacts.")

    val dataTemplateConfig = new Configuration(
      name = "dataTemplate",
      description = "pegasus data templates",
      isPublic = true,
      extendsConfigs = List(Compile),
      transitive = true)

    Defaults.packageTaskSettings(packageDataModel, packageDataModelMappings) ++
      restliArtifactSettings(packageDataModel)("dataModel") ++
      Seq(
        packagedArtifacts <++= Classpaths.packaged(Seq(packageDataModel)),
        artifacts <++= Classpaths.artifactDefs(Seq(packageDataModel)),
        ivyConfigurations ++= List(dataTemplateConfig, defaultConfig),
        artifact in (Compile, packageBin) ~= { (art: Artifact) =>
          art.copy(configurations = art.configurations ++ List(dataTemplateConfig))
        }
      )
  }

}
