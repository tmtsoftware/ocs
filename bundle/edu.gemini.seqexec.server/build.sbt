

// note: inter-project dependencies are declared at the top, in projects.sbt

name := "edu.gemini.seqexec.server"

// version set in ThisBuild

unmanagedJars in Compile ++= Seq(
  new File(baseDirectory.value, "../../lib/bundle/org.scala-lang.scala-library_2.10.1.v20130302-092018-VFINAL-33e32179fd.jar"),
  new File(baseDirectory.value, "../../lib/bundle/scalaz-core_2.11-7.0.6.jar"),
  new File(baseDirectory.value, "../../lib/bundle/scalaz-concurrent_2.11-7.0.6.jar"),
  new File(baseDirectory.value, "../../lib/bundle/squants_2.11-0.6.1.jar"),
  new File(baseDirectory.value, "../../lib/bundle/org-apache-commons-httpclient_2.10-2.0.0.jar"),
  new File(baseDirectory.value, "../../lib/bundle/argonaut_2.10-6.1.jar"),
  new File(baseDirectory.value, "../../lib/bundle/monocle-core_2.10-1.1.0.jar"),
  new File(baseDirectory.value, "../../lib/bundle/monocle-macro_2.10-1.1.0.jar"),
  new File(baseDirectory.value, "../../lib/bundle/quasiquotes_2.10-2.0.1.jar"),
  new File(baseDirectory.value, "../../lib/bundle/scala-reflect-2.10.5.jar")
)

osgiSettings

ocsBundleSettings

OsgiKeys.bundleActivator := Some("edu.gemini.seqexec.server.osgi.Activator")

OsgiKeys.bundleSymbolicName := name.value

OsgiKeys.dynamicImportPackage := Seq("")

OsgiKeys.exportPackage := Seq()
