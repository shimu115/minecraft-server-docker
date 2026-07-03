#!/bin/sh
# Minecraft server version resolution
# Given MC_VERSION, queries upstream APIs to determine the download URL.
#
# Functions return the download URL on stdout, or empty string on failure.
# The caller is responsible for checking the result and falling back to
# defaults or reporting an error.

# Usage: resolve_vanilla_url <mc_version>
# Queries Mojang version manifest to find the server jar download URL.
resolve_vanilla_url() {
  mc_version="$1"
  [ -z "$mc_version" ] && return 1

  manifest=$(curl -s --max-time 30 "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json")
  [ -z "$manifest" ] && return 1

  # Extract the version detail URL for the requested MC version
  # Pattern: "id": "1.21.1", ... "url": "https://..."
  version_url=$(echo "$manifest" | grep -o "\"id\":\"$mc_version\"")
  [ -z "$version_url" ] && return 1

  # Extract the url field from the version's entry
  # Look for the id match, then grab the url value that follows
  version_url=$(echo "$manifest" \
    | tr '}' '\n' \
    | grep "\"id\":\"$mc_version\"" \
    | grep -o '"url":"[^"]*"' \
    | head -1 \
    | sed 's/"url":"//;s/"//')
  [ -z "$version_url" ] && return 1

  # Fetch the version detail to get the server download URL
  version_json=$(curl -s --max-time 30 "$version_url")
  [ -z "$version_json" ] && return 1

  server_url=$(echo "$version_json" | grep -o '"server":{[^}]*}' | grep -o '"url":"[^"]*"' | head -1 | sed 's/"url":"//;s/"//')
  echo "$server_url"
}

# Usage: resolve_forge_url <mc_version>
# Queries Forge maven metadata to find the latest installer for the MC version.
resolve_forge_url() {
  mc_version="$1"
  [ -z "$mc_version" ] && return 1

  metadata=$(curl -s --max-time 30 "https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml")
  [ -z "$metadata" ] && return 1

  # Extract <version> entries, filter those starting with MC_VERSION-, sort, pick latest
  forge_version=$(echo "$metadata" \
    | grep -o '<version>[^<]*</version>' \
    | sed 's/<version>//;s/<\/version>//' \
    | grep "^${mc_version}-" \
    | sort -V \
    | tail -1)
  [ -z "$forge_version" ] && return 1

  echo "https://maven.minecraftforge.net/net/minecraftforge/forge/${forge_version}/forge-${forge_version}-installer.jar"
}

# Usage: resolve_fabric_url <mc_version>
# Queries Fabric Meta API to find latest loader + installer, builds server/jar URL.
resolve_fabric_url() {
  mc_version="$1"
  [ -z "$mc_version" ] && return 1

  # Get latest stable loader for this MC version
  loader_json=$(curl -s --max-time 30 "https://meta.fabricmc.net/v2/versions/loader/${mc_version}")
  [ -z "$loader_json" ] && return 1

  # Find first entry with "stable":true and extract its "version"
  loader_version=$(echo "$loader_json" \
    | grep -o '"[^"]*":{"separator":"\.[^"]*","build":[0-9]*,"maven":"[^"]*","version":"[^"]*","stable":true}' \
    | grep -o '"version":"[^"]*"' \
    | head -1 \
    | sed 's/"version":"//;s/"//')
  [ -z "$loader_version" ] && return 1

  # Get latest stable installer
  installer_json=$(curl -s --max-time 30 "https://meta.fabricmc.net/v2/versions/installer")
  [ -z "$installer_json" ] && return 1

  installer_version=$(echo "$installer_json" \
    | grep -o '"[^"]*":{"url":"[^"]*","maven":"[^"]*","version":"[^"]*","stable":true}' \
    | grep -o '"version":"[^"]*"' \
    | head -1 \
    | sed 's/"version":"//;s/"//')
  [ -z "$installer_version" ] && return 1

  echo "https://meta.fabricmc.net/v2/versions/loader/${mc_version}/${loader_version}/${installer_version}/server/jar"
}

# Usage: resolve_neoforge_url <mc_version>
# Queries NeoForge maven metadata to find the latest installer for the MC version.
resolve_neoforge_url() {
  mc_version="$1"
  [ -z "$mc_version" ] && return 1

  # Convert MC version to NeoForge prefix: strip "1." prefix
  # 1.21.1 -> 21.1
  neo_prefix=$(echo "$mc_version" | sed 's/^1\.//')
  [ -z "$neo_prefix" ] && return 1

  metadata=$(curl -s --max-time 30 "https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml")
  [ -z "$metadata" ] && return 1

  # Extract versions, filter by prefix, exclude beta/alpha when possible, pick latest
  neo_version=$(echo "$metadata" \
    | grep -o '<version>[^<]*</version>' \
    | sed 's/<version>//;s/<\/version>//' \
    | grep "^${neo_prefix}\." \
    | grep -v -- '-beta\|-alpha' \
    | sort -V \
    | tail -1)

  # If no stable version found, fall back to including beta/alpha
  if [ -z "$neo_version" ]; then
    neo_version=$(echo "$metadata" \
      | grep -o '<version>[^<]*</version>' \
      | sed 's/<version>//;s/<\/version>//' \
      | grep "^${neo_prefix}\." \
      | sort -V \
      | tail -1)
  fi
  [ -z "$neo_version" ] && return 1

  echo "https://maven.neoforged.net/releases/net/neoforged/neoforge/${neo_version}/neoforge-${neo_version}-installer.jar"
}
