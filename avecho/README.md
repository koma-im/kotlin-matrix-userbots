### Dependency

Common functions shared by several bots
are placed in the `text2img` directory in
the git repository. Run `gradle install` in
that directory to install it locally.

### Build

Run `gradle shadowJar` to make a self-contained
jar file.

You should get a small jar file in `build/libs`
named like `avecho-0.0.6.jar`.

### Run

The `access_token` of matrix server should be provided using environment variable named `TOKEN`.
Run `java -jar avecho-0.0.6.jar` in the command line
.

