### Dependency

Common functions shared by several bots
are placed in the `text2img` directory in
the git repository. Run `gradle install` in
that directory to install it locally.

### Build

Run `gradle shadowJar` to make a self-contained
jar file.

You should get a small jar file in `build/libs`
named like `picsay-0.2.jar`.

### Run

Run `java -jar picsay-0.2.jar` in the command line
.

The `access_token` of matrix server should be provided using environment
variable named `TOKEN`.

### Configuration

Edit the file `config.json` to add templates.

