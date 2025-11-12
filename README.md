# anmo/anmo-cli

A command line tool to help managing your Anno mods so you don't have to manually update your hundreds of mods anymore.

## Features:

- Add/remove mod IDs
- Download new mods or old mods with newer versions from `mod.io`
- Extract downloaded mods to designated location while preserving IMYA files and enabled/disabled states

## Notes

As part of the extraction process, the tool will create a metadata file called `anmo.edn` in each mod folder.

This file stores mod ID and version which is used by the tool during each run to check if the mod needs to be updated
or not.

As such, if you copy and paste the mods manually, the tool will not be able to handle these mods.

It is thus highly recommended to migrate from the ingame mod manager, then run `sync` to let the tool handle the mods.

## Prerequisites

- Comfortable with command line
- Java 21+ (either JRE or JDK will suffice)
    - You can install via [scoop](https://scoop.sh/), e.g. `scoop install java/temurin21-jre`

## Installation

Download the JAR file from https://github.com/aratare-jp/anmo-cli/releases and place it somewhere, e.g.
`C:\Users\my_user_name\Downloads`

## Usage

First you need to have 2 config files, i.e. `~/.config/anmo-cli/mods-conf.edn` and `~/.config/anmo-cli/mods-list.txt`

The `mods-conf.edn` file will have the following format:

```clojure
{:game-id      4169
 :base-url     "https://u-<user-id>.modapi.io/v1"
 :api-key      "<your mod.io api key>"
 :download-dir "<where you want the tool to download the mod files>"
 :mods-dir     "<where you want the tool to extract the mod files>"
 :scan-dir     "C:\\Users\\Public\\mod.io\\4169\\mods"}
```

Explanation:

- `:game-id`: The ID assigned to the Anno game in `mod.io`. For Anno 1800 it's `4169`.
- `:base-url`: Base URL used to interact with `mod.io`. Note that you will need to replace `<user-id>` with your actual
  user ID.
- `:api-key`: The user API key generated via `mod.io`.
- `:download-dir`: Where the tool will download the mod zip files to.
- `mods-dir`: Where the tool will extract the mod zip files to. This is your Anno 1800 `mods` folder, which is
  `...\SteamLibrary\steamapps\common\Anno 1800\mods`
- `:scan-dir`: Where the tool can scan mods that are managed by the game's builtin mod manager. This is to help with
  migration from that to manual mod management.

Here's an example:

```clojure
{:game-id      4169
 :base-url     "https://u-1234567.modapi.io/v1"
 :api-key      "1234567890"
 :download-dir "C:\\Users\\foobar\\Downloads\\anno-1800-mods"
 :mods-dir     "D:\\SteamLibrary\\steamapps\\common\\Anno 1800\\mods"
 :scan-dir     "C:\\Users\\Public\\mod.io\\4169\\mods"}
```

`mods-list.txt` is a text file that contains newline-separated mod IDs. You can find the IDs of mods in the `mod.io` mod
info page on the right hand side under `# ID`. If you don't have any, just create an empty file.

Here's an example with 3 mods

```text
123456
234577
189469
```

Open up a powershell terminal and go into the folder where you downloaded the JAR file:

```
$ cd C:\Users\my_user_name\Downloads
```

Check that you have Java available:

```shell
$ java -version
openjdk version "25.0.1" 2025-10-21 LTS
OpenJDK Runtime Environment Temurin-25.0.1+8 (build 25.0.1+8-LTS)
OpenJDK 64-Bit Server VM Temurin-25.0.1+8 (build 25.0.1+8-LTS, mixed mode, sharing)
```

Now you can check the available commands:

```shell
$ java -jar .\anmo-v0.0.1.jar -h
CLI tool to manage Anno mods

Usage: java -jar anmo-cli.jar [options] action

Options:
  -h, --help

Actions:
  add MOD_ID1 MOD_ID2...
  remove MOD_ID1 MOD_ID2...
  download
  extract
  sync
  scan
```

You may see some warnings such as:

```text
WARNING: A terminally deprecated method in sun.misc.Unsafe has been called
WARNING: sun.misc.Unsafe::allocateMemory has been called by io.netty.util.internal.PlatformDependent0$2 (file:/C:/Users/foobar/Downloads/anmo-v0.0.1.jar)
WARNING: Please consider reporting this to the maintainers of class io.netty.util.internal.PlatformDependent0$2
WARNING: sun.misc.Unsafe::allocateMemory will be removed in a future release 
```

These were caused by the underlying Netty library which the tool uses to download mods in parallel.

## Scan mods

To help with setting up the `mods-list.txt` file, do:

```shell
$ java -jar .\anmo-v0.0.1.jar scan
```

This will scan the `:scan-dir` location and retrieve all the mod IDs and put them into `mods-list.txt`

## Add mods

To add a mod or multiple mods, do:

```shell
$ java -jar .\anmo-v0.0.1.jar add 123456 346778 923456
```

Note that this command simply adds those mods to `mods-list.txt`. To download and extract them, use the `sync` command.

## Remove mods

To remove a mod or multiple mods, do:

```shell
$ java -jar .\anmo-v0.0.1.jar remove 123456 346778 923456
```

Note that this command will _both_ remove the mod IDs from `mods-list.txt` _and_ remove the actual mod folders (but not
the downloaded mod zip files).

## Download mods

To download all mods, do:

```shell
$ java -jar .\anmo-v0.0.1.jar download
```

Note that this will:

- Download all newly added mods
- Download all already added mods with newer versions available

to the `:download-dir` location.

## Extract mods

To extract all mods, do:

```shell
$ java -jar .\anmo-v0.0.1.jar extract
```

This will:

- Inspect, retrieve and copy all IMYA files for any current mods that were tweaked to another temporary location
- Inspect and keep track of all disabled mods
- Extract all downloaded mod zip files to a temporary location
- Move all newly extracted mod files to the `:mods-dir` location and disable any mods that were previously disabled
- Move all IMYA files back

## Sync mods

Instead of doing separate commands for downloading and extracting, you can do:

```shell
$ java -jar .\anmo-v0.0.1.jar sync
```

This will download and then extract the files, i.e. it just executes `download` and then `extract` for convenience.

# Development

Run the project directly, via `:exec-fn`:

    $ clojure -X:run-x
    Hello, Clojure!

Run the project, overriding the name to be greeted:

    $ clojure -X:run-x :name '"Someone"'
    Hello, Someone!

Run the project directly, via `:main-opts` (`-m anmo.anmo-bot`):

    $ clojure -M:run-m
    Hello, World!

Run the project, overriding the name to be greeted:

    $ clojure -M:run-m Via-Main
    Hello, Via-Main!

Run the project's tests (they'll fail until you edit them):

    $ clojure -T:build test

Run the project's CI pipeline and build an uberjar (this will fail until you edit the tests to pass):

    $ clojure -T:build ci

This will produce an updated `pom.xml` file with synchronized dependencies inside the `META-INF`
directory inside `target/classes` and the uberjar in `target`. You can update the version (and SCM tag)
information in generated `pom.xml` by updating `build.clj`.

If you don't want the `pom.xml` file in your project, you can remove it. The `ci` task will
still generate a minimal `pom.xml` as part of the `uber` task, unless you remove `version`
from `build.clj`.

Run that uberjar:

    $ java -jar target/net.clojars.anmo/anmo-bot-0.1.0-SNAPSHOT.jar

## License

Copyright © 2024 Suppaionigiri

_EPLv1.0 is just the default for projects generated by `deps-new`: you are not_
_required to open source this project, nor are you required to use EPLv1.0!_
_Feel free to remove or change the `LICENSE` file and remove or update this_
_section of the `README.md` file!_

Distributed under the Eclipse Public License version 1.0.
