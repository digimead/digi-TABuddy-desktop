//
// This file is part of the TA Buddy project.
// Copyright (c) 2012-2014 Alexey Aksenov ezh@ezh.msk.ru
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU Affero General Global License version 3
// as published by the Free Software Foundation with the addition of the
// following permission added to Section 15 as permitted in Section 7(a):
// FOR ANY PART OF THE COVERED WORK IN WHICH THE COPYRIGHT IS OWNED
// BY Limited Liability Company «MEZHGALAKTICHESKIJ TORGOVYJ ALIANS»,
// Limited Liability Company «MEZHGALAKTICHESKIJ TORGOVYJ ALIANS» DISCLAIMS
// THE WARRANTY OF NON INFRINGEMENT OF THIRD PARTY RIGHTS.
//
// This program is distributed in the hope that it will be useful, but
// WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
// or FITNESS FOR A PARTICULAR PURPOSE.
// See the GNU Affero General Global License for more details.
// You should have received a copy of the GNU Affero General Global License
// along with this program; if not, see http://www.gnu.org/licenses or write to
// the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
// Boston, MA, 02110-1301 USA, or download the license from the following URL:
// http://www.gnu.org/licenses/agpl.html
//
// The interactive user interfaces in modified source and object code versions
// of this program must display Appropriate Legal Notices, as required under
// Section 5 of the GNU Affero General Global License.
//
// In accordance with Section 7(b) of the GNU Affero General Global License,
// you must retain the producer line in every report, form or document
// that is created or manipulated using TA Buddy.
//
// You can be released from the requirements of the license by purchasing
// a commercial license. Buying such a license is mandatory as soon as you
// develop commercial activities involving the TA Buddy software without
// disclosing the source code of your own applications.
// These activities include: offering paid services to customers,
// serving files in a web or/and network application,
// shipping TA Buddy with a closed source product.
//
// For more information, please contact Digimead Team at this
// address: ezh@ezh.msk.ru

// DATE Thu, 01 Jan 2015 22:23:14 +0300
// BASE PROJECT for componet of TA Buddy: Desktop v0.1.0.2

name := "digi-tabuddy-desktop-base"

description := "TA Buddy: Desktop application base project."

version := "0.1.0.2"

licenses := Seq("GNU Affero General Public License" -> url("http://www.gnu.org/licenses/agpl.html"))

organization := "org.digimead"

organizationHomepage := Some(url("http://digimead.org"))

homepage := Some(url("https://github.com/digimead/digi-TABuddy-desktop"))

lazy val extConfiguration = config("external")

ivyConfigurations += extConfiguration.hide

managedClasspath in extConfiguration := Classpaths.managedJars((configuration in extConfiguration).value, classpathTypes.value, update.value)

managedClasspath in Compile <++= managedClasspath in extConfiguration

managedClasspath in Runtime <++= managedClasspath in extConfiguration

managedClasspath in Test <++= managedClasspath in extConfiguration

resolvers += "snapshots" at "http://oss.sonatype.org/content/repositories/snapshots"

resolvers += "digimead-maven" at "http://commondatastorage.googleapis.com/maven.repository.digimead.org/"

crossScalaVersions := Seq("2.11.2")

scalaVersion := "2.11.2"

scalacOptions ++= Seq("-encoding", "UTF-8", "-deprecation", "-unchecked", "-Xcheckinit", "-feature")

javacOptions ++= Seq("-Xlint:unchecked", "-Xlint:deprecation")

lazy val baseProject = Project("digi-tabuddy-desktop-base", file(".")).configs(extConfiguration)

libraryDependencies += "org.scalatest" %% "scalatest" % "2.2.1" % "test"

// Maven libraries

libraryDependencies += "com.cathive.fonts" % "fonts-fontawesome" % "3.2.1.0"

libraryDependencies += "javax.mail" % "mail" % "1.4"

libraryDependencies += "org.bouncycastle" % "bcmail-jdk15on" % "1.50"

libraryDependencies += "org.bouncycastle" % "bcpg-jdk15on" % "1.50"

libraryDependencies += "org.bouncycastle" % "bcpkix-jdk15on" % "1.50"

libraryDependencies += "org.bouncycastle" % "bcprov-jdk15on" % "1.50"

libraryDependencies += "org.digimead" %% "digi-configgy" % "2.2.2.1"

libraryDependencies += "org.digimead" %% "digi-lib-jfx4swt" % "0.1.0.6"

libraryDependencies += "org.digimead" %% "digi-lib-util" % "0.3.2.0"

libraryDependencies += "org.digimead" %% "digi-lib" % "0.3.0.1"

libraryDependencies += "org.digimead" %% "digi-tabuddy-desktop-core-keyring" % "0.1.0.1-SNAPSHOT" % extConfiguration

libraryDependencies += "org.digimead" %% "digi-tabuddy-desktop-core-ui" % "0.1.0.1-SNAPSHOT" % extConfiguration

libraryDependencies += "org.digimead" %% "digi-tabuddy-desktop-core" % "0.1.0.1-SNAPSHOT" % extConfiguration

libraryDependencies += "org.digimead" %% "digi-tabuddy-desktop-id" % "0.1.0.1-SNAPSHOT" % extConfiguration

libraryDependencies += "org.digimead" %% "digi-tabuddy-desktop-logic" % "0.1.0.1-SNAPSHOT" % extConfiguration

libraryDependencies += "org.digimead" %% "digi-tabuddy-model" % "0.3.0.6"


// OSGi libraries

libraryDependencies += {
  val moduleId = ("com.ibm.icu" % "com.ibm.icu" % "50.1.1.v201304230130" % extConfiguration from
    "http://download.eclipse.org/eclipse/updates/4.3/R-4.3-201306052000/plugins/com.ibm.icu_50.1.1.v201304230130.jar")
  val source = _root_.sbt.Artifact.classified("com.ibm.icu", _root_.sbt.Artifact.SourceClassifier).copy(url = Some(new URL("http://download.eclipse.org/eclipse/updates/4.3/R-4.3-201306052000/plugins/com.ibm.icu.source_50.1.1.v201304230130.jar")))
  moduleId.copy(explicitArtifacts = moduleId.explicitArtifacts :+ source)
}

libraryDependencies += {
  val moduleId = ("javax.annotation" % "javax.annotation" % "1.1.0.v201209060031" % extConfiguration from
    "http://download.eclipse.org/eclipse/updates/4.3/R-4.3-201306052000/plugins/javax.annotation_1.1.0.v201209060031.jar")
  val source = _root_.sbt.Artifact.classified("javax.annotation", _root_.sbt.Artifact.SourceClassifier).copy(url = Some(new URL("http://download.eclipse.org/eclipse/updates/4.3/R-4.3.1-201309111000/plugins/javax.annotation.source_1.1.0.v201209060031.jar")))
  moduleId.copy(explicitArtifacts = moduleId.explicitArtifacts :+ source)
}

libraryDependencies += {
  val moduleId = ("javax.inject" % "javax.inject" % "1.0.0.v20091030" % extConfiguration from
    "http://download.eclipse.org/eclipse/updates/4.3/R-4.3-201306052000/plugins/javax.inject_1.0.0.v20091030.jar")
  val source = _root_.sbt.Artifact.classified("javax.inject", _root_.sbt.Artifact.SourceClassifier).copy(url = Some(new URL("http://download.eclipse.org/eclipse/updates/4.3/R-4.3.1-201309111000/plugins/javax.inject.source_1.0.0.v20091030.jar")))
  moduleId.copy(explicitArtifacts = moduleId.explicitArtifacts :+ source)
}

libraryDependencies += {
  val moduleId = ("javax.xml" % "javax.xml" % "1.3.4.v201005080400" % extConfiguration from
    "http://download.eclipse.org/eclipse/updates/4.3/R-4.3-201306052000/plugins/javax.xml_1.3.4.v201005080400.jar")
  moduleId
}

libraryDependencies += {
  val moduleId = ("org.apache.batik.css" % "org.apache.batik.css" % "1.6.0.v201011041432" % extConfiguration from
    "http://download.eclipse.org/eclipse/updates/4.3/R-4.3-201306052000/plugins/org.apache.batik.css_1.6.0.v201011041432.jar")
  moduleId
}

libraryDependencies += {
  val moduleId = ("org.apache.batik.util.gui" % "org.apache.batik.util.gui" % "1.6.0.v201011041432" % extConfiguration from
    "http://download.eclipse.org/eclipse/updates/4.3/R-4.3-201306052000/plugins/org.apache.batik.util.gui_1.6.0.v201011041432.jar")
  moduleId
}

libraryDependencies += {
  val moduleId = ("org.apache.batik.util" % "org.apache.batik.util" % "1.6.0.v201011041432" % extConfiguration from
    "http://download.eclipse.org/eclipse/updates/4.3/R-4.3-201306052000/plugins/org.apache.batik.util_1.6.0.v201011041432.jar")
  moduleId
}

libraryDependencies += {
  val moduleId = ("org.eclipse.compare.core" % "org.eclipse.compare.core" % "3.5.300.v20130514-1224" % extConfiguration from
    "http://download.eclipse.org/eclipse/updates/4.3/R-4.3-201306052000/plugins/org.eclipse.compare.core_3.5.300.v20130514-1224.jar")
  val source = _root_.sbt.Artifact.classified("org.eclipse.compare.core", _root_.sbt.Artifact.SourceClassifier).copy(url = Some(new URL("http://download.eclipse.org/eclipse/updates/4.3/R-4.3-201306052000/plugins/org.eclipse.compare.core.source_3.5.300.v20130514-1224.jar")))
  moduleId.copy(explicitArtifacts = moduleId.explicitArtifacts :+ source)
}

libraryDependencies += {
  val moduleId = ("org.eclipse.core.commands" % "org.eclipse.core.commands" % "3.6.100.v20130515-1857" % extConfiguration from
    "http://download.eclipse.org/eclipse/updates/4.3/R-4.3-201306052000/plugins/org.eclipse.core.commands_3.6.100.v20130515-1857.jar")
  val source = _root_.sbt.Artifact.classified("org.eclipse.core.commands", _root_.sbt.Artifact.SourceClassifier).copy(url = Some(new URL("http://download.eclipse.org/eclipse/updates/4.3/R-4.3-201306052000/plugins/org.eclipse.core.commands.source_3.6.100.v20130515-1857.jar")))
  moduleId.copy(explicitArtifacts = moduleId.explicitArtifacts :+ source)
}

libraryDependencies += {
  val moduleId = ("org.eclipse.core.contenttype" % "org.eclipse.core.contenttype" % "3.4.200.v20130326-1255" % extConfiguration from
    "http://download.eclipse.org/eclipse/updates/4.3/R-4.3-201306052000/plugins/org.eclipse.core.contenttype_3.4.200.v20130326-1255.jar")
  val source = _root_.sbt.Artifact.classified("org.eclipse.core.contenttype", _root_.sbt.Artifact.SourceClassifier).copy(url = Some(new URL("http://download.eclipse.org/eclipse/updates/4.3/R-4.3-201306052000/plugins/org.eclipse.core.contenttype.source_3.4.200.v20130326-1255.jar")))
  moduleId.copy(explicitArtifacts = moduleId.explicitArtifacts :+ source)
}

libraryDependencies += {
  val moduleId = ("org.eclipse.core.databinding.beans" % "org.eclipse.core.databinding.beans" % "1.2.200.v20130515-1857" % extConfiguration from
    "http://download.eclipse.org/eclipse/updates/4.3/R-4.3-201306052000/plugins/org.eclipse.core.databinding.beans_1.2.200.v20130515-1857.jar")
  val source = _root_.sbt.Artifact.classified("org.eclipse.core.databinding.beans", _root_.sbt.Artifact.SourceClassifier).copy(url = Some(new URL("http://download.eclipse.org/eclipse/updates/4.3/R-4.3-201306052000/plugins/org.eclipse.core.databinding.beans.source_1.2.200.v20130515-1857.jar")))
  moduleId.copy(explicitArtifacts = moduleId.explicitArtifacts :+ source)
}

libraryDependencies += {
  val moduleId = ("org.eclipse.core.databinding.observable" % "org.eclipse.core.databinding.observable" % "1.4.1.v20130515-1857" % extConfiguration from
    "http://download.eclipse.org/eclipse/updates/4.3/R-4.3-201306052000/plugins/org.eclipse.core.databinding.observable_1.4.1.v20130515-1857.jar")
  val source = _root_.sbt.Artifact.classified("org.eclipse.core.databinding.observable", _root_.sbt.Artifact.SourceClassifier).copy(url = Some(new URL("http://download.eclipse.org/eclipse/updates/4.3/R-4.3-201306052000/plugins/org.eclipse.core.databinding.observable.source_1.4.1.v20130515-1857.jar")))
  moduleId.copy(explicitArtifacts = moduleId.explicitArtifacts :+ source)
}

libraryDependencies += {
  val moduleId = ("org.eclipse.core.databinding.property" % "org.eclipse.core.databinding.property" % "1.4.200.v20130515-1857" % extConfiguration from
    "http://download.eclipse.org/eclipse/updates/4.3/R-4.3-201306052000/plugins/org.eclipse.core.databinding.property_1.4.200.v20130515-1857.jar")
  val source = _root_.sbt.Artifact.classified("org.eclipse.core.databinding.property", _root_.sbt.Artifact.SourceClassifier).copy(url = Some(new URL("http://download.eclipse.org/eclipse/updates/4.3/R-4.3-201306052000/plugins/org.eclipse.core.databinding.property.source_1.4.200.v20130515-1857.jar")))
  moduleId.copy(explicitArtifacts = moduleId.explicitArtifacts :+ source)
}

libraryDependencies += {
  val moduleId = ("org.eclipse.core.databinding" % "org.eclipse.core.databinding" % "1.4.1.v20130515-1857" % extConfiguration from
    "http://download.eclipse.org/eclipse/updates/4.3/R-4.3-201306052000/plugins/org.eclipse.core.databinding_1.4.1.v20130515-1857.jar")
  val source = _root_.sbt.Artifact.classified("org.eclipse.core.databinding", _root_.sbt.Artifact.SourceClassifier).copy(url = Some(new URL("http://download.eclipse.org/eclipse/updates/4.3/R-4.3-201306052000/plugins/org.eclipse.core.databinding.source_1.4.1.v20130515-1857.jar")))
  moduleId.copy(explicitArtifacts = moduleId.explicitArtifacts :+ source)
}

libraryDependencies += {
  val moduleId = ("org.eclipse.core.expressions" % "org.eclipse.core.expressions" % "3.4.501.v20131118-1915" % extConfiguration from
    "http://download.eclipse.org/eclipse/updates/4.3/R-4.3.2-201402211700/plugins/org.eclipse.core.expressions_3.4.501.v20131118-1915.jar")
  val source = _root_.sbt.Artifact.classified("org.eclipse.core.expressions", _root_.sbt.Artifact.SourceClassifier).copy(url = Some(new URL("http://download.eclipse.org/eclipse/updates/4.3/R-4.3.2-201402211700/plugins/org.eclipse.core.expressions.source_3.4.501.v20131118-1915.jar")))
  moduleId.copy(explicitArtifacts = moduleId.explicitArtifacts :+ source)
}

libraryDependencies += {
  val moduleId = ("org.eclipse.core.filesystem" % "org.eclipse.core.filesystem" % "1.4.0.v20130514-1240" % extConfiguration from
    "http://download.eclipse.org/eclipse/updates/4.3/R-4.3-201306052000/plugins/org.eclipse.core.filesystem_1.4.0.v20130514-1240.jar")
  val source = _root_.sbt.Artifact.classified("org.eclipse.core.filesystem", _root_.sbt.Artifact.SourceClassifier).copy(url = Some(new URL("http://download.eclipse.org/eclipse/updates/4.3/R-4.3-201306052000/plugins/org.eclipse.core.filesystem.source_1.4.0.v20130514-1240.jar")))
  moduleId.copy(explicitArtifacts = moduleId.explicitArtifacts :+ source)
}

libraryDependencies += {
  val moduleId = ("org.eclipse.core.jobs" % "org.eclipse.core.jobs" % "3.5.300.v20130429-1813" % extConfiguration from
    "http://download.eclipse.org/eclipse/updates/4.3/R-4.3-201306052000/plugins/org.eclipse.core.jobs_3.5.300.v20130429-1813.jar")
  val source = _root_.sbt.Artifact.classified("org.eclipse.core.jobs", _root_.sbt.Artifact.SourceClassifier).copy(url = Some(new URL("http://download.eclipse.org/eclipse/updates/4.3/R-4.3-201306052000/plugins/org.eclipse.core.jobs.source_3.5.300.v20130429-1813.jar")))
  moduleId.copy(explicitArtifacts = moduleId.explicitArtifacts :+ source)
}

libraryDependencies += {
  val moduleId = ("org.eclipse.core.resources" % "org.eclipse.core.resources" % "3.8.101.v20130717-0806" % extConfiguration from
    "http://download.eclipse.org/eclipse/updates/4.3/R-4.3.1-201309111000/plugins/org.eclipse.core.resources_3.8.101.v20130717-0806.jar")
  val source = _root_.sbt.Artifact.classified("org.eclipse.core.resources", _root_.sbt.Artifact.SourceClassifier).copy(url = Some(new URL("http://download.eclipse.org/eclipse/updates/4.3/R-4.3.1-201309111000/plugins/org.eclipse.core.resources.source_3.8.101.v20130717-0806.jar")))
  moduleId.copy(explicitArtifacts = moduleId.explicitArtifacts :+ source)
}

libraryDependencies += {
  val moduleId = ("org.eclipse.core.runtime" % "org.eclipse.core.runtime" % "3.9.100.v20131218-1515" % extConfiguration from
    "http://download.eclipse.org/eclipse/updates/4.3/R-4.3.2-201402211700/plugins/org.eclipse.core.runtime_3.9.100.v20131218-1515.jar")
  val source = _root_.sbt.Artifact.classified("org.eclipse.core.runtime", _root_.sbt.Artifact.SourceClassifier).copy(url = Some(new URL("http://download.eclipse.org/eclipse/updates/4.3/R-4.3.2-201402211700/plugins/org.eclipse.core.runtime.source_3.9.100.v20131218-1515.jar")))
  moduleId.copy(explicitArtifacts = moduleId.explicitArtifacts :+ source)
}

libraryDependencies += {
  val moduleId = ("org.eclipse.core.variables" % "org.eclipse.core.variables" % "3.2.700.v20130402-1741" % extConfiguration from
    "http://download.eclipse.org/eclipse/updates/4.3/R-4.3-201306052000/plugins/org.eclipse.core.variables_3.2.700.v20130402-1741.jar")
  val source = _root_.sbt.Artifact.classified("org.eclipse.core.variables", _root_.sbt.Artifact.SourceClassifier).copy(url = Some(new URL("http://download.eclipse.org/eclipse/updates/4.3/R-4.3-201306052000/plugins/org.eclipse.core.variables.source_3.2.700.v20130402-1741.jar")))
  moduleId.copy(explicitArtifacts = moduleId.explicitArtifacts :+ source)
}

libraryDependencies += {
  val moduleId = ("org.eclipse.e4.core.commands" % "org.eclipse.e4.core.commands" % "0.10.2.v20130515-1857" % extConfiguration from
    "http://download.eclipse.org/eclipse/updates/4.3/R-4.3-201306052000/plugins/org.eclipse.e4.core.commands_0.10.2.v20130515-1857.jar")
  val source = _root_.sbt.Artifact.classified("org.eclipse.e4.core.commands", _root_.sbt.Artifact.SourceClassifier).copy(url = Some(new URL("http://download.eclipse.org/eclipse/updates/4.3/R-4.3-201306052000/plugins/org.eclipse.e4.core.commands.source_0.10.2.v20130515-1857.jar")))
  moduleId.copy(explicitArtifacts = moduleId.explicitArtifacts :+ source)
}

libraryDependencies += {
  val moduleId = ("org.eclipse.e4.core.contexts" % "org.eclipse.e4.core.contexts" % "1.3.1.v20130905-0905" % extConfiguration from
    "http://download.eclipse.org/eclipse/updates/4.3/R-4.3.1-201309111000/plugins/org.eclipse.e4.core.contexts_1.3.1.v20130905-0905.jar")
  val source = _root_.sbt.Artifact.classified("org.eclipse.e4.core.contexts", _root_.sbt.Artifact.SourceClassifier).copy(url = Some(new URL("http://download.eclipse.org/eclipse/updates/4.3/R-4.3.1-201309111000/plugins/org.eclipse.e4.core.contexts.source_1.3.1.v20130905-0905.jar")))
  moduleId.copy(explicitArtifacts = moduleId.explicitArtifacts :+ source)
}

libraryDependencies += {
  val moduleId = ("org.eclipse.e4.core.di.extensions" % "org.eclipse.e4.core.di.extensions" % "0.11.100.v20130514-1256" % extConfiguration from
    "http://download.eclipse.org/eclipse/updates/4.3/R-4.3-201306052000/plugins/org.eclipse.e4.core.di.extensions_0.11.100.v20130514-1256.jar")
  val source = _root_.sbt.Artifact.classified("org.eclipse.e4.core.di.extensions", _root_.sbt.Artifact.SourceClassifier).copy(url = Some(new URL("http://download.eclipse.org/eclipse/updates/4.3/R-4.3-201306052000/plugins/org.eclipse.e4.core.di.extensions.source_0.11.100.v20130514-1256.jar")))
  moduleId.copy(explicitArtifacts = moduleId.explicitArtifacts :+ source)
}

libraryDependencies += {
  val moduleId = ("org.eclipse.e4.core.di" % "org.eclipse.e4.core.di" % "1.3.0.v20130514-1256" % extConfiguration from
    "http://download.eclipse.org/eclipse/updates/4.3/R-4.3-201306052000/plugins/org.eclipse.e4.core.di_1.3.0.v20130514-1256.jar")
  val source = _root_.sbt.Artifact.classified("org.eclipse.e4.core.di", _root_.sbt.Artifact.SourceClassifier).copy(url = Some(new URL("http://download.eclipse.org/eclipse/updates/4.3/R-4.3-201306052000/plugins/org.eclipse.e4.core.di.source_1.3.0.v20130514-1256.jar")))
  moduleId.copy(explicitArtifacts = moduleId.explicitArtifacts :+ source)
}

libraryDependencies += {
  val moduleId = ("org.eclipse.e4.core.services" % "org.eclipse.e4.core.services" % "1.1.0.v20130515-1343" % extConfiguration from
    "http://download.eclipse.org/eclipse/updates/4.3/R-4.3-201306052000/plugins/org.eclipse.e4.core.services_1.1.0.v20130515-1343.jar")
  val source = _root_.sbt.Artifact.classified("org.eclipse.e4.core.services", _root_.sbt.Artifact.SourceClassifier).copy(url = Some(new URL("http://download.eclipse.org/eclipse/updates/4.3/R-4.3-201306052000/plugins/org.eclipse.e4.core.services.source_1.1.0.v20130515-1343.jar")))
  moduleId.copy(explicitArtifacts = moduleId.explicitArtifacts :+ source)
}

libraryDependencies += {
  val moduleId = ("org.eclipse.e4.ui.bindings" % "org.eclipse.e4.ui.bindings" % "0.10.102.v20140117-1939" % extConfiguration from
    "http://download.eclipse.org/eclipse/updates/4.3/R-4.3.2-201402211700/plugins/org.eclipse.e4.ui.bindings_0.10.102.v20140117-1939.jar")
  val source = _root_.sbt.Artifact.classified("org.eclipse.e4.ui.bindings", _root_.sbt.Artifact.SourceClassifier).copy(url = Some(new URL("http://download.eclipse.org/eclipse/updates/4.3/R-4.3.2-201402211700/plugins/org.eclipse.e4.ui.bindings.source_0.10.102.v20140117-1939.jar")))
  moduleId.copy(explicitArtifacts = moduleId.explicitArtifacts :+ source)
}

libraryDependencies += {
  val moduleId = ("org.eclipse.e4.ui.css.core" % "org.eclipse.e4.ui.css.core" % "0.10.100.v20130515-1857" % extConfiguration from
    "http://download.eclipse.org/eclipse/updates/4.3/R-4.3-201306052000/plugins/org.eclipse.e4.ui.css.core_0.10.100.v20130515-1857.jar")
  val source = _root_.sbt.Artifact.classified("org.eclipse.e4.ui.css.core", _root_.sbt.Artifact.SourceClassifier).copy(url = Some(new URL("http://download.eclipse.org/eclipse/updates/4.3/R-4.3-201306052000/plugins/org.eclipse.e4.ui.css.core.source_0.10.100.v20130515-1857.jar")))
  moduleId.copy(explicitArtifacts = moduleId.explicitArtifacts :+ source)
}

libraryDependencies += {
  val moduleId = ("org.eclipse.e4.ui.css.swt.theme" % "org.eclipse.e4.ui.css.swt.theme" % "0.9.100.v20130515-1857" % extConfiguration from
    "http://download.eclipse.org/eclipse/updates/4.3/R-4.3-201306052000/plugins/org.eclipse.e4.ui.css.swt.theme_0.9.100.v20130515-1857.jar")
  val source = _root_.sbt.Artifact.classified("org.eclipse.e4.ui.css.swt.theme", _root_.sbt.Artifact.SourceClassifier).copy(url = Some(new URL("http://download.eclipse.org/eclipse/updates/4.3/R-4.3-201306052000/plugins/org.eclipse.e4.ui.css.swt.theme.source_0.9.100.v20130515-1857.jar")))
  moduleId.copy(explicitArtifacts = moduleId.explicitArtifacts :+ source)
}

libraryDependencies += {
  val moduleId = ("org.eclipse.e4.ui.css.swt" % "org.eclipse.e4.ui.css.swt" % "0.11.0.v20130515-1857" % extConfiguration from
    "http://download.eclipse.org/eclipse/updates/4.3/R-4.3-201306052000/plugins/org.eclipse.e4.ui.css.swt_0.11.0.v20130515-1857.jar")
  val source = _root_.sbt.Artifact.classified("org.eclipse.e4.ui.css.swt", _root_.sbt.Artifact.SourceClassifier).copy(url = Some(new URL("http://download.eclipse.org/eclipse/updates/4.3/R-4.3-201306052000/plugins/org.eclipse.e4.ui.css.swt.source_0.11.0.v20130515-1857.jar")))
  moduleId.copy(explicitArtifacts = moduleId.explicitArtifacts :+ source)
}

libraryDependencies += {
  val moduleId = ("org.eclipse.e4.ui.di" % "org.eclipse.e4.ui.di" % "1.0.0.v20130515-1857" % extConfiguration from
    "http://download.eclipse.org/eclipse/updates/4.3/R-4.3-201306052000/plugins/org.eclipse.e4.ui.di_1.0.0.v20130515-1857.jar")
  val source = _root_.sbt.Artifact.classified("org.eclipse.e4.ui.di", _root_.sbt.Artifact.SourceClassifier).copy(url = Some(new URL("http://download.eclipse.org/eclipse/updates/4.3/R-4.3-201306052000/plugins/org.eclipse.e4.ui.di.source_1.0.0.v20130515-1857.jar")))
  moduleId.copy(explicitArtifacts = moduleId.explicitArtifacts :+ source)
}

libraryDependencies += {
  val moduleId = ("org.eclipse.e4.ui.model.workbench" % "org.eclipse.e4.ui.model.workbench" % "1.0.1.v20131118-1956" % extConfiguration from
    "http://download.eclipse.org/eclipse/updates/4.3/R-4.3.2-201402211700/plugins/org.eclipse.e4.ui.model.workbench_1.0.1.v20131118-1956.jar")
  val source = _root_.sbt.Artifact.classified("org.eclipse.e4.ui.model.workbench", _root_.sbt.Artifact.SourceClassifier).copy(url = Some(new URL("http://download.eclipse.org/eclipse/updates/4.3/R-4.3.2-201402211700/plugins/org.eclipse.e4.ui.model.workbench.source_1.0.1.v20131118-1956.jar")))
  moduleId.copy(explicitArtifacts = moduleId.explicitArtifacts :+ source)
}

libraryDependencies += {
  val moduleId = ("org.eclipse.e4.ui.services" % "org.eclipse.e4.ui.services" % "1.0.1.v20131118-1940" % extConfiguration from
    "http://download.eclipse.org/eclipse/updates/4.3/R-4.3.2-201402211700/plugins/org.eclipse.e4.ui.services_1.0.1.v20131118-1940.jar")
  val source = _root_.sbt.Artifact.classified("org.eclipse.e4.ui.services", _root_.sbt.Artifact.SourceClassifier).copy(url = Some(new URL("http://download.eclipse.org/eclipse/updates/4.3/R-4.3.2-201402211700/plugins/org.eclipse.e4.ui.services.source_1.0.1.v20131118-1940.jar")))
  moduleId.copy(explicitArtifacts = moduleId.explicitArtifacts :+ source)
}

libraryDependencies += {
  val moduleId = ("org.eclipse.e4.ui.widgets" % "org.eclipse.e4.ui.widgets" % "1.0.0.v20130515-1857" % extConfiguration from
    "http://download.eclipse.org/eclipse/updates/4.3/R-4.3-201306052000/plugins/org.eclipse.e4.ui.widgets_1.0.0.v20130515-1857.jar")
  val source = _root_.sbt.Artifact.classified("org.eclipse.e4.ui.widgets", _root_.sbt.Artifact.SourceClassifier).copy(url = Some(new URL("http://download.eclipse.org/eclipse/updates/4.3/R-4.3-201306052000/plugins/org.eclipse.e4.ui.widgets.source_1.0.0.v20130515-1857.jar")))
  moduleId.copy(explicitArtifacts = moduleId.explicitArtifacts :+ source)
}

libraryDependencies += {
  val moduleId = ("org.eclipse.e4.ui.workbench.addons.swt" % "org.eclipse.e4.ui.workbench.addons.swt" % "1.0.2.v20131129-1621" % extConfiguration from
    "http://download.eclipse.org/eclipse/updates/4.3/R-4.3.2-201402211700/plugins/org.eclipse.e4.ui.workbench.addons.swt_1.0.2.v20131129-1621.jar")
  val source = _root_.sbt.Artifact.classified("org.eclipse.e4.ui.workbench.addons.swt", _root_.sbt.Artifact.SourceClassifier).copy(url = Some(new URL("http://download.eclipse.org/eclipse/updates/4.3/R-4.3.2-201402211700/plugins/org.eclipse.e4.ui.workbench.addons.swt.source_1.0.2.v20131129-1621.jar")))
  moduleId.copy(explicitArtifacts = moduleId.explicitArtifacts :+ source)
}

libraryDependencies += {
  val moduleId = ("org.eclipse.e4.ui.workbench.renderers.swt" % "org.eclipse.e4.ui.workbench.renderers.swt" % "0.11.2.v20140205-1834" % extConfiguration from
    "http://download.eclipse.org/eclipse/updates/4.3/R-4.3.2-201402211700/plugins/org.eclipse.e4.ui.workbench.renderers.swt_0.11.2.v20140205-1834.jar")
  val source = _root_.sbt.Artifact.classified("org.eclipse.e4.ui.workbench.renderers.swt", _root_.sbt.Artifact.SourceClassifier).copy(url = Some(new URL("http://download.eclipse.org/eclipse/updates/4.3/R-4.3.2-201402211700/plugins/org.eclipse.e4.ui.workbench.renderers.swt.source_0.11.2.v20140205-1834.jar")))
  moduleId.copy(explicitArtifacts = moduleId.explicitArtifacts :+ source)
}

libraryDependencies += {
  val moduleId = ("org.eclipse.e4.ui.workbench.swt" % "org.eclipse.e4.ui.workbench.swt" % "0.12.2.v20140117-1939" % extConfiguration from
    "http://download.eclipse.org/eclipse/updates/4.3/R-4.3.2-201402211700/plugins/org.eclipse.e4.ui.workbench.swt_0.12.2.v20140117-1939.jar")
  val source = _root_.sbt.Artifact.classified("org.eclipse.e4.ui.workbench.swt", _root_.sbt.Artifact.SourceClassifier).copy(url = Some(new URL("http://download.eclipse.org/eclipse/updates/4.3/R-4.3.2-201402211700/plugins/org.eclipse.e4.ui.workbench.swt.source_0.12.2.v20140117-1939.jar")))
  moduleId.copy(explicitArtifacts = moduleId.explicitArtifacts :+ source)
}

libraryDependencies += {
  val moduleId = ("org.eclipse.e4.ui.workbench3" % "org.eclipse.e4.ui.workbench3" % "0.12.0.v20130515-1857" % extConfiguration from
    "http://download.eclipse.org/eclipse/updates/4.3/R-4.3-201306052000/plugins/org.eclipse.e4.ui.workbench3_0.12.0.v20130515-1857.jar")
  val source = _root_.sbt.Artifact.classified("org.eclipse.e4.ui.workbench3", _root_.sbt.Artifact.SourceClassifier).copy(url = Some(new URL("http://download.eclipse.org/eclipse/updates/4.3/R-4.3-201306052000/plugins/org.eclipse.e4.ui.workbench3.source_0.12.0.v20130515-1857.jar")))
  moduleId.copy(explicitArtifacts = moduleId.explicitArtifacts :+ source)
}

libraryDependencies += {
  val moduleId = ("org.eclipse.e4.ui.workbench" % "org.eclipse.e4.ui.workbench" % "1.0.2.v20131202-1739" % extConfiguration from
    "http://download.eclipse.org/eclipse/updates/4.3/R-4.3.2-201402211700/plugins/org.eclipse.e4.ui.workbench_1.0.2.v20131202-1739.jar")
  val source = _root_.sbt.Artifact.classified("org.eclipse.e4.ui.workbench", _root_.sbt.Artifact.SourceClassifier).copy(url = Some(new URL("http://download.eclipse.org/eclipse/updates/4.3/R-4.3.2-201402211700/plugins/org.eclipse.e4.ui.workbench.source_1.0.2.v20131202-1739.jar")))
  moduleId.copy(explicitArtifacts = moduleId.explicitArtifacts :+ source)
}

libraryDependencies += {
  val moduleId = ("org.eclipse.emf.common" % "org.eclipse.emf.common" % "2.9.2.v20131212-0545" % extConfiguration from
    "http://download.eclipse.org/eclipse/updates/4.3/R-4.3.2-201402211700/plugins/org.eclipse.emf.common_2.9.2.v20131212-0545.jar")
  val source = _root_.sbt.Artifact.classified("org.eclipse.emf.common", _root_.sbt.Artifact.SourceClassifier).copy(url = Some(new URL("http://download.eclipse.org/eclipse/updates/4.3/R-4.3.2-201402211700/plugins/org.eclipse.emf.common.source_2.9.2.v20131212-0545.jar")))
  moduleId.copy(explicitArtifacts = moduleId.explicitArtifacts :+ source)
}

libraryDependencies += {
  val moduleId = ("org.eclipse.emf.ecore.change" % "org.eclipse.emf.ecore.change" % "2.9.0.v20131212-0545" % extConfiguration from
    "http://download.eclipse.org/eclipse/updates/4.3/R-4.3.2-201402211700/plugins/org.eclipse.emf.ecore.change_2.9.0.v20131212-0545.jar")
  val source = _root_.sbt.Artifact.classified("org.eclipse.emf.ecore.change", _root_.sbt.Artifact.SourceClassifier).copy(url = Some(new URL("http://download.eclipse.org/eclipse/updates/4.3/R-4.3.2-201402211700/plugins/org.eclipse.emf.ecore.change.source_2.9.0.v20131212-0545.jar")))
  moduleId.copy(explicitArtifacts = moduleId.explicitArtifacts :+ source)
}

libraryDependencies += {
  val moduleId = ("org.eclipse.emf.ecore.xmi" % "org.eclipse.emf.ecore.xmi" % "2.9.1.v20131212-0545" % extConfiguration from
    "http://download.eclipse.org/eclipse/updates/4.3/R-4.3.2-201402211700/plugins/org.eclipse.emf.ecore.xmi_2.9.1.v20131212-0545.jar")
  val source = _root_.sbt.Artifact.classified("org.eclipse.emf.ecore.xmi", _root_.sbt.Artifact.SourceClassifier).copy(url = Some(new URL("http://download.eclipse.org/eclipse/updates/4.3/R-4.3.2-201402211700/plugins/org.eclipse.emf.ecore.xmi.source_2.9.1.v20131212-0545.jar")))
  moduleId.copy(explicitArtifacts = moduleId.explicitArtifacts :+ source)
}

libraryDependencies += {
  val moduleId = ("org.eclipse.emf.ecore" % "org.eclipse.emf.ecore" % "2.9.2.v20131212-0545" % extConfiguration from
    "http://download.eclipse.org/eclipse/updates/4.3/R-4.3.2-201402211700/plugins/org.eclipse.emf.ecore_2.9.2.v20131212-0545.jar")
  val source = _root_.sbt.Artifact.classified("org.eclipse.emf.ecore", _root_.sbt.Artifact.SourceClassifier).copy(url = Some(new URL("http://download.eclipse.org/eclipse/updates/4.3/R-4.3.2-201402211700/plugins/org.eclipse.emf.ecore.source_2.9.2.v20131212-0545.jar")))
  moduleId.copy(explicitArtifacts = moduleId.explicitArtifacts :+ source)
}

libraryDependencies += {
  val moduleId = ("org.eclipse.equinox.app" % "org.eclipse.equinox.app" % "1.3.100.v20130327-1442" % extConfiguration from
    "http://download.eclipse.org/eclipse/updates/4.3/R-4.3-201306052000/plugins/org.eclipse.equinox.app_1.3.100.v20130327-1442.jar")
  val source = _root_.sbt.Artifact.classified("org.eclipse.equinox.app", _root_.sbt.Artifact.SourceClassifier).copy(url = Some(new URL("http://download.eclipse.org/eclipse/updates/4.3/R-4.3-201306052000/plugins/org.eclipse.equinox.app.source_1.3.100.v20130327-1442.jar")))
  moduleId.copy(explicitArtifacts = moduleId.explicitArtifacts :+ source)
}

libraryDependencies += {
  val moduleId = ("org.eclipse.equinox.common" % "org.eclipse.equinox.common" % "3.6.200.v20130402-1505" % extConfiguration from
    "http://download.eclipse.org/eclipse/updates/4.3/R-4.3-201306052000/plugins/org.eclipse.equinox.common_3.6.200.v20130402-1505.jar")
  val source = _root_.sbt.Artifact.classified("org.eclipse.equinox.common", _root_.sbt.Artifact.SourceClassifier).copy(url = Some(new URL("http://download.eclipse.org/eclipse/updates/4.3/R-4.3-201306052000/plugins/org.eclipse.equinox.common.source_3.6.200.v20130402-1505.jar")))
  moduleId.copy(explicitArtifacts = moduleId.explicitArtifacts :+ source)
}

libraryDependencies += {
  val moduleId = ("org.eclipse.equinox.ds" % "org.eclipse.equinox.ds" % "1.4.101.v20130813-1853" % extConfiguration from
    "http://download.eclipse.org/eclipse/updates/4.3/R-4.3.1-201309111000/plugins/org.eclipse.equinox.ds_1.4.101.v20130813-1853.jar")
  val source = _root_.sbt.Artifact.classified("org.eclipse.equinox.ds", _root_.sbt.Artifact.SourceClassifier).copy(url = Some(new URL("http://download.eclipse.org/eclipse/updates/4.3/R-4.3.1-201309111000/plugins/org.eclipse.equinox.ds.source_1.4.101.v20130813-1853.jar")))
  moduleId.copy(explicitArtifacts = moduleId.explicitArtifacts :+ source)
}

libraryDependencies += {
  val moduleId = ("org.eclipse.equinox.event" % "org.eclipse.equinox.event" % "1.3.0.v20130327-1442" % extConfiguration from
    "http://download.eclipse.org/eclipse/updates/4.3/R-4.3-201306052000/plugins/org.eclipse.equinox.event_1.3.0.v20130327-1442.jar")
  val source = _root_.sbt.Artifact.classified("org.eclipse.equinox.event", _root_.sbt.Artifact.SourceClassifier).copy(url = Some(new URL("http://download.eclipse.org/eclipse/updates/4.3/R-4.3-201306052000/plugins/org.eclipse.equinox.event.source_1.3.0.v20130327-1442.jar")))
  moduleId.copy(explicitArtifacts = moduleId.explicitArtifacts :+ source)
}

libraryDependencies += {
  val moduleId = ("org.eclipse.equinox.preferences" % "org.eclipse.equinox.preferences" % "3.5.100.v20130422-1538" % extConfiguration from
    "http://download.eclipse.org/eclipse/updates/4.3/R-4.3-201306052000/plugins/org.eclipse.equinox.preferences_3.5.100.v20130422-1538.jar")
  val source = _root_.sbt.Artifact.classified("org.eclipse.equinox.preferences", _root_.sbt.Artifact.SourceClassifier).copy(url = Some(new URL("http://download.eclipse.org/eclipse/updates/4.3/R-4.3-201306052000/plugins/org.eclipse.equinox.preferences.source_3.5.100.v20130422-1538.jar")))
  moduleId.copy(explicitArtifacts = moduleId.explicitArtifacts :+ source)
}

libraryDependencies += {
  val moduleId = ("org.eclipse.equinox.registry" % "org.eclipse.equinox.registry" % "3.5.301.v20130717-1549" % extConfiguration from
    "http://download.eclipse.org/eclipse/updates/4.3/R-4.3.1-201309111000/plugins/org.eclipse.equinox.registry_3.5.301.v20130717-1549.jar")
  val source = _root_.sbt.Artifact.classified("org.eclipse.equinox.registry", _root_.sbt.Artifact.SourceClassifier).copy(url = Some(new URL("http://download.eclipse.org/eclipse/updates/4.3/R-4.3.1-201309111000/plugins/org.eclipse.equinox.registry.source_3.5.301.v20130717-1549.jar")))
  moduleId.copy(explicitArtifacts = moduleId.explicitArtifacts :+ source)
}

libraryDependencies += {
  val moduleId = ("org.eclipse.equinox.util" % "org.eclipse.equinox.util" % "1.0.500.v20130404-1337" % extConfiguration from
    "http://download.eclipse.org/eclipse/updates/4.3/R-4.3-201306052000/plugins/org.eclipse.equinox.util_1.0.500.v20130404-1337.jar")
  val source = _root_.sbt.Artifact.classified("org.eclipse.equinox.util", _root_.sbt.Artifact.SourceClassifier).copy(url = Some(new URL("http://download.eclipse.org/eclipse/updates/4.3/R-4.3-201306052000/plugins/org.eclipse.equinox.util.source_1.0.500.v20130404-1337.jar")))
  moduleId.copy(explicitArtifacts = moduleId.explicitArtifacts :+ source)
}

libraryDependencies += {
  val moduleId = ("org.eclipse.help" % "org.eclipse.help" % "3.6.0.v20130326-1254" % extConfiguration from
    "http://download.eclipse.org/eclipse/updates/4.3/R-4.3-201306052000/plugins/org.eclipse.help_3.6.0.v20130326-1254.jar")
  val source = _root_.sbt.Artifact.classified("org.eclipse.help", _root_.sbt.Artifact.SourceClassifier).copy(url = Some(new URL("http://download.eclipse.org/eclipse/updates/4.3/R-4.3-201306052000/plugins/org.eclipse.help.source_3.6.0.v20130326-1254.jar")))
  moduleId.copy(explicitArtifacts = moduleId.explicitArtifacts :+ source)
}

libraryDependencies += {
  val moduleId = ("org.eclipse.jface.databinding" % "org.eclipse.jface.databinding" % "1.6.200.v20130515-1857" % extConfiguration from
    "http://download.eclipse.org/eclipse/updates/4.3/R-4.3-201306052000/plugins/org.eclipse.jface.databinding_1.6.200.v20130515-1857.jar")
  val source = _root_.sbt.Artifact.classified("org.eclipse.jface.databinding", _root_.sbt.Artifact.SourceClassifier).copy(url = Some(new URL("http://download.eclipse.org/eclipse/updates/4.3/R-4.3-201306052000/plugins/org.eclipse.jface.databinding.source_1.6.200.v20130515-1857.jar")))
  moduleId.copy(explicitArtifacts = moduleId.explicitArtifacts :+ source)
}

libraryDependencies += {
  val moduleId = ("org.eclipse.jface.text" % "org.eclipse.jface.text" % "3.8.101.v20130802-1147" % extConfiguration from
    "http://download.eclipse.org/eclipse/updates/4.3/R-4.3.1-201309111000/plugins/org.eclipse.jface.text_3.8.101.v20130802-1147.jar")
  val source = _root_.sbt.Artifact.classified("org.eclipse.jface.text", _root_.sbt.Artifact.SourceClassifier).copy(url = Some(new URL("http://download.eclipse.org/eclipse/updates/4.3/R-4.3.1-201309111000/plugins/org.eclipse.jface.text.source_3.8.101.v20130802-1147.jar")))
  moduleId.copy(explicitArtifacts = moduleId.explicitArtifacts :+ source)
}

libraryDependencies += {
  val moduleId = ("org.eclipse.jface" % "org.eclipse.jface" % "3.9.1.v20130725-1141" % extConfiguration from
    "http://download.eclipse.org/eclipse/updates/4.3/R-4.3.1-201309111000/plugins/org.eclipse.jface_3.9.1.v20130725-1141.jar")
  val source = _root_.sbt.Artifact.classified("org.eclipse.jface", _root_.sbt.Artifact.SourceClassifier).copy(url = Some(new URL("http://download.eclipse.org/eclipse/updates/4.3/R-4.3.1-201309111000/plugins/org.eclipse.jface.source_3.9.1.v20130725-1141.jar")))
  moduleId.copy(explicitArtifacts = moduleId.explicitArtifacts :+ source)
}

libraryDependencies += {
  val moduleId = ("org.eclipse.nebula.widgets.gallery" % "org.eclipse.nebula.widgets.gallery" % "0.6.0.201409040043" % extConfiguration from
    "http://download.eclipse.org/technology/nebula/archives/Q32014/release/plugins/org.eclipse.nebula.widgets.gallery_0.6.0.201409040043.jar")
  val source = _root_.sbt.Artifact.classified("org.eclipse.nebula.widgets.gallery", _root_.sbt.Artifact.SourceClassifier).copy(url = Some(new URL("http://download.eclipse.org/technology/nebula/archives/Q32014/release/plugins/org.eclipse.nebula.widgets.gallery.source_0.6.0.201409040043.jar")))
  moduleId.copy(explicitArtifacts = moduleId.explicitArtifacts :+ source)
}

libraryDependencies += {
  val moduleId = ("org.eclipse.osgi.services" % "org.eclipse.osgi.services" % "3.3.100.v20130513-1956" % extConfiguration from
    "http://download.eclipse.org/eclipse/updates/4.3/R-4.3-201306052000/plugins/org.eclipse.osgi.services_3.3.100.v20130513-1956.jar")
  val source = _root_.sbt.Artifact.classified("org.eclipse.osgi.services", _root_.sbt.Artifact.SourceClassifier).copy(url = Some(new URL("http://download.eclipse.org/eclipse/updates/4.3/R-4.3-201306052000/plugins/org.eclipse.osgi.services.source_3.3.100.v20130513-1956.jar")))
  moduleId.copy(explicitArtifacts = moduleId.explicitArtifacts :+ source)
}

libraryDependencies += {
  val moduleId = ("org.eclipse.osgi" % "org.eclipse.osgi" % "3.9.1.v20140110-1610" % extConfiguration from
    "http://download.eclipse.org/eclipse/updates/4.3/R-4.3.2-201402211700/plugins/org.eclipse.osgi_3.9.1.v20140110-1610.jar")
  val source = _root_.sbt.Artifact.classified("org.eclipse.osgi", _root_.sbt.Artifact.SourceClassifier).copy(url = Some(new URL("http://download.eclipse.org/eclipse/updates/4.3/R-4.3.2-201402211700/plugins/org.eclipse.osgi.source_3.9.1.v20140110-1610.jar")))
  moduleId.copy(explicitArtifacts = moduleId.explicitArtifacts :+ source)
}

libraryDependencies += {
  val moduleId = ("org.eclipse.swt.gtk.linux.x86_64" % "org.eclipse.swt.gtk.linux.x86_64" % "3.102.1.v20140206-1358" % extConfiguration from
    "http://download.eclipse.org/eclipse/updates/4.3/R-4.3.2-201402211700/plugins/org.eclipse.swt.gtk.linux.x86_64_3.102.1.v20140206-1358.jar")
  moduleId
}

libraryDependencies += {
  val moduleId = ("org.eclipse.swt" % "org.eclipse.swt" % "3.102.1.v20140206-1334" % extConfiguration from
    "http://download.eclipse.org/eclipse/updates/4.3/R-4.3.2-201402211700/plugins/org.eclipse.swt_3.102.1.v20140206-1334.jar")
  moduleId
}

libraryDependencies += {
  val moduleId = ("org.eclipse.text" % "org.eclipse.text" % "3.5.300.v20130515-1451" % extConfiguration from
    "http://download.eclipse.org/eclipse/updates/4.3/R-4.3-201306052000/plugins/org.eclipse.text_3.5.300.v20130515-1451.jar")
  val source = _root_.sbt.Artifact.classified("org.eclipse.text", _root_.sbt.Artifact.SourceClassifier).copy(url = Some(new URL("http://download.eclipse.org/eclipse/updates/4.3/R-4.3-201306052000/plugins/org.eclipse.text.source_3.5.300.v20130515-1451.jar")))
  moduleId.copy(explicitArtifacts = moduleId.explicitArtifacts :+ source)
}

libraryDependencies += {
  val moduleId = ("org.eclipse.ui.console" % "org.eclipse.ui.console" % "3.5.200.v20130514-0954" % extConfiguration from
    "http://download.eclipse.org/eclipse/updates/4.3/R-4.3-201306052000/plugins/org.eclipse.ui.console_3.5.200.v20130514-0954.jar")
  val source = _root_.sbt.Artifact.classified("org.eclipse.ui.console", _root_.sbt.Artifact.SourceClassifier).copy(url = Some(new URL("http://download.eclipse.org/eclipse/updates/4.3/R-4.3-201306052000/plugins/org.eclipse.ui.console.source_3.5.200.v20130514-0954.jar")))
  moduleId.copy(explicitArtifacts = moduleId.explicitArtifacts :+ source)
}

libraryDependencies += {
  val moduleId = ("org.eclipse.ui.forms" % "org.eclipse.ui.forms" % "3.6.1.v20130822-1117" % extConfiguration from
    "http://download.eclipse.org/eclipse/updates/4.3/R-4.3.1-201309111000/plugins/org.eclipse.ui.forms_3.6.1.v20130822-1117.jar")
  val source = _root_.sbt.Artifact.classified("org.eclipse.ui.forms", _root_.sbt.Artifact.SourceClassifier).copy(url = Some(new URL("http://download.eclipse.org/eclipse/updates/4.3/R-4.3.1-201309111000/plugins/org.eclipse.ui.forms.source_3.6.1.v20130822-1117.jar")))
  moduleId.copy(explicitArtifacts = moduleId.explicitArtifacts :+ source)
}

libraryDependencies += {
  val moduleId = ("org.eclipse.ui.workbench.texteditor" % "org.eclipse.ui.workbench.texteditor" % "3.8.101.v20130729-1318" % extConfiguration from
    "http://download.eclipse.org/eclipse/updates/4.3/R-4.3.1-201309111000/plugins/org.eclipse.ui.workbench.texteditor_3.8.101.v20130729-1318.jar")
  val source = _root_.sbt.Artifact.classified("org.eclipse.ui.workbench.texteditor", _root_.sbt.Artifact.SourceClassifier).copy(url = Some(new URL("http://download.eclipse.org/eclipse/updates/4.3/R-4.3.1-201309111000/plugins/org.eclipse.ui.workbench.texteditor.source_3.8.101.v20130729-1318.jar")))
  moduleId.copy(explicitArtifacts = moduleId.explicitArtifacts :+ source)
}

libraryDependencies += {
  val moduleId = ("org.eclipse.ui.workbench" % "org.eclipse.ui.workbench" % "3.105.2.v20140211-1711" % extConfiguration from
    "http://download.eclipse.org/eclipse/updates/4.3/R-4.3.2-201402211700/plugins/org.eclipse.ui.workbench_3.105.2.v20140211-1711.jar")
  val source = _root_.sbt.Artifact.classified("org.eclipse.ui.workbench", _root_.sbt.Artifact.SourceClassifier).copy(url = Some(new URL("http://download.eclipse.org/eclipse/updates/4.3/R-4.3.2-201402211700/plugins/org.eclipse.ui.workbench.source_3.105.2.v20140211-1711.jar")))
  moduleId.copy(explicitArtifacts = moduleId.explicitArtifacts :+ source)
}

libraryDependencies += {
  val moduleId = ("org.eclipse.ui" % "org.eclipse.ui" % "3.105.0.v20130522-1122" % extConfiguration from
    "http://download.eclipse.org/eclipse/updates/4.3/R-4.3-201306052000/plugins/org.eclipse.ui_3.105.0.v20130522-1122.jar")
  val source = _root_.sbt.Artifact.classified("org.eclipse.ui", _root_.sbt.Artifact.SourceClassifier).copy(url = Some(new URL("http://download.eclipse.org/eclipse/updates/4.3/R-4.3-201306052000/plugins/org.eclipse.ui.source_3.105.0.v20130522-1122.jar")))
  moduleId.copy(explicitArtifacts = moduleId.explicitArtifacts :+ source)
}

libraryDependencies += {
  val moduleId = ("org.w3c.css.sac" % "org.w3c.css.sac" % "1.3.1.v200903091627" % extConfiguration from
    "http://download.eclipse.org/eclipse/updates/4.3/R-4.3-201306052000/plugins/org.w3c.css.sac_1.3.1.v200903091627.jar")
  moduleId
}

libraryDependencies += {
  val moduleId = ("org.w3c.dom.smil" % "org.w3c.dom.smil" % "1.0.0.v200806040011" % extConfiguration from
    "http://download.eclipse.org/eclipse/updates/4.3/R-4.3-201306052000/plugins/org.w3c.dom.smil_1.0.0.v200806040011.jar")
  moduleId
}

libraryDependencies += {
  val moduleId = ("org.w3c.dom.svg" % "org.w3c.dom.svg" % "1.1.0.v201011041433" % extConfiguration from
    "http://download.eclipse.org/eclipse/updates/4.3/R-4.3-201306052000/plugins/org.w3c.dom.svg_1.1.0.v201011041433.jar")
  moduleId
}

