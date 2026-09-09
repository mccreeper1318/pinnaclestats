#!/usr/bin/env bash
set -euo pipefail

project_version="$(./gradlew properties -q --no-daemon | sed -n 's/^version: //p')"
jar_path="${1:-$(find build/libs -maxdepth 1 -type f -name 'PinnacleStats-*.jar' ! -name '*-plain.jar' -print -quit)}"

if [[ -z "$jar_path" || ! -f "$jar_path" ]]; then
    echo "No PinnacleStats JAR was produced." >&2
    exit 1
fi

expected_jar_name="PinnacleStats-${project_version}.jar"
actual_jar_name="$(basename "$jar_path")"
if [[ "$actual_jar_name" != "$expected_jar_name" ]]; then
    echo "Expected release JAR $expected_jar_name but found $actual_jar_name." >&2
    exit 1
fi

plugin_version="$(unzip -p "$jar_path" plugin.yml | sed -n 's/^version:[[:space:]]*//p' | head -n 1 | tr -d "\"'")"
if [[ "$plugin_version" != "$project_version" ]]; then
    echo "plugin.yml version $plugin_version does not match Gradle version $project_version." >&2
    exit 1
fi

javap_output="$(javap -classpath "$jar_path" -verbose io.github.mccreeper1318.pinnaclestats.PinnacleStatsPlugin)"
if ! grep -Fq "major version: 69" <<<"$javap_output"; then
    echo "PinnacleStatsPlugin is not compiled as Java 25 bytecode (major version 69)." >&2
    exit 1
fi

listener_descriptor="org/bukkit/plugin/PluginManager.registerEvents:(Lorg/bukkit/event/Listener;Lorg/bukkit/plugin/Plugin;)V"
if ! grep -Fq "$listener_descriptor" <<<"$javap_output"; then
    echo "PinnacleStatsPlugin does not contain the expected Paper/Bukkit listener registration descriptor." >&2
    exit 1
fi

echo "Verified $actual_jar_name: version $project_version, Java 25 bytecode, and listener registration descriptor."
