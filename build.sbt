lazy val api = (project in file("api"))

lazy val daemon = (project in file("daemon"))
  .dependsOn(api)
  .configs(IntegrationTest)

lazy val testtool = (project in file("testtool"))

lazy val detective = (project in file("."))
  .aggregate(api, daemon, testtool)
