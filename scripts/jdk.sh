#!/bin/sh
# JDK utilities for Minecraft server
# Provides: download_jdk, detect_java_version

# Usage: download_jdk <jdk_version>
# Downloads Eclipse Temurin JDK to /usr/lib/jvm/<version>/
download_jdk() {
  local jdk_version="$1"
  local jdk_dir="/usr/lib/jvm/${jdk_version}"

  if [ -d "$jdk_dir" ] && [ -x "$jdk_dir/bin/java" ]; then
    echo "[info] JDK $jdk_version already present, skipping download."
    return
  fi

  echo "[info] Downloading JDK $jdk_version..."
  mkdir -p "$jdk_dir"
  wget -q -O /tmp/jdk.tar.gz \
    "https://api.adoptium.net/v3/binary/latest/${jdk_version}/ga/linux/x64/jdk/hotspot/normal/eclipse"
  tar -xzf /tmp/jdk.tar.gz -C "$jdk_dir" --strip-components=1
  rm /tmp/jdk.tar.gz
  echo "[info] JDK $jdk_version installed to $jdk_dir"
}

# Usage: java_version=$(detect_java_version "$MC_VERSION" "$SERVER_TYPE")
#
# Priority:
#   1. JAVA_VERSION explicitly set (and not "auto") → use as override
#   2. JAVA_VERSION unset or "auto" + MC_VERSION set → map from MC_VERSION
#   3. Neither → fallback to per-type defaults (backward compatibility)
#
# Mapping rules:
#   1.7.x ~ 1.16.x → Java 8
#   1.17.x         → Java 16
#   1.18.x ~ 1.20.4 → Java 17
#   1.20.5+        → Java 21
detect_java_version() {
  mc_version="${1:-}"
  server_type="${2:-vanilla}"

  # Priority 1: explicit override (user set JAVA_VERSION to a specific number)
  if [ -n "${JAVA_VERSION:-}" ] && [ "$JAVA_VERSION" != "auto" ]; then
    echo "$JAVA_VERSION"
    return
  fi

  # Priority 2: auto-detect from MC_VERSION
  if [ -n "$mc_version" ]; then
    major=$(echo "$mc_version" | cut -d. -f2)
    patch=$(echo "$mc_version" | cut -d. -f3)
    patch="${patch:-0}"

    if [ -n "$major" ]; then
      if [ "$major" -le 16 ]; then
        # 1.7.x ~ 1.16.x
        echo "8"
        return
      elif [ "$major" -eq 17 ]; then
        # 1.17.x
        echo "16"
        return
      elif [ "$major" -eq 18 ] || [ "$major" -eq 19 ]; then
        # 1.18.x ~ 1.19.x
        echo "17"
        return
      elif [ "$major" -eq 20 ]; then
        if [ "$patch" -ge 5 ]; then
          # 1.20.5+
          echo "21"
        else
          # 1.20.0 ~ 1.20.4
          echo "17"
        fi
        return
      else
        # 1.21+
        echo "21"
        return
      fi
    fi
  fi

  # Priority 3: fallback to per-type defaults (backward compatibility)
  case "$server_type" in
    vanilla) echo "21" ;;
    forge|fabric) echo "17" ;;
    *) echo "21" ;;
  esac
}
